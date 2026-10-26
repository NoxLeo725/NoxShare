package com.noxleo.share;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;

public final class AppLanguage {
    private static JSONObject english;
    public static String preference(Context context){return context.getSharedPreferences("share",0).getString("language","system");}
    public static String system(Context context){return context.getResources().getConfiguration().getLocales().get(0).getLanguage();}
    public static String resolved(Context context){String value=preference(context);return "system".equals(value)?(system(context).startsWith("zh")?"zh":"en"):value;}
    public static void set(Context context,String value)throws IOException{
        if(!"system".equals(value)&&!"zh".equals(value)&&!"en".equals(value))throw new IOException("Invalid language");
        if(!context.getSharedPreferences("share",0).edit().putString("language",value).commit())throw new IOException("语言设置保存失败");
    }
    public static String text(Context context,String source){
        if(source==null)return "";if(!"en".equals(resolved(context)))return source;
        try{synchronized(AppLanguage.class){if(english==null){try(InputStream input=context.getAssets().open("translations.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){HttpServer.pipe(input,bytes);english=new JSONObject(bytes.toString("UTF-8"));}}if(source.matches("^[0-9]+ 个文件无法添加，请从本地存储重新选择$"))return source.substring(0,source.indexOf(' '))+" file(s) could not be added. Choose them from local storage.";return english.optString(source,source);}}
        catch(Exception ignored){return source;}
    }
}
