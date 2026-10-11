package cn.cloudcurtain.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoginSessionTest {
    @Test public void qrNonceIsSingleUseAndExpires(){
        LoginSession s=new LoginSession();String first=s.start(1000);
        assertTrue(s.accepts(first,1001));
        assertFalse(s.accepts("old",1001));
        assertFalse(s.accepts(first,1000+LoginSession.MAX_AGE_MS));
        s.stop();assertFalse(s.accepts(first,1002));
        String second=s.start(2000);assertNotEquals(first,second);assertTrue(s.accepts(second,2001));
    }
    @Test public void qrOriginsStayOnSelectedProvider(){
        assertTrue(LoginQr.originAllowed(Provider.TIANYI,"https://cloud.189.cn/web/login.html"));
        assertTrue(LoginQr.originAllowed(Provider.TIANYI,"https://open.e.189.cn/api/logbox"));
        assertFalse(LoginQr.originAllowed(Provider.TIANYI,"https://evil.example/qr.png"));
        assertTrue(LoginQr.originAllowed(Provider.QUARK,"https://uop.quark.cn/cas/custom/login"));
        assertTrue(LoginQr.originAllowed(Provider.ALI,"https://passport.aliyundrive.com/login"));
        assertFalse(LoginQr.originAllowed(Provider.ALI,"http://www.alipan.com/drive"));
    }
}
