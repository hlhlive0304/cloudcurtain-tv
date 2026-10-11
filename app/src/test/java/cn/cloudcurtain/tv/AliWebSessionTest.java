package cn.cloudcurtain.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AliWebSessionTest {
    private String session()throws Exception{return JSONObject.quote(new JSONObject().put("access_token","test-access").put("refresh_token","test-refresh").put("user_id","test-user").put("resource_drive_id","test-drive").put("nickname","private-name").put("user_data",new JSONObject().put("private","data")).toString());}
    @Test public void onlyTopLevelOfficialOriginsAreEligible()throws Exception{
        for(String url:new String[]{"http://www.alipan.com/drive","https://www.alipan.com.evil.example/drive","https://passport.alipan.com/mini_login.htm","https://evil.example/drive"})assertNull(AliWebSession.parse(url,session()));
        assertNotNull(AliWebSession.parse("https://www.alipan.com/drive",session()));
        assertNotNull(AliWebSession.parse("https://www.aliyundrive.com/sign/callback",session()));
    }
    @Test public void completedSessionKeepsOnlyNativeApiFields()throws Exception{
        JSONObject token=AliWebSession.parse("https://www.alipan.com/sign/in",session());assertEquals("test-access",token.getString("access_token"));assertEquals("test-drive",token.getString("resource_drive_id"));assertFalse(token.has("nickname"));assertFalse(token.has("user_data"));
        assertNull(AliWebSession.parse("https://www.alipan.com/drive","null"));assertNull(AliWebSession.parse("https://www.alipan.com/drive",JSONObject.quote("{access_token:'partial'}")));
    }
    @Test public void deviceManagementMustBeCompletedByUser()throws Exception{
        JSONObject token=new JSONObject(new JSONTokener(session()).nextValue().toString()).put("deviceAmountLimit",true);
        assertThrows(java.io.IOException.class,()->AliWebSession.parse("https://www.alipan.com/sign/in",JSONObject.quote(token.toString())));
    }
}