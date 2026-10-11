package cn.cloudcurtain.tv;

import okhttp3.HttpUrl;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BaiduQrApiTest {
    private byte[] bytes(String value)throws Exception{return value.getBytes("UTF-8");}
    @Test public void completeQrFlowUsesTheSameChannelAndOfficialCallback()throws Exception{
        List<String> calls=new ArrayList<>();BaiduQrApi api=new BaiduQrApi(url->{
            calls.add(url);HttpUrl u=HttpUrl.get(url);
            switch(u.encodedPath()){
                case "/v2/api/getqrcode":assertEquals("netdisk",u.queryParameter("tpl"));return bytes("{\"errno\":0,\"sign\":\"session-test\",\"imgurl\":\"passport.baidu.com/qr/image.png\"}");
                case "/qr/image.png":return new byte[]{1,2,3};
                case "/channel/unicast":assertEquals("session-test",u.queryParameter("channel_id"));return bytes("{\"errno\":0,\"channel_v\":\"{\\\"status\\\":0,\\\"v\\\":\\\"test-ticket\\\"}\"}");
                case "/v3/login/main/qrbdusslogin":assertEquals("test-ticket",u.queryParameter("bduss"));assertEquals("cloudCurtainQr",u.queryParameter("callback"));return bytes("cloudCurtainQr({\"errInfo\":{\"no\":\"0\"},\"data\":{\"u\":\"https%3A%2F%2Fpan.baidu.com%2Fdisk%2Fmain\"}});");
                case "/disk/main":return bytes("ok");
                default:throw new AssertionError("Unexpected request");
            }
        });
        assertArrayEquals(new byte[]{1,2,3},api.start());assertEquals(BaiduQrApi.State.CONFIRMED,api.poll());api.complete();assertEquals(5,calls.size());
    }
    @Test public void distinguishesWaitingScanningCancellationAndBadResponses()throws Exception{
        assertEquals(BaiduQrApi.State.WAITING,BaiduQrApi.state(new JSONObject("{\"errno\":1}")));
        assertEquals(BaiduQrApi.State.SCANNED,BaiduQrApi.state(new JSONObject("{\"errno\":0,\"channel_v\":\"{\\\"status\\\":1}\"}")));
        assertEquals(BaiduQrApi.State.CANCELLED,BaiduQrApi.state(new JSONObject("{\"errno\":0,\"channel_v\":{\"status\":2}}")));
        assertThrows(IOException.class,()->BaiduQrApi.state(new JSONObject("{\"errno\":0,\"channel_v\":{\"status\":0}}")));
        assertThrows(IOException.class,()->BaiduQrApi.state(new JSONObject()));
    }
    @Test public void rejectsExternalQrImageBeforeRequestingIt()throws Exception{
        List<String> calls=new ArrayList<>();BaiduQrApi api=new BaiduQrApi(url->{calls.add(url);return bytes("{\"errno\":0,\"sign\":\"test\",\"imgurl\":\"https://example.com/qr.png\"}");});
        assertThrows(IOException.class,api::start);assertEquals(1,calls.size());
        assertFalse(BaiduQrApi.allowed("http://passport.baidu.com/a"));assertFalse(BaiduQrApi.allowed("https://passport.baidu.com.evil.example/a"));
    }
    @Test public void requiresServerConfirmationBeforeSavingLogin()throws Exception{
        BaiduQrApi api=new BaiduQrApi(url->{throw new AssertionError("Must not make a request before phone confirmation");});
        assertThrows(IOException.class,api::complete);
        assertThrows(IOException.class,()->BaiduQrApi.validateExchange(new JSONObject("{\"errInfo\":{\"no\":400023}}")));
        assertThrows(IOException.class,()->BaiduQrApi.validateExchange(new JSONObject("{\"data\":{}}")));
        assertTrue(api.cookies().isEmpty());
    }
    @Test public void neverEvaluatesArbitraryJsonp()throws Exception{
        assertEquals(0,BaiduQrApi.parse(bytes("cloudCurtainQr({\"errno\":0});")).getInt("errno"));
        assertThrows(Exception.class,()->BaiduQrApi.parse(bytes("otherCallback({\"errno\":0});")));
        assertThrows(Exception.class,()->BaiduQrApi.parse(bytes("cloudCurtainQr({\"errno\":0});alert('x');")));
    }
}
