package cn.cloudcurtain.tv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;import java.lang.reflect.*;import java.io.*;
final class FileBrowserLiveCheck {
 static Field field(String name)throws Exception{Field f=CloudFilesActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
 static void run(Instrumentation test,Bundle result)throws Exception{
  Context context=test.getTargetContext();SharedPreferences prefs=context.getSharedPreferences("file_browser",0);Map<String,?> backup=prefs.getAll();
  try{int checks=0;for(Provider provider:MovieLibrary.ORDER){
   CloudFilesActivity activity=(CloudFilesActivity)test.startActivitySync(new Intent(context,CloudFilesActivity.class).putExtra("provider",provider.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   long start=SystemClock.elapsedRealtime();final boolean[] loaded={false};while(SystemClock.elapsedRealtime()-start<30000&&!loaded[0]){Thread.sleep(200);test.runOnMainSync(()->{try{loaded[0]=!(boolean)field("loading").get(activity);}catch(Exception e){throw new RuntimeException(e);}});}
   if(!loaded[0])throw new IOException("Cloud directory timeout");final boolean[] readOK={false};test.runOnMainSync(()->{try{readOK[0]=!((TextView)field("status").get(activity)).getText().toString().startsWith("未能");}catch(Exception e){throw new RuntimeException(e);}});if(!readOK[0])throw new IOException("Cloud directory unavailable");result.putBoolean(provider.name()+"_directory_loaded",true);
   List<CloudApi.Entry> fixtures=new ArrayList<>();fixtures.add(new CloudApi.Entry("folder","测试文件夹",true,0));for(String name:new String[]{"电影 A.mkv","电影 B.mp4","字幕.ass","文档.pdf","封面.jpg","音乐.flac","文件.torrent","压缩包.zip"}){CloudApi.Entry e=new CloudApi.Entry(name,name,false,1000000);e.modified=1700000000000L;e.source=name.endsWith("mp4")?"shared":"unknown";fixtures.add(e);}
   Method render=CloudFilesActivity.class.getDeclaredMethod("render");render.setAccessible(true);
   for(int mode=0;mode<4;mode++){final int selected=mode;test.runOnMainSync(()->{try{field("current").set(activity,fixtures);field("more").setBoolean(activity,false);FilePresentation.Options options=new FilePresentation.Options();options.view=selected;field("options").set(activity,options);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});test.waitForIdleSync();Thread.sleep(200);
    final int[] count={0};test.runOnMainSync(()->{try{FrameLayout area=(FrameLayout)field("area").get(activity);count[0]=check(area,area.getWidth());}catch(Exception e){throw new RuntimeException(e);}});if(count[0]!=fixtures.size())throw new IOException("File card count or bounds failed");checks++;
    if(provider==Provider.QUARK){android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"browser-view-"+mode+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
   }
   final int[] filtered={0};test.runOnMainSync(()->{try{FilePresentation.Options options=new FilePresentation.Options();options.types.add("video");options.sources.add("shared");options.sort=5;options.descending=true;field("options").set(activity,options);render.invoke(activity);filtered[0]=((LinearLayout)field("rows").get(activity)).getChildCount();}catch(Exception e){throw new RuntimeException(e);}});if(filtered[0]!=3)throw new IOException("Folder and shared-video filter failed");checks++;test.runOnMainSync(activity::finish);
  }result.putInt("mode_filter_checks",checks);}
  finally{SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> row:backup.entrySet()){Object v=row.getValue();if(v instanceof String)edit.putString(row.getKey(),(String)v);else if(v instanceof Integer)edit.putInt(row.getKey(),(Integer)v);else if(v instanceof Boolean)edit.putBoolean(row.getKey(),(Boolean)v);else if(v instanceof Set)edit.putStringSet(row.getKey(),(Set<String>)v);}edit.commit();}
 }
 static int check(View view,int width){
  if(view instanceof FileRow||view instanceof FileCard){if(!view.isFocusable()||view.getWidth()<=0||view.getHeight()<=0||view.getRight()>width+2)throw new IllegalStateException("File view clipped");return 1;}
  int count=0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=check(group.getChildAt(i),group.getWidth());}return count;
 }
}