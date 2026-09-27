package com.signalx.android;

import android.app.Service;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.view.*;
import android.widget.*;

public class OverlayService extends Service {
    WindowManager wm; View panel; TextView signal,score,reason,status;
    WindowManager.LayoutParams lp;
    boolean expanded=false;

    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=build();

        // Small circular floating button; tap to expand the full signal panel.
        lp=new WindowManager.LayoutParams(
            dp(48), dp(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity=Gravity.TOP|Gravity.END;
        lp.x=dp(10);
        lp.y=dp(100);
        wm.addView(panel,lp);

        panel.setOnTouchListener(new View.OnTouchListener(){
            float dx,dy; int sx,sy;
            public boolean onTouch(View v,MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){
                    dx=e.getRawX(); dy=e.getRawY();
                    sx=lp.x; sy=lp.y;
                    return true;
                }
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    lp.x=sx+(int)(e.getRawX()-dx);
                    lp.y=sy+(int)(e.getRawY()-dy);
                    wm.updateViewLayout(panel,lp);
                    return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP){
                    float moved=Math.abs(e.getRawX()-dx)+Math.abs(e.getRawY()-dy);
                    if(moved<dp(8)) toggleExpanded();
                    return true;
                }
                return true;
            }
        });

        registerReceiver(rx,new IntentFilter(ScreenCaptureService.ACTION_ANALYSIS),RECEIVER_NOT_EXPORTED);
    }

    View build(){
        FrameLayout root=new FrameLayout(this);
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4),dp(3),dp(4),dp(3));

        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.rgb(9,22,38));
        g.setShape(GradientDrawable.OVAL);
        b.setBackground(g);

        signal=t("WAIT",13,Color.WHITE);
        score=t("Score 0/100",11,Color.LTGRAY);
        reason=t("চার্ট capture অপেক্ষায়…",10,Color.LTGRAY);
        reason.setGravity(Gravity.CENTER);
        reason.setMaxLines(2);
        reason.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status=t("ডাটা অপেক্ষায়",9,Color.GRAY);
        status.setGravity(Gravity.CENTER);

        root.addView(b,new FrameLayout.LayoutParams(dp(48),dp(48)));
        rebuild(b);
        return root;
    }

    void rebuild(LinearLayout b){
        b.removeAllViews();
        b.setGravity(Gravity.CENTER);
        if(expanded){
            b.setPadding(dp(10),dp(8),dp(10),dp(8));
            TextView h=t("SignalX",13,Color.WHITE);
            b.addView(h);
            b.addView(signal);
            b.addView(score);
            b.addView(reason);
            b.addView(status);
            Button x=new Button(this);
            x.setText("×");
            x.setTextSize(12);
            x.setMinWidth(0);
            x.setMinHeight(0);
            x.setPadding(0,0,0,0);
            x.setOnClickListener(v->stopSelf());
            b.addView(x,new LinearLayout.LayoutParams(dp(34),dp(28)));
        }else{
            b.setPadding(dp(4),dp(3),dp(4),dp(3));
            b.addView(t("SX",8,Color.WHITE));
            b.addView(signal);
        }
    }

    void toggleExpanded(){
        expanded=!expanded;
        int size=expanded?dp(180):dp(48);
        lp.width=size;
        lp.height=size;
        wm.updateViewLayout(panel,lp);
        LinearLayout b=(LinearLayout)((FrameLayout)panel).getChildAt(0);
        FrameLayout.LayoutParams child=(FrameLayout.LayoutParams)b.getLayoutParams();
        child.width=size;
        child.height=size;
        b.setLayoutParams(child);
        rebuild(b);
    }

    final BroadcastReceiver rx=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            signal.setText(i.getStringExtra("signal"));
            score.setText("Score "+i.getIntExtra("score",0)+"/100");
            reason.setText(i.getStringExtra("reason"));
            status.setText("Chart • "+i.getIntExtra("candles",0)+" candles");
        }
    };

    TextView t(String s,float z,int c){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(c);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0,dp(1),0,dp(1));
        return v;
    }

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    public IBinder onBind(Intent i){return null;}

    public void onDestroy(){
        try{unregisterReceiver(rx);}catch(Exception ignored){}
        if(panel!=null)wm.removeView(panel);
        super.onDestroy();
    }
}