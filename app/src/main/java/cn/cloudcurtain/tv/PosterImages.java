package cn.cloudcurtain.tv;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Public TMDB/Wikimedia artwork only; isolated from drive authentication. */
final class PosterImages {
    private final File directory;private final OkHttpClient client;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}};
    PosterImages(Context c){directory=new File(c.getCacheDir(),"movie-posters");directory.mkdirs();client=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();}
    Bitmap load(String path)throws Exception{return load(path,720,1080);}
    Bitmap load(String path,int requestedWidth,int requestedHeight)throws Exception{
        int width=Math.max(64,Math.min(720,((requestedWidth+63)/64)*64)),height=Math.max(96,Math.min(1080,((requestedHeight+63)/64)*64));
        String memoryKey=path+":"+width+":"+height;
        boolean wiki=PublicPosters.imageUrl(path);if(!wiki&&!path.matches("/[A-Za-z0-9_-]+\\.(jpg|png)"))throw new IOException("无效海报地址");Bitmap image=memory.get(memoryKey);if(image!=null)return image;
        String cacheName=wiki?hash(path)+".image":path.substring(1);File file=new File(directory,cacheName);byte[] data;
        if(file.isFile()){try(InputStream in=new FileInputStream(file)){data=Tmdb.readBounded(in,5*1024*1024);}}
        else{Request.Builder request=new Request.Builder().url(wiki?path:Tmdb.IMAGE+path).header("User-Agent","CloudCurtainTV/0.1.14 (personal movie library)");if(wiki&&HttpUrl.get(path).host().endsWith(".doubanio.com"))request.header("Referer","https://movie.douban.com/");try(Response response=client.newCall(request.build()).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new IOException("海报暂时不可用（HTTP "+response.code()+"）");data=Tmdb.readBounded(response.body().byteStream(),5*1024*1024);
        }}
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>10000||options.outHeight>10000)throw new IOException("无效海报图片");
        options.inSampleSize=1;while(options.outWidth/options.inSampleSize>width||options.outHeight/options.inSampleSize>height)options.inSampleSize*=2;options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.RGB_565;image=BitmapFactory.decodeByteArray(data,0,data.length,options);if(image==null)throw new IOException("无法显示海报");
        if(!file.isFile()){try(OutputStream out=new FileOutputStream(file)){out.write(data);}trim();}file.setLastModified(System.currentTimeMillis());memory.put(memoryKey,image);return image;
    }
    void cancelPending(){client.dispatcher().cancelAll();}
    void close(){cancelPending();memory.evictAll();}
    private static String hash(String value)throws Exception{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte v:b)result.append(String.format(Locale.ROOT,"%02x",v&255));return result.toString();}
    private void trim(){File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long size=0;for(File f:files)size+=f.length();for(File f:files)if(size>24*1024*1024L&&f.isFile()){long length=f.length();if(f.delete())size-=length;}}
}