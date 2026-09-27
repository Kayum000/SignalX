package com.signalx.android;

import java.nio.ByteBuffer;
import java.util.*;

final class Candle {
    double o,h,l,c;
    Candle(double o,double h,double l,double c){this.o=o;this.h=h;this.l=l;this.c=c;}
}
final class DetectedCandle {
    Candle candle; float centerX;
    DetectedCandle(Candle candle,float centerX){this.candle=candle;this.centerX=centerX;}
}

final class Analysis {
    String signal,reason,strategy,regime;
    int score;
    Analysis(String s,int n,String r,String reg,String strat){
        signal=s;score=n;reason=r;regime=reg;strategy=strat;
    }
}

/** Screen-data implementation of the MMC adaptive_real strategy. */
public final class ChartFrameAnalyzer {
    static boolean green(int r,int g,int b){
        int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
        return mx>70 && g>r*1.18 && g>b*1.04 && mx-mn>28;
    }
    static boolean red(int r,int g,int b){
        int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
        return mx>70 && r>g*1.18 && r>b*1.04 && mx-mn>28;
    }

    static ArrayList<DetectedCandle> extractDetected(ByteBuffer p,int w,int h,int stride,int pix){
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
        ArrayList<DetectedCandle> out=new ArrayList<>();
        for(int[] z:groups){
            if(z[1]-z[0]<2||z[1]-z[0]>34)continue;
            int min=y1,max=y0,gn=0,rn=0;
            for(int x=z[0];x<=z[1];x++) for(int y=y0;y<y1;y+=2){
                int i=y*stride+x*pix;
                if(i<0||i+2>=p.limit())continue;
                int r=p.get(i)&255,g=p.get(i+1)&255,b=p.get(i+2)&255;
                if(green(r,g,b)){gn++;min=Math.min(min,y);max=Math.max(max,y);}
                else if(red(r,g,b)){rn++;min=Math.min(min,y);max=Math.max(max,y);}
            }
            int total=gn+rn;
            if(max-min<4||total<8)continue;
            double high=h-max,low=h-min,mid=(high+low)/2.0;
            double body=Math.max(2,(max-min)*.55);
            boolean up=gn>=rn;
            double o=up?mid-body/2:mid+body/2;
            double c=up?mid+body/2:mid-body/2;
            out.add(new DetectedCandle(new Candle(o,high,low,c),(z[0]+z[1])/2f));
        }
        while(out.size()>80)out.remove(0);
        return out;
    }

    static ArrayList<Candle> extract(ByteBuffer p,int w,int h,int stride,int pix){
        ArrayList<Candle> out=new ArrayList<>();
        for(DetectedCandle d:extractDetected(p,w,h,stride,pix)) out.add(d.candle);
        return out;
    }

    static Analysis signal(ArrayList<Candle> c){
        if(c.size()<60)
            return new Analysis("WAIT",0,
                "MMC Strategy: 60টি closed candle history দরকার ("+c.size()+"টি পাওয়া গেছে)।",
                "UNKNOWN","NONE");

        int n=c.size()-1;
        double close=c.get(n).c;
        double ema20=ema(c,20,n), ema50=ema(c,50,n);
        double atr14=atr(c,14,n), atrPct=close==0?Double.NaN:atr14/close;
        double emaGap=close==0?Double.NaN:Math.abs(ema20-ema50)/close;
        double ema20Prev=ema(c,20,n-5);
        double emaSlope=Math.abs(ema20-ema20Prev)/Math.max(Math.abs(ema20Prev),1e-12);
        double[] bb=bb(c,20,n);
        double rsi14=rsi(c,14,n);

        String regime;
        if(Double.isNaN(atrPct)||Double.isNaN(bb[2])||Double.isNaN(emaGap)) regime="UNKNOWN";
        else if(atrPct>=0.0025) regime="HIGH_VOLATILITY";
        else if(emaGap>=0.00055 && emaSlope>=0.00020) regime="TREND";
        else if(bb[2]<=0.0018 && atrPct<=0.0012) regime="RANGE";
        else if(bb[2]>=0.0030 || emaSlope>=0.00035) regime="BREAKOUT";
        else regime="UNCLEAR";

        if("TREND".equals(regime)){
            if(ema20>ema50 && close>ema20)
                return new Analysis("CALL",72,"EMA20 above EMA50 with price above EMA20","TREND","TREND_EMA");
            if(ema20<ema50 && close<ema20)
                return new Analysis("PUT",72,"EMA20 below EMA50 with price below EMA20","TREND","TREND_EMA");
            return new Analysis("WAIT",0,"trend alignment absent","TREND","TREND_EMA");
        }
        if("BREAKOUT".equals(regime)){
            double hi=rangeHigh(c,20,n-1),lo=rangeLow(c,20,n-1);
            if(close>hi)return new Analysis("CALL",75,"20-bar high breakout","BREAKOUT","BREAKOUT_20");
            if(close<lo)return new Analysis("PUT",75,"20-bar low breakout","BREAKOUT","BREAKOUT_20");
            return new Analysis("WAIT",0,"breakout not confirmed","BREAKOUT","BREAKOUT_20");
        }
        if("RANGE".equals(regime)){
            if(close<=bb[0]&&rsi14<=35)
                return new Analysis("CALL",68,"lower Bollinger touch + oversold RSI","RANGE","MEAN_REVERSION_BB_RSI");
            if(close>=bb[1]&&rsi14>=65)
                return new Analysis("PUT",68,"upper Bollinger touch + overbought RSI","RANGE","MEAN_REVERSION_BB_RSI");
            return new Analysis("WAIT",0,"mean-reversion setup absent","RANGE","MEAN_REVERSION_BB_RSI");
        }
        if("HIGH_VOLATILITY".equals(regime)){
            double hi=rangeHigh(c,20,n-1),lo=rangeLow(c,20,n-1);
            if(close>hi)return new Analysis("CALL",75,"20-bar high breakout; high-volatility filter","HIGH_VOLATILITY","BREAKOUT_20");
            if(close<lo)return new Analysis("PUT",75,"20-bar low breakout; high-volatility filter","HIGH_VOLATILITY","BREAKOUT_20");
            return new Analysis("WAIT",0,"high volatility without clean breakout","HIGH_VOLATILITY","VOLATILITY_FILTER");
        }
        return new Analysis("WAIT",0,"market regime unclear",regime,"NO_TRADE");
    }

    // Pandas ewm(span=period, adjust=False): seed from the first candle, then
    // apply the recursive EMA across the entire available history.
    static double ema(ArrayList<Candle> c,int period,int end){
        if(end<0)return Double.NaN;
        double e=c.get(0).c, k=2.0/(period+1.0);
        for(int i=1;i<=end;i++)e=c.get(i).c*k+e*(1-k);
        return e;
    }

    static double atr(ArrayList<Candle> c,int period,int end){
        if(end<period)return Double.NaN;
        int start=end-period+1; double sum=0;
        for(int i=start;i<=end;i++){
            double prev=c.get(i-1).c;
            sum+=Math.max(c.get(i).h-c.get(i).l,
                Math.max(Math.abs(c.get(i).h-prev),Math.abs(c.get(i).l-prev)));
        }
        return sum/period;
    }

    static double[] bb(ArrayList<Candle> c,int period,int end){
        if(end<period-1)return new double[]{Double.NaN,Double.NaN,Double.NaN};
        int start=end-period+1; double mean=0;
        for(int i=start;i<=end;i++)mean+=c.get(i).c;
        mean/=period;
        double var=0;
        for(int i=start;i<=end;i++){double d=c.get(i).c-mean;var+=d*d;}
        double sd=Math.sqrt(var/period);
        double upper=mean+2*sd,lower=mean-2*sd;
        return new double[]{lower,upper,(upper-lower)/mean};
    }

    static double rsi(ArrayList<Candle> c,int period,int end){
        if(end<period)return Double.NaN;
        double gain=0,loss=0;
        for(int i=end-period+1;i<=end;i++){
            double d=c.get(i).c-c.get(i-1).c;
            if(d>0)gain+=d;else loss-=d;
        }
        if(loss==0)return Double.NaN;
        return 100-(100/(1+(gain/loss)));
    }

    static double rangeHigh(ArrayList<Candle> c,int period,int end){
        if(end<0)return Double.NaN;
        int start=Math.max(0,end-period+1);double x=-Double.MAX_VALUE;
        for(int i=start;i<=end;i++)x=Math.max(x,c.get(i).h);
        return x;
    }
    static double rangeLow(ArrayList<Candle> c,int period,int end){
        if(end<0)return Double.NaN;
        int start=Math.max(0,end-period+1);double x=Double.MAX_VALUE;
        for(int i=start;i<=end;i++)x=Math.min(x,c.get(i).l);
        return x;
    }
}
