package com.signalx.android;

import android.app.Service;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

public class OverlayService extends Service {
    WindowManager wm;
    FrameLayout panel;
    LinearLayout card;
    TextView signal, score, reason, status;
    WindowManager.LayoutParams lp;
    boolean expanded=false;

    final int COLLAPSED = 52;
    final int EXPANDED_W = 292;
    final int EXPANDED_H = 238;

    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=build();

        lp=new WindowManager.LayoutParams(
            dp(COLLAPSED), dp(COLLAPSED),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity=Gravity.TOP|Gravity.END;
        lp.x=dp(10);
        lp.y=dp(100);
        wm.addView(panel,lp);

        panel.setOnTouchListener(new WindowTouch());
        registerReceiver(rx,new IntentFilter(ScreenCaptureService.ACTION_ANALYSIS),RECEIVER_NOT_EXPORTED);
    }

    FrameLayout build(){
        FrameLayout root=new FrameLayout(this);
        card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        applyBackground();

        signal=t("WAIT",14,Color.WHITE);
        score=t("0/100",10,Color.LTGRAY);
        reason=t("চার্ট capture অপেক্ষায়…",10,Color.rgb(205,214,232));
        reason.setGravity(Gravity.CENTER);
        reason.setMaxLines(3);
        reason.setEllipsize(android.text.TextUtils.TruncateAt.END);

        status=t("চার্ট ডাটা অপেক্ষায়",9,Color.rgb(145,160,184));
        status.setGravity(Gravity.CENTER);

        root.addView(card,new FrameLayout.LayoutParams(dp(COLLAPSED),dp(COLLAPSED)));
        rebuild();
        return root;
    }

    void applyBackground(){
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.rgb(9,22,38));
        g.setCornerRadius(dp(expanded?18:COLLAPSED/2));
        g.setStroke(dp(1),Color.rgb(45,73,105));
        card.setBackground(g);
        card.setElevation(dp(8));
    }

    void rebuild(){
        card.removeAllViews();
        if(!expanded){
            card.setPadding(dp(2),0,dp(2),0);
            TextView brand=t("SX",9,Color.WHITE);
            brand.setTypeface(null,android.graphics.Typeface.BOLD);
            card.addView(brand);
            card.addView(signal,new LinearLayout.LayoutParams(-1,dp(22)));
            return;
        }

        card.setPadding(dp(12),dp(10),dp(12),dp(10));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=t("SignalX",15,Color.WHITE);
        brand.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView sub=t("  CHART SIGNAL",8,Color.rgb(145,160,184));
        top.addView(brand);
        top.addView(sub);
        Space spacer=new Space(this);
        top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        TextView close=t("×",22,Color.LTGRAY);
        close.setOnClickListener(v->setExpanded(false));
        top.addView(close,new LinearLayout.LayoutParams(dp(28),dp(28)));
        card.addView(top);

        LinearLayout divider=new LinearLayout(this);
        divider.setBackgroundColor(Color.rgb(35,52,73));
        card.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));

        TextView sigLabel=t("SIGNAL",8,Color.rgb(145,160,184));
        sigLabel.setGravity(Gravity.CENTER);
        card.addView(sigLabel,new LinearLayout.LayoutParams(-1,dp(18)));

        signal.setTextSize(28);
        signal.setTypeface(null,android.graphics.Typeface.BOLD);
        card.addView(signal,new LinearLayout.LayoutParams(-1,dp(40)));

        LinearLayout scoreRow=new LinearLayout(this);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sl=t("Signal Score",10,Color.rgb(180,192,211));
        scoreRow.addView(sl);
        Space s=new Space(this);
        scoreRow.addView(s,new LinearLayout.LayoutParams(0,1,1));
        score.setTextSize(11);
        scoreRow.addView(score);
        card.addView(scoreRow);

        card.addView(reason,new LinearLayout.LayoutParams(-1,dp(50)));
        card.addView(status,new LinearLayout.LayoutParams(-1,dp(22)));

        Button get=new Button(this);
        get.setText("GET SIGNAL");
        get.setTextSize(10);
        get.setTextColor(Color.WHITE);
        get.setAllCaps(false);
        get.setPadding(0,0,0,0);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.rgb(61,120,255));
        bg.setCornerRadius(dp(10));
        get.setBackground(bg);
        get.setOnClickListener(v->{
            status.setText("চার্ট থেকে নতুন analysis নেওয়া হচ্ছে…");
            v.postDelayed(()->status.setText("Chart analysis active"),350);
        });
        card.addView(get,new LinearLayout.LayoutParams(-1,dp(34)));

        applyBackground();
    }

    void setExpanded(boolean value){
        if(expanded==value)return;
        expanded=value;
        int w=dp(expanded?EXPANDED_W:COLLAPSED);
        int h=dp(expanded?EXPANDED_H:COLLAPSED);
        lp.width=w; lp.height=h;
        wm.updateViewLayout(panel,lp);

        FrameLayout.LayoutParams cp=(FrameLayout.LayoutParams)card.getLayoutParams();
        cp.width=w; cp.height=h; card.setLayoutParams(cp);
        rebuild();
        panel.setScaleX(.96f); panel.setScaleY(.96f);
        panel.animate().scaleX(1f).scaleY(1f).setDuration(140)
            .setInterpolator(new DecelerateInterpolator()).start();
    }

    class WindowTouch implements View.OnTouchListener{
        float downX,downY; int startX,startY; boolean moved;
        public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=e.getRawX(); downY=e.getRawY();
                    startX=lp.x; startY=lp.y; moved=false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-downX, dy=e.getRawY()-downY;
                    if(Math.abs(dx)+Math.abs(dy)>dp(6))moved=true;
                    if(moved){
                        lp.x=startX+(int)dx; lp.y=startY+(int)dy;
                        wm.updateViewLayout(panel,lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved)setExpanded(!expanded);
                    return true;
            }
            return true;
        }
    }

    final BroadcastReceiver rx=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            String s=i.getStringExtra("signal");
            signal.setText(s==null?"WAIT":s);
            score.setText(i.getIntExtra("score",0)+"/100");
            reason.setText(i.getStringExtra("reason")==null?"—":i.getStringExtra("reason"));
            status.setText("Chart • "+i.getIntExtra("candles",0)+" candles");
        }
    };

    TextView t(String s,float z,int c){
        TextView v=new TextView(this);
        v.setText(s); v.setTextSize(z); v.setTextColor(c);
        v.setGravity(Gravity.CENTER); v.setPadding(0,dp(1),0,dp(1));
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