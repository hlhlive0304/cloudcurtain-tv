package cn.cloudcurtain.tv;
import android.app.Instrumentation;
import android.os.*;
import android.content.Intent;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.*;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;
import okhttp3.*;
final class PlaybackLiveCheck {
 static void run(Instrumentation test,Bundle result,String provider,String hash,String mode,int seconds)throws Exception{
  MovieLibrary.Movie movie=null;for(MovieLibrary.Movie m:new MovieLibrary(test.getTargetContext()).movies())if(m.provider.name().equals(provider)&&!m.personal&&(hash.isEmpty()||Integer.toHexString(m.playbackKey().hashCode()).equals(hash))){movie=m;break;}
  if(movie==null)throw new IOException("Movie missing");
  result.putString("key_hash",Integer.toHexString(movie.playbackKey().hashCode()));result.putLong("file_bytes",movie.size);
  CloudApi api=new CloudApi(movie.provider);if(movie.provider==Provider.ALI)api.selectAliSpace(movie.space);
  MediaSpec media=mode.equals("compatible")?api.resolveCompatible(movie.entry()):mode.equals("original")?api.resolveOriginal(movie.entry()):mode.equals("auto")?api.resolveQuality(movie.entry(),"auto"):api.resolveSelected(movie.entry());
  result.putString("resolved_quality",media.quality);result.putBoolean("resolved_compatible",media.compatible);
  result.putString("returned_scheme",okhttp3.HttpUrl.get(media.url).scheme());if(mode.equals("network_https"))media.url=okhttp3.HttpUrl.get(media.url).newBuilder().scheme("https").port(443).build().toString();
  if(mode.equals("cdn_https")){try(okhttp3.Response first=Net.client(media).newCall(new okhttp3.Request.Builder().url(media.url).header("Range","bytes=0-0").build()).execute()){String target=first.request().url().newBuilder().scheme("https").port(443).build().toString();if(!Net.playbackHeadersHost(media,target))throw new IOException("Unverified CDN HTTPS target");media.url=target;}}
  if(mode.equals("datasource_request")){datasourceRequest(media,result);return;}
  if(mode.equals("request_ranges")){requestRanges(media,result);return;}
  sample(media,result,mode.equals("throughput")?64*1024*1024:8*1024*1024);
  if(mode.equals("network")||mode.equals("network_https")||mode.equals("cdn_https")||mode.equals("throughput"))return;
  PlayerActivity activity=(PlayerActivity)test.startActivitySync(new Intent(test.getTargetContext(),PlayerActivity.class).putExtra("media",media.json()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  Field pf=PlayerActivity.class.getDeclaredField("player"),mf=PlayerActivity.class.getDeclaredField("media"),sf=PlayerActivity.class.getDeclaredField("playbackStats"),ff=PlayerActivity.class.getDeclaredField("firstFrame");pf.setAccessible(true);mf.setAccessible(true);sf.setAccessible(true);ff.setAccessible(true);
  long start=SystemClock.elapsedRealtime();final long[] prev={-1,0};final int[] ready={0},waiting={0};final boolean[] sought={false};
  while(SystemClock.elapsedRealtime()-start<Math.max(30,seconds)*1000L){
   Thread.sleep(1000);
   test.runOnMainSync(()->{try{
    ExoPlayer p=(ExoPlayer)pf.get(activity);if(p==null){result.putBoolean("activity_player_missing",true);return;}
    result.putBoolean("rendered_frame",ff.getBoolean(activity));if(p.isPlaying()&&ff.getBoolean(activity)&&p.getVideoFormat()!=null&&PlaybackVideo.selected(p.getCurrentTracks()))ready[0]++;if(p.getPlaybackState()==Player.STATE_BUFFERING)waiting[0]++;
    result.putLong("position_ms",p.getCurrentPosition());result.putLong("duration_ms",p.getDuration());result.putLong("buffer_ahead_ms",Math.max(0,p.getBufferedPosition()-p.getCurrentPosition()));result.putInt("player_state",p.getPlaybackState());
    PlaybackStats stats=(PlaybackStats)sf.get(activity);result.putLong("downloaded_bytes",stats.bytes);result.putInt("transfer_requests",stats.requests);result.putLong("recent_bytes_per_second",stats.recentBytesPerSecond());result.putBoolean("play_when_ready",p.getPlayWhenReady());result.putInt("track_groups",p.getCurrentTracks().getGroups().size());result.putInt("buffer_cap_bytes",PlaybackBuffer.targetBytes(Runtime.getRuntime().maxMemory(),(MediaSpec)mf.get(activity)));
    for(Tracks.Group g:p.getCurrentTracks().getGroups())if(g.getType()==C.TRACK_TYPE_VIDEO){Format format=g.getTrackFormat(0);result.putInt("source_width",format.width);result.putInt("source_height",format.height);result.putString("source_codec",format.codecs);result.putBoolean("video_supported",g.isSupported());result.putBoolean("video_selected",g.isSelected());}
    if(prev[0]>=0&&p.getCurrentPosition()>prev[0]+400)prev[1]++;prev[0]=p.getCurrentPosition();
    Format f=p.getVideoFormat();if(f!=null){result.putInt("decoded_width",f.width);result.putInt("decoded_height",f.height);result.putString("decoded_mime",f.sampleMimeType);}
    if(p.getPlayerError()!=null){result.putString("player_error",p.getPlayerError().getErrorCodeName());result.putInt("player_http",PlaybackErrors.httpStatus(p.getPlayerError()));if(p.getPlayerError() instanceof androidx.media3.exoplayer.ExoPlaybackException){androidx.media3.common.Format failed=((androidx.media3.exoplayer.ExoPlaybackException)p.getPlayerError()).rendererFormat;if(failed!=null){result.putString("failed_mime",failed.sampleMimeType);result.putString("failed_codecs",failed.codecs);result.putInt("failed_width",failed.width);result.putInt("failed_height",failed.height);}}for(Throwable cause=p.getPlayerError();cause!=null;cause=cause.getCause())if(cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException){androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException failure=(androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)cause;result.putString("failure_host",UrlRules.host(failure.dataSpec.uri.toString()));result.putLong("failure_position",failure.dataSpec.position);result.putLong("failure_length",failure.dataSpec.length);result.putInt("failure_flags",failure.dataSpec.flags);String body=new String(failure.responseBody,java.nio.charset.StandardCharsets.UTF_8);java.util.regex.Matcher code=java.util.regex.Pattern.compile("<Code>([A-Za-z0-9_.-]{1,80})</Code>").matcher(body);result.putString("failure_server_code",code.find()?code.group(1):"");}}
    result.putString("actual_quality",((MediaSpec)mf.get(activity)).quality);
    if(!sought[0]&&ready[0]>=12){sought[0]=true;p.seekTo(Math.max(60000,p.getDuration()/2));prev[0]=-1;result.putBoolean("seek_requested",true);}
   }catch(Exception e){throw new RuntimeException(e);}});
  }
  result.putInt("playing_seconds",ready[0]);result.putInt("buffering_seconds",waiting[0]);result.putLong("advancing_samples",prev[1]);result.putLong("elapsed_ms",SystemClock.elapsedRealtime()-start);
  test.runOnMainSync(activity::finish);
  if(ready[0]<20||prev[1]<15)throw new IOException("Playback did not advance sufficiently");
 }
 private static void datasourceRequest(MediaSpec media,Bundle r)throws Exception{
  final int[] calls={0};
  OkHttpClient client=Net.client(media).newBuilder().addNetworkInterceptor(chain->{
   int call=calls[0]++;Request req=chain.request();String tag="wire_"+call;
   r.putString(tag+"_host",req.url().host());r.putString(tag+"_scheme",req.url().scheme());r.putString(tag+"_range",java.util.Objects.toString(req.header("Range"),""));r.putString(tag+"_encoding",java.util.Objects.toString(req.header("Accept-Encoding"),""));r.putBoolean(tag+"_referer",media.headers.get("Referer").equals(req.header("Referer")));r.putBoolean(tag+"_ua",media.headers.get("User-Agent").equals(req.header("User-Agent")));
   Response res=chain.proceed(req);r.putInt(tag+"_http",res.code());r.putString(tag+"_code",safeCode(res));return res;
  }).build();
  for(int i=0;i<3;i++){
   androidx.media3.datasource.okhttp.OkHttpDataSource ds=new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client).setUserAgent(media.headers.get("User-Agent")).createDataSource();
   long pos=i==1?Math.max(0,76315188624L-262144):0;androidx.media3.datasource.DataSpec spec=new androidx.media3.datasource.DataSpec.Builder().setUri(media.url).setPosition(pos).setFlags(i==2?1:0).build();
   try{r.putLong("datasource_"+i+"_length",ds.open(spec));r.putInt("datasource_"+i+"_bytes",ds.read(new byte[1024],0,1024));}catch(Exception e){r.putString("datasource_"+i+"_error",e.getClass().getSimpleName());r.putInt("datasource_"+i+"_http",PlaybackErrors.httpStatus(e));}finally{ds.close();}
  }
 }
 private static void requestRanges(MediaSpec media,Bundle r)throws Exception{
  String[] ranges={null,"bytes=0-","bytes=0-8388607","bytes=0-131071"};
  for(int i=0;i<ranges.length;i++){
   Request.Builder request=new Request.Builder().url(media.url);if(ranges[i]!=null)request.header("Range",ranges[i]);
   try(Response response=Net.client(media).newCall(request.build()).execute()){
    r.putInt("request_"+i+"_http",response.code());r.putString("request_"+i+"_code",safeCode(response));r.putString("request_"+i+"_scheme",response.request().url().scheme());r.putString("request_"+i+"_host",response.request().url().host());r.putString("request_"+i+"_content_range",response.header("Content-Range",""));r.putLong("request_"+i+"_length",response.body()==null?-1:response.body().contentLength());r.putInt("request_"+i+"_bytes",response.isSuccessful()?response.peekBody(1024).bytes().length:0);
   }Thread.sleep(400);
  }
 }
 private static void sample(MediaSpec media,Bundle r,int cap)throws Exception{
  OkHttpClient client=Net.client(media).newBuilder().addNetworkInterceptor(chain->{Request actual=chain.request();r.putString("wire_host",actual.url().host());r.putString("wire_scheme",actual.url().scheme());r.putBoolean("wire_referer",media.headers.get("Referer").equals(actual.header("Referer")));r.putBoolean("wire_ua",media.headers.get("User-Agent").equals(actual.header("User-Agent")));return chain.proceed(actual);}).build();
  long started=SystemClock.elapsedRealtime();
  try(Response response=client.newCall(new Request.Builder().url(media.url).header("Range","bytes=0-"+(cap-1)).build()).execute()){
   r.putInt("range_http",response.code());r.putString("final_host",response.request().url().host());r.putString("referer_host",UrlRules.host(java.util.Objects.toString(response.request().header("Referer"),"")));r.putString("ua_kind",CloudApi.QUARK_UA.equals(response.request().header("User-Agent"))?"quark":Net.UA.equals(response.request().header("User-Agent"))?"browser":"other");r.putString("safe_service_code",safeCode(response));
   if(!response.isSuccessful()||response.body()==null)throw new IOException("Video response status "+response.code());
   long size=0;byte[] b=new byte[32768];try(InputStream in=response.body().byteStream()){int n;while(size<cap&&(n=in.read(b,0,(int)Math.min(b.length,cap-size)))!=-1){size+=n;if(SystemClock.elapsedRealtime()-started>20000)break;}}
   long elapsed=Math.max(1,SystemClock.elapsedRealtime()-started);r.putLong("sample_bytes",size);r.putLong("sample_ms",elapsed);r.putLong("sample_kib_per_second",size*1000/elapsed/1024);
   String range=response.header("Content-Range","");long length=0;try{length=Long.parseLong(range.substring(range.lastIndexOf('/')+1));}catch(Exception ignored){}
   if(length>1048576)for(int i=0;i<2;i++){long pos=i==0?length/2:Math.max(0,length-262144);try(Response part=client.newCall(new Request.Builder().url(media.url).header("Range","bytes="+pos+"-"+(pos+4095)).build()).execute()){r.putInt("seek_"+i+"_http",part.code());r.putBoolean("seek_"+i+"_range",part.header("Content-Range","").startsWith("bytes "+pos+"-"));r.putInt("seek_"+i+"_bytes",part.peekBody(4096).bytes().length);r.putString("seek_"+i+"_code",safeCode(part));}}
  }
 }
 private static String safeCode(Response r){if(r.isSuccessful())return "";try{String text=r.peekBody(16384).string();java.util.regex.Matcher m=java.util.regex.Pattern.compile("<Code>([A-Za-z0-9_.-]{1,80})</Code>").matcher(text);return m.find()?m.group(1):"";}catch(Exception e){return "";}}
}
