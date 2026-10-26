package com.noxleo.share;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;
import java.io.*;

public class LanguageInstrumentation extends FeatureInstrumentation {
    private String notificationTitle(){for(StatusBarNotification notification:target.getSystemService(NotificationManager.class).getActiveNotifications())if(notification.getId()==1)return notification.getNotification().extras.getString(Notification.EXTRA_TITLE,"");return "";}
    @Override public void onStart(){Bundle result=new Bundle();try{
        target=getTargetContext();check(target.getPackageName().endsWith(".verify"),"Isolated language test");
        ShareStore store=ShareStore.get(target);store.setClearOnStop(true);store.onSharingStopped();store.setClearOnStop(false);AppLanguage.set(target,"system");
        MainActivity activity=(MainActivity)startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->"true".equals(js(activity,"!!window.NoxI18n&&!!document.getElementById('language-choice')")),"Localized WebView ready");
        check(AppLanguage.preference(target).equals("system"),"System language preference");
        js(activity,"NoxI18n.set('en')");await(()->"\"en\"".equals(js(activity,"document.documentElement.lang")),"English interface");check(AppLanguage.resolved(target).equals("en"),"Native English setting");check("Allow app installation".equals(AppLanguage.text(target,"允许安装应用")),"English native permission dialog text");
        target.startForegroundService(new Intent(target,ShareService.class));await(()->ShareService.running&&notificationTitle().equals("NoxShare · Sharing active"),"English sharing notification");
        store.addMessage("中文消息，不应翻译","host");byte[] bytes={3,2,1};store.receive("中文文件名.txt","text/plain","10.0.0.5",new ByteArrayInputStream(bytes),bytes.length,()->true);
        js(activity,"NoxI18n.set('zh')");await(()->"\"zh-CN\"".equals(js(activity,"document.documentElement.lang"))&&notificationTitle().equals("NoxShare · 共享进行中"),"Chinese interface and live notification refresh");
        check("允许安装应用".equals(AppLanguage.text(target,"允许安装应用")),"Chinese native dialog text");check(ShareService.running,"Language switch keeps sharing active");
        JSONObject state=store.state();check(state.getJSONArray("messages").getJSONObject(0).getString("text").equals("中文消息，不应翻译"),"Original message preserved");check(state.getJSONArray("files").getJSONObject(0).getString("name").equals("中文文件名.txt"),"Original filename preserved");
        js(activity,"NoxI18n.set('en')");await(()->notificationTitle().equals("NoxShare · Sharing active"),"Notification switches back to English");check(target.getSharedPreferences("share",0).getString("language","").equals("en"),"Language persisted");
        String error=js(activity,"NoxNative.setLanguage('invalid')");check(error!=null&&!error.equals("\"\""),"Invalid language rejected");check(AppLanguage.preference(target).equals("en"),"Invalid preference not persisted");
        js(activity,"NoxI18n.set('system')");await(()->AppLanguage.preference(target).equals("system"),"Return to system preference");
        store.setClearOnStop(true);target.stopService(new Intent(target,ShareService.class));await(()->!ShareService.running&&store.state().getJSONArray("files").length()==0,"Cleanup test data");store.setClearOnStop(false);runOnMainSync(activity::finish);
        result.putString("stream","PASS: actual Android system/English/Chinese preferences, immediate WebView updates, native permission strings, live notification switching, persisted settings, uninterrupted sharing, original message/filename preservation.");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
