package cn.cloudcurtain.tv;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import okhttp3.HttpUrl;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LoginRegressionTest {
    @Test public void explicitOfficialPageDoesNotScheduleAutomaticFileNavigation()throws Exception{
        android.content.Intent input=new android.content.Intent(RuntimeEnvironment.getApplication(),CloudBrowserActivity.class).putExtra("provider","QUARK");
        try(var control=Robolectric.buildActivity(CloudBrowserActivity.class,input).create()){
            CloudBrowserActivity activity=control.get();java.lang.reflect.Method check=CloudBrowserActivity.class.getDeclaredMethod("checkAccount");check.setAccessible(true);check.invoke(activity);
            java.lang.reflect.Field checking=CloudBrowserActivity.class.getDeclaredField("checking");checking.setAccessible(true);assertFalse(checking.getBoolean(activity));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }
    @Test public void tianyiImageAndPollReferToSameTicketWithoutDoubleEncoding()throws Exception{
        AtomicInteger step=new AtomicInteger();
        try(TianyiQrApi api=new TianyiQrApi((url,form,headers)->{
            String response;
            switch(step.getAndIncrement()){
                case 0:return new TianyiQrApi.Reply(TianyiQrApi.AUTH+"/login?lt=page-lt&reqId=page-request&appId=app",new byte[0]);
                case 1:assertEquals("page-request",headers.get("REQID"));response="{result:0,data:{appKey:'app',reqId:'code-request',returnUrl:'https://cloud.189.cn/web/main',paramId:'params'}}";break;
                case 2:assertEquals("page-request",headers.get("REQID"));response="{result:0,uuid:'plain',encryuuid:'encrypted+/=',encodeuuid:'encrypted%2B%2F%3D'}";break;
                case 3:HttpUrl parsed=HttpUrl.get(url);assertEquals("encrypted+/=",parsed.queryParameter("uuid"));assertFalse(url.contains("%252B"));assertEquals("code-request",parsed.queryParameter("REQID"));return new TianyiQrApi.Reply(url,new byte[]{1,2,3});
                case 4:assertEquals("code-request",headers.get("REQID"));assertEquals("plain",form.get("uuid"));assertEquals("encrypted+/=",form.get("encryuuid"));response="{status:-106}";break;
                default:throw new AssertionError("Unexpected request");
            }
            return new TianyiQrApi.Reply(url,response.getBytes("UTF-8"));
        })){api.start();assertEquals(TianyiQrApi.State.WAITING,api.poll());assertEquals(5,step.get());}
    }
    @Test public void errorDisplayHandlesChineseMultilineNullAndTransportErrorsWithoutRegex(){
        assertEquals("阿里请求失败\n请重试",LoginErrors.friendly(new IOException("阿里请求失败\n请重试")));
        assertTrue(LoginErrors.friendly(new IOException((String)null)).contains("未完成"));
        assertTrue(LoginErrors.friendly(new java.net.SocketTimeoutException()).contains("超时"));
        assertTrue(LoginErrors.friendly(new javax.net.ssl.SSLHandshakeException("certificate")).contains("安全连接"));
        assertFalse(LoginErrors.friendly(new IOException("错误 https://example.com/?token=secret")).contains("secret"));
    }
    @Test public void diagnosticsNeverPersistExceptionMessagesOrCredentials(){
        android.content.Context c=RuntimeEnvironment.getApplication();IOException problem=new IOException("BDUSS=private; https://example.com/?token=private");
        LoginDiagnostics.record(c,Provider.BAIDU,"测试读取",problem);String report=LoginDiagnostics.read(c);
        assertTrue(report.contains("IOException"));assertFalse(report.contains("private"));assertFalse(report.contains("BDUSS"));assertFalse(report.contains("example.com"));
    }
    @Test public void authenticatedNavigationStartsOneFileActivityForBaiduAndAli()throws Exception{
        for(Provider provider:new Provider[]{Provider.BAIDU,Provider.ALI}){
            android.content.Intent input=new android.content.Intent(RuntimeEnvironment.getApplication(),CloudBrowserActivity.class).putExtra("provider",provider.name());
            try(var control=Robolectric.buildActivity(CloudBrowserActivity.class,input).create()){
                CloudBrowserActivity activity=control.get();java.lang.reflect.Method open=CloudBrowserActivity.class.getDeclaredMethod("openFiles");open.setAccessible(true);open.invoke(activity);open.invoke(activity);
                android.content.Intent next=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(next);assertEquals(CloudFilesActivity.class.getName(),next.getComponent().getClassName());assertEquals(provider.name(),next.getStringExtra("provider"));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            }
        }
    }
    @Test public void aliRegistersItsOwnDeviceOnceThenRetriesAndReusesIdentity()throws Exception{
        AtomicReference<String> saved=new AtomicReference<>("");AtomicInteger registration=new AtomicInteger(),calls=new AtomicInteger();AtomicReference<String> device=new AtomicReference<>();
        AliDeviceSession.Store store=new AliDeviceSession.Store(){public String read(){return saved.get();}public void write(String data){saved.set(data);}};
        CloudApi.Transport transport=(url,body,headers)->{
            assertEquals("Bearer access",headers.get("Authorization"));assertEquals(130,headers.get("X-Signature").length());
            if(device.get()==null)device.set(headers.get("X-Device-Id"));else assertEquals(device.get(),headers.get("X-Device-Id"));
            if(url.endsWith("create_session")){registration.incrementAndGet();assertEquals("云幕 TV",body.getString("deviceName"));assertEquals(130,body.getString("pubKey").length());assertFalse(body.has("refreshToken"));return new JSONObject("{result:true}");}
            if(calls.getAndIncrement()==0)throw new Net.ServiceException(400,"DeviceSessionSignatureInvalid");return new JSONObject("{items:[]}");
        };
        JSONObject token=new JSONObject("{access_token:'access',user_id:'user'}");
        new AliDeviceSession(transport,store).request("/adrive/v3/file/list",new JSONObject(),token);
        new AliDeviceSession(transport,store).request("/adrive/v3/file/list",new JSONObject(),token);
        assertEquals(1,registration.get());assertEquals(3,calls.get());assertFalse(saved.get().contains("access"));
    }
    @Test public void aliDeviceLimitFailureDoesNotEvictDevicesOrLoop()throws Exception{
        AtomicInteger calls=new AtomicInteger();AliDeviceSession api=new AliDeviceSession((url,body,headers)->{calls.incrementAndGet();if(url.endsWith("create_session"))return new JSONObject("{result:false}");throw new Net.ServiceException(400,"DeviceSessionSignatureInvalid");},new AliDeviceSession.Store(){String value="";public String read(){return value;}public void write(String v){value=v;}});
        IOException error=assertThrows(IOException.class,()->api.request("/v2/file/list",new JSONObject(),new JSONObject("{access_token:'access',user_id:'user'}")));assertTrue(error.getMessage().contains("设备"));assertEquals(2,calls.get());
    }
    @Test public void aliSignatureIsValidLowSAndContainsRecoveryId()throws Exception{
        java.math.BigInteger key=java.math.BigInteger.valueOf(123);String message="app:device:user:0";byte[] sig=org.bouncycastle.util.encoders.Hex.decode(AliDeviceSession.sign(key,message));
        java.math.BigInteger r=new java.math.BigInteger(1,Arrays.copyOfRange(sig,0,32)),s=new java.math.BigInteger(1,Arrays.copyOfRange(sig,32,64));
        assertTrue(s.compareTo(AliDeviceSession.CURVE.getN().shiftRight(1))<=0);assertTrue(sig[64]>=0&&sig[64]<=3);
        org.bouncycastle.crypto.signers.ECDSASigner verifier=new org.bouncycastle.crypto.signers.ECDSASigner();verifier.init(false,new org.bouncycastle.crypto.params.ECPublicKeyParameters(AliDeviceSession.CURVE.getG().multiply(key),AliDeviceSession.DOMAIN));
        assertTrue(verifier.verifySignature(java.security.MessageDigest.getInstance("SHA-256").digest(message.getBytes("UTF-8")),r,s));
        assertFalse(verifier.verifySignature(new byte[32],r,s));
    }
}
