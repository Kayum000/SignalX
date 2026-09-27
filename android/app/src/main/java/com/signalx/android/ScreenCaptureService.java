package com.signalx.android;

import android.app.*;
import android.content.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.graphics.*;
import java.nio.ByteBuffer;
import java.util.*;

public class ScreenCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE="resultCode", EXTRA_DATA="data", ACTION_ANALYSIS="com.signalx.android.ANALYSIS";
    MediaProjection projection; ImageReader reader; Handler handler;

    @Override public void onCreate(){
        super.onCreate(); handler=new Handler(Looper.getMainLooper());
        NotificationChannel ch=new NotificationChannel("signalx_capture","SignalX chart analysis",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n=new Notification.Builder(this,"signalx_capture")
            .setContentTitle("SignalX চলছে").setContentText("Quotex chart analysis active")
            .setSmallIcon(android.R.drawable.ic_menu_view).build();
        startForeground(7,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    @Override public int onStartCommand(Intent in,int flags,int id){
        if(projection!=null)return START_STICKY;
        if(in==null){stopSelf();return START_NOT_STICKY;}
        int code=in.getIntExtra(EXTRA_RESULT_CODE,-1); Intent data;
        if(Build.VERSION.SDK_INT>=33)data=in.getParcelableExtra(EXTRA_DATA,Intent.class); else data=in.getParcelableExtra(EXTRA_DATA);
        if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY;}
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=m.getMediaProjection(code,data);
        if(projection==null){stopSelf();return START_NOT_STICKY;}
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        int w=dm.widthPixels,h=dm.heightPixels;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->analyze(r),handler);
        projection.createVirtualDisplay("SignalX",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
        sendStatus("চার্ট capture চালু হয়েছে; MMC Strategy candle history সংগ্রহ করা হচ্ছে…",0,0,"UNKNOWN","NONE");
        handler.postDelayed(()->sendStatus("Capture চালু আছে; MMC Adaptive Strategy চলছে…",0,0,"UNKNOWN","NONE"),300);
        return START_STICKY;
    }

    void sendStatus(String reason,int score,int candles,String regime,String strategy){
        Intent o=new Intent(ACTION_ANALYSIS);o.setPackage(getPackageName());
        o.putExtra("captureActive",true);o.putExtra("signal","WAIT");o.putExtra("score",score);
        o.putExtra("reason",reason);o.putExtra("candles",candles);o.putExtra("regime",regime);o.putExtra("strategy",strategy);
        sendBroadcast(o);
    }

    void analyze(ImageReader r){
        Image img=null;
        try{
            img=r.acquireLatestImage();if(img==null)return;
            Image.Plane p=img.getPlanes()[0];ByteBuffer b=p.getBuffer();
            ArrayList<Candle> cs=ChartFrameAnalyzer.extract(b,img.getWidth(),img.getHeight(),p.getRowStride(),p.getPixelStride());
            Analysis a=ChartFrameAnalyzer.signal(cs);
            Intent o=new Intent(ACTION_ANALYSIS);o.setPackage(getPackageName());
            o.putExtra("score",a.score);o.putExtra("signal",a.signal);o.putExtra("reason",a.reason);
            o.putExtra("candles",cs.size());o.putExtra("captureActive",true);o.putExtra("regime",a.regime);o.putExtra("strategy",a.strategy);
            sendBroadcast(o);
        }finally{if(img!=null)img.close();}
    }

    public void onDestroy(){if(reader!=null)reader.close();if(projection!=null)projection.stop();super.onDestroy();}
    public IBinder onBind(Intent i){return null;}
}
