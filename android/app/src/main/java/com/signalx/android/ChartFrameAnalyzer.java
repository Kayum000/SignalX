package com.signalx.android;

import java.nio.ByteBuffer;
import java.util.*;

final class Candle {
    double o,h,l,c;
    Candle(double o,double h,double l,double c){this.o=o;this.h=h;this.l=l;this.c=c;}
}
final class Analysis {
    String signal,reason; int score;
    Analysis(String s,int n,String r){signal=s;score=n;reason=r;}
}

/** Extracts relative OHLC geometry from the visible Quotex chart. */
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
        int y0=(int)(h*.20), y1=(int)(h*.68), x0=(int)(w*.04), x1=(int)(w*.96);
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
                if(x-last>9){groups.add(new int[]{s,last});s=x;}
                last=x;
            }
            groups.add(new int[]{s,last});
        }

        ArrayList<Candle> out=new ArrayList<>();
        for(int[] z:groups){
            if(z[1]-z[0]<2||z[1]-z[0]>44)continue;
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
        if(c.size()<25)return new Analysis("WAIT",0,"কমপক্ষে ২৫টি সম্পূর্ণ চার্ট ক্যান্ডেল শনাক্ত করা দরকার.");
        int call=0,put=0;
        int recent=Math.min(8,c.size()-1);
        for(int i=c.size()-recent;i<c.size();i++){
            Candle x=c.get(i); double body=Math.abs(x.c-x.o),range=Math.max(x.h-x.l,1e-6);
            if(x.c>x.o){call+=5;if(body/range>.55)call+=2;}
            else if(x.c<x.o){put+=5;if(body/range>.55)put+=2;}
        }
        double last=c.get(c.size()-1).c,prev=c.get(c.size()-2).c;
        if(last>prev)call+=15; else if(last<prev)put+=15;
        double fast=ema(c,9),slow=ema(c,21);
        if(fast>slow)call+=25; else if(fast<slow)put+=25;
        double rsi=rsi(c,14);
        if(rsi>=55&&rsi<=75)call+=15;
        else if(rsi<=45&&rsi>=25)put+=15;
        int score=Math.min(100,Math.max(call,put));
        String sig=score>=60?(call>put?"CALL":put>call?"PUT":"WAIT"):"WAIT";
        String why=sig.equals("CALL")
            ?"ট্রেন্ড, মোমেন্টাম ও সাম্প্রতিক বুলিশ ক্যান্ডেল একদিকে মিলেছে."
            :sig.equals("PUT")
            ?"ট্রেন্ড, মোমেন্টাম ও সাম্প্রতিক বিয়ারিশ ক্যান্ডেল একদিকে মিলেছে."
            :"কনফার্মেশন যথেষ্ট শক্ত নয়; NO SIGNAL দেখানো হচ্ছে.";
        return new Analysis(sig,score,why);
    }

    static double ema(ArrayList<Candle> c,int period){
        if(c.size()<period)return 0;
        double e=0;
        for(int i=c.size()-period;i<c.size();i++)e+=c.get(i).c;
        e/=period;
        double k=2.0/(period+1);
        for(int i=c.size()-period+1;i<c.size();i++)e=c.get(i).c*k+e*(1-k);
        return e;
    }

    static double rsi(ArrayList<Candle> c,int p){
        if(c.size()<=p)return 50;
        double g=0,l=0;
        for(int i=c.size()-p;i<c.size();i++){
            double d=c.get(i).c-c.get(i-1).c;
            if(d>0)g+=d;else l-=d;
        }
        if(l==0)return 100;
        return 100-100/(1+g/l);
    }
}
