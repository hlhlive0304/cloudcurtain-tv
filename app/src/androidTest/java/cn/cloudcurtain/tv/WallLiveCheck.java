package cn.cloudcurtain.tv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import android.graphics.*;import android.graphics.drawable.BitmapDrawable;import org.json.*;import java.io.*;import java.util.*;import java.lang.reflect.*;
final class WallLiveCheck{
 static void run(Instrumentation test,Bundle result)throws Exception{
  Context c=test.getTargetContext();SharedPreferences library=c.getSharedPreferences("movie_library",0),metadata=c.getSharedPreferences("movie_metadata",0);
  Map<String,?> oldLibrary=new HashMap<>(library.getAll()),oldMetadata=new HashMap<>(metadata.getAll());File recovery=new File(c.getFilesDir(),"wall-test-recovery.json");
  if(recovery.exists())throw new IOException("Previous wall test requires recovery");
  try(FileOutputStream out=new FileOutputStream(recovery)){out.write(new JSONObject().put("library",new JSONObject(oldLibrary)).put("metadata",new JSONObject(oldMetadata)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  MovieWallActivity wall=null;List<File> fixtures=new ArrayList<>();long maxUiDelay=0,maxImages=0,maxHeap=0;int cycles=0;
  try{
   JSONArray films=new JSONArray();SharedPreferences.Editor meta=metadata.edit();
   File dir=new File(c.getCacheDir(),"movie-posters");dir.mkdirs();Bitmap bitmap=Bitmap.createBitmap(720,1080,Bitmap.Config.RGB_565);
   for(int i=0;i<24;i++){bitmap.eraseColor(Color.rgb((i*17)%255,(i*43)%255,(i*79)%255));File file=new File(dir,"wall_stress_"+i+".jpg");try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,80,out);}fixtures.add(file);}bitmap.recycle();
   for(int i=0;i<3000;i++){MovieLibrary.Movie m=new MovieLibrary.Movie();m.provider=Provider.QUARK;m.id="wall-stress-"+i;m.name=String.format(Locale.ROOT,"Wall Stress %04d.2020.mkv",i);m.folder="fixture";m.identify();films.put(m.json());MovieMetadata md=new MovieMetadata();md.title=m.title;md.year="2020";md.poster="/wall_stress_"+(i%24)+".jpg";meta.putString("match:"+m.key(),md.json().toString());}
   meta.commit();SharedPreferences.Editor e=library.edit();for(Provider p:Provider.values())e.putString("items:"+p.name(),p==Provider.QUARK?films.toString():"[]");e.commit();
   for(int cycle=0;cycle<6;cycle++){
    wall=(MovieWallActivity)test.startActivitySync(new Intent(c,MovieWallActivity.class).putExtra("provider","QUARK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    final MovieWallActivity a=wall;Field gf=MovieWallActivity.class.getDeclaredField("grid"),rf=MovieWallActivity.class.getDeclaredField("render");gf.setAccessible(true);rf.setAccessible(true);
    long began=SystemClock.elapsedRealtime();final int[] cards={0};while(SystemClock.elapsedRealtime()-began<10000){Thread.sleep(100);test.runOnMainSync(()->{try{PosterGrid g=(PosterGrid)gf.get(a);cards[0]=g.getChildCount()>0&&g.getChildAt(0) instanceof PosterCard?g.getChildCount():0;}catch(Exception ex){throw new RuntimeException(ex);}});if(cards[0]>0)break;}
    if(cards[0]==0||cards[0]>24)throw new IOException("Movie wall page not bounded");result.putInt("max_cards",cards[0]);result.putInt("single_resume_generation",rf.getInt(wall));if(rf.getInt(wall)!=1)throw new IOException("Movie wall double reload");
    Thread.sleep(1200);
    for(int step=0;step<12;step++){
     final long[] pixels={0};long posted=SystemClock.elapsedRealtime();final int action=step;
     test.runOnMainSync(()->{try{
      if(a.isFinishing())throw new RuntimeException("Wall unexpectedly finished");
      PosterGrid grid=(PosterGrid)gf.get(a);for(int i=0;i<grid.getChildCount();i++)if(grid.getChildAt(i) instanceof PosterCard){PosterCard card=(PosterCard)grid.getChildAt(i);FrameLayout frame=(FrameLayout)card.getChildAt(0);ImageView image=(ImageView)frame.getChildAt(0);if(image.getDrawable() instanceof BitmapDrawable)pixels[0]+=((BitmapDrawable)image.getDrawable()).getBitmap().getByteCount();}
      Field sf=MovieWallActivity.class.getDeclaredField("scroll");sf.setAccessible(true);ScrollView scroll=(ScrollView)sf.get(a);scroll.scrollTo(0,action%2==0?Math.max(0,grid.getHeight()-scroll.getHeight()):0);
      if(action==5||action==10){Field nf=MovieWallActivity.class.getDeclaredField("next");nf.setAccessible(true);((Button)nf.get(a)).performClick();}
     }catch(Exception ex){throw new RuntimeException(ex);}});
     maxUiDelay=Math.max(maxUiDelay,SystemClock.elapsedRealtime()-posted);maxImages=Math.max(maxImages,pixels[0]);maxHeap=Math.max(maxHeap,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());Thread.sleep(150);
    }
    test.runOnMainSync(a::finish);wall=null;cycles++;Thread.sleep(250);
   }
   result.putInt("indexed_fixture_movies",3000);result.putInt("open_close_cycles",cycles);result.putLong("largest_ui_roundtrip_ms",maxUiDelay);result.putLong("visible_image_bytes",maxImages);result.putLong("peak_java_heap_bytes",maxHeap);result.putLong("heap_limit_bytes",Runtime.getRuntime().maxMemory());
   if(maxUiDelay>1500||maxImages>12L*1024*1024)throw new IOException("Movie wall exceeded responsiveness or artwork budget");
  }finally{
   if(wall!=null){MovieWallActivity last=wall;test.runOnMainSync(last::finish);}
   restore(library,oldLibrary);restore(metadata,oldMetadata);for(File f:fixtures)if(f.isFile())f.delete();recovery.delete();
  }
 }
 private static void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
