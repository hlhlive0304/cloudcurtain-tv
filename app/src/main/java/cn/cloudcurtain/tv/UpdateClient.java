package cn.cloudcurtain.tv;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Isolated HTTP client: no web-view cookies, cloud headers, auth tokens or analytics. */
final class UpdateClient {
    interface Transport { Response execute(Request request)throws IOException; }
    interface Cancellation { boolean cancelled(); }
    interface Progress { void changed(int percent); default void transferred(long bytes,long size){} }
    interface ArchiveValidator { void verify(File file)throws Exception; }
    private final Transport transport;
    private final Set<Call> calls=Collections.newSetFromMap(new ConcurrentHashMap<Call,Boolean>());
    private volatile String route="";
    private static final class Unavailable extends IOException {
        Unavailable(String message){super(message);}
        Unavailable(IOException cause){super("更新线路连接超时或中断，请稍后重试。",cause);}
    }
    UpdateClient(){
        OkHttpClient metadata=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build();
        OkHttpClient asset=metadata.newBuilder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS)
            .protocols(Collections.singletonList(Protocol.HTTP_1_1)).callTimeout(15,TimeUnit.MINUTES).build();
        transport=request->{
            Call active=("application/octet-stream".equals(request.header("Accept"))?asset:metadata).newCall(request);calls.add(active);
            try{
                Response received=active.execute();ResponseBody body=received.body();if(body==null){calls.remove(active);return received;}
                okio.BufferedSource source=okio.Okio.buffer(new okio.ForwardingSource(body.source()){
                    @Override public void close()throws IOException{try{super.close();}finally{calls.remove(active);}}
                });
                return received.newBuilder().body(new ResponseBody(){public MediaType contentType(){return body.contentType();}public long contentLength(){return body.contentLength();}public okio.BufferedSource source(){return source;}}).build();
            }catch(IOException|RuntimeException e){calls.remove(active);throw e;}
        };
    }
    UpdateClient(Transport transport){this.transport=transport;}
    String route(){return route;}
    void cancel(){for(Call active:calls)active.cancel();}
    private static void checkCancelled(Cancellation cancel)throws IOException {
        if(Thread.currentThread().isInterrupted()||cancel.cancelled())throw new InterruptedIOException("已取消更新操作。");
    }
    private Response response(String address,boolean asset,Cancellation cancel)throws IOException {
        return response(address,asset,cancel,null,null);
    }
    private Response response(String address,boolean asset,Cancellation cancel,String range,String etag)throws IOException {
        HttpUrl url=HttpUrl.get(address);
        for(int n=0;n<6;n++){
            checkCancelled(cancel);
            if(asset?!UpdateManifest.assetRedirect(url):!url.toString().equals(address))throw new IOException("更新服务器跳转地址不受信任。");
            String accept=asset?"application/octet-stream":address.equals(UpdateManifest.API_FEED)?"application/vnd.github.raw+json":"application/vnd.github+json";
            Response response;
            try{Request.Builder request=new Request.Builder().url(url).header("Accept-Encoding","identity").header("Accept",accept)
                .header("X-GitHub-Api-Version","2022-11-28").header("Cache-Control","no-cache").header("User-Agent","CloudCurtainTV-Updater");
                if(range!=null)request.header("Range",range);if(etag!=null)request.header("If-Range",etag);response=transport.execute(request.build());}
            catch(IOException e){checkCancelled(cancel);throw new Unavailable(e);}
            int code=response.code();
            if(code==301||code==302||code==303||code==307||code==308){
                String location=response.header("Location");HttpUrl next=location==null?null:url.resolve(location);response.close();
                if(next==null)throw new IOException("更新服务器缺少有效跳转地址。");url=next;continue;
            }
            if((code!=200&&!(asset&&range!=null&&code==206))||response.body()==null){response.close();if(code==404&&!asset)throw new Unavailable("仓库尚未发布更新信息，请稍后重试。");throw new Unavailable("更新服务器返回 HTTP "+code+"，请稍后重试。");}
            return response;
        }
        throw new IOException("更新服务器跳转次数过多。");
    }
    UpdateManifest check(Cancellation cancel)throws IOException {
        Unavailable last=null;
        for(String address:new String[]{UpdateManifest.API_FEED,UpdateManifest.FEED}){
            checkCancelled(cancel);route=address.equals(UpdateManifest.API_FEED)?"官方 API":"官方文件线路";
            try{return UpdateManifest.parse(text(address,65536,cancel));}
            catch(Unavailable e){last=e;}
        }
        checkCancelled(cancel);throw new IOException("两条官方更新线路均未连接成功。"+(last==null?"":last.getMessage())+"可通过手机打开发布页下载安装包。",last);
    }
    private String text(String address,int limit,Cancellation cancel)throws IOException{
        try(Response response=response(address,false,cancel);InputStream in=response.body().byteStream()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while(true){checkCancelled(cancel);try{n=in.read(buffer);}catch(IOException e){checkCancelled(cancel);throw new Unavailable(e);}if(n==-1)break;
                if(out.size()+n>limit)throw new IOException("更新信息超过大小限制。");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }
    }
    File download(File directory,UpdateManifest release,Cancellation cancel,Progress progress,ArchiveValidator validator)throws Exception {
        checkCancelled(cancel);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建更新缓存。");
        File target=new File(directory,"cloudcurtain-"+release.versionCode+"-"+release.sha256.substring(0,16)+".apk");
        if(target.isFile()){verifyFile(target,release);validator.verify(target);progress.changed(100);return target;}
        if(directory.getUsableSpace()<release.apkSize+2L*1024*1024)throw new IOException("电视存储空间不足，请清理后重试。");
        File partial=new File(directory,target.getName()+".part");
        Unavailable last=null;
        try{
          for(boolean api:new boolean[]{true,false}){
            checkCancelled(cancel);route=api?"官方 API 下载":"官方下载页";
            try{
              String address=api?release.assetUrl(text(release.metadataUrl(),524288,cancel)):release.apkUrl;
              progress.changed(0);
              if(release.apkSize>=1024*1024){
                  try{parallel(address,partial,release,cancel,progress);}
                  catch(RangeUnsupported|Unavailable e){checkCancelled(cancel);route=api?"官方 API 下载":"官方下载页";progress.changed(0);sequential(address,partial,release,cancel,progress);}
              }else sequential(address,partial,release,cancel,progress);
              verifyFile(partial,release);validator.verify(partial);checkCancelled(cancel);
              if(!partial.renameTo(target))throw new IOException("无法保存已校验的安装包。");
              progress.changed(100);return target;
            }catch(Unavailable e){last=e;if(partial.exists()&&!partial.delete())throw new IOException("无法清理未完成的更新文件。");}
          }
          checkCancelled(cancel);throw new IOException("两条官方安装包线路均未连接成功。"+(last==null?"":last.getMessage())+"可通过手机打开发布页下载安装包。",last);
        }finally{if(partial.exists())partial.delete();}
    }
    private void sequential(String address,File partial,UpdateManifest release,Cancellation cancel,Progress progress)throws Exception {
              try(Response response=response(address,true,cancel);InputStream in=response.body().byteStream();FileOutputStream out=new FileOutputStream(partial)){
                long declared=response.body().contentLength();if(declared>=0&&declared!=release.apkSize)throw new IOException("下载文件大小与发布信息不一致。");
                MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long total=0;int n,lastPercent=-1;
                while(true){
                    checkCancelled(cancel);try{n=in.read(buffer);}catch(IOException e){checkCancelled(cancel);throw new Unavailable(e);}if(n==-1)break;
                    total+=n;if(total>release.apkSize)throw new IOException("下载数据超过发布文件大小。");out.write(buffer,0,n);hash.update(buffer,0,n);
                    int percent=(int)(total*100/release.apkSize);if(percent!=lastPercent){lastPercent=percent;progress.changed(percent);}
                    progress.transferred(total,release.apkSize);
                }
                checkCancelled(cancel);out.getFD().sync();
                if(total!=release.apkSize||!hex(hash.digest()).equals(release.sha256))throw new IOException("安装包完整性校验失败，请重新下载。");
              }
    }
    private static final class RangeUnsupported extends IOException {}
    private static void range(Response response,long start,long end,long size)throws IOException {
        if(response.code()==200)throw new RangeUnsupported();
        if(!("bytes "+start+"-"+end+"/"+size).equals(response.header("Content-Range")))throw new IOException("更新分段范围与发布信息不一致。");
        if(response.body().contentLength()>=0&&response.body().contentLength()!=end-start+1)throw new IOException("更新分段长度不一致。");
        String encoding=response.header("Content-Encoding","identity");if(!encoding.equalsIgnoreCase("identity"))throw new IOException("更新分段编码无效。");
    }
    private void parallel(String address,File partial,UpdateManifest release,Cancellation cancel,Progress progress)throws Exception {
        String source,etag;
        try(Response probe=response(address,true,cancel,"bytes=0-0",null)){
            range(probe,0,0,release.apkSize);try(InputStream input=probe.body().byteStream()){
                int first,extra;try{first=input.read();extra=input.read();}catch(IOException e){checkCancelled(cancel);throw new Unavailable(e);}
                if(first<0)throw new Unavailable("更新分段探测连接提前结束。");if(extra!=-1)throw new IOException("更新分段探测数据无效。");
            }
            source=probe.request().url().toString();etag=probe.header("ETag");if(etag!=null&&(etag.startsWith("W/")||!etag.startsWith("\"")))etag=null;
        }
        checkCancelled(cancel);route=route+" · 4 路加速";
        try(RandomAccessFile output=new RandomAccessFile(partial,"rw")){output.setLength(release.apkSize);}
        ExecutorService workers=Executors.newFixedThreadPool(4);CompletionService<Void> completed=new ExecutorCompletionService<>(workers);
        java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
        Cancellation scoped=()->stopped.get()||cancel.cancelled();final String finalEtag=etag;
        long[] received=new long[4];int[] lastPercent={-1};long chunk=(release.apkSize+3)/4;
        try{
            for(int index=0;index<4;index++){
                final int part=index;final long start=chunk*part,end=Math.min(release.apkSize-1,start+chunk-1);
                completed.submit(()->{
                    try(Response response=response(source,true,scoped,"bytes="+start+"-"+end,finalEtag)){
                        range(response,start,end,release.apkSize);if(finalEtag!=null&&!finalEtag.equals(response.header("ETag")))throw new IOException("更新分段文件版本不一致。");
                        try(InputStream input=response.body().byteStream();RandomAccessFile output=new RandomAccessFile(partial,"rw")){
                            output.seek(start);byte[] buffer=new byte[65536];long count=0;int n;
                            while(true){checkCancelled(scoped);try{n=input.read(buffer);}catch(IOException e){checkCancelled(scoped);throw new Unavailable(e);}if(n==-1)break;
                                count+=n;if(count>end-start+1)throw new IOException("更新分段数据过多。");output.write(buffer,0,n);
                                synchronized(received){received[part]=count;long total=0;for(long value:received)total+=value;int percent=(int)(total*99/release.apkSize);
                                    if(percent!=lastPercent[0]){lastPercent[0]=percent;progress.changed(percent);}progress.transferred(total,release.apkSize);}
                            }
                            if(count!=end-start+1)throw new Unavailable("更新分段连接提前结束。");output.getFD().sync();
                        }
                    }
                    return null;
                });
            }
            for(int part=0;part<4;part++){
                Future<Void> finished;do{checkCancelled(cancel);finished=completed.poll(200,TimeUnit.MILLISECONDS);}while(finished==null);
                try{finished.get();}catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("更新分段失败。",cause);}
            }
        }finally{
            stopped.set(true);cancel();workers.shutdownNow();
            boolean interrupted=Thread.interrupted();try{while(true){try{if(workers.awaitTermination(200,TimeUnit.MILLISECONDS))break;}catch(InterruptedException e){interrupted=true;cancel();}} }finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    static void verifyFile(File file,UpdateManifest release)throws Exception {
        if(!file.isFile()||file.length()!=release.apkSize)throw new IOException("安装包文件不完整。");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;
        try(InputStream in=new FileInputStream(file)){while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}
        if(!hex(hash.digest()).equals(release.sha256))throw new IOException("安装包完整性校验失败。");
    }
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
}
