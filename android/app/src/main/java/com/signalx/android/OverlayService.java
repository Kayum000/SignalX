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

    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=build();

        // Compact circular floating overlay.
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            dp(180), dp(180),
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
                if(e.getAction()==0){
                    dx=e.getRawX(); dy=e.getRawY();
                    sx=lp.x; sy=lp.y;
                    return true;
                }
                if(e.getAction()==2){
                    lp.x=sx+(int)(e.getRawX()-dx);
                    lp.y=sy+(int)(e.getRawY()-dy);
                    wm.updateViewLayout(panel,lp);
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
        b.setPadding(dp(10),dp(8),dp(10),dp(8));

        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.rgb(9,22,38));
        g.setShape(GradientDrawable.OVAL);
        b.setBackground(g);

        TextView h=t("SignalX",13,Color.WHITE);
        signal=t("WAIT",25,Color.WHITE);
        score=t("Score 0/100",11,Color.LTGRAY);
        reason=t("চার্ট capture অপেক্ষায়…",10,Color.LTGRAY);
        reason.setGravity(Gravity.CENTER);
        reason.setMaxLines(2);
        reason.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status=t("ডাটা অপেক্ষায়",9,Color.GRAY);
        status.setGravity(Gravity.CENTER);

        Button x=new Button(this);
        x.setText("×");
        x.setTextSize(12);
        x.setMinWidth(0);
        x.setMinHeight(0);
        x.setPadding(0,0,0,0);
        x.setOnClickListener(v->stopSelf());

        b.addView(h);
        b.addView(signal);
        b.addView(score);
        b.addView(reason);
        b.addView(status);
        b.addView(x,new LinearLayout.LayoutParams(dp(34),dp(28)));

        root.addView(b,new FrameLayout.LayoutParams(dp(180),dp(180)));
        return root;
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
        v.setPadding(0,dp(2),0,dp(2));
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
