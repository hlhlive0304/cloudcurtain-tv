package cn.cloudcurtain.tv;

import android.view.*;
import android.widget.TextView;
import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AppSmokeTest {
    private boolean has(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(has(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    @Test public void homeHasFourCloudCardsAndRemoteFocus(){
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            Activity a=c.get();View root=a.getWindow().getDecorView();for(Provider p:Provider.values())assertTrue(p.title,has(root,p.title));assertNotNull(root.findFocus());
        }
    }
    @Test public void mediaStatePreservesHeadersAndSubtitleSources()throws Exception{
        MediaSpec m=new MediaSpec("https://cdn.example/a.mkv?sign=1","一部电影");m.key="TIANYI:42";m.provider="TIANYI";m.headers.put("Referer","https://cloud.189.cn/");m.subtitles.add(new MediaSpec.Sub("cloud:50","中文.ass","text/x-ssa"));
        MediaSpec copy=MediaSpec.parse(m.json());assertEquals(m.key,copy.key);assertEquals(m.url,copy.url);assertEquals("cloud:50",copy.subtitles.get(0).url);assertEquals(m.headers,copy.headers);
    }
    @Test public void settingsCanOpenWithoutApiCredentials(){try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){assertTrue(has(c.get().getWindow().getDecorView(),"外部字幕搜索 · OpenSubtitles（可选）"));}}
}
