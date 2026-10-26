package com.noxleo.share;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.nio.charset.StandardCharsets;

public class TestOpenActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);Uri uri=getIntent().getData();String result;
        try{
            String name="";long size=-1;
            try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c==null||!c.moveToFirst())throw new Exception("Missing metadata");name=c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));size=c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE));}
            byte[] bytes;try(java.io.InputStream input=getContentResolver().openInputStream(uri)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[128];int count;while((count=input.read(buffer))!=-1)out.write(buffer,0,count);bytes=out.toByteArray();}
            boolean readOnly=false;try(java.io.OutputStream ignored=getContentResolver().openOutputStream(uri,"w")){}catch(Exception expected){readOnly=true;}
            result=name+"|"+size+"|"+new String(bytes,StandardCharsets.UTF_8)+"|"+readOnly;
        }catch(Exception e){result="ERROR "+e;}
        sendBroadcast(new Intent("com.noxleo.share.VERIFY_OPEN").setPackage(uri.getAuthority().replace(".sharedfiles","")).putExtra("result",result));finish();
    }
}
