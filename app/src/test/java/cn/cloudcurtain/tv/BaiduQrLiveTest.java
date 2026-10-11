package cn.cloudcurtain.tv;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BaiduQrLiveTest {
    @Test public void officialServiceReturnsQrWithoutWebView()throws Exception{
        Assume.assumeTrue("Opt-in network check; never scans or logs into an account", "1".equals(System.getenv("CLOUDCURTAIN_LIVE_QR")));
        try(BaiduQrApi api=new BaiduQrApi()){
            byte[] bytes=api.start();android.graphics.Bitmap bitmap=LoginQr.decode(bytes);
            assertTrue(bitmap.getWidth()>=80);assertEquals(bitmap.getWidth(),bitmap.getHeight());bitmap.recycle();
            try{assertEquals(BaiduQrApi.State.WAITING,api.poll());}catch(java.net.SocketTimeoutException expected){/* Long polling with no phone scanning may time out. */}
            assertTrue("No user login session can exist before a scan",api.cookies().isEmpty());
        }
    }
}
