package com.noxleo.share;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public final class SharedFileProvider extends ContentProvider {
    public static Uri uri(Context context,JSONObject file){return new Uri.Builder().scheme("content").authority(context.getPackageName()+".sharedfiles").appendPath("files").appendPath(file.optString("id")).appendPath(file.optString("name","文件")).build();}
    private JSONObject find(Uri uri)throws FileNotFoundException{
        List<String> parts=uri.getPathSegments();
        if(parts.size()!=3||!"files".equals(parts.get(0)))throw new FileNotFoundException("Invalid file");
        JSONObject file=ShareStore.get(getContext()).find(parts.get(1));if(file==null)throw new FileNotFoundException("File removed");return file;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){try{JSONObject f=find(uri);if(f.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk"))return "application/vnd.android.package-archive";String type=f.optString("type","");
        if(!type.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")||"application/octet-stream".equals(type)){
            String name=f.optString("name"),ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }return type==null?"application/octet-stream":type;
    }catch(FileNotFoundException e){return "application/octet-stream";}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        if(projection==null)projection=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor cursor=new MatrixCursor(projection);try{JSONObject f=find(uri);Object[] row=new Object[projection.length];for(int i=0;i<projection.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(projection[i]))row[i]=f.optString("name","文件");
            else if(OpenableColumns.SIZE.equals(projection[i]))row[i]=f.optLong("size",-1)<0?null:f.optLong("size");
        }cursor.addRow(row);}catch(FileNotFoundException ignored){}return cursor;
    }
    private Uri source(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return Uri.parse(find(uri).optString("uri"));}
    @Override public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException{return getContext().getContentResolver().openAssetFileDescriptor(source(uri,mode),"r");}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return getContext().getContentResolver().openFileDescriptor(source(uri,mode),"r");}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String s,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String s,String[] args){throw new UnsupportedOperationException("Read only");}
}
