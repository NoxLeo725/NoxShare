package com.noxleo.share;

import java.io.*;
import java.util.*;

public final class MediaContent {
    public static final class Response implements AutoCloseable {
        public int status=200;
        public String type;
        public long length;
        public InputStream body;
        public final Map<String,String> headers=new LinkedHashMap<>();
        public String reason(){return status==206?"Partial Content":status==416?"Range Not Satisfiable":status==415?"Unsupported Media Type":"OK";}
        @Override public void close()throws IOException{body.close();}
    }
    public static String type(String name,String supplied){
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String[] extensions={"jpg","jpeg","png","gif","webp","bmp","avif","heic","heif","mp4","m4v","webm","mov","mkv","avi","3gp","ogv"};
        String[] types={"image/jpeg","image/jpeg","image/png","image/gif","image/webp","image/bmp","image/avif","image/heic","image/heif","video/mp4","video/mp4","video/webm","video/quicktime","video/x-matroska","video/x-msvideo","video/3gpp","video/ogg"};
        for(int i=0;i<extensions.length;i++)if(extensions[i].equals(ext))return types[i];
        for(String type:types)if(type.equals(supplied))return type;
        return null;
    }
    public static Response open(String name,String type,long size,String range,boolean head,SharingRouter.Opener opener)throws Exception{
        Response r=new Response();r.type=type(name,type);r.length=0;r.body=new ByteArrayInputStream(new byte[0]);
        r.headers.put("Cache-Control","no-store");r.headers.put("X-Content-Type-Options","nosniff");
        r.headers.put("Content-Security-Policy","default-src 'none'; sandbox");
        if(r.type==null){r.status=415;r.type="text/plain";return r;}
        long start=0,end=size-1;
        r.headers.put("Accept-Ranges",size>=0?"bytes":"none");
        // Ignore unsupported multi-ranges; a single range supports browser seeking.
        if(!head&&size>=0&&range!=null&&range.matches("bytes=[0-9]*-[0-9]*")){
            try{
                String[] parts=range.substring(6).split("-",-1);
                if(parts[0].isEmpty()){long suffix=Long.parseLong(parts[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,size-suffix);}
                else{start=Long.parseLong(parts[0]);if(!parts[1].isEmpty())end=Math.min(end,Long.parseLong(parts[1]));}
                if(start>=size||start>end)throw new IllegalArgumentException();
                r.status=206;r.headers.put("Content-Range","bytes "+start+"-"+end+"/"+size);
            }catch(IllegalArgumentException e){r.status=416;r.headers.put("Content-Range","bytes */"+size);return r;}
        }
        r.length=size<0?-1:end-start+1;
        if(head)return r;
        InputStream input=opener.open();if(input==null)throw new FileNotFoundException();
        try{
            long left=start;while(left>0){long skipped=input.skip(left);if(skipped>0)left-=skipped;else{if(input.read()<0)throw new EOFException();left--;}}
            if(r.length<0)r.body=input;
            else{final long count=r.length;r.body=new FilterInputStream(input){long remaining=count;
                @Override public int read()throws IOException{if(remaining==0)return -1;int b=in.read();if(b>=0)remaining--;return b;}
                @Override public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;if(remaining==0)return -1;int n=in.read(b,off,(int)Math.min(len,remaining));if(n>0)remaining-=n;return n;}
                @Override public long skip(long n)throws IOException{long used=in.skip(Math.min(Math.max(0,n),remaining));remaining-=used;return used;}
            };}
            return r;
        }catch(Exception e){input.close();throw e;}
    }
}
