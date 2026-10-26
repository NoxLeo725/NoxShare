package com.noxleo.share;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class ShareService extends Service {
    public static volatile boolean running=false;
    private static volatile int port=8787;
    private HttpServer server;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private ShareStore store;
    private SharingRouter router;
    private volatile boolean acceptingUploads;
    private final Handler heartbeat=new Handler(Looper.getMainLooper());
    private final Runnable renewWake=new Runnable(){@Override public void run(){if(running&&wake!=null){wake.acquire(10*60*1000L);heartbeat.postDelayed(this,5*60*1000L);}}};
    public static JSONArray urls(Context context){
        JSONArray urls=new JSONArray();if(!running)return urls;
        try{ConnectivityManager cm=(ConnectivityManager)context.getSystemService(CONNECTIVITY_SERVICE);Set<String> addresses=new LinkedHashSet<>();
            for(Network network:cm.getAllNetworks()){NetworkCapabilities cap=cm.getNetworkCapabilities(network);if(cap==null||(!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;LinkProperties lp=cm.getLinkProperties(network);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())addresses.add(a.getAddress().getHostAddress());}
            if(addresses.isEmpty()){Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){NetworkInterface n=all.nextElement();String name=n.getName();if(!n.isUp()||n.isLoopback()||!(name.startsWith("wlan")||name.startsWith("ap")||name.startsWith("swlan")||name.startsWith("eth")))continue;Enumeration<InetAddress> a=n.getInetAddresses();while(a.hasMoreElements()){InetAddress ip=a.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())addresses.add(ip.getHostAddress());}}}
            for(String ip:addresses)urls.put("http://"+ip+":"+port);
        }catch(Exception ignored){}return urls;
    }
    @Override public void onCreate(){super.onCreate();store=ShareStore.get(this);NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("sharing",AppLanguage.text(this,"局域网共享"),NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"STOP".equals(intent.getAction())){shutdown();stopSelf();return START_NOT_STICKY;}
        if(running){getSystemService(NotificationManager.class).notify(1,notification());return START_NOT_STICKY;}
        Notification notification=notification();
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(1,notification);
            router=new SharingRouter(new SharingRouter.Backend(){
                public boolean running(){return ShareService.running;}
                public String state(String clientIp)throws Exception{return store.state().put("clientIp",clientIp).toString();}
                public SharingRouter.FileEntry find(String id)throws Exception{JSONObject f=store.find(id);return f==null?null:new SharingRouter.FileEntry(f.getString("name"),f.optString("type"),f.optLong("size",-1),()->store.open(f),()->Thumbnail.open(ShareService.this,f));}
                public InputStream asset(String name)throws Exception{return getAssets().open(name);}
                public void message(String text,String sender)throws Exception{store.addMessage(text,"guest",sender);}
                public void upload(String name,String type,String uploader,InputStream input,long length)throws Exception{store.receive(name,type,uploader,input,length,()->acceptingUploads&&ShareService.running);}
            });
            server=new HttpServer(8787,router);port=server.port();acceptingUploads=true;running=true;store.error="";store.serviceCheck="检测中";
            HttpServer started=server;
            new Thread(()->{boolean healthy=HttpServer.probe("127.0.0.1",port);if(running&&server==started)store.serviceCheck=healthy?"本机访问正常":"本机访问失败，请重新开启共享";},"NoxShare-health").start();
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NoxShare:Sharing");wake.setReferenceCounted(false);renewWake.run();
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifi=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"NoxShare");wifi.acquire();
        }catch(Exception e){store.error="无法开启共享，请检查网络或端口是否被占用";shutdown();stopSelf();}
        return START_NOT_STICKY;
    }
    private Notification notification(){
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("sharing",AppLanguage.text(this,"局域网共享"),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ShareService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"sharing").setSmallIcon(R.drawable.ic_launcher).setContentTitle(AppLanguage.text(this,"NoxShare · 共享进行中")).setContentText(AppLanguage.text(this,"同一网络内可下载文件、互发文字")).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,AppLanguage.text(this,"关闭共享"),stop).build()).build();
    }
    private void shutdown(){boolean wasRunning=running;acceptingUploads=false;running=false;store.serviceCheck="未启动";heartbeat.removeCallbacks(renewWake);if(server!=null){server.close();server=null;}if(wasRunning)store.onSharingStopped();if(wake!=null&&wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();stopForeground(STOP_FOREGROUND_REMOVE);}
    @Override public void onDestroy(){shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
