package com.noxleo.share;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class HttpServer implements AutoCloseable {
    public interface Handler { void handle(Request request, OutputStream out) throws Exception; }
    public static final class Request {
        public final String method, target, body;
        public final InputStream bodyStream;
        public final long contentLength;
        public final String remoteAddress;
        public final Map<String,String> headers;
        Request(String method,String target,String body,Map<String,String> headers,InputStream bodyStream,long contentLength,String remoteAddress) {
            this.method=method;this.target=target;this.body=body;this.headers=headers;this.bodyStream=bodyStream;this.contentLength=contentLength;this.remoteAddress=remoteAddress;
        }
    }
    private final Handler handler;
    private final ServerSocket listener;
    private final Set<Socket> clients=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(0,24,30,TimeUnit.SECONDS,new SynchronousQueue<>());
    private volatile boolean running=true;
    public HttpServer(int port,Handler handler) throws IOException {
        this.handler=handler;listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress("0.0.0.0",port));
        Thread accept=new Thread(()->{while(running){try{
            Socket socket=listener.accept();socket.setSoTimeout(5000);clients.add(socket);
            try{workers.execute(()->serve(socket));}catch(RejectedExecutionException e){clients.remove(socket);socket.close();}
        }catch(IOException e){if(!running)break;}}},"NoxShare-listener");accept.setDaemon(true);accept.start();
    }
    public int port(){return listener.getLocalPort();}
    public static boolean probe(String host,int port){
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),2000);socket.setSoTimeout(2000);
            socket.getOutputStream().write(("GET /health HTTP/1.1\r\nHost: "+host+":"+port+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            String first=reader.readLine();if(first==null||!first.startsWith("HTTP/1.1 200 "))return false;
            String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            return "NOXSHARE_OK".equals(reader.readLine());
        }catch(IOException e){return false;}
    }
    private void serve(Socket socket){
        try(Socket s=socket;InputStream in=new BufferedInputStream(s.getInputStream());OutputStream out=new BufferedOutputStream(s.getOutputStream())){
            ByteArrayOutputStream raw=new ByteArrayOutputStream();int matched=0;
            byte[] end={13,10,13,10};
            while(matched<4){int b=in.read();if(b<0)return;raw.write(b);if(raw.size()>16384){text(out,431,"Headers too large");return;}matched=(b==end[matched])?matched+1:(b==13?1:0);}
            String[] lines=raw.toString("ISO-8859-1").split("\r\n");String[] start=lines[0].split(" ");
            if(start.length!=3||!start[1].startsWith("/")||start[1].startsWith("//")){text(out,400,"Bad request");return;}
            Map<String,String> headers=new HashMap<>();
            for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon<1){text(out,400,"Bad header");return;}String key=lines[i].substring(0,colon).toLowerCase(Locale.ROOT);if(headers.containsKey(key)){text(out,400,"Duplicate header");return;}headers.put(key,lines[i].substring(colon+1).trim());}
            if(headers.containsKey("transfer-encoding")){text(out,400,"Unsupported encoding");return;}
            boolean upload="POST".equals(start[0])&&"/api/upload".equals(start[1]);
            long length;
            try{length=Long.parseLong(headers.getOrDefault("content-length","0"));}catch(NumberFormatException e){text(out,400,"Bad length");return;}
            if(length<0||length>(upload?UploadStorage.MAX_FILE_SIZE:16384)){text(out,413,upload?"单个文件最大 2 GB":"Text too large");return;}
            if(upload&&!headers.containsKey("content-length")){text(out,411,"Content-Length required");return;}
            byte[] body=new byte[upload?0:(int)length];int offset=0;while(offset<body.length){int n=in.read(body,offset,body.length-offset);if(n<0)return;offset+=n;}
            if(upload)socket.setSoTimeout(30000);
            if(!running)return;
            final long[] written={0};
            OutputStream response=new FilterOutputStream(out){@Override public void write(int b)throws IOException{out.write(b);written[0]++;}@Override public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);written[0]+=len;}};
            try{handler.handle(new Request(start[0],start[1],new String(body,StandardCharsets.UTF_8),headers,in,length,socket.getInetAddress().getHostAddress()),response);}catch(Exception e){if(written[0]==0)text(out,500,"请求未完成，请重试");}
            out.flush();
        }catch(IOException ignored){}finally{clients.remove(socket);}
    }
    public static void headers(OutputStream out,int status,String type,long length,String extra)throws IOException{
        String reason=status==200?"OK":status==206?"Partial Content":status==416?"Range Not Satisfiable":status==415?"Unsupported Media Type":status==404?"Not Found":status==403?"Forbidden":status==405?"Method Not Allowed":status==413?"Payload Too Large":status==429?"Too Many Requests":"Error";
        String h="HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+type+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"+(length>=0?"Content-Length: "+length+"\r\n":"")+(extra==null?"":extra)+"\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
    }
    public static void bytes(OutputStream out,int code,String type,byte[] body)throws IOException{headers(out,code,type,body.length,null);out.write(body);}
    public static void text(OutputStream out,int code,String body)throws IOException{bytes(out,code,"text/plain; charset=utf-8",body.getBytes(StandardCharsets.UTF_8));}
    public static void pipe(InputStream in,OutputStream out)throws IOException{byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
    @Override public void close(){running=false;try{listener.close();}catch(IOException ignored){}for(Socket s:clients)try{s.close();}catch(IOException ignored){}workers.shutdownNow();}
}
