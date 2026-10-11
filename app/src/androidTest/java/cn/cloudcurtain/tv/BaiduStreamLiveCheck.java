package cn.cloudcurtain.tv;
import android.app.Instrumentation;import android.os.Bundle;import android.net.Uri;import okhttp3.*;import java.util.*;import org.json.*;
/** Probe only the signed-in account's normal streaming endpoint; no entitlement or ad parameters. */
final class BaiduStreamLiveCheck {
 static void run(Instrumentation test,Bundle out,String hash)throws Exception{
  MovieLibrary.Movie movie=null;for(MovieLibrary.Movie m:new MovieLibrary(test.getTargetContext()).movies())if(m.provider==Provider.BAIDU&&Integer.toHexString(m.playbackKey().hashCode()).equals(hash)){movie=m;break;}if(movie==null)throw new java.io.IOException("Movie missing");
  for(int height:new int[]{480,720,1080,2160}){
   String url=Uri.parse("https://pan.baidu.com/api/streaming").buildUpon().appendQueryParameter("path",movie.id).appendQueryParameter("app_id","250528").appendQueryParameter("web","1").appendQueryParameter("clienttype","0").appendQueryParameter("type","M3U8_AUTO_"+height).build().toString();MediaSpec spec=new MediaSpec(url,movie.name);spec.provider="BAIDU";spec.headers.put("Referer",Provider.BAIDU.origin);spec.headers.put("User-Agent",Net.UA);
   try(Response r=Net.client(spec).newCall(new Request.Builder().url(url).build()).execute()){
    String body=new String(Tmdb.readBounded(r.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8);out.putInt("stream_"+height+"_http",r.code());out.putBoolean("stream_"+height+"_manifest",body.trim().startsWith("#EXTM3U"));
    if(body.trim().startsWith("{"))out.putInt("stream_"+height+"_errno",new JSONObject(body).optInt("errno",-999));
    double duration=0;int segments=0;for(String line:body.split("\n"))if(line.startsWith("#EXTINF:")){duration+=Double.parseDouble(line.substring(8).split(",")[0]);segments++;}out.putInt("stream_"+height+"_segments",segments);out.putDouble("stream_"+height+"_seconds",duration);out.putBoolean("stream_"+height+"_endlist",body.contains("#EXT-X-ENDLIST"));
   }Thread.sleep(700);
  }
 }
}
