package cn.cloudcurtain.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import android.graphics.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AliQrPixelsTest {
    @Test public void capturesOnlyFullyVisibleAuthRegion()throws Exception{
        Rect r=AliQrPixels.bounds(JSONObject.quote("{x:100,y:50,width:300,height:300,viewport:640}"),1280,720);assertEquals(new Rect(200,100,800,700),r);
        assertNull(AliQrPixels.bounds("null",1280,720));assertNull(AliQrPixels.bounds(JSONObject.quote("{x:100,y:50,width:300,height:600,viewport:640}"),1280,720));assertNull(AliQrPixels.bounds(JSONObject.quote("{x:-1,y:0,width:300,height:300,viewport:640}"),1280,720));
    }
    @Test public void qrPixelsRoundTripAndRejectOtherProvider()throws Exception{
        Bitmap image=AliQrPixels.qr("https://passport.alipan.com/qr/test");try{assertEquals("https://passport.alipan.com/qr/test",AliQrPixels.decode(image));}finally{image.recycle();}
        image=AliQrPixels.qr("https://evil.example/login");try{assertEquals("",AliQrPixels.decode(image));}finally{image.recycle();}
    }
}