package com.noxleo.share;

import java.io.*;
import java.util.UUID;

public final class UploadStorage {
    public static final long MAX_FILE_SIZE=2L*1024*1024*1024;
    public static final class FullException extends IOException { public FullException(){super("主机存储空间不足");} }
    public static File receive(File directory,InputStream input,long length)throws IOException{
        if(length<0||length>MAX_FILE_SIZE)throw new IOException("单个文件最大 2 GB");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建接收目录");
        if(directory.getUsableSpace()<length+16L*1024*1024)throw new FullException();
        String id=UUID.randomUUID().toString();File partial=new File(directory,id+".part"),complete=new File(directory,id);boolean committed=false;
        try{
            try(FileOutputStream out=new FileOutputStream(partial)){
                byte[] buffer=new byte[65536];long remaining=length;
                while(remaining>0){int count=input.read(buffer,0,(int)Math.min(remaining,buffer.length));if(count<0)throw new EOFException("上传中断");if(count==0)continue;out.write(buffer,0,count);remaining-=count;}
                out.getFD().sync();
            }
            if(!partial.renameTo(complete))throw new IOException("无法保存接收文件");committed=true;return complete;
        }finally{if(!committed)partial.delete();}
    }
}
