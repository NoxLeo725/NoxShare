import com.noxleo.share.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class ServerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static class Store implements SharingRouter.Backend {
        volatile boolean active=true;volatile String message="";
        volatile String uploadedName="",uploadedBy="";volatile byte[] uploadedBytes;
        Map<String,SharingRouter.FileEntry> files=new ConcurrentHashMap<>();
        Store(){files.put("one",new SharingRouter.FileEntry("测试.txt",()->new ByteArrayInputStream("你好，局域网！".getBytes(StandardCharsets.UTF_8))));files.put("two",new SharingRouter.FileEntry("测试.txt",()->new ByteArrayInputStream(new byte[]{0,1,2,3})));files.put("bad",new SharingRouter.FileEntry("missing",()->{throw new IOException();}));}
        public boolean running(){return active;}public String state(String clientIp){return "{\"running\":true,\"files\":[]}";}public SharingRouter.FileEntry find(String id){return files.get(id);}public InputStream asset(String name)throws Exception{return Files.newInputStream(Path.of("app/src/main/assets",name));}public void message(String text,String sender){message=text;}
        public void upload(String name,String type,String sender,InputStream input,long length)throws Exception{
            Path directory=Files.createTempDirectory("noxshare-upload-test");File saved=null;
            try{saved=UploadStorage.receive(directory.toFile(),input,length);uploadedName=name;uploadedBy=sender;uploadedBytes=Files.readAllBytes(saved.toPath());byte[] data=uploadedBytes;files.put("uploaded",new SharingRouter.FileEntry(name,()->new ByteArrayInputStream(data)));}
            finally{if(saved!=null)saved.delete();Files.delete(directory);}
        }
    }
    static HttpClient client=HttpClient.newHttpClient();static String base;
    static HttpResponse<byte[]> get(String path)throws Exception{return client.send(HttpRequest.newBuilder(URI.create(base+path)).build(),HttpResponse.BodyHandlers.ofByteArray());}
    static String raw(int port,String request)throws Exception{try(Socket s=new Socket("127.0.0.1",port)){s.setSoTimeout(3000);s.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));return new String(s.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}}
    public static void main(String[] args)throws Exception{
        Store store=new Store();HttpServer server=new HttpServer(0,new SharingRouter(store));int port=server.port();base="http://127.0.0.1:"+port;
        check(get("/").statusCode()==200,"short address opens visitor page");check(get("/s/wrong/api/state").statusCode()==404,"old long path not routed");
        check(HttpServer.probe("127.0.0.1",port),"loopback service health");
        byte[] movie=new byte[1024];for(int i=0;i<movie.length;i++)movie[i]=(byte)i;
        store.files.put("movie",new SharingRouter.FileEntry("video.mp4","video/mp4",movie.length,()->new ByteArrayInputStream(movie),null));
        check(Arrays.equals(get("/media/movie").body(),movie),"full media preserves original bytes");
        var mediaRequest=HttpRequest.newBuilder(URI.create(base+"/media/movie")).header("Range","bytes=100-199").build();
        var media=client.send(mediaRequest,HttpResponse.BodyHandlers.ofByteArray());
        check(media.statusCode()==206&&Arrays.equals(media.body(),Arrays.copyOfRange(movie,100,200)),"seek range exact bytes");
        check(media.headers().firstValue("content-range").orElse("").equals("bytes 100-199/1024"),"media content range");
        check(media.headers().firstValue("content-type").orElse("").equals("video/mp4"),"playable media type");
        for(String range:new String[]{"bytes=1000-","bytes=-24"}){
            var r=client.send(HttpRequest.newBuilder(URI.create(base+"/media/movie")).header("Range",range).build(),HttpResponse.BodyHandlers.ofByteArray());
            check(r.statusCode()==206&&Arrays.equals(r.body(),Arrays.copyOfRange(movie,1000,1024)),"suffix/open range");
        }
        for(String range:new String[]{"bytes=1024-","bytes=500-200","bytes=-0","bytes=999999999999999999999999-"}){
            var r=client.send(HttpRequest.newBuilder(URI.create(base+"/media/movie")).header("Range",range).build(),HttpResponse.BodyHandlers.ofByteArray());
            check(r.statusCode()==416&&r.body().length==0,"invalid range rejected");
        }
        var head=client.send(HttpRequest.newBuilder(URI.create(base+"/media/movie")).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
        check(head.statusCode()==200&&head.body().length==0&&head.headers().firstValue("content-length").orElse("").equals("1024"),"media HEAD length without body");
        check(get("/media/one").statusCode()==415,"text cannot become executable inline media");
        store.files.remove("movie");check(get("/media/movie").statusCode()==404,"removed media unavailable");
        byte[] upload=new byte[512*1024+17];new Random(12).nextBytes(upload);
        var uploadRequest=HttpRequest.newBuilder(URI.create(base+"/api/upload")).header("X-NoxShare","1").header("X-File-Name",URLEncoder.encode("测试 文件.bin","UTF-8")).header("X-Forwarded-For","10.0.0.99").header("Content-Type","application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(upload)).build();
        check(client.send(uploadRequest,HttpResponse.BodyHandlers.ofString()).statusCode()==201,"stream upload accepted");check(Arrays.equals(upload,store.uploadedBytes),"binary upload exact bytes");check(store.uploadedName.equals("测试 文件.bin"),"UTF8 upload name");check(store.uploadedBy.equals("127.0.0.1"),"uploader uses actual peer, not spoofed header");check(Arrays.equals(get("/download/uploaded").body(),upload),"uploaded file downloadable");
        var deniedUpload=HttpRequest.newBuilder(URI.create(base+"/api/upload")).header("X-File-Name","test.txt").POST(HttpRequest.BodyPublishers.ofString("bad")).build();check(client.send(deniedUpload,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"upload requires same origin custom header");
        check(raw(port,"POST /api/upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: 2147483649\r\n\r\n").startsWith("HTTP/1.1 413"),"oversized upload rejected before allocation");
        Path storageTest=Files.createTempDirectory("noxshare-partial-test");
        try{try{UploadStorage.receive(storageTest.toFile(),new ByteArrayInputStream(new byte[3]),10);throw new AssertionError("partial upload accepted");}catch(EOFException expected){}check(storageTest.toFile().list().length==0,"partial upload cleaned");File empty=UploadStorage.receive(storageTest.toFile(),new ByteArrayInputStream(new byte[0]),0);check(empty.length()==0,"empty file upload");empty.delete();}finally{Files.delete(storageTest);}
        List<Socket> speculative=new ArrayList<>();try{for(int i=0;i<6;i++)speculative.add(new Socket("127.0.0.1",port));Thread.sleep(100);
            var fast=client.send(HttpRequest.newBuilder(URI.create(base+"/api/state")).timeout(java.time.Duration.ofSeconds(2)).build(),HttpResponse.BodyHandlers.ofByteArray());check(fast.statusCode()==200,"idle browser preconnections must not block page requests");
        }finally{for(Socket s:speculative)s.close();}
        HttpResponse<byte[]> home=get("/");check(home.statusCode()==200,"visitor page");check(new String(home.body(),StandardCharsets.UTF_8).contains("聊天记录"),"page UTF-8");check(home.headers().firstValue("content-security-policy").isPresent(),"CSP");
        check(get("/api/state").statusCode()==200,"state");check(get("/../../secret").statusCode()==404,"path traversal");check(get("/style.css").statusCode()==200,"static styles");
        HttpResponse<byte[]> file=get("/download/one");check(Arrays.equals(file.body(),"你好，局域网！".getBytes(StandardCharsets.UTF_8)),"exact UTF-8 download");check(file.headers().firstValue("content-disposition").orElse("").contains("filename*=UTF-8"),"UTF-8 filename");check(get("/download/bad").statusCode()==404,"missing storage file");
        HttpResponse<byte[]> bundle=get("/bundle?ids=one%2Ctwo%2Cone");check(bundle.statusCode()==200,"bundle status");int count=0;Set<String> names=new HashSet<>();try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bundle.body()))){ZipEntry e;while((e=in.getNextEntry())!=null){names.add(e.getName());byte[] bytes=in.readAllBytes();check(bytes.length>0,"zip file contents");count++;}}check(count==2&&names.size()==2,"ZIP duplicate IDs/names");check(get("/bundle?ids=one,bad").statusCode()==404,"bundle opens files before headers");
        check(raw(port,"GET /api/state HTTP/1.1\r\nHost: evil.example\r\n\r\n").startsWith("HTTP/1.1 403"),"DNS host rebinding blocked");
        var post=HttpRequest.newBuilder(URI.create(base+"/api/messages")).header("X-NoxShare","1").header("Content-Type","text/plain;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString("你好\nhttps://example.org/?a=1&b=2")).build();check(client.send(post,HttpResponse.BodyHandlers.ofString()).statusCode()==200,"send message");check(store.message.equals("你好\nhttps://example.org/?a=1&b=2"),"exact text preserved");
        var invalid=HttpRequest.newBuilder(URI.create(base+"/api/messages")).POST(HttpRequest.BodyPublishers.ofString("test")).build();check(client.send(invalid,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"custom header required");
        var cross=HttpRequest.newBuilder(URI.create(base+"/api/messages")).header("X-NoxShare","1").header("Origin","http://evil.example").POST(HttpRequest.BodyPublishers.ofString("test")).build();check(client.send(cross,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"cross-origin rejected");
        var big=HttpRequest.newBuilder(URI.create(base+"/api/messages")).header("X-NoxShare","1").POST(HttpRequest.BodyPublishers.ofString("x".repeat(4001))).build();check(client.send(big,HttpResponse.BodyHandlers.ofString()).statusCode()==400,"text length limit");
        check(raw(port,"POST /api/messages HTTP/1.1\r\nHost: localhost\r\nContent-Length: 20000\r\n\r\n").startsWith("HTTP/1.1 413"),"body memory limit");
        check(raw(port,"GET / HTTP/1.1\r\nContent-Length: 0\r\nContent-Length: 1\r\n\r\n").startsWith("HTTP/1.1 400"),"duplicate lengths rejected");
        check(raw(port,"POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 400"),"chunked request rejected");
        var requests=new ArrayList<CompletableFuture<HttpResponse<byte[]>>>();for(int i=0;i<8;i++)requests.add(client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/download/two")).build(),HttpResponse.BodyHandlers.ofByteArray()));for(var r:requests)check(Arrays.equals(r.get().body(),new byte[]{0,1,2,3}),"parallel download");
        store.files.remove("one");check(get("/download/one").statusCode()==404,"removed files stop being shared");
        store.active=false;check(get("/api/state").statusCode()==503,"stopped state");
        Socket held=new Socket("127.0.0.1",port);held.setSoTimeout(3000);held.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes());Thread.sleep(30);server.close();try{check(held.getInputStream().read()==-1,"active connection closed");}catch(SocketException expected){}held.close();
        try(Socket ignored=new Socket("127.0.0.1",port)){throw new AssertionError("port still open");}catch(ConnectException expected){}
        System.out.println("PASS: streaming upload/download exact bytes, UTF8 filename, uploader peer IP, spoof rejection, partial cleanup, empty/oversized uploads, HTTP routing, ZIP, messages, preconnects, shutdown.");
    }
}
