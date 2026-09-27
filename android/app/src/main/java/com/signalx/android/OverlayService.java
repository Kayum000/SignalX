package com.signalx.android;

import android.app.Service;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class OverlayService extends Service {
    WindowManager wm; FrameLayout panel; LinearLayout card; SignalBubble bubble; WindowManager.LayoutParams lp;
    Handler clock=new Handler(Looper.getMainLooper()); boolean expanded=false;
    String signal="WAIT",reason="চার্ট capture অপেক্ষায়…",strategy="NONE",regime="UNKNOWN";
    int score=0,candles=0;
    final int BUBBLE=78,EXPANDED_W=300,EXPANDED_H=300;

    final Runnable clockTick=new Runnable(){public void run(){
        if(bubble!=null){bubble.setClock(new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date()));bubble.invalidate();}
        clock.postDelayed(this,1000);
    }};

    @Override public void onCreate(){
        super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);panel=build();
        lp=new WindowManager.LayoutParams(dp(BUBBLE),dp(BUBBLE),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.END;lp.x=dp(12);lp.y=dp(150);wm.addView(panel,lp);
        panel.setOnTouchListener(new WindowTouch());registerReceiver(rx,new IntentFilter(ScreenCaptureService.ACTION_ANALYSIS),RECEIVER_NOT_EXPORTED);clock.post(clockTick);
    }
    FrameLayout build(){FrameLayout root=new FrameLayout(this);bubble=new SignalBubble(this);root.addView(bubble,new FrameLayout.LayoutParams(dp(BUBBLE),dp(BUBBLE)));return root;}

    void setExpanded(boolean value){
        if(expanded==value)return;expanded=value;
        int w=dp(expanded?EXPANDED_W:BUBBLE),h=dp(expanded?EXPANDED_H:BUBBLE);lp.width=w;lp.height=h;wm.updateViewLayout(panel,lp);
        panel.removeAllViews();
        if(expanded)panel.addView(buildExpanded(),new FrameLayout.LayoutParams(w,h));
        else{bubble=new SignalBubble(this);bubble.setState(signal,score);panel.addView(bubble,new FrameLayout.LayoutParams(w,h));}
    }
    void refreshExpanded(){if(!expanded)return;panel.removeAllViews();panel.addView(buildExpanded(),new FrameLayout.LayoutParams(dp(EXPANDED_W),dp(EXPANDED_H)));}

    View buildExpanded(){
        card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(9,18,31));bg.setCornerRadius(dp(20));bg.setStroke(dp(1),Color.rgb(55,74,102));card.setBackground(bg);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=t("SignalX",17,Color.WHITE);title.setTypeface(null,Typeface.BOLD);head.addView(title);
        Space sp=new Space(this);head.addView(sp,new LinearLayout.LayoutParams(0,1,1));
        TextView close=t("×",24,Color.LTGRAY);close.setOnClickListener(v->setExpanded(false));head.addView(close,new LinearLayout.LayoutParams(dp(30),dp(30)));card.addView(head);

        TextView meta=t("QUOTEX CHART  •  MMC LIVE SCREEN ANALYSIS",9,Color.rgb(135,151,174));meta.setGravity(Gravity.CENTER);card.addView(meta,new LinearLayout.LayoutParams(-1,dp(22)));
        TextView sig=t(signalText(signal),25,signalColor(signal));sig.setGravity(Gravity.CENTER);sig.setTypeface(null,Typeface.BOLD);card.addView(sig,new LinearLayout.LayoutParams(-1,dp(44)));
        TextView ai=t("AI "+score,12,Color.WHITE);ai.setGravity(Gravity.CENTER);card.addView(ai,new LinearLayout.LayoutParams(-1,dp(22)));

        TextView strat=t("STRATEGY: "+strategyLabel(strategy),10,Color.rgb(75,180,255));strat.setGravity(Gravity.CENTER);strat.setTypeface(null,Typeface.BOLD);card.addView(strat,new LinearLayout.LayoutParams(-1,dp(22)));
        TextView reg=t("REGIME: "+regime,9,Color.rgb(155,174,198));reg.setGravity(Gravity.CENTER);card.addView(reg,new LinearLayout.LayoutParams(-1,dp(20)));

        TextView why=t(reason,10,Color.rgb(198,210,226));why.setGravity(Gravity.CENTER);why.setMaxLines(3);card.addView(why,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView st=t(candles+" candles detected  •  MMC requires 60",9,Color.rgb(125,143,166));st.setGravity(Gravity.CENTER);card.addView(st,new LinearLayout.LayoutParams(-1,dp(22)));

        Button b=new Button(this);b.setText("GET SIGNAL");b.setTextColor(Color.WHITE);b.setTextSize(11);b.setAllCaps(false);
        GradientDrawable bb=new GradientDrawable();bb.setColor(Color.rgb(43,111,255));bb.setCornerRadius(dp(10));b.setBackground(bb);b.setOnClickListener(v->refreshExpanded());
        card.addView(b,new LinearLayout.LayoutParams(-1,dp(38)));return card;
    }

    String strategyLabel(String s){
        if("TREND_EMA".equals(s))return "TREND EMA20/EMA50";
        if("BREAKOUT_20".equals(s))return "BREAKOUT 20-BAR";
        if("MEAN_REVERSION_BB_RSI".equals(s))return "MEAN REVERSION BB + RSI";
        if("VOLATILITY_FILTER".equals(s))return "VOLATILITY FILTER";
        if("NO_TRADE".equals(s))return "NO TRADE";
        return "MMC ADAPTIVE";
    }
    String signalText(String s){return "CALL".equals(s)?"BUY SIGNAL":"PUT".equals(s)?"SELL SIGNAL":"NO SIGNAL";}
    int signalColor(String s){return "CALL".equals(s)?Color.rgb(75,235,145):"PUT".equals(s)?Color.rgb(255,105,120):Color.WHITE;}
    TextView t(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(Gravity.CENTER);return v;}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    class WindowTouch implements View.OnTouchListener{
        float downX,downY;int startX,startY;boolean moved;
        public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=lp.x;startY=lp.y;moved=false;return true;
                case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(Math.abs(dx)+Math.abs(dy)>dp(7))moved=true;if(moved){lp.x=startX+(int)dx;lp.y=startY+(int)dy;wm.updateViewLayout(panel,lp);}return true;
                case MotionEvent.ACTION_UP:if(!moved)setExpanded(!expanded);return true;
            }return true;
        }
    }

    final BroadcastReceiver rx=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            signal=i.getStringExtra("signal");if(signal==null)signal="WAIT";
            score=Math.max(0,Math.min(100,i.getIntExtra("score",0)));
            reason=i.getStringExtra("reason");if(reason==null)reason="—";
            candles=i.getIntExtra("candles",0);strategy=i.getStringExtra("strategy");if(strategy==null)strategy="NONE";
            regime=i.getStringExtra("regime");if(regime==null)regime="UNKNOWN";
            if(!expanded&&bubble!=null){bubble.setState(signal,score);bubble.invalidate();}else if(expanded)refreshExpanded();
        }
    };
    public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){clock.removeCallbacks(clockTick);try{unregisterReceiver(rx);}catch(Exception ignored){}if(panel!=null)wm.removeView(panel);super.onDestroy();}

    class SignalBubble extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);String time="--:--:--",s="WAIT";int ai=0;
        SignalBubble(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setClock(String x){time=x;}void setState(String x,int a){s=x;ai=a;}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.45f;
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(235,5,12,22));c.drawCircle(cx,cy,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setShader(new SweepGradient(cx,cy,new int[]{Color.rgb(35,128,255),Color.rgb(125,70,255),Color.rgb(255,95,160),Color.rgb(35,128,255)},null));c.drawCircle(cx,cy,r,p);p.setShader(null);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            p.setTextSize(dp(9));p.setColor(Color.WHITE);c.drawText(time,cx,cy-dp(13),p);
            p.setTextSize(dp(8));p.setColor(signalColor(s));c.drawText(signalText(s),cx,cy+dp(1),p);
            p.setTextSize(dp(7));p.setColor(Color.WHITE);c.drawText("AI "+ai,cx,cy+dp(14),p);
            p.setColor(Color.rgb(50,230,130));c.drawCircle(cx,cy+r-dp(5),dp(3),p);
            p.setTextSize(dp(11));p.setColor(Color.WHITE);c.drawText("×",cx+r-dp(2),cy-r+dp(7),p);
        }
    }
}