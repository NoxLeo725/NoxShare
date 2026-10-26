package com.noxleo.share;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;

public class LinkPermissionInstrumentation extends FeatureInstrumentation {
    private ActivityMonitor monitor(String action,String scheme,String type)throws Exception{
        IntentFilter filter=new IntentFilter(action);if(scheme!=null)filter.addDataScheme(scheme);if(type!=null)filter.addDataType(type);filter.addCategory(Intent.CATEGORY_BROWSABLE);
        return addMonitor(filter,new ActivityResult(Activity.RESULT_OK,null),true);
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        target=getTargetContext();check(target.getPackageName().endsWith(".verify"),"Isolated test package");
        ShareStore store=ShareStore.get(target);store.setClearOnStop(true);store.onSharingStopped();store.setClearOnStop(false);
        MainActivity activity=(MainActivity)startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->"true".equals(js(activity,"!!window.NoxNative&&typeof linkifyText==='function'")),"WebView startup");
        JSONObject state=new JSONObject(new JSONTokener(js(activity,"NoxNative.state()")).nextValue().toString());check(state.has("permissions"),"Permission status exposed to app UI");
        check(!target.getPackageManager().canRequestPackageInstalls(),"Installation starts unapproved");
        ActivityMonitor settings=monitor(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,"package",null);
        js(activity,"NoxNative.requestInstallPermission()");await(()->settings.getHits()==1,"Installation settings intent");removeMonitor(settings);
        ActivityMonitor web=monitor(Intent.ACTION_VIEW,"https",null);
        js(activity,"NoxNative.openUrl('https://example.invalid/noxshare-test')");await(()->web.getHits()==1,"URL handed to external browser");
        js(activity,"NoxNative.openUrl('javascript:alert(1)')");await(()->"true".equals(js(activity,"document.getElementById('toast').textContent===t('仅支持 http 或 https 网页链接')")),"Unsafe URL rejected");check(web.getHits()==1,"Unsafe URL did not launch");removeMonitor(web);
        js(activity,"NoxNative.copy('NoxShare clipboard 验证')");
        byte[] bytes={1,2,3,4};store.receive("授权验证.apk","application/octet-stream","10.0.0.1",new ByteArrayInputStream(bytes),bytes.length,()->true);
        JSONObject apk=store.state().getJSONArray("files").getJSONObject(0);check("application/vnd.android.package-archive".equals(target.getContentResolver().getType(SharedFileProvider.uri(target,apk))),"APK MIME type normalized");
        try(ParcelFileDescriptor shell=getUiAutomation().executeShellCommand("appops set "+target.getPackageName()+" REQUEST_INSTALL_PACKAGES allow");InputStream in=new FileInputStream(shell.getFileDescriptor())){byte[] buffer=new byte[128];while(in.read(buffer)!=-1){}}
        await(()->target.getPackageManager().canRequestPackageInstalls(),"Test installation grant active");
        java.lang.reflect.Field pending=MainActivity.class.getDeclaredField("pendingInstall");pending.setAccessible(true);runOnMainSync(()->{try{pending.set(activity,apk.optString("id"));}catch(Exception e){throw new RuntimeException(e);}});
        ActivityMonitor installer=monitor(Intent.ACTION_VIEW,"content","application/vnd.android.package-archive");
        runOnMainSync(()->activity.onActivityResult(31,Activity.RESULT_CANCELED,null));await(()->installer.getHits()==1,"Returning from permission settings opens system installer");removeMonitor(installer);
        check(pending.get(activity)==null,"Pending APK cleared after return");store.setClearOnStop(true);store.onSharingStopped();store.setClearOnStop(false);runOnMainSync(activity::finish);
        result.putString("stream","PASS: real Android permission status and settings intent, HTTPS browser handoff, unsafe URL rejection, clipboard bridge, APK MIME, permission return -> system installer handoff (installation blocked by test monitor).");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
