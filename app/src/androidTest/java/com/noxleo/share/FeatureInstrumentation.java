package com.noxleo.share;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class FeatureInstrumentation extends Instrumentation {
    protected Context target;
    protected void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    protected interface Condition {boolean ok()throws Exception;}
    protected void await(Condition condition,String message)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(condition.ok())return;Thread.sleep(100);}throw new AssertionError(message);}
    protected String js(MainActivity activity,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] answer={null};runOnMainSync(()->{try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("web");field.setAccessible(true);((WebView)field.get(activity)).evaluateJavascript(script,result->{answer[0]=result;done.countDown();});}catch(Exception e){answer[0]="ERROR "+e;done.countDown();}});check(done.await(5,TimeUnit.SECONDS),"JS callback");return answer[0];
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        target=getTargetContext();check(target.getPackageName().endsWith(".verify"),"Run only in isolated .verify application");
        ShareStore store=ShareStore.get(target);store.setClearOnStop(true);store.onSharingStopped();store.setClearOnStop(false);
        MainActivity activity=(MainActivity)startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->"true".equals(js(activity,"!!window.NoxNative&&!!document.getElementById('message-input')")),"WebView startup");
        File original=new File(target.getCacheDir(),"noxshare-test-original.txt");try(FileOutputStream output=new FileOutputStream(original)){output.write("original retained".getBytes(StandardCharsets.UTF_8));}
        store.add(Uri.fromFile(original));
        byte[] bytes="default app bytes".getBytes(StandardCharsets.UTF_8);
        store.receive("默认打开.nox","application/x-noxshare-test","10.0.0.2",new ByteArrayInputStream(bytes),bytes.length,()->true);
        JSONArray files=store.state().getJSONArray("files");JSONObject received=files.getJSONObject(1);String id=received.getString("id");File copy=new File(target.getFilesDir(),"uploads/"+id);
        CountDownLatch opened=new CountDownLatch(1);String[] openedResult={null};BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){openedResult[0]=intent.getStringExtra("result");opened.countDown();}};
        target.registerReceiver(receiver,new IntentFilter("com.noxleo.share.VERIFY_OPEN"),Context.RECEIVER_EXPORTED);
        try{js(activity,"NoxNative.openFile("+JSONObject.quote(id)+")");check(opened.await(10,TimeUnit.SECONDS),"Default application opens");check(("默认打开.nox|"+bytes.length+"|default app bytes|true").equals(openedResult[0]),"External app reads original bytes/metadata with read-only grant: "+openedResult[0]);}finally{target.unregisterReceiver(receiver);}
        check("\"\"".equals(js(activity,"NoxNative.setClearOnStop(false)")),"Settings bridge saves");
        target.startForegroundService(new Intent(target,ShareService.class));await(()->ShareService.running,"First sharing startup");store.addMessage("clear text","host");target.stopService(new Intent(target,ShareService.class));await(()->!ShareService.running&&store.state().getJSONArray("messages").length()==0,"Stop clears messages");
        check(store.state().getJSONArray("files").length()==2&&original.exists()&&copy.exists(),"Setting off retains originals and received files");
        check("\"\"".equals(js(activity,"NoxNative.setClearOnStop(true)")),"Settings bridge enables cleanup");check(target.getSharedPreferences("share",0).getBoolean("clearOnStop",false),"Preference persisted");
        target.startForegroundService(new Intent(target,ShareService.class));await(()->ShareService.running,"Second sharing startup");store.addMessage("also clear","host");target.stopService(new Intent(target,ShareService.class));await(()->!ShareService.running&&store.state().getJSONArray("files").length()==0,"Stop cleans sharing list");
        check(original.exists()&&!copy.exists()&&store.state().getJSONArray("messages").length()==0,"Cleanup deletes managed copy, preserves original, clears messages");
        try{target.getContentResolver().openInputStream(SharedFileProvider.uri(target,received));throw new AssertionError("Removed file still opens");}catch(FileNotFoundException expected){}
        store.setClearOnStop(false);original.delete();runOnMainSync(activity::finish);
        result.putString("stream","PASS: Android WebView bridge -> default external app, original bytes/metadata, read-only URI grant, setting persistence, actual service shutdown with cleanup on/off, source preservation, removed file access denied.");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
