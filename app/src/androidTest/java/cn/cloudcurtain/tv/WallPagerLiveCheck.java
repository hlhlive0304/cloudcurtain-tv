package cn.cloudcurtain.tv;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
import java.io.IOException;import java.lang.reflect.*;

/** Exercises TV key events on the actual retained multi-page library. */
final class WallPagerLiveCheck {
    private static Field field(String name)throws Exception{Field f=MovieWallActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void ready(Instrumentation test,MovieWallActivity wall)throws Exception {
        long end=SystemClock.elapsedRealtime()+15000;boolean[] done={false};do{Thread.sleep(100);test.runOnMainSync(()->{try{done[0]=!((TextView)field("status").get(wall)).getText().toString().equals("正在加载影片库…");}catch(Exception e){throw new RuntimeException(e);}});}while(!done[0]&&SystemClock.elapsedRealtime()<end);
        if(!done[0])throw new IOException("Wall loading timed out");test.waitForIdleSync();Thread.sleep(200);
    }
    static void run(Instrumentation test,Bundle result)throws Exception {
        MovieWallActivity wall=(MovieWallActivity)test.startActivitySync(new Intent(test.getTargetContext(),MovieWallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            ready(test,wall);test.runOnMainSync(()->wall.findViewById(android.R.id.content).findViewWithTag("category:all").performClick());ready(test,wall);
            Button next=(Button)field("next").get(wall),previous=(Button)field("previous").get(wall);PosterGrid grid=(PosterGrid)field("grid").get(wall);
            if(!next.isEnabled()||grid.getChildCount()<2)throw new IOException("Multi-page library required");
            result.putInt("first_page_cards",grid.getChildCount());test.runOnMainSync(()->grid.getChildAt(0).requestFocus());
            boolean[] reached={false};for(int n=0;n<4&&!reached[0];n++){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);Thread.sleep(150);test.runOnMainSync(()->reached[0]=wall.getCurrentFocus()==next||wall.getCurrentFocus()==previous);}
            result.putBoolean("pager_reachable_by_remote",reached[0]);if(!reached[0])throw new IOException("Poster grid bypasses page buttons");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);ready(test,wall);
            result.putInt("page_after_next",field("pageIndex").getInt(wall));if(field("pageIndex").getInt(wall)!=1)throw new IOException("Remote next did not change page");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);ready(test,wall);
            if(field("pageIndex").getInt(wall)!=0)throw new IOException("Remote previous did not change page");result.putBoolean("remote_next_previous_work",true);
            boolean[] focused={false};test.runOnMainSync(()->focused[0]=wall.getCurrentFocus()==next);if(!focused[0])throw new IOException("Focus lost when previous became disabled");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(150);test.runOnMainSync(()->focused[0]=wall.getCurrentFocus()==grid.getChildAt(0));if(!focused[0])throw new IOException("Pager cannot enter posters");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);Thread.sleep(100);
            int pages=1;while(next.isEnabled()&&pages<20){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);ready(test,wall);pages++;}
            result.putInt("pages_reached_with_remote",pages);if(next.isEnabled()||!previous.isEnabled())throw new IOException("Last page boundary incorrect");
            test.runOnMainSync(()->focused[0]=wall.getCurrentFocus()==previous);if(!focused[0])throw new IOException("Last page lost focus when next became disabled");result.putBoolean("first_last_boundaries_keep_focus",true);
            Method show=MovieWallActivity.class.getDeclaredMethod("show",boolean.class);show.setAccessible(true);
            test.runOnMainSync(()->{try{field("selected").set(wall,((PosterCard)grid.getChildAt(0)).movie.key());show.invoke(wall,false);}catch(Exception e){throw new RuntimeException(e);}});ready(test,wall);
            test.runOnMainSync(()->focused[0]=wall.getCurrentFocus()==previous);if(!focused[0])throw new IOException("Library refresh stole page-button focus");result.putBoolean("refresh_retains_pager_focus",true);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(100);String[] tag={""};test.runOnMainSync(()->tag[0]=String.valueOf(wall.getCurrentFocus().getTag()));
            test.runOnMainSync(()->{try{show.invoke(wall,false);}catch(Exception e){throw new RuntimeException(e);}});ready(test,wall);test.runOnMainSync(()->focused[0]=wall.getCurrentFocus()!=null&&tag[0].equals(wall.getCurrentFocus().getTag()));
            if(!focused[0])throw new IOException("Library refresh lost focused poster");result.putBoolean("refresh_retains_poster_focus",true);
            test.runOnMainSync(()->wall.findViewById(android.R.id.content).findViewWithTag("filter:ALI").performClick());ready(test,wall);
            if(previous.getVisibility()==View.VISIBLE||next.getVisibility()==View.VISIBLE)throw new IOException("Small library exposes inactive pagination");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);Thread.sleep(100);test.runOnMainSync(()->focused[0]=wall.getCurrentFocus()!=null&&"filter:ALI".equals(wall.getCurrentFocus().getTag()));
            if(!focused[0])throw new IOException("Hidden page buttons trapped focus");result.putBoolean("single_page_filter_navigation",true);
        }finally{test.runOnMainSync(wall::finish);}
    }
}
