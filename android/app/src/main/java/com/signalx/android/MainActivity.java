package com.signalx.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.media.projection.MediaProjectionManager;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    static final int REQ_CAPTURE=501;
    MediaProjectionManager pm;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,44,32,32); root.setBackgroundColor(Color.rgb(7,17,31));
        TextView title=new TextView(this); title.setText("SignalX"); title.setTextColor(Color.WHITE); title.setTextSize(30); title.setGravity(Gravity.CENTER);
        TextView info=new TextView(this); info.setText("Quotex-এর উপর SignalX overlay চালু হবে।\n\nExternal market-data API ব্যবহার করা হবে না। বর্তমান Quotex screen-এর chart visual data বিশ্লেষণ করা হবে।"); info.setTextColor(Color.LTGRAY); info.setTextSize(16); info.setPadding(0,24,0,24);
        Button perm=new Button(this); perm.setText("১. Overlay Permission"); perm.setOnClickListener(v->{ if(!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); });
        Button start=new Button(this); start.setText("২. SignalX চালু করুন"); start.setOnClickListener(v->startCapture());
        root.addView(title); root.addView(info); root.addView(perm); root.addView(start); setContentView(root);
    }
    void startCapture(){
        if(!Settings.canDrawOverlays(this)){ startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); return; }
        startService(new Intent(this,OverlayService.class));
        startActivityForResult(pm.createScreenCaptureIntent(),REQ_CAPTURE);
    }
    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==REQ_CAPTURE && c==RESULT_OK && d!=null){
            Intent i=new Intent(this,ScreenCaptureService.class); i.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,c); i.putExtra(ScreenCaptureService.EXTRA_DATA,d); startForegroundService(i);
        }
    }
}
