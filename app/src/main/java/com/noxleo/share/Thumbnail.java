package com.noxleo.share;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;

public final class Thumbnail {
    public static InputStream open(Context context,JSONObject file)throws Exception{
        if(file==null)return null;
        Uri uri=Uri.parse(file.getString("uri"));BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)return null;
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>960)options.inSampleSize*=2;
        Bitmap bitmap;try(InputStream in=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)return null;
        try{ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,82,output);return new ByteArrayInputStream(output.toByteArray());}finally{bitmap.recycle();}
    }
}
