package cn.cloudcurtain.tv;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;
import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;

/** Retained-account checks. Output contains state, hashes and dimensions, never signed addresses. */
final class OriginalPlaybackLiveCheck {
    static Field field(String name)throws Exception{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    static void invoke(PlayerActivity a,String name)throws Exception{Method m=PlayerActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    static ExoPlayer player(PlayerActivity a)throws Exception{return (ExoPlayer)field("player").get(a);}
    static MediaSpec media(PlayerActivity a)throws Exception{return (MediaSpec)field("media").get(a);}
    static MovieLibrary.Movie movie(Instrumentation t,String provider,String hash)throws Exception{for(MovieLibrary.Movie m:new MovieLibrary(t.getTargetContext()).movies())if(m.provider.name().equals(provider)&&Integer.toHexString(m.playbackKey().hashCode()).equals(hash))return m;throw new IOException("Fixture reference missing");}
    static PlayerActivity open(Instrumentation t,MediaSpec m,long at){return (PlayerActivity)t.startActivitySync(new Intent(t.getTargetContext(),PlayerActivity.class).putExtra("media",m.json()).putExtra("resume_position",at).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    static void waitFor(Instrumentation t,Callable<Boolean> condition)throws Exception{long until=SystemClock.elapsedRealtime()+60000;do{final boolean[] ready={false};t.runOnMainSync(()->{try{ready[0]=condition.call();}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(250);}while(SystemClock.elapsedRealtime()<until);throw new IOException("Expected playback state not reached");}
    static void advance(Instrumentation t,PlayerActivity a)throws Exception{final long[] before={0};t.runOnMainSync(()->{try{before[0]=player(a).getCurrentPosition();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(3500);t.runOnMainSync(()->{try{if(player(a).getCurrentPosition()<before[0]+2000)throw new IllegalStateException("Video clock stopped");}catch(Exception e){throw new RuntimeException(e);}});}
    static void put(android.content.SharedPreferences.Editor e,String key,Object value){if(value instanceof String)e.putString(key,(String)value);else if(value instanceof Long)e.putLong(key,(Long)value);else if(value instanceof Integer)e.putInt(key,(Integer)value);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);else if(value instanceof Float)e.putFloat(key,(Float)value);}
    static void run(Instrumentation t,Bundle out,String task)throws Exception{
        android.content.SharedPreferences progress=t.getTargetContext().getSharedPreferences("progress",0),history=t.getTargetContext().getSharedPreferences("playback_history",0);
        PlaybackHistory.IO.submit(()->{}).get(10,TimeUnit.SECONDS);Map<String,?> oldProgress=new HashMap<>(progress.getAll()),oldHistory=new HashMap<>(history.getAll());
        PlayerActivity a=null;java.net.ServerSocket listener=null;List<java.net.Socket> sockets=Collections.synchronizedList(new ArrayList<>());
        try{
            if(task.equals("quark_original_next")){
                MovieLibrary.Movie first=movie(t,"QUARK","80430006"),second=movie(t,"QUARK","87dff3f6");
                progress.edit().putString("quality:"+Integer.toHexString(first.playbackKey().hashCode()),"original").putString("quality:"+Integer.toHexString(second.playbackKey().hashCode()),"original").commit();
                MediaSpec source=new CloudApi(Provider.QUARK).resolveOriginal(first.entry()).inFolder(first.folder,first.parents);a=open(t,source,40000);final PlayerActivity screen=a;
                waitFor(t,()->field("firstFrame").getBoolean(screen)&&screen.findViewById(android.R.id.content).findViewWithTag("episode_next").isEnabled());
                Instrumentation.ActivityMonitor monitor=t.addMonitor(PlayerActivity.class.getName(),null,false);
                t.runOnMainSync(()->screen.findViewById(android.R.id.content).findViewWithTag("episode_next").requestFocus());t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                PlayerActivity next=(PlayerActivity)t.waitForMonitorWithTimeout(monitor,45000);t.removeMonitor(monitor);if(next==null)throw new IOException("Quark next episode did not open");a=next;
                waitFor(t,()->field("firstFrame").getBoolean(next)&&player(next).isPlaying());advance(t,next);
                t.runOnMainSync(()->{try{Format video=player(next).getVideoFormat();if(!media(next).key.equals(second.playbackKey())||media(next).compatible||!media(next).quality.equals("original")||video.width!=1920||video.height!=1080)throw new IllegalStateException("Next episode was not the original");out.putInt("next_width",video.width);out.putInt("next_height",video.height);out.putBoolean("next_from_start",player(next).getCurrentPosition()<15000);}catch(Exception e){throw new RuntimeException(e);}});
                out.putBoolean("remote_next_original_renders",true);out.putBoolean("next_advances",true);
            }else if(task.equals("quark_hd_guard")){
                MovieLibrary.Movie movie=movie(t,"QUARK","22b1a8b1");MediaSpec source=new CloudApi(Provider.QUARK).resolveQuality(movie.entry(),"auto");
                if(source.compatible)throw new IOException("An original is required for this test");a=open(t,source,52000);final PlayerActivity screen=a;
                waitFor(t,()->{AlertDialog d=(AlertDialog)field("errorDialog").get(screen);return d!=null&&d.isShowing()&&((android.widget.TextView)d.findViewById(android.R.id.message)).getText().toString().contains("低清");});
                t.runOnMainSync(()->{try{if(media(screen).compatible||!media(screen).key.equals(source.key)||!media(screen).quality.equals("auto")||player(screen).getCurrentPosition()<52000)throw new IllegalStateException("Original source/position lost");((AlertDialog)field("errorDialog").get(screen)).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();}catch(Exception e){throw new RuntimeException(e);}});
                out.putBoolean("low_quality_not_automatically_selected",true);out.putBoolean("original_position_preserved",true);
                waitFor(t,()->{AlertDialog d=(AlertDialog)field("playbackPanel").get(screen);return d!=null&&d.isShowing()&&d.findViewById(android.R.id.content).findViewWithTag("playback-choice:2")!=null;});
                t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                waitFor(t,()->field("firstFrame").getBoolean(screen)&&media(screen).compatible&&player(screen).isPlaying());advance(t,screen);
                t.runOnMainSync(()->{try{Format f=player(screen).getVideoFormat();out.putInt("manual_width",f.width);out.putInt("manual_height",f.height);out.putString("manual_quality",media(screen).quality);}catch(Exception e){throw new RuntimeException(e);}});
                out.putBoolean("manual_low_quality_requires_choice",true);out.putBoolean("manual_video_advances",true);
            }else if(task.equals("original_stall_refresh")){
                CloudApi api=new CloudApi(Provider.TIANYI);CloudApi.Entry entry=null;for(CloudApi.Entry e:api.list(Provider.TIANYI.root(),1).entries)if(!e.directory&&UrlRules.video(e.name)&&e.size>0&&e.size<400L*1024*1024){entry=e;break;}if(entry==null)throw new IOException("Small retained Tianyi video missing");
                MediaSpec source=api.resolveOriginal(entry);String key=Integer.toHexString(source.key.hashCode());progress.edit().putBoolean("hevc_base:"+key,true).commit();
                listener=new java.net.ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"));final java.net.ServerSocket server=listener;
                Thread accept=new Thread(()->{try{while(!server.isClosed())sockets.add(server.accept());}catch(IOException ignored){}});accept.setDaemon(true);accept.start();
                source.url="http://127.0.0.1:"+server.getLocalPort()+"/waiting.mp4";source.headers.clear();a=open(t,source,30000);final PlayerActivity screen=a;
                Thread.sleep(1500);t.runOnMainSync(()->{try{field("waitingSince").setLong(screen,SystemClock.elapsedRealtime()-45001);((Runnable)field("watchdog").get(screen)).run();}catch(Exception e){throw new RuntimeException(e);}});
                waitFor(t,()->field("firstFrame").getBoolean(screen)&&player(screen).isPlaying());advance(t,screen);
                t.runOnMainSync(()->{try{MediaSpec fresh=media(screen);Format f=player(screen).getVideoFormat();if(fresh.compatible||!fresh.quality.equals("original")||!fresh.key.equals(source.key)||player(screen).getCurrentPosition()<30000||field("baseLayerApplied").getBoolean(screen))throw new IllegalStateException("Original refresh changed the source");out.putInt("decoded_width",f.width);out.putInt("decoded_height",f.height);out.putString("decoded_mime",f.sampleMimeType);out.putBoolean("address_changed",!fresh.url.equals(source.url));out.putBoolean("one_refresh_used",((PlaybackRecovery)field("recovery").get(screen)).used());}catch(Exception e){throw new RuntimeException(e);}});
                out.putBoolean("original_stall_refresh_renders",true);out.putBoolean("original_quality_position_preserved",true);out.putBoolean("ordinary_video_unchanged_in_base_mode",true);
            }else if(task.equals("tianyi_base_layer")){
                MovieLibrary.Movie movie=movie(t,"TIANYI","2a142554");MediaSpec source=new CloudApi(Provider.TIANYI).resolveOriginal(movie.entry());String key=Integer.toHexString(source.key.hashCode());progress.edit().putBoolean("hevc_base:"+key,true).commit();a=open(t,source,52000);final PlayerActivity screen=a;
                waitFor(t,()->field("baseLayerApplied").getBoolean(screen)&&!player(screen).getCurrentTracks().getGroups().isEmpty());
                t.runOnMainSync(()->{try{for(Tracks.Group g:player(screen).getCurrentTracks().getGroups())if(g.getType()==C.TRACK_TYPE_VIDEO){Format f=g.getTrackFormat(0);if(!MimeTypes.VIDEO_H265.equals(f.sampleMimeType)||f.width!=3840||f.height!=2020)throw new IllegalStateException("Original HEVC layer dimensions changed");out.putInt("base_width",f.width);out.putInt("base_height",f.height);out.putString("base_mime",f.sampleMimeType);out.putBoolean("device_reports_supported",g.isSupported());}out.putBoolean("rendered_frame",field("firstFrame").getBoolean(screen));AlertDialog error=(AlertDialog)field("errorDialog").get(screen);if(error!=null)error.dismiss();invoke(screen,"decodeMenu");}catch(Exception e){throw new RuntimeException(e);}});
                waitFor(t,()->{AlertDialog d=(AlertDialog)field("playbackPanel").get(screen);View choice=d.findViewById(android.R.id.content).findViewWithTag("playback-choice:0");return d.isShowing()&&choice!=null&&choice.hasFocus()&&d.getWindow().getDecorView().hasWindowFocus();});t.waitForIdleSync();
                t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                waitFor(t,()->!field("hevcBase").getBoolean(screen)&&!field("baseLayerApplied").getBoolean(screen)&&!player(screen).getCurrentTracks().getGroups().isEmpty());
                out.putBoolean("same_file_original_resolution_preserved",true);out.putBoolean("remote_returns_to_auto_decode",true);out.putBoolean("real_tv_rendering_verified",false);
            }else throw new IOException("Unknown original check");
        }finally{
            if(a!=null){PlayerActivity screen=a;t.runOnMainSync(screen::finish);}if(listener!=null)listener.close();synchronized(sockets){for(java.net.Socket socket:sockets)socket.close();}
            Thread.sleep(500);PlaybackHistory.IO.submit(()->{}).get(10,TimeUnit.SECONDS);
            android.content.SharedPreferences.Editor p=progress.edit().clear(),h=history.edit().clear();for(Map.Entry<String,?> e:oldProgress.entrySet())put(p,e.getKey(),e.getValue());for(Map.Entry<String,?> e:oldHistory.entrySet())put(h,e.getKey(),e.getValue());p.commit();h.commit();
        }
    }
}
