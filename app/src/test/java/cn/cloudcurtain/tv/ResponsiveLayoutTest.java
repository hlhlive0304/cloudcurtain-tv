package cn.cloudcurtain.tv;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="land-mdpi")
public class ResponsiveLayoutTest {
    private void layout(View view,int width,int height){
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
    }
    private void separated(ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){
            View a=group.getChildAt(i);assertTrue("Control extends past right edge",a.getRight()<=group.getWidth());assertTrue(a.getLeft()>=0);
            assertTrue("Control extends past bottom edge",a.getBottom()<=group.getHeight());
            for(int j=i+1;j<group.getChildCount();j++){
                View b=group.getChildAt(j);
                assertFalse("Controls overlap",Rect.intersects(new Rect(a.getLeft(),a.getTop(),a.getRight(),a.getBottom()),new Rect(b.getLeft(),b.getTop(),b.getRight(),b.getBottom())));
            }
        }
    }
    private void home(int width,int height,int expectedColumns,boolean scrollExpected){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();ScrollView scroll=(ScrollView)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
            layout(scroll,width,height);
            ViewGroup cards=scroll.findViewWithTag("home_cards"),actions=scroll.findViewWithTag("home_actions");
            assertTrue("Card/action gap must remain clear",actions.getTop()-cards.getBottom()>=Ui.dp(a,32));
            int columns=0;for(int i=0;i<cards.getChildCount();i++)if(cards.getChildAt(i).getTop()==0)columns++;
            assertEquals(expectedColumns,columns);separated(cards);separated(actions);
            for(int i=0;i<cards.getChildCount();i++){
                View card=cards.getChildAt(i);card.requestFocus();assertEquals(1f,card.getScaleX(),0f);assertEquals(1f,card.getScaleY(),0f);
                separated((ViewGroup)card);
            }
            View next=FocusFinder.getInstance().findNextFocus(scroll,cards.getChildAt(4-expectedColumns),View.FOCUS_DOWN);
            assertNotNull("Remote can leave the last card row",next);
            assertEquals(actions,next.getParent());
            if(scrollExpected){
                assertTrue(scroll.getChildAt(0).getHeight()>height);
                View last=actions.getChildAt(actions.getChildCount()-1);last.requestFocus();
                Rect target=new Rect();last.getDrawingRect(target);scroll.offsetDescendantRectToMyCoords(last,target);
                scroll.requestChildRectangleOnScreen(scroll,target,true);
                assertTrue("Bottom actions remain reachable",scroll.getScrollY()>0);
            }
        }
    }
    @Test public void standardTvAndLiveResizeKeepSpacing(){
        home(960,540,4,false);home(640,360,2,true);home(360,640,1,true);home(1600,900,4,false);
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=((ViewGroup)controller.get().findViewById(android.R.id.content)).getChildAt(0);
            layout(root,960,540);ViewGroup cards=root.findViewWithTag("home_cards");assertEquals(0,cards.getChildAt(3).getTop());
            layout(root,640,360);assertTrue(cards.getChildAt(2).getTop()>0);separated(cards);
            layout(root,960,540);assertEquals(0,cards.getChildAt(3).getTop());
        }
    }
    @Test @Config(qualifiers="land-xhdpi") public void fullHdUsesDensityAwareLayout(){home(1920,1080,4,false);}
    @Test @Config(qualifiers="land-xxxhdpi") public void fourKUsesDensityAwareLayout(){home(3840,2160,4,false);}
    @Test public void largeSystemTextReflowsWithoutClipping(){
        android.content.res.Resources resources=RuntimeEnvironment.getApplication().getResources();
        Configuration original=new Configuration(resources.getConfiguration()),large=new Configuration(original);large.fontScale=1.5f;
        resources.updateConfiguration(large,resources.getDisplayMetrics());
        try{home(960,540,2,true);}finally{resources.updateConfiguration(original,resources.getDisplayMetrics());}
    }
    @Test public void toolbarWrapsLongButtonsAndCanReturnToOneRow(){
        var c=RuntimeEnvironment.getApplication();AdaptiveLayout.Flow flow=new AdaptiveLayout.Flow(c);
        for(String text:new String[]{"返回","官网首页","文件模式","原生播放 (123)","网页操作","向下翻页","向上翻页","刷新"})flow.addView(Ui.button(c,text,()->{}));
        flow.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));flow.layout(0,0,480,flow.getMeasuredHeight());
        assertTrue(flow.getChildAt(7).getTop()>0);separated(flow);
        flow.measure(View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));flow.layout(0,0,2000,flow.getMeasuredHeight());
        assertEquals(0,flow.getChildAt(7).getTop());separated(flow);
    }
}
