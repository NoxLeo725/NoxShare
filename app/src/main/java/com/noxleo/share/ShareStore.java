package com.noxleo.share;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.*;
import java.io.*;
import java.util.*;

public final class ShareStore {
    private static ShareStore instance;
    public static synchronized ShareStore get(Context c){if(instance==null)instance=new ShareStore(c.getApplicationContext());return instance;}
    private final android.app.Application context;
    private final List<JSONObject> files=new ArrayList<>();
    private final List<JSONObject> messages=new ArrayList<>();
    private long messageId=0;
    public volatile String error="";
    public volatile String serviceCheck="未启动";
    private ShareStore(Context c){context=(android.app.Application)c.getApplicationContext();try{JSONArray a=new JSONArray(c.getSharedPreferences("share",0).getString("files","[]"));for(int i=0;i<a.length();i++)files.add(a.getJSONObject(i));}catch(Exception ignored){}}
    public synchronized void add(Uri uri)throws Exception{
        for(JSONObject f:files)if(f.getString("uri").equals(uri.toString()))return;
        String name="未命名文件";long size=-1;
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){name=cursor.getString(0);if(!cursor.isNull(1))size=cursor.getLong(1);}
        }
        if(name==null||name.isEmpty())name="未命名文件";
        JSONObject f=new JSONObject();f.put("id",UUID.randomUUID().toString());f.put("uri",uri.toString());f.put("name",name);f.put("size",size);f.put("type",context.getContentResolver().getType(uri));f.put("added",System.currentTimeMillis());f.put("uploader","主机");files.add(f);save();
    }
    public void receive(String name,String type,String uploader,InputStream input,long length,java.util.function.BooleanSupplier active)throws Exception{
        File received=UploadStorage.receive(new File(context.getFilesDir(),"uploads"),input,length);boolean saved=false;
        try{synchronized(this){if(!active.getAsBoolean())throw new IllegalStateException("共享已关闭");JSONObject f=new JSONObject();f.put("id",received.getName());f.put("uri",Uri.fromFile(received).toString());f.put("name",name);f.put("size",length);f.put("type",type);f.put("added",System.currentTimeMillis());f.put("uploader",uploader);f.put("uploaded",true);files.add(f);save();saved=true;}}
        finally{if(!saved)received.delete();}
    }
    public InputStream open(JSONObject f)throws Exception{return context.getContentResolver().openInputStream(Uri.parse(f.getString("uri")));}
    private void save(){context.getSharedPreferences("share",0).edit().putString("files",new JSONArray(files).toString()).apply();}
    public boolean clearOnStop(){return context.getSharedPreferences("share",0).getBoolean("clearOnStop",false);}
    public void setClearOnStop(boolean enabled)throws IOException{if(!context.getSharedPreferences("share",0).edit().putBoolean("clearOnStop",enabled).commit())throw new IOException("设置保存失败");}
    public synchronized void onSharingStopped(){
        messages.clear();if(!clearOnStop())return;
        JSONArray ids=new JSONArray();for(JSONObject f:files)ids.put(f.optString("id"));
        try{remove(ids);}catch(Exception e){error="部分接收文件未能清理，请在文件列表重试";}
    }
    public synchronized void remove(JSONArray ids)throws Exception{
        Set<String> remove=new HashSet<>();for(int i=0;i<ids.length();i++)remove.add(ids.getString(i));
        IOException failure=null;
        Iterator<JSONObject> it=files.iterator();while(it.hasNext()){JSONObject f=it.next();if(remove.contains(f.getString("id"))){try{if(f.optBoolean("uploaded")){File dir=new File(context.getFilesDir(),"uploads");File received=new File(dir,f.getString("id"));if(!received.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new IOException("无效接收路径");if(received.exists()&&!received.delete())throw new IOException("接收副本暂时无法移除");}else{try{context.getContentResolver().releasePersistableUriPermission(Uri.parse(f.getString("uri")),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}context.revokeUriPermission(SharedFileProvider.uri(context,f),Intent.FLAG_GRANT_READ_URI_PERMISSION);it.remove();}catch(IOException e){failure=e;}}}save();if(failure!=null)throw failure;
    }
    public synchronized JSONObject find(String id){for(JSONObject f:files)if(f.optString("id").equals(id))return f;return null;}
    public synchronized void addMessage(String text,String from)throws Exception{
        addMessage(text,from,"主机");
    }
    public synchronized void addMessage(String text,String from,String sender)throws Exception{
        text=text.trim();if(text.isEmpty()||text.length()>4000)throw new IllegalArgumentException("文字限 1–4000 字");
        if(!ShareService.running)throw new IllegalStateException("请先开启共享");
        JSONObject m=new JSONObject();m.put("id",String.valueOf(++messageId));m.put("text",text);m.put("from",from);m.put("sender",sender);m.put("time",System.currentTimeMillis());messages.add(m);while(messages.size()>100)messages.remove(0);
    }
    public synchronized void clearMessages(){messages.clear();}
    public synchronized JSONObject state()throws Exception{
        JSONObject obj=new JSONObject();JSONArray visible=new JSONArray();
        for(JSONObject f:files){JSONObject v=new JSONObject();for(String key:new String[]{"id","name","size","type","added"})v.put(key,f.opt(key));v.put("uploader",f.optString("uploader","主机"));v.put("uploaded",f.optBoolean("uploaded",false));visible.put(v);}
        obj.put("files",visible);obj.put("messages",new JSONArray(messages));obj.put("running",ShareService.running);obj.put("urls",ShareService.urls(context));obj.put("network",NetworkInfo.read(context));obj.put("serviceCheck",serviceCheck);obj.put("error",error);obj.put("clearOnStop",clearOnStop());return obj;
    }
}
