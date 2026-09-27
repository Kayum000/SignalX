package com.signalx.android;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * MMC running adaptive strategy port for screen-derived Quotex candles.
 *
 * Regime selection:
 * TREND -> EMA20/EMA50 alignment
 * BREAKOUT -> previous 20-bar high/low break
 * RANGE -> Bollinger Band + RSI mean reversion
 * HIGH_VOLATILITY -> breakout only, otherwise HOLD
 *
 * The original MMC engine requires 60 closed candles. The Android screen
 * analyzer uses the candles currently visible on the chart, so it can only
 * reproduce the full strategy when at least 60 usable candles are visible.
 */
final class Candle {
    double o,h,l,c;
    Candle(double o,double h,double l,double c){this.o=o;this.h=h;this.l=l;this.c=c;}
}
final class Analysis {
    String signal,reason; int score;
    Analysis(String s,int n,String r){signal=s;score=n;reason=r;}
}

public final class ChartFrameAnalyzer {
    static boolean green(int r,int g,int b){
        int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
        return mx>70 && g>r*1.18 && g>b*1.04 && mx-mn>28;
    }
    static boolean red(int r,int g,int b){
        int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
        return mx>70 && r>g*1.18 && r>b*1.04 && mx-mn>28;
    }

    static ArrayList<Candle> extract(ByteBuffer p,int w,int h,int stride,int pix){
        int y0=(int)(h*.20), y1=(int)(h*.72), x0=(int)(w*.02), x1=(int)(w*.98);
        ArrayList<Integer> xs=new ArrayList<>();

        for(int x=x0;x<x1;x+=2){
            int n=0;
            for(int y=y0;y<y1;y+=3){
                int i=y*stride+x*pix;
                if(i<0||i+2>=p.limit()) continue;
                int r=p.get(i)&255,g=p.get(i+1)&255,b=p.get(i+2)&255;
                if(green(r,g,b)||red(r,g,b)) n++;
            }
            if(n>=3) xs.add(x);
        }

        ArrayList<int[]> groups=new ArrayList<>();
        if(!xs.isEmpty()){
            int s=xs.get(0),last=s;
            for(int x:xs){
                if(x-last>3){groups.add(new int[]{s,last});s=x;}
                last=x;
            }
            groups.add(new int[]{s,last});
        }

        ArrayList<Candle> out=new ArrayList<>();
        for(int[] z:groups){
            if(z[1]-z[0]<2||z[1]-z[0]>34)continue;
            int min=y1,max=y0,gn=0,rn=0;
            for(int x=z[0];x<=z[1];x++){
                for(int y=y0;y<y1;y+=2){
                    int i=y*stride+x*pix;
                    if(i<0||i+2>=p.limit())continue;
                    int r=p.get(i)&255,g=p.get(i+1)&255,b=p.get(i+2)&255;
                    if(green(r,g,b)){gn++;min=Math.min(min,y);max=Math.max(max,y);}
                    else if(red(r,g,b)){rn++;min=Math.min(min,y);max=Math.max(max,y);}
                }
            }
            int total=gn+rn;
            if(max-min<4||total<8)continue;

            double high=h-max,low=h-min,mid=(high+low)/2.0;
            double body=Math.max(2,(max-min)*.55);
            boolean up=gn>=rn;
            double o=up?mid-body/2:mid+body/2;
            double c=up?mid+body/2:mid-body/2;
            out.add(new Candle(o,high,low,c));
        }

        while(out.size()>80)out.remove(0);
        return out;
    }

    static Analysis signal(ArrayList<Candle> c){
        // Exact MMC normal strategy uses 60 closed candles.
        if(c.size()<60)
            return new Analysis("WAIT",0,"MMC Strategy: 60টি closed candle history দরকার ("+c.size()+"টি পাওয়া গেছে)।");

        int n=c.size()-1;
        double close=c.get(n).c;
        double ema20=ema(c,20), ema50=ema(c,50);
        double atr14=atr(c,14,n);
        double atrPct=close==0?0:atr14/close;
        double emaGap=close==0?0:Math.abs(ema20-ema50)/close;
        double emaSlope=Math.abs(ema(c,20,n)-ema(c,20,n-5))/Math.max(Math.abs(ema(c,20,n-5)),1e-9);
        double[] bb=bb(c,20,n);
        double bbWidth=bb[2];
        double rsi14=rsi(c,14,n);

        String regime;
        if(Double.isNaN(atrPct)||Double.isNaN(bbWidth)||Double.isNaN(emaGap))
            regime="UNKNOWN";
        else if(atrPct>=0.0025)
            regime="HIGH_VOLATILITY";
        else if(emaGap>=0.00055 && emaSlope>=0.00020)
            regime="TREND";
        else if(bbWidth<=0.0018 && atrPct<=0.0012)
            regime="RANGE";
        else if(bbWidth>=0.0030 || emaSlope>=0.00035)
            regime="BREAKOUT";
        else
            regime="UNCLEAR";

        if("TREND".equals(regime)){
            if(ema20>ema50 && close>ema20)
                return new Analysis("CALL",72,"MMC TREND_EMA: EMA20 > EMA50 এবং price EMA20-এর উপরে।");
            if(ema20<ema50 && close<ema20)
                return new Analysis("PUT",72,"MMC TREND_EMA: EMA20 < EMA50 এবং price EMA20-এর নিচে।");
            return new Analysis("WAIT",0,"MMC TREND_EMA: trend alignment নেই।");
        }

        if("BREAKOUT".equals(regime)){
            double hi=rangeHigh(c,20,n-1), lo=rangeLow(c,20,n-1);
            if(close>hi) return new Analysis("CALL",75,"MMC BREAKOUT_20: previous 20-bar high breakout।");
            if(close<lo) return new Analysis("PUT",75,"MMC BREAKOUT_20: previous 20-bar low breakout।");
            return new Analysis("WAIT",0,"MMC BREAKOUT_20: breakout confirm হয়নি।");
        }

        if("RANGE".equals(regime)){
            double lower=bb[0],upper=bb[1];
            if(close<=lower && rsi14<=35)
                return new Analysis("CALL",68,"MMC MEAN_REVERSION_BB_RSI: lower Bollinger touch + oversold RSI।");
            if(close>=upper && rsi14>=65)
                return new Analysis("PUT",68,"MMC MEAN_REVERSION_BB_RSI: upper Bollinger touch + overbought RSI।");
            return new Analysis("WAIT",0,"MMC MEAN_REVERSION_BB_RSI: mean-reversion setup নেই।");
        }

        if("HIGH_VOLATILITY".equals(regime)){
            double hi=rangeHigh(c,20,n-1), lo=rangeLow(c,20,n-1);
            if(close>hi) return new Analysis("CALL",75,"MMC BREAKOUT_20: high-volatility breakout filter pass।");
            if(close<lo) return new Analysis("PUT",75,"MMC BREAKOUT_20: high-volatility breakout filter pass।");
            return new Analysis("WAIT",0,"MMC VOLATILITY_FILTER: volatility বেশি, clean breakout নেই।");
        }

        return new Analysis("WAIT",0,"MMC NO_TRADE: market regime পরিষ্কার নয়।");
    }

    static double ema(ArrayList<Candle> c,int period){
        return ema(c,period,c.size()-1);
    }

    static double ema(ArrayList<Candle> c,int period,int end){
        if(end<0)return Double.NaN;
        int start=Math.max(0,end-period+1);
        double e=c.get(start).c;
        double k=2.0/(period+1.0);
        for(int i=start+1;i<=end;i++) e=c.get(i).c*k+e*(1-k);
        return e;
    }

    static double atr(ArrayList<Candle> c,int period,int end){
        int start=Math.max(1,end-period+1);
        if(start>end)return Double.NaN;
        double sum=0; int count=0;
        for(int i=start;i<=end;i++){
            double prev=c.get(i-1).c;
            double tr=Math.max(c.get(i).h-c.get(i).l,
                Math.max(Math.abs(c.get(i).h-prev),Math.abs(c.get(i).l-prev)));
            sum+=tr; count++;
        }
        return count==0?Double.NaN:sum/count;
    }

    // returns lower, upper, width
    static double[] bb(ArrayList<Candle> c,int period,int end){
        int start=Math.max(0,end-period+1);
        int count=end-start+1;
        if(count<=0)return new double[]{Double.NaN,Double.NaN,Double.NaN};
        double mean=0;
        for(int i=start;i<=end;i++)mean+=c.get(i).c;
        mean/=count;
        double var=0;
        for(int i=start;i<=end;i++){double d=c.get(i).c-mean;var+=d*d;}
        double sd=Math.sqrt(var/count);
        double upper=mean+2*sd, lower=mean-2*sd;
        return new double[]{lower,upper,(mean==0?0:(upper-lower)/mean)};
    }

    static double rsi(ArrayList<Candle> c,int period,int end){
        if(end<period)return Double.NaN;
        double gain=0,loss=0;
        int start=end-period+1;
        for(int i=start;i<=end;i++){
            double d=c.get(i).c-c.get(i-1).c;
            if(d>0)gain+=d; else loss-=d;
        }
        if(loss==0)return 100;
        double rs=gain/loss;
        return 100-(100/(1+rs));
    }

    static double rangeHigh(ArrayList<Candle> c,int period,int end){
        int start=Math.max(0,end-period+1); double x=-Double.MAX_VALUE;
        for(int i=start;i<=end;i++)x=Math.max(x,c.get(i).h);
        return x;
    }
    static double rangeLow(ArrayList<Candle> c,int period,int end){
        int start=Math.max(0,end-period+1); double x=Double.MAX_VALUE;
        for(int i=start;i<=end;i++)x=Math.min(x,c.get(i).l);
        return x;
    }
}
