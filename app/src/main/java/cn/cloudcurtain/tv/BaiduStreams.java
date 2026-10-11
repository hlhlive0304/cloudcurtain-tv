package cn.cloudcurtain.tv;
import android.net.Uri;import java.io.*;import okhttp3.*;import org.json.*;
/** Normal account-authorized VOD manifests. Never supplies entitlement or advertisement overrides. */
final class BaiduStreams {
 interface Fetcher {boolean available(MediaSpec spec)throws Exception;}
 static MediaSpec spec(CloudApi.Entry entry,int height){
  if(height!=480&&height!=720&&height!=1080)throw new IllegalArgumentException("Unsupported streaming quality");
  String url=Uri.parse("https://pan.baidu.com/api/streaming").buildUpon().appendQueryParameter("path",entry.id).appendQueryParameter("app_id","250528").appendQueryParameter("web","1").appendQueryParameter("clienttype","0").appendQueryParameter("type","M3U8_AUTO_"+height).build().toString();
  MediaSpec m=new MediaSpec(url,entry.name);m.provider="BAIDU";m.key="BAIDU:"+entry.id;m.sourceBytes=entry.size;m.compatible=true;m.quality="baidu:"+height;m.qualityLabel=height+"p · 在线转码";m.mime="application/x-mpegURL";m.headers.put("Referer",Provider.BAIDU.origin);m.headers.put("User-Agent",Net.UA);return m;
 }
 static boolean completeManifest(String body){return body.trim().startsWith("#EXTM3U")&&body.contains("#EXTINF:")&&body.contains("#EXT-X-ENDLIST");}
 static boolean available(MediaSpec m)throws Exception{try(Response r=Net.client(m).newCall(new Request.Builder().url(m.url).build()).execute()){
  if(r.body()==null)return false;String body=new String(Tmdb.readBounded(r.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8);
  if(r.code()==401||r.code()==403)throw new Net.ServiceException(r.code(),"");return r.isSuccessful()&&completeManifest(body);
 }}
 static MediaSpec highest(CloudApi.Entry entry,Fetcher fetch)throws Exception{for(int h:new int[]{1080,720,480}){MediaSpec m=spec(entry,h);if(fetch.available(m))return m;}throw new IOException("百度未提供可用的完整在线转码视频，请检查该文件的播放权限或稍后再试。");}
}
