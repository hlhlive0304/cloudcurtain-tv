package cn.cloudcurtain.tv;

import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Isolated HTTP client: no web-view cookies, cloud headers, auth tokens or analytics. */
final class UpdateClient {
    interface Transport { Response execute(Request request)throws IOException; }
    interface Cancellation { boolean cancelled(); }
    interface Progress { void changed(int percent); }
    interface ArchiveValidator { void verify(File file)throws Exception; }
    private final Transport transport;
    private volatile Call call;
    UpdateClient(){
        OkHttpClient http=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(15,TimeUnit.MINUTES).build();
        transport=request->{call=http.newCall(request);return call.execute();};
    }
    UpdateClient(Transport transport){this.transport=transport;}
    void cancel(){Call active=call;if(active!=null)active.cancel();}
    private static void checkCancelled(Cancellation cancel)throws IOException {
        if(Thread.currentThread().isInterrupted()||cancel.cancelled())throw new InterruptedIOException("已取消更新操作。");
    }
    private Response response(String address,boolean asset,Cancellation cancel)throws IOException {
        HttpUrl url=HttpUrl.get(address);
        for(int n=0;n<6;n++){
            checkCancelled(cancel);
            if(asset?!UpdateManifest.assetRedirect(url):!url.toString().equals(UpdateManifest.FEED))throw new IOException("更新服务器跳转地址不受信任。");
            Response response=transport.execute(new Request.Builder().url(url).header("Accept-Encoding","identity")
                .header("Cache-Control","no-cache").header("User-Agent","CloudCurtainTV-Updater").build());
            int code=response.code();
            if(code==301||code==302||code==303||code==307||code==308){
                String location=response.header("Location");HttpUrl next=location==null?null:url.resolve(location);response.close();
                if(next==null)throw new IOException("更新服务器缺少有效跳转地址。");url=next;continue;
            }
            if(code!=200||response.body()==null){response.close();if(code==404&&!asset)throw new IOException("仓库尚未发布更新信息，请稍后重试。");throw new IOException("更新服务器返回 HTTP "+code+"，请稍后重试。");}
            return response;
        }
        throw new IOException("更新服务器跳转次数过多。");
    }
    UpdateManifest check(Cancellation cancel)throws IOException {
        try(Response response=response(UpdateManifest.FEED,false,cancel);InputStream in=response.body().byteStream()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){checkCancelled(cancel);if(out.size()+n>65536)throw new IOException("更新信息超过大小限制。");out.write(buffer,0,n);}
            return UpdateManifest.parse(out.toString("UTF-8"));
        }
    }
    File download(File directory,UpdateManifest release,Cancellation cancel,Progress progress,ArchiveValidator validator)throws Exception {
        checkCancelled(cancel);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建更新缓存。");
        File target=new File(directory,"cloudcurtain-"+release.versionCode+"-"+release.sha256.substring(0,16)+".apk");
        if(target.isFile()){verifyFile(target,release);validator.verify(target);progress.changed(100);return target;}
        if(directory.getUsableSpace()<release.apkSize+2L*1024*1024)throw new IOException("电视存储空间不足，请清理后重试。");
        File partial=new File(directory,target.getName()+".part");
        try{
            try(Response response=response(release.apkUrl,true,cancel);InputStream in=response.body().byteStream();FileOutputStream out=new FileOutputStream(partial)){
                long declared=response.body().contentLength();if(declared>=0&&declared!=release.apkSize)throw new IOException("下载文件大小与发布信息不一致。");
                MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long total=0;int n,last=-1;
                while((n=in.read(buffer))!=-1){
                    checkCancelled(cancel);total+=n;if(total>release.apkSize)throw new IOException("下载数据超过发布文件大小。");out.write(buffer,0,n);hash.update(buffer,0,n);
                    int percent=(int)(total*100/release.apkSize);if(percent!=last){last=percent;progress.changed(percent);}
                }
                checkCancelled(cancel);out.getFD().sync();
                if(total!=release.apkSize||!hex(hash.digest()).equals(release.sha256))throw new IOException("安装包完整性校验失败，请重新下载。");
            }
            validator.verify(partial);checkCancelled(cancel);
            if(!partial.renameTo(target))throw new IOException("无法保存已校验的安装包。");
            progress.changed(100);return target;
        }finally{if(partial.exists())partial.delete();}
    }
    static void verifyFile(File file,UpdateManifest release)throws Exception {
        if(!file.isFile()||file.length()!=release.apkSize)throw new IOException("安装包文件不完整。");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;
        try(InputStream in=new FileInputStream(file)){while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}
        if(!hex(hash.digest()).equals(release.sha256))throw new IOException("安装包完整性校验失败。");
    }
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
}
