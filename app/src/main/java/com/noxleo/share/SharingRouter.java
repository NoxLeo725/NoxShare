package com.noxleo.share;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class SharingRouter implements HttpServer.Handler {
    public interface Opener { InputStream open() throws Exception; }
    public static final class FileEntry {
        public final String name;
        public final Opener opener;
        public final Opener preview;
        public final String type;
        public final long size;
        public FileEntry(String name,Opener opener){this(name,opener,null);}
        public FileEntry(String name,Opener opener,Opener preview){this(name,"",-1,opener,preview);}
        public FileEntry(String name,String type,long size,Opener opener,Opener preview){this.name=name;this.type=type;this.size=size;this.opener=opener;this.preview=preview;}
    }
    public interface Backend {
        boolean running();
        String state(String clientIp) throws Exception;
        FileEntry find(String id) throws Exception;
        InputStream asset(String name) throws Exception;
        void message(String text,String sender) throws Exception;
        void upload(String name,String type,String uploader,InputStream input,long length) throws Exception;
    }
    private final Backend backend;
    private long lastGuestMessage;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    public SharingRouter(Backend backend){this.backend=backend;}
    @Override public void handle(HttpServer.Request req,OutputStream out)throws Exception {
        if(!backend.running()){HttpServer.text(out,503,"Sharing stopped");return;}
        String host=req.headers.getOrDefault("host","");
        if(!host.matches("(?:localhost|(?:[0-9]{1,3}\\.){3}[0-9]{1,3})(?::[0-9]{1,5})?")){HttpServer.text(out,403,"Use the local IP address");return;}
        String target=req.target.substring(1),path=target.split("\\?",2)[0];
        if("POST".equals(req.method)&&"api/upload".equals(path)){
            if(!"1".equals(req.headers.get("x-noxshare"))){HttpServer.text(out,403,"Forbidden");return;}
            String origin=req.headers.get("origin");if(origin!=null&&!origin.equals("http://"+host)){HttpServer.text(out,403,"Forbidden");return;}
            String name;
            try{name=URLDecoder.decode(req.headers.getOrDefault("x-file-name",""),"UTF-8").replaceAll("[\\\\/\\x00-\\x1f\\x7f]","_").trim();}catch(IllegalArgumentException e){HttpServer.text(out,400,"文件名无效");return;}
            if(name.isEmpty()||name.length()>255||name.equals(".")||name.equals("..")){HttpServer.text(out,400,"文件名无效或过长");return;}
            if(!uploads.tryAcquire()){HttpServer.text(out,429,"主机正在接收其他文件，请稍后重试");return;}
            try{backend.upload(name,req.headers.getOrDefault("content-type","application/octet-stream"),req.remoteAddress,req.bodyStream,req.contentLength);HttpServer.text(out,201,"上传完成");}
            catch(UploadStorage.FullException e){HttpServer.text(out,507,e.getMessage());}
            catch(IllegalStateException e){HttpServer.text(out,503,"共享已关闭，请重新连接");}
            catch(IOException e){HttpServer.text(out,500,"上传未完成，请检查主机存储空间后重试");}
            finally{uploads.release();}return;
        }
        if("POST".equals(req.method)&&"api/messages".equals(path)){
            if(!"1".equals(req.headers.get("x-noxshare"))){HttpServer.text(out,403,"Forbidden");return;}
            String origin=req.headers.get("origin");if(origin!=null&&!origin.equals("http://"+req.headers.get("host"))){HttpServer.text(out,403,"Forbidden");return;}
            String text=req.body.trim();if(text.isEmpty()||text.length()>4000){HttpServer.text(out,400,"Text must contain 1 to 4000 characters");return;}
            synchronized(this){long now=System.currentTimeMillis();if(now-lastGuestMessage<250){HttpServer.text(out,429,"Please wait");return;}lastGuestMessage=now;}
            backend.message(text,req.remoteAddress);HttpServer.text(out,200,"OK");return;
        }
        if(("GET".equals(req.method)||"HEAD".equals(req.method))&&path.startsWith("media/")){
            FileEntry f=backend.find(path.substring(6));if(f==null){HttpServer.text(out,404,"File no longer shared");return;}
            MediaContent.Response media;try{media=MediaContent.open(f.name,f.type,f.size,req.headers.get("range"),"HEAD".equals(req.method),f.opener);}catch(Exception e){HttpServer.text(out,404,"File unavailable");return;}
            try(MediaContent.Response r=media){StringBuilder headers=new StringBuilder();for(Map.Entry<String,String> h:r.headers.entrySet())headers.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");HttpServer.headers(out,r.status,r.type,r.length,headers.toString());if(!"HEAD".equals(req.method))HttpServer.pipe(r.body,out);}return;
        }
        if(!"GET".equals(req.method)){HttpServer.text(out,405,"Method not allowed");return;}
        if("health".equals(path)){HttpServer.text(out,200,"NOXSHARE_OK");return;}
        if("api/state".equals(path)){HttpServer.bytes(out,200,"application/json; charset=utf-8",backend.state(req.remoteAddress).getBytes(StandardCharsets.UTF_8));return;}
        if(path.startsWith("preview/")){
            FileEntry f=backend.find(path.substring(8));if(f==null||f.preview==null){HttpServer.text(out,404,"No preview");return;}
            try(InputStream in=f.preview.open()){if(in==null){HttpServer.text(out,415,"No preview");return;}HttpServer.headers(out,200,"image/jpeg",-1,null);HttpServer.pipe(in,out);}return;
        }
        if(path.startsWith("download/")){
            FileEntry f=backend.find(path.substring(9));if(f==null){HttpServer.text(out,404,"File no longer shared");return;}
            InputStream input;try{input=f.opener.open();if(input==null)throw new IOException();}catch(Exception e){HttpServer.text(out,404,"File unavailable. Ask the host to add it again.");return;}
            try(InputStream in=input){HttpServer.headers(out,200,"application/octet-stream",-1,disposition(f.name));HttpServer.pipe(in,out);}return;
        }
        if("bundle".equals(path)){
            String query=target.contains("?")?target.substring(target.indexOf('?')+1):"";if(!query.startsWith("ids=")){HttpServer.text(out,400,"Select files");return;}
            String[] ids;try{ids=URLDecoder.decode(query.substring(4),"UTF-8").split(",");}catch(IllegalArgumentException e){HttpServer.text(out,400,"Invalid file selection");return;}
            if(ids.length>200){HttpServer.text(out,400,"Select at most 200 files");return;}
            List<FileEntry> items=new ArrayList<>();List<InputStream> streams=new ArrayList<>();
            try{for(String id:new LinkedHashSet<>(Arrays.asList(ids))){FileEntry f=backend.find(id);if(f==null)throw new IOException();InputStream in=f.opener.open();if(in==null)throw new IOException();streams.add(in);items.add(f);}}
            catch(Exception e){for(InputStream in:streams)try{in.close();}catch(Exception ignored){}HttpServer.text(out,404,"Some files are unavailable. Refresh and try again.");return;}
            try{HttpServer.headers(out,200,"application/zip",-1,disposition("NoxShare.zip"));ZipOutputStream zip=new ZipOutputStream(out);Set<String> names=new HashSet<>();
                for(int i=0;i<items.size();i++){String original=items.get(i).name.replaceAll("[\\\\/\\x00-\\x1f]","_");if(original.equals(".")||original.equals(".."))original="file";String name=original;int n=2;while(!names.add(name))name="("+(n++)+") "+original;zip.putNextEntry(new ZipEntry(name));HttpServer.pipe(streams.get(i),zip);zip.closeEntry();}zip.finish();zip.flush();
            }finally{for(InputStream in:streams)try{in.close();}catch(Exception ignored){}}return;
        }
        String file=path.isEmpty()?"index.html":path;
        if(!Arrays.asList("index.html","style.css","app.js","qrcode.js","i18n.js","i18n-translations.js").contains(file)){HttpServer.text(out,404,"Not found");return;}
        String mime=file.endsWith("css")?"text/css":file.endsWith("js")?"application/javascript":"text/html";
        try(InputStream in=backend.asset(file)){ByteArrayOutputStream data=new ByteArrayOutputStream();HttpServer.pipe(in,data);HttpServer.headers(out,200,mime+"; charset=utf-8",data.size(),"Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; media-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'\r\n");out.write(data.toByteArray());}
    }
    private static String disposition(String name)throws Exception{return "Content-Disposition: attachment; filename=\"download\"; filename*=UTF-8''"+URLEncoder.encode(name,"UTF-8").replace("+","%20")+"\r\n";}
}
