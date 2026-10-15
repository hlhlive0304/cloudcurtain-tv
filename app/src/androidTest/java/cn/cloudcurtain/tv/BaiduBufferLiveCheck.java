package cn.cloudcurtain.tv;

import android.app.*;import android.content.*;import android.os.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;
import java.io.*;import java.util.*;import org.json.*;

/** Reads only the requested animation folder; captures playback timing without account secrets. */
final class BaiduBufferLiveCheck {
    private static java.lang.reflect.Field playerField(String name)throws Exception{java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    static void playback(Instrumentation test,Bundle result,String hash,boolean compatible,int seconds)throws Exception {
        MovieLibrary.Movie movie=chosen(test,hash);CloudApi api=new CloudApi(Provider.BAIDU);MediaSpec media=compatible?api.resolveCompatible(movie.entry()):api.resolveQuality(movie.entry(),"auto");
        android.content.SharedPreferences preferences=test.getTargetContext().getSharedPreferences("progress",0);String key=Integer.toHexString(media.key.hashCode());Map<String,?> saved=new HashMap<>(preferences.getAll());
        preferences.edit().putLong(key,0).commit();PlayerActivity activity=null;
        File trace=new File(test.getTargetContext().getCacheDir(),"baidu-buffer-trace.jsonl");
        try(FileOutputStream output=new FileOutputStream(trace)){
            activity=(PlayerActivity)test.startActivitySync(new Intent(test.getTargetContext(),PlayerActivity.class).putExtra("media",media.json()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final PlayerActivity screen=activity;
            long began=SystemClock.elapsedRealtime(),previous=-1;int playing=0,waiting=0,advancing=0;long minBuffer=Long.MAX_VALUE;boolean sought=false;
            while(SystemClock.elapsedRealtime()-began<seconds*1000L){
                Thread.sleep(1000);JSONObject row=new JSONObject();test.runOnMainSync(()->{try{
                    ExoPlayer player=(ExoPlayer)playerField("player").get(screen);PlaybackStats stats=(PlaybackStats)playerField("playbackStats").get(screen);MediaSpec current=(MediaSpec)playerField("media").get(screen);
                    row.put("elapsed_ms",SystemClock.elapsedRealtime()-began).put("state",player.getPlaybackState()).put("playing",player.isPlaying()).put("position_ms",player.getCurrentPosition())
                        .put("buffer_ahead_ms",player.getTotalBufferedDuration()).put("duration_ms",player.getDuration()).put("downloaded_bytes",stats.bytes).put("rate_bytes_per_second",stats.recentBytesPerSecond())
                        .put("requests",stats.requests).put("quality",current.quality).put("cap_bytes",PlaybackBuffer.targetBytes(Runtime.getRuntime().maxMemory(),current)).put("first_frame",playerField("firstFrame").getBoolean(screen));
                    Format video=player.getVideoFormat();if(video!=null)row.put("width",video.width).put("height",video.height).put("mime",video.sampleMimeType);
                    if(player.getVideoDecoderCounters()!=null)row.put("rendered_frames",player.getVideoDecoderCounters().renderedOutputBufferCount).put("dropped_frames",player.getVideoDecoderCounters().droppedBufferCount);
                    if(player.getPlayerError()!=null)row.put("error",player.getPlayerError().getErrorCodeName());
                }catch(Exception e){throw new RuntimeException(e);}});
                output.write((row.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));output.flush();
                long position=row.getLong("position_ms");if(row.getBoolean("playing")){playing++;if(position>previous+500)advancing++;minBuffer=Math.min(minBuffer,row.getLong("buffer_ahead_ms"));}if(row.getInt("state")==Player.STATE_BUFFERING)waiting++;previous=position;
                if(!sought&&playing>=40){sought=true;test.runOnMainSync(()->{try{ExoPlayer player=(ExoPlayer)playerField("player").get(screen);player.seekTo(Math.max(60000,player.getDuration()/2));}catch(Exception e){throw new RuntimeException(e);}});result.putBoolean("seek_requested",true);}
                result.putInt("playing_samples",playing);result.putInt("buffering_samples",waiting);result.putInt("advancing_samples",advancing);result.putLong("last_position_ms",position);result.putString("actual_quality",row.getString("quality"));
            }
            result.putLong("minimum_playing_buffer_ms",minBuffer);result.putInt("continuous_test_seconds",seconds);
            if(playing<seconds-30||advancing<seconds-35)throw new IOException("Continuous animation playback did not advance sufficiently");
        }finally {
            if(activity!=null){PlayerActivity screen=activity;test.runOnMainSync(screen::finish);Thread.sleep(300);}
            android.content.SharedPreferences.Editor edit=preferences.edit();for(String field:new String[]{key,"quality:"+key,"compatible:"+key}){Object value=saved.get(field);if(value==null)edit.remove(field);else if(value instanceof Long)edit.putLong(field,(Long)value);else if(value instanceof String)edit.putString(field,(String)value);else if(value instanceof Boolean)edit.putBoolean(field,(Boolean)value);}edit.commit();
        }
    }
    static MovieLibrary.Movie chosen(Instrumentation test,String hash)throws Exception {
        File source=new File(test.getTargetContext().getCacheDir(),"baidu-buffer-selection.json");JSONArray rows=new JSONArray(new String(java.nio.file.Files.readAllBytes(source.toPath()),java.nio.charset.StandardCharsets.UTF_8));
        for(int n=0;n<rows.length();n++){MovieLibrary.Movie m=MovieLibrary.Movie.read(rows.getJSONObject(n));if(hash.isEmpty()||Integer.toHexString(m.playbackKey().hashCode()).equals(hash))return m;}throw new IOException("Requested animation missing");
    }
    static void probe(Instrumentation test,Bundle result,String hash)throws Exception {
        MovieLibrary.Movie movie=chosen(test,hash);result.putString("movie_hash",Integer.toHexString(movie.playbackKey().hashCode()));result.putLong("movie_bytes",movie.size);
        CloudApi api=new CloudApi(Provider.BAIDU);
        for(String mode:new String[]{"original","compatible"}){
            MediaSpec spec=mode.equals("original")?api.resolveOriginal(movie.entry()):api.resolveCompatible(movie.entry());String address=spec.url;
            try(okhttp3.Response response=Net.client(spec).newCall(new okhttp3.Request.Builder().url(address).header("Range","bytes=0-4194303").build()).execute()){
                result.putInt(mode+"_http",response.code());result.putString(mode+"_scheme",response.request().url().scheme());result.putString(mode+"_host",response.request().url().host());
                if(!response.isSuccessful())continue;
                if(spec.compatible){String text=response.body().string();int segments=0;double seconds=0;for(String line:text.split("\n"))if(line.startsWith("#EXTINF:")){segments++;seconds+=Double.parseDouble(line.substring(8).split(",")[0]);}result.putDouble(mode+"_duration_seconds",seconds);result.putInt(mode+"_segments",segments);for(String line:text.split("\n"))if(!line.trim().isEmpty()&&!line.startsWith("#")){address=response.request().url().resolve(line.trim()).toString();break;}}
                else {sample(response,result,mode);continue;}
            }
            try(okhttp3.Response response=Net.client(spec).newCall(new okhttp3.Request.Builder().url(address).build()).execute()){result.putInt(mode+"_segment_http",response.code());result.putString(mode+"_segment_scheme",response.request().url().scheme());sample(response,result,mode);}
        }
    }
    private static void sample(okhttp3.Response response,Bundle result,String mode)throws Exception {
        long start=SystemClock.elapsedRealtime(),total=0;byte[] bytes=new byte[65536];try(InputStream in=response.body().byteStream()){int n;while(total<4194304&&(n=in.read(bytes,0,(int)Math.min(bytes.length,4194304-total)))!=-1){total+=n;if(SystemClock.elapsedRealtime()-start>12000)break;}}
        result.putLong(mode+"_sample_bytes",total);result.putLong(mode+"_sample_ms",SystemClock.elapsedRealtime()-start);result.putLong(mode+"_kib_per_second",total*1000/Math.max(1,SystemClock.elapsedRealtime()-start)/1024);
    }
    static void inventory(Instrumentation test,Bundle result)throws Exception {
        CloudApi api=new CloudApi(Provider.BAIDU);ArrayDeque<MovieLibrary.Movie> queue=new ArrayDeque<>();
        for(CloudApi.Entry e:api.list("/",1).entries)if(e.directory&&e.name.trim().equals("漫剧")){MovieLibrary.Movie root=new MovieLibrary.Movie();root.id=e.id;root.parents.add("漫剧");queue.add(root);}
        List<MovieLibrary.Movie> videos=new ArrayList<>();int pages=0,files=0;Set<String> seen=new HashSet<>();
        while(!queue.isEmpty()&&pages<16){MovieLibrary.Movie folder=queue.remove();if(!seen.add(folder.id))continue;
            for(int page=1;page<=4&&pages<16;page++){
                CloudApi.Page listing=api.list(folder.id,page);pages++;
                for(CloudApi.Entry entry:listing.entries){
                    if(entry.directory&&folder.parents.size()<4){MovieLibrary.Movie child=new MovieLibrary.Movie();child.id=entry.id;child.parents.addAll(folder.parents);child.parents.add(entry.name);queue.add(child);}
                    else if(!entry.directory){files++;if(UrlRules.video(entry.name)){MovieLibrary.Movie movie=new MovieLibrary.Movie();movie.provider=Provider.BAIDU;movie.id=entry.id;movie.folder=folder.id;movie.name=entry.name;movie.size=entry.size;movie.parents.addAll(folder.parents);movie.identify();videos.add(movie);}}
                }
                if(!listing.more)break;Thread.sleep(300);
            }
        }
        videos.sort((a,b)->Integer.compare(b.name.length(),a.name.length()));JSONArray saved=new JSONArray();for(MovieLibrary.Movie m:videos)saved.put(m.json());
        try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getCacheDir(),"baidu-buffer-selection.json"))){out.write(saved.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        for(int n=0;n<Math.min(10,videos.size());n++){MovieLibrary.Movie movie=videos.get(n);result.putString("video_"+n+"_name",movie.name);result.putString("video_"+n+"_hash",Integer.toHexString(movie.playbackKey().hashCode()));result.putLong("video_"+n+"_bytes",movie.size);}
        result.putInt("animation_pages",pages);result.putInt("animation_files",files);result.putInt("animation_videos",videos.size());if(videos.isEmpty())throw new IOException("Requested animation folder has no videos");
    }
}
