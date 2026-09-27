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
        super.onCreate(); wm=(WindowManager)getSystemService(WINDOW_SERVICE); panel=build();
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(dp(292),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.END; lp.x=dp(12); lp.y=dp(110); wm.addView(panel,lp);
        panel.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy; public boolean onTouch(View v,MotionEvent e){if(e.getAction()==0){dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;return true;}if(e.getAction()==2){lp.x=sx+(int)(dx-e.getRawX());lp.y=sy+(int)(e.getRawY()-dy);wm.updateViewLayout(panel,lp);return true;}return true;}});
        registerReceiver(rx,new IntentFilter(ScreenCaptureService.ACTION_ANALYSIS),RECEIVER_NOT_EXPORTED);
    }
    View build(){
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(9,22,38));g.setCornerRadius(dp(18));b.setBackground(g);
        TextView h=t("SignalX • Quotex",17,Color.WHITE);signal=t("WAIT",30,Color.WHITE);score=t("Signal Score 0/100",15,Color.LTGRAY);reason=t("চার্ট capture অপেক্ষায়…",13,Color.LTGRAY);status=t("ডাটা অপেক্ষায়",12,Color.GRAY);
        Button x=new Button(this);x.setText("বন্ধ");x.setOnClickListener(v->stopSelf());
        b.addView(h);b.addView(signal);b.addView(score);b.addView(reason);b.addView(status);b.addView(x);return b;
    }
    final BroadcastReceiver rx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){signal.setText(i.getStringExtra("signal"));score.setText("Signal Score "+i.getIntExtra("score",0)+"/100");reason.setText(i.getStringExtra("reason"));status.setText("Quotex chart • "+i.getIntExtra("candles",0)+" candle sample");}};
    TextView t(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(0,dp(4),0,dp(4));return v;}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){try{unregisterReceiver(rx);}catch(Exception ignored){}if(panel!=null)wm.removeView(panel);super.onDestroy();}
}
