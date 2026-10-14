package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

/** Read-only checks on the user's retained account; never log paths, signed URLs or subtitle text. */
final class BaiduSubtitleLiveCheck {
    static void run(Instrumentation test,Bundle result,String task,String hash)throws Exception{
        List<MovieLibrary.Movie> indexed=new MovieLibrary(test.getTargetContext()).movies();
        File selection=new File(test.getTargetContext().getCacheDir(),"baidu-subtitle-selection.json");
        if(task.equals("baidu_subtitle_discover")){
            CloudApi api=new CloudApi(Provider.BAIDU);Set<String> folders=new LinkedHashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add("/");
            for(MovieLibrary.Movie movie:indexed)if(movie.provider==Provider.BAIDU&&!queue.contains(movie.folder))queue.add(movie.folder);
            int read=0;while(!queue.isEmpty()&&read<100){
                String folder=queue.remove();if(!folders.add(folder))continue;read++;CloudApi.Page page=api.list(folder,1);
                MovieLibrary.Movie movie=null;for(MovieLibrary.Movie row:indexed)if(row.provider==Provider.BAIDU&&row.folder.equals(folder)){movie=row;break;}
                if(movie==null)for(CloudApi.Entry item:page.entries)if(!item.directory&&UrlRules.video(item.name)){movie=new MovieLibrary.Movie();movie.provider=Provider.BAIDU;movie.id=item.id;movie.name=item.name;movie.size=item.size;movie.folder=folder;movie.identify();break;}
                for(CloudApi.Entry item:page.entries){
                    if(item.directory&&(folder.equals("/")?Arrays.asList("电影","漫剧","电视剧").contains(item.name):true))queue.add(item.id);
                    if(movie==null)continue;
                    if(item.directory||!item.name.toLowerCase(Locale.ROOT).endsWith(".srt"))continue;
                    JSONObject json=new JSONObject().put("movie",movie.json()).put("id",item.id).put("name",item.name);
                    try(FileOutputStream out=new FileOutputStream(selection)){out.write(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                    result.putInt("folders_read",read);result.putBoolean("real_srt_found",true);result.putBoolean("generic_chinese_srt",item.name.equals("简中.srt"));result.putLong("subtitle_size",item.size);return;
                }
            }
            result.putInt("folders_read",read);throw new IOException("No SRT found in indexed movie folders");
        }
        MovieLibrary.Movie chosen=null;MediaSpec.Sub selected=null;int movies=0,subs=0;
        for(MovieLibrary.Movie movie:indexed){
            if(movie.provider!=Provider.BAIDU)continue;movies++;subs+=movie.subtitles.size();
            if(!hash.isEmpty()&&!hash.equals(Integer.toHexString(movie.playbackKey().hashCode())))continue;
            for(MediaSpec.Sub sub:movie.subtitles)if(chosen==null||sub.label.equals("简中.srt")){
                chosen=movie;selected=sub;if(sub.label.equals("简中.srt"))break;
            }
            if(selected!=null&&selected.label.equals("简中.srt"))break;
        }
        if(task.endsWith("_srt")){
            JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(selection.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            chosen=MovieLibrary.Movie.read(saved.getJSONObject("movie"));selected=new MediaSpec.Sub("cloud:"+saved.getString("id"),saved.getString("name"),"application/x-subrip");
        }
        result.putInt("indexed_movies_scanned",movies);result.putInt("indexed_subtitles",subs);
        if(chosen==null)throw new IOException("No indexed Baidu sidecar subtitle; refresh the selected folder first");
        result.putString("movie_hash",Integer.toHexString(chosen.playbackKey().hashCode()));
        result.putString("subtitle_hash",Integer.toHexString(selected.url.hashCode()));
        result.putBoolean("generic_chinese_srt",selected.label.equals("简中.srt"));
        result.putString("subtitle_mime",selected.mime);
        CloudApi api=new CloudApi(Provider.BAIDU);
        CloudApi.Entry entry=null;for(CloudApi.Entry item:api.list(chosen.folder,1).entries)if(!item.directory&&("cloud:"+item.id).equals(selected.url)){entry=item;break;}
        if(entry==null)throw new IOException("Indexed subtitle no longer present in folder");
        result.putLong("subtitle_size",entry.size);
        if(task.equals("baidu_subtitle_web_probe")){
            Map<String,String> headers=Net.headers("Referer",Provider.BAIDU.origin,"Origin","https://pan.baidu.com");
            JSONObject meta=Net.json("https://pan.baidu.com/api/filemetas?target="+android.net.Uri.encode(new JSONArray().put(entry.id).toString())+"&web=1",null,headers);
            JSONObject info=meta.getJSONArray("info").getJSONObject(0);String fsid=String.valueOf(info.getLong("fs_id"));
            result.putString("metadata_keys",CloudLiveInstrumentation.keys(info));
            JSONObject vars=Net.json("https://pan.baidu.com/api/gettemplatevariable?fields="+android.net.Uri.encode("[\"sign1\",\"sign2\",\"sign3\",\"timestamp\",\"bdstoken\"]")+"&web=1",null,headers);
            result.putString("vars_keys",CloudLiveInstrumentation.keys(vars));JSONObject data=vars.optJSONObject("result");if(data==null)data=vars;
            result.putString("session_keys",CloudLiveInstrumentation.keys(data));
            String one=data.optString("sign1"),three=data.optString("sign3");result.putBoolean("session_sign_available",!one.isEmpty()&&!three.isEmpty());
            if(one.isEmpty()||three.isEmpty())return;
            javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("RC4");cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(three.getBytes(java.nio.charset.StandardCharsets.UTF_8),"RC4"));
            String sign=android.util.Base64.encodeToString(cipher.doFinal(one.getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
            String url=android.net.Uri.parse("https://pan.baidu.com/api/download").buildUpon().appendQueryParameter("type","dlink").appendQueryParameter("fidlist","["+fsid+"]").appendQueryParameter("sign",sign).appendQueryParameter("timestamp",data.optString("timestamp")).appendQueryParameter("bdstoken",data.optString("bdstoken")).appendQueryParameter("web","1").build().toString();
            JSONObject reply=Net.json(url,null,headers);result.putInt("web_download_errno",reply.optInt("errno",-999));result.putString("web_download_keys",CloudLiveInstrumentation.keys(reply));
            JSONArray links=reply.optJSONArray("dlink");if(links==null||links.length()==0)return;
            String address=links.getJSONObject(0).optString("dlink");if(!UrlRules.isWeb(address))return;
            MediaSpec scope=new MediaSpec(address,"");scope.provider="BAIDU";scope.headers.put("Referer",Provider.BAIDU.origin);scope.headers.put("User-Agent",Net.UA);
            try(Response response=Net.client(scope).newCall(new Request.Builder().url(address).build()).execute()){
                result.putInt("web_file_http",response.code());result.putString("web_file_host",response.request().url().host());
                if(response.isSuccessful()){byte[] bytes=response.peekBody(4*1024*1024).bytes();String text=SubtitleText.decode(bytes);result.putInt("web_file_bytes",bytes.length);result.putBoolean("web_file_timeline",text.contains("-->"));result.putBoolean("web_file_ass_events",text.contains("[Events]"));result.putBoolean("web_file_chinese",text.matches("(?s).*[\\u4e00-\\u9fff].*"));}
                if(!response.isSuccessful())try{result.putInt("web_file_error_code",new JSONObject(response.peekBody(16384).string()).optInt("error_code",-1));}catch(Exception ignored){}
            }
            return;
        }
        if(task.equals("baidu_subtitle_probe")){
            MediaSpec scope=api.resolve(entry);HttpUrl source=HttpUrl.get(scope.url);
            result.putString("resolved_host",source.host());result.putString("resolved_query_names",String.join(",",source.queryParameterNames()));
            for(int i=0;i<6;i++){
                final String label="probe_"+i;boolean browser=i==2||i==3,range=i%2==1;
                scope.headers.put("User-Agent",i>=4?"netdisk":browser?Net.UA:"pan.baidu.com");
                OkHttpClient client=Net.client(scope).newBuilder().callTimeout(25,java.util.concurrent.TimeUnit.SECONDS)
                    .addNetworkInterceptor(chain->{Request request=chain.request();result.putBoolean(label+"_cookie",request.header("Cookie")!=null);result.putBoolean(label+"_browser_ua",Net.UA.equals(request.header("User-Agent")));result.putBoolean(label+"_referer",scope.headers.get("Referer").equals(request.header("Referer")));Response response=chain.proceed(request);result.putString(label+"_wire_host",request.url().host());return response;}).build();
                Request.Builder request=new Request.Builder().url(scope.url).header("User-Agent",Net.UA);if(range)request.header("Range","bytes=0-");
                try(Response response=client.newCall(request.build()).execute()){
                    result.putInt(label+"_http",response.code());result.putString(label+"_final_host",response.request().url().host());
                    result.putBoolean(label+"_timeline",response.isSuccessful()&&response.peekBody(4*1024*1024).string().contains("-->"));
                    if(!response.isSuccessful()){String body=response.peekBody(16384).string();java.util.regex.Matcher code=java.util.regex.Pattern.compile("<Code>([A-Za-z0-9_.-]{1,80})</Code>").matcher(body);result.putString(label+"_service_code",code.find()?code.group(1):"");try{result.putInt(label+"_error_code",new JSONObject(body).optInt("error_code",-1));}catch(Exception ignored){}}
                }catch(IOException e){result.putString(label+"_error",e.getClass().getSimpleName());}
            }
            return;
        }
        Activity activity=test.startActivitySync(new Intent(test.getTargetContext(),UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            MediaSpec media=new MediaSpec("https://pan.baidu.com/fixture","字幕测试");media.provider="BAIDU";
            MediaSpec.Sub cached=new Subtitles(activity).cache(selected,media);
            result.putBoolean("cached_local_file",cached.url.startsWith("file:"));
            File file=new File(android.net.Uri.parse(cached.url).getPath());String text=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);
            result.putBoolean("cached_timeline",text.contains("-->")||text.contains("[Events]"));result.putLong("cached_bytes",file.length());
            if(!result.getBoolean("cached_local_file")||!result.getBoolean("cached_timeline"))throw new IOException("Cached subtitle is not usable");
            if(task.startsWith("baidu_subtitle_render")){test.runOnMainSync(activity::finish);render(test,result,chosen,selected,text);}
        }finally{test.runOnMainSync(activity::finish);}
    }
    private static java.lang.reflect.Field field(String name)throws Exception{java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void render(Instrumentation test,Bundle result,MovieLibrary.Movie movie,MediaSpec.Sub selected,String text)throws Exception{
        List<Long> starts=new ArrayList<>();List<androidx.media3.extractor.text.CuesWithTiming> intervals=new ArrayList<>();final int[] count={0};
        new androidx.media3.extractor.text.DefaultSubtitleParserFactory().create(new androidx.media3.common.Format.Builder().setSampleMimeType(selected.mime).build())
            .parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),androidx.media3.extractor.text.SubtitleParser.OutputOptions.allCues(),cues->{count[0]++;intervals.add(cues);if(cues.durationUs<3500000)return;for(androidx.media3.common.text.Cue cue:cues.cues)if(cue.text!=null&&cue.text.toString().matches("(?s).*[\\u4e00-\\u9fff].*")){starts.add(cues.startTimeUs/1000+600);break;}});
        result.putInt("parsed_intervals",count[0]);result.putInt("parsed_long_chinese_intervals",starts.size());if(starts.isEmpty())throw new IOException("No decoded Chinese subtitle cue in this file");
        long at=starts.get(0);result.putLong("selected_cue_ms",at);
        MediaSpec media=new CloudApi(Provider.BAIDU).resolveCompatible(movie.entry());media.subtitles.add(selected);
        android.content.SharedPreferences progress=test.getTargetContext().getSharedPreferences("progress",0);String key=Integer.toHexString(media.key.hashCode());boolean had=progress.contains(key);long saved=progress.getLong(key,0);progress.edit().putLong(key,at).commit();
        PlayerActivity activity=null;
        try{
            activity=(PlayerActivity)test.startActivitySync(new Intent(test.getTargetContext(),PlayerActivity.class).putExtra("media",media.json()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final PlayerActivity screen=activity;
            java.lang.reflect.Field playerField=field("player"),addedField=field("added"),viewField=field("view");
            long end=SystemClock.elapsedRealtime()+60000;boolean[] ready={false};
            do{Thread.sleep(250);test.runOnMainSync(()->{try{androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)playerField.get(screen);ready[0]=p!=null&&p.isPlaying()&&field("firstFrame").getBoolean(screen)&&p.getVideoFormat()!=null&&PlaybackVideo.selected(p.getCurrentTracks());}catch(Exception e){throw new RuntimeException(e);}});}while(!ready[0]&&SystemClock.elapsedRealtime()<end);
            if(!ready[0])throw new IOException("Subtitle test video did not render");result.putBoolean("video_rendered",true);
            java.lang.reflect.Method chooser=PlayerActivity.class.getDeclaredMethod("cloudSubs");chooser.setAccessible(true);java.lang.reflect.Field dialogField=field("subtitleSuggestion");
            test.runOnMainSync(()->{try{((androidx.media3.exoplayer.ExoPlayer)playerField.get(screen)).pause();chooser.invoke(screen);AlertDialog dialog=(AlertDialog)dialogField.get(screen);dialog.getListView().performItemClick(dialog.getListView().getChildAt(0),0,0);}catch(Exception e){throw new RuntimeException(e);}});
            result.putBoolean("actual_cloud_chooser_used",true);end=SystemClock.elapsedRealtime()+45000;boolean[] attached={false};
            do{Thread.sleep(250);test.runOnMainSync(()->{try{attached[0]=!((List<?>)addedField.get(screen)).isEmpty();}catch(Exception e){throw new RuntimeException(e);}});}while(!attached[0]&&SystemClock.elapsedRealtime()<end);
            if(!attached[0])throw new IOException("Chosen cloud subtitle did not attach");result.putBoolean("chosen_subtitle_attached",true);
            test.runOnMainSync(()->{try{androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)playerField.get(screen);p.seekTo(at);p.play();}catch(Exception e){throw new RuntimeException(e);}});
            end=SystemClock.elapsedRealtime()+30000;boolean[] visible={false};
            do{Thread.sleep(250);test.runOnMainSync(()->{try{androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)playerField.get(screen);boolean chinese=false;for(androidx.media3.common.text.Cue cue:p.getCurrentCues().cues)chinese|=cue.text!=null&&cue.text.toString().matches("(?s).*[\\u4e00-\\u9fff].*");androidx.media3.ui.PlayerView view=(androidx.media3.ui.PlayerView)viewField.get(screen);visible[0]=chinese&&view.getSubtitleView().getVisibility()==android.view.View.VISIBLE;result.putBoolean("chinese_cue",chinese);result.putBoolean("subtitle_view_visible",view.getSubtitleView().getVisibility()==android.view.View.VISIBLE);result.putInt("cue_count",p.getCurrentCues().cues.size());result.putLong("position_ms",p.getCurrentPosition());result.putInt("player_state",p.getPlaybackState());result.putBoolean("pending_external_selection",field("pendingSubtitleLabel").get(screen)!=null);for(androidx.media3.common.Tracks.Group g:p.getCurrentTracks().getGroups())if(g.getType()==androidx.media3.common.C.TRACK_TYPE_TEXT){result.putBoolean("text_supported",g.isSupported());result.putBoolean("text_selected",g.isSelected());}}catch(Exception e){throw new RuntimeException(e);}});}while(!visible[0]&&SystemClock.elapsedRealtime()<end);
            result.putBoolean("visible_chinese_cue",visible[0]);if(!visible[0])throw new IOException("Chinese subtitle was parsed but not visible");
            result.putBoolean("cue_matches_file_timeline",waitAligned(test,screen,intervals,15000));if(!result.getBoolean("cue_matches_file_timeline"))throw new IOException("Displayed cue does not match the source timeline");
            android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),selected.mime.equals("text/x-ssa")?"baidu-ass-visible.png":"baidu-srt-visible.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            long target=starts.get(starts.size()/2);test.runOnMainSync(()->{try{((androidx.media3.exoplayer.ExoPlayer)playerField.get(screen)).seekTo(target);}catch(Exception e){throw new RuntimeException(e);}});
            if(!waitAligned(test,screen,intervals,30000))throw new IOException("Subtitle did not follow mid-film seek");result.putBoolean("seek_cue_matches_file",true);
            long[] before={0},after={0};test.runOnMainSync(()->{try{before[0]=((androidx.media3.exoplayer.ExoPlayer)playerField.get(screen)).getCurrentPosition();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(2000);test.runOnMainSync(()->{try{after[0]=((androidx.media3.exoplayer.ExoPlayer)playerField.get(screen)).getCurrentPosition();}catch(Exception e){throw new RuntimeException(e);}});if(after[0]<=before[0]+1000)throw new IOException("Video froze after attaching subtitles");result.putBoolean("video_advances_with_subtitle",true);
            java.lang.reflect.Field panel=field("playbackPanel");
            test.runOnMainSync(()->{try{screen.findViewById(android.R.id.content).findViewWithTag("player-tab:0").performClick();((AlertDialog)panel.get(screen)).findViewById(android.R.id.content).findViewWithTag("playback-choice:2").performClick();((AlertDialog)panel.get(screen)).findViewById(android.R.id.content).findViewWithTag("playback-choice:0").performClick();}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(1000);boolean[] disabled={false};test.runOnMainSync(()->{try{androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)playerField.get(screen);disabled[0]=p.getTrackSelectionParameters().disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT)&&p.getCurrentCues().cues.isEmpty();}catch(Exception e){throw new RuntimeException(e);}});if(!disabled[0])throw new IOException("Manual subtitle close was not respected");result.putBoolean("manual_close_respected",true);
            int old=((List<?>)addedField.get(screen)).size();test.runOnMainSync(()->{try{screen.findViewById(android.R.id.content).findViewWithTag("player-tab:0").performClick();((AlertDialog)panel.get(screen)).findViewById(android.R.id.content).findViewWithTag("playback-choice:3").performClick();AlertDialog dialog=(AlertDialog)dialogField.get(screen);dialog.getListView().performItemClick(dialog.getListView().getChildAt(0),0,0);}catch(Exception e){throw new RuntimeException(e);}});
            end=SystemClock.elapsedRealtime()+45000;do{Thread.sleep(250);}while(((List<?>)addedField.get(screen)).size()<=old&&SystemClock.elapsedRealtime()<end);if(((List<?>)addedField.get(screen)).size()<=old)throw new IOException("Reselected subtitle did not load");
            test.runOnMainSync(()->{try{androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)playerField.get(screen);p.seekTo(target);p.play();}catch(Exception e){throw new RuntimeException(e);}});
            if(!waitAligned(test,screen,intervals,30000))throw new IOException("Reselected subtitle did not display");result.putBoolean("manual_reselect_displays",true);
        }finally{if(activity!=null){final Activity screen=activity;test.runOnMainSync(screen::finish);Thread.sleep(500);}android.content.SharedPreferences.Editor edit=progress.edit();if(had)edit.putLong(key,saved);else edit.remove(key);edit.commit();}
    }
    private static boolean waitAligned(Instrumentation test,PlayerActivity activity,List<androidx.media3.extractor.text.CuesWithTiming> intervals,long timeout)throws Exception{
        boolean[] aligned={false};long end=SystemClock.elapsedRealtime()+timeout;
        do{Thread.sleep(250);test.runOnMainSync(()->{try{
            androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)field("player").get(activity);if(p==null||p.getPlaybackState()!=androidx.media3.common.Player.STATE_READY)return;
            for(androidx.media3.common.text.Cue shown:p.getCurrentCues().cues){if(shown.text==null||!shown.text.toString().matches("(?s).*[\\u4e00-\\u9fff].*"))continue;
                for(androidx.media3.extractor.text.CuesWithTiming interval:intervals){long now=p.getCurrentPosition()*1000;if(now<interval.startTimeUs-500000||now>interval.endTimeUs+500000)continue;for(androidx.media3.common.text.Cue expected:interval.cues)if(expected.text!=null&&shown.text.toString().equals(expected.text.toString()))aligned[0]=true;}}
        }catch(Exception e){throw new RuntimeException(e);}});}while(!aligned[0]&&SystemClock.elapsedRealtime()<end);return aligned[0];
    }
}
