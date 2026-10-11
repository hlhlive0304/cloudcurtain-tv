package cn.cloudcurtain.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import android.webkit.CookieManager;
import android.view.*;
import android.widget.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="land-mdpi")
public class NewLoginTest {
    @Test public void nativeQrImagesAndWaitingStatesAreLive()throws Exception{
        Assume.assumeTrue("1".equals(System.getenv("CLOUDCURTAIN_LIVE_QR")));
        try(TianyiQrApi t=new TianyiQrApi()){
            byte[] bytes=t.start();assertTrue(LoginQr.decode(bytes).getWidth()>=80);
            // JVM ImageIO is independent of Robolectric's simulated Android bitmap.
            // Reflection keeps this desktop-only reader out of the Android compile API.
            Object bitmap=Class.forName("javax.imageio.ImageIO").getMethod("read",java.io.InputStream.class).invoke(null,new java.io.ByteArrayInputStream(bytes));
            int w=(Integer)bitmap.getClass().getMethod("getWidth").invoke(bitmap),h=(Integer)bitmap.getClass().getMethod("getHeight").invoke(bitmap);
            int[] pixels=(int[])bitmap.getClass().getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class).invoke(bitmap,0,0,w,h,null,0,w);
            String text=new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(w,h,pixels)))).getText();
            okhttp3.HttpUrl scanned=okhttp3.HttpUrl.get(text);assertTrue("Official Tianyi QR destination",Provider.TIANYI.allows(text));
            java.lang.reflect.Field field=TianyiQrApi.class.getDeclaredField("code");field.setAccessible(true);JSONObject code=(JSONObject)field.get(t);
            // Tianyi's uuid is the complete scan URL, containing a nested paras field.
            // encryuuid is a separate encrypted polling parameter, not the QR contents.
            assertTrue("Decoded QR must equal the original scan URL used by state polling",code.getString("uuid").equals(text));
            assertTrue("Encoded image argument must decode exactly once to the original URL",code.getString("uuid").equals(java.net.URLDecoder.decode(code.getString("encodeuuid"),"UTF-8")));
            assertEquals(TianyiQrApi.State.WAITING,t.poll());
        }
        try(AliQrApi a=new AliQrApi()){assertEquals(480,LoginQr.decode(a.start()).getWidth());assertEquals(AliQrApi.State.WAITING,a.poll());}
    }
    @Test public void tianyiRequiresConfirmationBeforeSavingAndRejectsForeignRedirect()throws Exception{
        TianyiQrApi t=new TianyiQrApi((u,f,h)->{throw new AssertionError();});
        assertThrows(java.io.IOException.class,t::complete);assertEquals(TianyiQrApi.State.SCANNED,TianyiQrApi.state(new JSONObject("{status:-11002}")));
        assertThrows(java.io.IOException.class,()->TianyiQrApi.state(new JSONObject("{status:0,redirectUrl:'https://example.com/'}")));
    }
    @Test public void baiduListingNeedsAuthenticatedResponseAndSupportsFolders()throws Exception{
        CloudApi api=new CloudApi(Provider.BAIDU,(u,b,h)->new JSONObject("{errno:0,list:[{path:'/电影',server_filename:'电影',isdir:1},{path:'/a.mp4',server_filename:'a.mp4',isdir:0,size:500}]}"));
        CloudApi.Page page=api.list("/",1);assertTrue(page.entries.get(0).directory);assertEquals("/电影",page.entries.get(0).id);
        assertThrows(java.io.IOException.class,()->new CloudApi(Provider.BAIDU,(u,b,h)->new JSONObject("{errno:-6}")).list("/",1));
    }
    @Test public void logoutRemovesOnlyChosenProviderIncludingHttpOnlyCookies(){
        // RoboCookieManager ignores expired Set-Cookie instead of deleting the old
        // value. Use OkHttp's independent RFC cookie parser for this regression.
        AccountSessions.Cookies cm=new AccountSessions.Cookies(){final List<okhttp3.Cookie> values=new ArrayList<>();public String getCookie(String url){List<String> found=new ArrayList<>();for(okhttp3.Cookie c:values)if(c.matches(okhttp3.HttpUrl.get(url))&&c.expiresAt()>System.currentTimeMillis())found.add(c.name()+"="+c.value());return String.join("; ",found);}public void setCookie(String url,String raw,android.webkit.ValueCallback<Boolean> done){okhttp3.Cookie c=okhttp3.Cookie.parse(okhttp3.HttpUrl.get(url),raw);if(c!=null){values.removeIf(old->old.name().equals(c.name())&&old.domain().equals(c.domain())&&old.path().equals(c.path()));if(c.expiresAt()>System.currentTimeMillis())values.add(c);}if(done!=null)done.onReceiveValue(c!=null);}public void flush(){}};
        cm.setCookie("https://pan.quark.cn/","quark_auth=test; Domain=.quark.cn; Path=/; Secure; HttpOnly",null);cm.setCookie("https://pan.baidu.com/","BDUSS=other; Domain=.baidu.com; Path=/; Secure; HttpOnly",null);
        AccountSessions.remember(Provider.QUARK,"https://drive.quark.cn/1/clouddrive/file/sort");int old=AccountSessions.generation(Provider.QUARK);int other=AccountSessions.generation(Provider.BAIDU);
        final boolean[] done={false};AccountSessions.clear(Provider.QUARK,cm,()->done[0]=true);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(old+1,AccountSessions.generation(Provider.QUARK));assertEquals(other,AccountSessions.generation(Provider.BAIDU));
        assertTrue("logout completion callback",done[0]);String q=cm.getCookie("https://pan.quark.cn/");assertTrue("selected provider cookie removed",q==null||!q.contains("quark_auth=test"));assertTrue("other provider retained",cm.getCookie("https://pan.baidu.com/").contains("BDUSS=other"));
    }
    @Test public void fileRowsKeepSpacingAndRemoteFocusAcrossWidths(){
        android.content.Context c=RuntimeEnvironment.getApplication();LinearLayout rows=Ui.column(c);
        for(int i=0;i<3;i++)rows.addView(new FileRow(c,new CloudApi.Entry(""+i,"一部很长的电影名称与字幕说明.mkv",false,123456),()->{}));
        for(int width:new int[]{640,960,1920}){rows.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));rows.layout(0,0,width,rows.getMeasuredHeight());
            assertTrue(rows.getChildAt(1).getTop()-rows.getChildAt(0).getBottom()>=12);assertTrue(rows.getChildAt(0).getHeight()>=76);assertTrue(rows.getChildAt(0).isFocusable());ViewGroup detail=(ViewGroup)((ViewGroup)rows.getChildAt(0)).getChildAt(1);assertTrue(detail.getChildAt(1).getTop()>=detail.getChildAt(0).getBottom());}
    }
}
