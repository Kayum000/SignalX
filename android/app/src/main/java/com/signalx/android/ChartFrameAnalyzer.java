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

public final class ChartFrameAnalyzer {
    static boolean green(int r,int g,int b){
        int mx=Math.max(r,Math.max(g,b)), mn=Math.min(r,Math.min(g,b));
        return mx>75 && g>=r*1.12 && g>=b*1.03 && mx-mn>22;
    }
    static boolean red(int r,int g,int b){
        int mx=Math.max(r,Math.max(g,b)), mn=Math.min(r,Math.min(g,b));
        return mx>75 && r>=g*1.12 && r>=b*1.03 && mx-mn>22;
    }
    static boolean candleColor(int r,int g,int b){return green(r,g,b)||red(r,g,b);}

    static ArrayList<Candle> extract(ByteBuffer p,int w,int h,int stride,int pix){
        // Ignore browser/header areas and inspect the central chart region.
        int y0=(int)(h*.18), y1=(int)(h*.78), x0=(int)(w*.05), x1=(int)(w*.95);
        ArrayList<Integer> xs=new ArrayList<>();
        for(int x=x0;x<x1;x+=2){
            int n=0;
            for(int y=y0;y<y1;y+=3){
                int i=y*stride+x*pix;
                if(i+2>=p.limit()) continue;
                int r=p.get(i)&255,g=p.get(i+1)&255,b=p.get(i+2)&255;
                if(candleColor(r,g,b)) n++;
            }
            if(n>=3) xs.add(x);
        }

        ArrayList<int[]> groups=new ArrayList<>();
        if(!xs.isEmpty()){
            int s=xs.get(0), last=s;
            for(int x:xs){
                if(x-last>10){groups.add(new int[]{s,last});s=x;}
                last=x;
            }
            groups.add(new int[]{s,last});
        }

        ArrayList<Candle> out=new ArrayList<>();
        for(int[] z:groups){
            if(z[1]-z[0]<2 || z[1]-z[0]>48) continue;
            int min=y1,max=y0,gn=0,rn=0;
            for(int x=z[0];x<=z[1];x++){
                for(int y=y0;y<y1;y+=2){
                    int i=y*stride+x*pix;
                    if(i+2>=p.limit()) continue;
                    int r=p.get(i)&255,g=p.get(i+1)&255,b=p.get(i+2)&255;
                    if(green(r,g,b)){gn++;min=Math.min(min,y);max=Math.max(max,y);}
                    else if(red(r,g,b)){rn++;min=Math.min(min,y);max=Math.max(max,y);}
                }
            }
            if(min>=max || gn+rn<7) continue;
            double hi=h-max, lo=h-min, mid=(hi+lo)/2;
            double body=Math.max(2,(max-min)*.55);
            double o=mid,c=(gn>=rn?mid+body/2:mid-body/2);
            if(c<o){double t=o;o=c;c=t;}
            out.add(new Candle(o,hi,lo,c));
        }
        while(out.size()>80) out.remove(0);
        return out;
    }

    static Analysis signal(ArrayList<Candle> c){
        if(c.size()<25)
            return new Analysis("WAIT",0,"কমপক্ষে ২৫টি চার্ট ক্যান্ডেল শনাক্ত করা দরকার।");

        int call=0,put=0;
        for(int i=c.size()-1;i>=Math.max(1,c.size()-6);i--){
            if(c.get(i).c>c.get(i).o) call+=6; else put+=6;
        }
        double last=c.get(c.size()-1).c,prev=c.get(c.size()-2).c;
        if(last>prev)call+=20;else put+=20;

        double fast=0,slow=0;
        for(int i=c.size()-1;i>c.size()-10;i--)fast+=c.get(i).c;
        for(int i=c.size()-1;i>c.size()-22;i--)slow+=c.get(i).c;
        fast/=9;slow/=21;
        if(fast>slow)call+=25;else put+=25;

        int score=Math.min(100,Math.max(call,put));
        String sig=score>=55?(call>put?"CALL":"PUT"):"WAIT";
        String why=sig.equals("CALL")
            ?"চার্টের সাম্প্রতিক মোমেন্টাম ও ক্যান্ডেল দিক ঊর্ধ্বমুখী।"
            :sig.equals("PUT")
            ?"চার্টের সাম্প্রতিক মোমেন্টাম ও ক্যান্ডেল দিক নিম্নমুখী।"
            :"চার্টের কনফার্মেশন যথেষ্ট শক্ত নয়।";
        return new Analysis(sig,score,why);
    }
}