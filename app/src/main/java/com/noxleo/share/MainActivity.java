package com.noxleo.share;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private WebView web;
    private ShareStore store;
    private String pendingSave;
    private String pendingInstall;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle b){
        super.onCreate(b);if(b!=null){pendingSave=b.getString("pendingSave");pendingInstall=b.getString("pendingInstall");}store=ShareStore.get(this);web=new WebView(this);web.setBackgroundColor(Color.rgb(246,245,251));
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(root);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom);}return insets;});
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"NoxNative");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){String url=req.getUrl().toString();if(req.isForMainFrame()&&("http".equals(req.getUrl().getScheme())||"https".equals(req.getUrl().getScheme()))){openWeb(url);return true;}return !url.startsWith("file:///android_asset/index.html");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
                Uri url=req.getUrl();
                if("https".equals(url.getScheme())&&"noxshare.local".equals(url.getHost())){
                    try{
                        JSONObject f=store.find(url.getLastPathSegment());String path=url.getPath();
                        if(f!=null&&path!=null&&path.startsWith("/media/")){
                            String range=null;for(java.util.Map.Entry<String,String> h:req.getRequestHeaders().entrySet())if("range".equalsIgnoreCase(h.getKey()))range=h.getValue();
                            MediaContent.Response r=MediaContent.open(f.optString("name"),f.optString("type"),f.optLong("size",-1),range,"HEAD".equals(req.getMethod()),()->store.open(f));
                            if(r.length>=0)r.headers.put("Content-Length",String.valueOf(r.length));
                            return new WebResourceResponse(r.type,null,r.status,r.reason(),r.headers,r.body);
                        }
                        if(f!=null&&path!=null&&path.startsWith("/preview/")){java.io.InputStream image=Thumbnail.open(MainActivity.this,f);if(image!=null)return new WebResourceResponse("image/jpeg",null,image);}
                    }catch(Exception ignored){}
                    return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",java.util.Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view,req);
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }
    private void update(){runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.onNativeUpdate&&onNativeUpdate()",null);});}
    private void error(String message){runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.onNativeError&&onNativeError("+JSONObject.quote(AppLanguage.text(this,message))+")",null);});}
    private JSONObject permissions()throws JSONException{return new JSONObject().put("install",getPackageManager().canRequestPackageInstalls()).put("notifications",getSystemService(NotificationManager.class).areNotificationsEnabled());}
    private void openWeb(String value){
        Uri uri=Uri.parse(value);String scheme=uri.getScheme();if(!("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))||uri.getHost()==null||uri.getHost().isEmpty()){error("仅支持 http 或 https 网页链接");return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){error("未找到可打开网页的浏览器");}catch(Exception e){error("无法打开链接，请复制后使用浏览器打开");}
    }
    private void installPermission(){try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),31);}catch(Exception e){pendingInstall=null;error("无法进入安装授权，请在系统应用设置中允许安装未知应用");}}
    private void launchFile(String id){
        JSONObject f=store.find(id);if(f==null){error("文件已移除");return;}Uri uri=SharedFileProvider.uri(this,f);String type=getContentResolver().getType(uri);
        try{Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);view.setClipData(ClipData.newRawUri("NoxShare",uri));startActivity(view);}
        catch(ActivityNotFoundException e){error("没有可打开此文件的应用，可先保存到手机");}catch(Exception e){error("无法打开文件，请重新添加或先保存到手机");}
    }
    private void openSharedFile(String id){
        JSONObject f=store.find(id);if(f==null){error("文件已移除");return;}
        if("application/vnd.android.package-archive".equals(getContentResolver().getType(SharedFileProvider.uri(this,f)))&&!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle(AppLanguage.text(this,"允许安装应用")).setMessage(AppLanguage.text(this,"安装 APK 需要允许 NoxShare 安装未知应用。授权返回后，将打开系统安装确认页面。")).setNegativeButton(AppLanguage.text(this,"取消"),(dialog,which)->pendingInstall=null).setPositiveButton(AppLanguage.text(this,"去授权"),(dialog,which)->{pendingInstall=id;installPermission();}).show();return;
        }launchFile(id);
    }
    private final class Bridge {
        @JavascriptInterface public String state(){try{String s=store.state().put("permissions",permissions()).put("language",AppLanguage.preference(MainActivity.this)).put("resolvedLanguage",AppLanguage.resolved(MainActivity.this)).toString();if(!store.error.isEmpty()){error(store.error);store.error="";}return s;}catch(Exception e){return "{\"running\":false,\"files\":[],\"messages\":[],\"urls\":[]}";}}
        @JavascriptInterface public String getLanguage(){return AppLanguage.preference(MainActivity.this);}
        @JavascriptInterface public String systemLanguage(){return AppLanguage.system(MainActivity.this);}
        @JavascriptInterface public String setLanguage(String value){try{AppLanguage.set(MainActivity.this,value);runOnUiThread(()->{if(ShareService.running)startService(new Intent(MainActivity.this,ShareService.class).setAction("REFRESH_LANGUAGE"));update();});return "";}catch(Exception e){return AppLanguage.text(MainActivity.this,"语言设置保存失败");}}
        @JavascriptInterface public void pick(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,10);});}
        @JavascriptInterface public void pickImages(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,10);});}
        @JavascriptInterface public void toggle(){runOnUiThread(()->{if(ShareService.running)stopService(new Intent(MainActivity.this,ShareService.class));else{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);startForegroundService(new Intent(MainActivity.this,ShareService.class));}update();});}
        @JavascriptInterface public String remove(String ids){try{store.remove(new JSONArray(ids));update();return "";}catch(Exception e){return "移除失败，请重试";}}
        @JavascriptInterface public String setClearOnStop(boolean enabled){try{store.setClearOnStop(enabled);return "";}catch(Exception e){return "设置保存失败，请重试";}}
        @JavascriptInterface public void openFile(String id){runOnUiThread(()->openSharedFile(id));}
        @JavascriptInterface public void openUrl(String url){runOnUiThread(()->openWeb(url));}
        @JavascriptInterface public void requestInstallPermission(){runOnUiThread(()->{pendingInstall=null;installPermission();});}
        @JavascriptInterface public void appSettings(){runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){error("请手动进入系统应用设置");}});}
        @JavascriptInterface public void notificationSettings(){runOnUiThread(()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);return;}try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}catch(Exception e){error("请手动进入系统通知设置");}});}
        @JavascriptInterface public void copy(String text){runOnUiThread(()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NoxShare",text)));}
        @JavascriptInterface public void saveFile(String id){runOnUiThread(()->{JSONObject f=store.find(id);if(f==null){error("文件已移除");return;}pendingSave=id;String type=f.optString("type","application/octet-stream");if(!type.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+"))type="application/octet-stream";try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type).putExtra(Intent.EXTRA_TITLE,f.optString("name","文件")),11);}catch(ActivityNotFoundException e){pendingSave=null;error("未找到文件保存应用");}});}
        @JavascriptInterface public void openLocal(){io.execute(()->{
            if(!ShareService.running){error("请先开启共享");return;}
            if(!HttpServer.probe("127.0.0.1",8787)){store.serviceCheck="本机访问失败，请重新开启共享";error("共享服务未响应，请关闭后重新开启共享");update();return;}
            store.serviceCheck="本机访问正常";
            runOnUiThread(()->{if(isDestroyed())return;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("http://127.0.0.1:8787/")).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){error("未找到浏览器，请手动打开 http://127.0.0.1:8787");}});
        });}
        @JavascriptInterface public String send(String text){try{store.addMessage(text,"host");update();return "";}catch(Exception e){return e.getMessage();}}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==31){String id=pendingInstall;pendingInstall=null;update();if(id!=null){if(getPackageManager().canRequestPackageInstalls())launchFile(id);else error("未授予安装权限，可在设置中重新授权");}return;}
        if(request==11){String id=pendingSave;pendingSave=null;if(result!=RESULT_OK||data==null||data.getData()==null||id==null)return;Uri destination=data.getData();io.execute(()->{JSONObject f=store.find(id);if(f==null){error("文件已移除");return;}try(java.io.InputStream in=store.open(f);java.io.OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(in==null||out==null)throw new java.io.IOException();HttpServer.pipe(in,out);error("文件已保存");}catch(Exception e){error("保存未完成，请重新选择保存位置");}});return;}
        if(request!=10||result!=RESULT_OK||data==null)return;
        io.execute(()->{int failed=0;int count=data.getClipData()!=null?data.getClipData().getItemCount():1;for(int i=0;i<count;i++){Uri uri=data.getClipData()!=null?data.getClipData().getItemAt(i).getUri():data.getData();if(uri==null)continue;try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);store.add(uri);}catch(Exception e){failed++;}}update();if(failed>0)error(failed+" 个文件无法添加，请从本地存储重新选择");});
    }
    @Override protected void onResume(){super.onResume();if(web!=null)update();if(ShareService.running)startService(new Intent(this,ShareService.class).setAction("REFRESH_LANGUAGE"));}
    @Override protected void onPause(){if(web!=null)web.evaluateJavascript("window.pauseMedia&&pauseMedia()",null);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("pendingSave",pendingSave);out.putString("pendingInstall",pendingInstall);super.onSaveInstanceState(out);}
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);update();}
    @Override protected void onDestroy(){web.removeJavascriptInterface("NoxNative");web.destroy();io.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){web.evaluateJavascript("window.closeTopDialog&&closeTopDialog()",result->{if(!"true".equals(result))moveTaskToBack(true);});}
}
