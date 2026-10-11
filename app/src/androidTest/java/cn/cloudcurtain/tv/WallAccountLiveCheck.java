package cn.cloudcurtain.tv;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;import java.io.IOException;

/** Uses the user's existing, read-only movie index and real artwork cache. */
final class WallAccountLiveCheck {
 static Field field(String name)throws Exception{Field f=MovieWallActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
 static void awaitPage(Instrumentation test,MovieWallActivity a)throws Exception{
  Field status=field("status");long start=SystemClock.elapsedRealtime();final boolean[] ready={false};
  while(SystemClock.elapsedRealtime()-start<15000){test.runOnMainSync(()->{try{ready[0]=!((TextView)status.get(a)).getText().toString().equals("正在加载影片库…");if(a.isFinishing())throw new RuntimeException("Wall unexpectedly finished");}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(100);}throw new IOException("Wall page failed to load");
 }
 static void run(Instrumentation test,Bundle result)throws Exception{
  android.content.Context c=test.getTargetContext();MovieLibrary library=new MovieLibrary(c);result.putInt("real_indexed_movies",library.movies().size());if(library.movies().isEmpty())throw new IOException("Real movie index empty");
  long maxRoundtrip=0,maxHeap=0,firstLoad=0;int cycles=0,details=0,filters=0,artwork=0;MovieWallActivity wall=null;
  try{for(int cycle=0;cycle<3;cycle++){
   long begin=SystemClock.elapsedRealtime();wall=(MovieWallActivity)test.startActivitySync(new Intent(c,MovieWallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MovieWallActivity a=wall;awaitPage(test,a);firstLoad=Math.max(firstLoad,SystemClock.elapsedRealtime()-begin);
   for(Provider provider:MovieLibrary.ORDER){test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("filter:"+provider.name()).performClick());awaitPage(test,a);filters++;
    Field grid=field("grid"),dialog=field("detailsDialog"),scroll=field("scroll");
    test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("category:all").performClick());awaitPage(test,a);
    final boolean[] opened={false};test.runOnMainSync(()->{try{PosterGrid g=(PosterGrid)grid.get(a);if(g.getChildCount()>0&&g.getChildAt(0) instanceof PosterCard){g.getChildAt(0).performClick();AlertDialog d=(AlertDialog)dialog.get(a);opened[0]=d!=null&&d.isShowing();if(d!=null)d.dismiss();}}catch(Exception e){throw new RuntimeException(e);}});if(opened[0])details++;
    for(int step=0;step<8;step++){final int action=step;long post=SystemClock.elapsedRealtime();final int[] painted={0};test.runOnMainSync(()->{try{if(a.isFinishing()||!a.getWindow().getDecorView().isShown())throw new RuntimeException("Wall returned to home");PosterGrid g=(PosterGrid)grid.get(a);ScrollView s=(ScrollView)scroll.get(a);s.scrollTo(0,action%2==0?Math.max(0,g.getHeight()-s.getHeight()):0);for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i) instanceof PosterCard){FrameLayout frame=(FrameLayout)((PosterCard)g.getChildAt(i)).getChildAt(0);if(((ImageView)frame.getChildAt(0)).getDrawable()!=null)painted[0]++;}}catch(Exception e){throw new RuntimeException(e);}});maxRoundtrip=Math.max(maxRoundtrip,SystemClock.elapsedRealtime()-post);artwork=Math.max(artwork,painted[0]);maxHeap=Math.max(maxHeap,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());Thread.sleep(350);}
   }
   test.runOnMainSync(a::finish);wall=null;cycles++;Thread.sleep(400);
  }}finally{if(wall!=null){final MovieWallActivity last=wall;test.runOnMainSync(last::finish);}}
  result.putInt("open_close_cycles",cycles);result.putInt("provider_filter_cycles",filters);result.putInt("details_opened",details);result.putInt("peak_visible_posters",artwork);result.putLong("largest_ui_roundtrip_ms",maxRoundtrip);result.putLong("largest_initial_load_ms",firstLoad);result.putLong("peak_java_heap_bytes",maxHeap);result.putLong("heap_limit_bytes",Runtime.getRuntime().maxMemory());
  if(cycles!=3||filters!=12||maxRoundtrip>1500)throw new IOException("Real movie wall failed responsiveness check");
 }
}
