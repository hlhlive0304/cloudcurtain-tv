package cn.cloudcurtain.tv;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
import androidx.media3.common.Player;import androidx.media3.exoplayer.ExoPlayer;
import java.io.*;import java.lang.reflect.*;import java.util.*;

/** Actual retained cloud episodes and actual DPAD events; no movie media is bundled. */
final class EpisodeNavigationLiveCheck {
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static MediaSpec media(PlayerActivity a)throws Exception{return (MediaSpec)field(PlayerActivity.class,"media").get(a);}
    private static void require(boolean value,String reason)throws IOException{if(!value)throw new IOException(reason);}
    private static void ready(Instrumentation test,PlayerActivity a,String id)throws Exception {
        long until=SystemClock.elapsedRealtime()+60000;boolean[] ready={false};
        do{Thread.sleep(200);test.runOnMainSync(()->{try{ExoPlayer p=(ExoPlayer)field(PlayerActivity.class,"player").get(a);ready[0]=!a.isFinishing()&&a.hasWindowFocus()&&media(a).key.equals("BAIDU:"+id)&&field(PlayerActivity.class,"episodes").get(a)!=null&&field(PlayerActivity.class,"firstFrame").getBoolean(a)&&p!=null&&p.getPlaybackState()==Player.STATE_READY;}catch(Exception e){throw new RuntimeException(e);}});}while(!ready[0]&&SystemClock.elapsedRealtime()<until);
        require(ready[0],"Episode did not render or directory did not finish");test.waitForIdleSync();
    }
    private static PlayerActivity opened(Instrumentation.ActivityMonitor monitor)throws Exception{Activity a=monitor.waitForActivityWithTimeout(60000);require(a instanceof PlayerActivity,"Player did not open");return (PlayerActivity)a;}
    private static PlayerActivity press(Instrumentation test,PlayerActivity current,int direction,Bundle result)throws Exception {
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);Thread.sleep(100);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);Thread.sleep(100);
        boolean[] moveRight={false};test.runOnMainSync(()->moveRight[0]=direction>0&&"episode_previous".equals(current.getCurrentFocus()==null?null:current.getCurrentFocus().getTag()));if(moveRight[0])test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        boolean[] focused={false};test.runOnMainSync(()->focused[0]=current.getCurrentFocus()==current.findViewById(android.R.id.content).findViewWithTag(direction<0?"episode_previous":"episode_next"));
        test.runOnMainSync(()->{View focus=current.getCurrentFocus();result.putString("remote_focus",focus==null?"none":String.valueOf(focus.getTag())+":"+focus.getClass().getSimpleName()+":"+focus.getId());View target=current.findViewById(android.R.id.content).findViewWithTag(direction<0?"episode_previous":"episode_next");result.putString("button_bounds",target.getLeft()+","+target.getTop()+","+target.getRight()+","+target.getBottom()+"/"+target.isShown()+"/"+target.isFocusable());});
        if(!focused[0]){android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getCacheDir(),"episode-focus-failure.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
        require(focused[0],"Episode button not reachable with DPAD");Instrumentation.ActivityMonitor monitor=test.addMonitor(PlayerActivity.class.getName(),null,false);
        try{test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);return opened(monitor);}finally{test.removeMonitor(monitor);}
    }
    static void run(Instrumentation test,Bundle result)throws Exception {
        Context context=test.getTargetContext();MovieLibrary.Movie first=BaiduBufferLiveCheck.chosen(test,"878005e");MediaSpec seed=new MediaSpec("https://unused.example/video",first.name).inFolder(first.folder,first.parents);seed.provider="BAIDU";seed.key=first.playbackKey();CloudApi api=new CloudApi(Provider.BAIDU);EpisodeQueue actual=EpisodeQueue.load(seed,api::list);
        require(actual.index==0&&actual.videos.size()>=12,"Expected actual first episode and series");result.putInt("actual_folder_videos",actual.videos.size());
        android.content.SharedPreferences prefs=context.getSharedPreferences("progress",0);Map<String,?> saved=new HashMap<>(prefs.getAll());List<String> touched=new ArrayList<>();android.content.SharedPreferences.Editor edit=prefs.edit();
        for(CloudApi.Entry e:actual.videos){String hash=Integer.toHexString(("BAIDU:"+e.id).hashCode());for(String prefix:new String[]{"","quality:","compatible:"})touched.add(prefix+hash);edit.putString("quality:"+hash,"baidu:1080").putBoolean("compatible:"+hash,true).putLong(hash,600000);}edit.commit();
        PlayerActivity active=null;CloudFilesActivity files=null;MovieWallActivity wall=null;
        try{
            files=(CloudFilesActivity)test.startActivitySync(new Intent(context,CloudFilesActivity.class).putExtra("provider","BAIDU").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));CloudFilesActivity browser=files;
            long until=SystemClock.elapsedRealtime()+20000;while(field(CloudFilesActivity.class,"loading").getBoolean(files)&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
            Method navigate=CloudFilesActivity.class.getDeclaredMethod("navigate",int.class,CloudApi.Entry.class);navigate.setAccessible(true);
            test.runOnMainSync(()->{try{navigate.invoke(browser,0,new CloudApi.Entry(first.folder,first.parents.get(first.parents.size()-1),true,0));}catch(Exception e){throw new RuntimeException(e);}});
            until=SystemClock.elapsedRealtime()+20000;while(field(CloudFilesActivity.class,"loading").getBoolean(files)&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
            prefs.edit().putLong(Integer.toHexString(first.playbackKey().hashCode()),0).commit();Method play=CloudFilesActivity.class.getDeclaredMethod("play",CloudApi.Entry.class);play.setAccessible(true);Instrumentation.ActivityMonitor monitor=test.addMonitor(PlayerActivity.class.getName(),null,false);
            try{test.runOnMainSync(()->{try{play.invoke(browser,first.entry());}catch(Exception e){throw new RuntimeException(e);}});active=opened(monitor);}finally{test.removeMonitor(monitor);}
            ready(test,active,first.id);require(media(active).folder.equals(first.folder),"File browser lost folder context");result.putBoolean("file_browser_context",true);
            PlayerActivity refreshed=active;test.runOnMainSync(()->{try{Method refresh=PlayerActivity.class.getDeclaredMethod("refreshAddress",boolean.class);refresh.setAccessible(true);refresh.invoke(refreshed,true);}catch(Exception e){throw new RuntimeException(e);}});ready(test,active,first.id);require(media(active).folder.equals(first.folder),"Address refresh lost episode context");result.putBoolean("address_refresh_keeps_episode_context",true);
            Button previous=(Button)active.findViewById(android.R.id.content).findViewWithTag("episode_previous");require(!previous.isEnabled(),"First episode can wrap backwards");
            active=press(test,active,1,result);ready(test,active,actual.videos.get(1).id);result.putBoolean("remote_next_rendered",true);
            PlayerActivity second=active;long[] position={-1};test.runOnMainSync(()->{try{position[0]=((ExoPlayer)field(PlayerActivity.class,"player").get(second)).getCurrentPosition();}catch(Exception e){throw new RuntimeException(e);}});require(position[0]<300000,"Next episode resumed stale progress");result.putBoolean("next_starts_from_beginning",true);
            require(prefs.getLong(Integer.toHexString(first.playbackKey().hashCode()),0)>0,"Departed episode progress missing");result.putBoolean("departed_progress_saved",true);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            boolean[] horizontal={false};test.runOnMainSync(()->horizontal[0]=second.getCurrentFocus()==second.findViewById(android.R.id.content).findViewWithTag("episode_next"));require(horizontal[0],"Cannot move right between episode buttons");test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);test.runOnMainSync(()->horizontal[0]=second.getCurrentFocus()==second.findViewById(android.R.id.content).findViewWithTag("episode_previous"));require(horizontal[0],"Cannot move left between episode buttons");result.putBoolean("remote_horizontal_navigation",true);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"episode-controls.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            active=press(test,active,-1,result);ready(test,active,first.id);result.putBoolean("remote_previous_rendered",true);require(active.getIntent().getBooleanExtra("from_start",false),"Previous episode not started afresh");
            PlayerActivity close=active;test.runOnMainSync(close::finish);test.waitForIdleSync();active=null;
            wall=(MovieWallActivity)test.startActivitySync(new Intent(context,MovieWallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MovieWallActivity library=wall;Thread.sleep(500);
            CloudApi.Entry end=actual.videos.get(actual.videos.size()-1);MovieLibrary.Movie last=new MovieLibrary.Movie();last.provider=Provider.BAIDU;last.id=end.id;last.name=end.name;last.size=end.size;last.folder=first.folder;last.parents.addAll(first.parents);last.identify();
            Method wallPlay=MovieWallActivity.class.getDeclaredMethod("play",MovieLibrary.Movie.class,boolean.class);wallPlay.setAccessible(true);monitor=test.addMonitor(PlayerActivity.class.getName(),null,false);
            try{test.runOnMainSync(()->{try{wallPlay.invoke(library,last,true);}catch(Exception e){throw new RuntimeException(e);}});active=opened(monitor);}finally{test.removeMonitor(monitor);}
            ready(test,active,end.id);require(media(active).folder.equals(first.folder),"Wall lost folder context");require(!active.findViewById(android.R.id.content).findViewWithTag("episode_next").isEnabled(),"Last episode can wrap forward");result.putBoolean("wall_context_and_last_boundary",true);
            PlayerActivity lastScreen=active;test.runOnMainSync(()->{try{Method tracks=PlayerActivity.class.getDeclaredMethod("tracks",int.class);tracks.setAccessible(true);tracks.invoke(lastScreen,androidx.media3.common.C.TRACK_TYPE_TEXT);AlertDialog panel=(AlertDialog)field(PlayerActivity.class,"playbackPanel").get(lastScreen);panel.findViewById(android.R.id.content).findViewWithTag("playback-choice:0").performClick();}catch(Exception e){throw new RuntimeException(e);}});require(field(PlayerActivity.class,"textDisabled").getBoolean(active),"Subtitle off selection not applied");
            active=press(test,active,-1,result);ready(test,active,actual.videos.get(actual.videos.size()-2).id);result.putBoolean("last_previous_rendered",true);
            require(!field(PlayerActivity.class,"textDisabled").getBoolean(active)&&!field(PlayerActivity.class,"manualSubtitle").getBoolean(active),"Prior episode subtitle choice leaked");result.putBoolean("subtitle_choice_reset_for_new_episode",true);
            PlayerActivity finalPlayer=active;test.runOnMainSync(()->{try{((androidx.media3.ui.PlayerView)field(PlayerActivity.class,"view").get(finalPlayer)).showController();}catch(Exception e){throw new RuntimeException(e);}});test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();Thread.sleep(300);require(wall.hasWindowFocus(),"Back key returned to an old episode instead of wall");result.putBoolean("back_returns_to_wall",true);
        }finally{
            if(active!=null){PlayerActivity close=active;test.runOnMainSync(close::finish);}if(wall!=null){MovieWallActivity close=wall;test.runOnMainSync(close::finish);}if(files!=null){CloudFilesActivity close=files;test.runOnMainSync(close::finish);}test.waitForIdleSync();Thread.sleep(300);
            edit=prefs.edit();for(String key:touched){Object value=saved.get(key);if(value==null)edit.remove(key);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);}edit.commit();
        }
    }
}
