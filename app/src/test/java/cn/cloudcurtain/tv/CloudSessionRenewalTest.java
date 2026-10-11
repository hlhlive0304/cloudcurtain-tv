package cn.cloudcurtain.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CloudSessionRenewalTest {
    private static final long NOW=1801526400000L; // 2027-02-02 00:00 UTC
    static class AliStore implements AliTokenSession.Store {String value;int writes;AliStore(String s){value=s;}public String read(){return value;}public void write(String s){value=s;writes++;}}
    static class TianyiStore implements TianyiSession.Store {String value="stale";int writes;public String read(){return value;}public void write(String s){value=s;writes++;}}
    @Test public void aliUsesAbsoluteExpiryAndRotatesOnlyNearExpiry()throws Exception{
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US);format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        AliStore store=new AliStore(new JSONObject().put("access_token","old").put("refresh_token","rotation-1").put("user_id","same").put("resource_drive_id","resource").put("expire_time",format.format(new java.util.Date(NOW+3600000))).toString());
        AtomicLong clock=new AtomicLong(NOW);AtomicInteger calls=new AtomicInteger();
        AliTokenSession session=new AliTokenSession((u,b,h)->{assertEquals("https://api.aliyundrive.com/token/refresh",u);assertEquals("rotation-1",b.getString("refresh_token"));assertNull(h.get("Authorization"));calls.incrementAndGet();return new JSONObject("{access_token:'new',refresh_token:'rotation-2',expires_in:7200,user_id:'same'}");},store,clock::get);
        assertEquals("old",session.read().getString("access_token"));assertEquals(0,calls.get());clock.addAndGet(3500000);JSONObject token=session.read();assertEquals("new",token.getString("access_token"));assertEquals("rotation-2",token.getString("refresh_token"));assertEquals("resource",token.getString("resource_drive_id"));assertEquals("new",session.read().getString("access_token"));assertEquals(1,calls.get());assertEquals(1,store.writes);
    }
    @Test public void aliFailureAndLogoutNeverReplaceStoredCredentials()throws Exception{
        AliStore store=new AliStore("{access_token:'old',refresh_token:'refresh',user_id:'same'}");String before=store.value;
        AliTokenSession failure=new AliTokenSession((u,b,h)->new JSONObject("{code:'InvalidRefreshToken'}"),store,()->NOW);assertThrows(Net.ServiceException.class,failure::read);assertEquals(before,store.value);assertEquals(0,store.writes);
        AliTokenSession logout=new AliTokenSession((u,b,h)->{AccountSessions.invalidate(Provider.ALI);return new JSONObject("{access_token:'new',refresh_token:'next',user_id:'same',expires_in:7200}");},store,()->NOW);assertThrows(IOException.class,logout::read);assertEquals(before,store.value);assertEquals(0,store.writes);
    }
    @Test public void aliConcurrentInvalidAccessUsesAlreadyRotatedToken()throws Exception{
        AliStore store=new AliStore("{access_token:'new',refresh_token:'rotated',user_id:'same',_saved_at:"+NOW+",expires_in:7200}");AliTokenSession session=new AliTokenSession((u,b,h)->{throw new AssertionError("Must reuse newer token");},store,()->NOW);
        assertEquals("new",session.refresh(new JSONObject("{access_token:'old'}")).getString("access_token"));assertFalse(AliTokenSession.expiresSoon(new JSONObject("{expire_time:'2027-02-02T09:00:00+08:00'}"),NOW));
    }
    @Test public void tianyiRenewsRejectedPortalKeyOnceAndReusesIt()throws Exception{
        TianyiStore store=new TianyiStore();AtomicInteger calls=new AtomicInteger();TianyiSession session=new TianyiSession((u,b,h)->{int n=calls.incrementAndGet();if(n==1){assertEquals("stale",h.get("sessionKey"));throw new Net.ServiceException(400,"InvalidSessionKey");}if(n==2){assertTrue(u.contains("getUserBriefInfo.action"));assertNull(h.get("sessionKey"));return new JSONObject("{sessionKey:'renewed'}");}assertEquals("renewed",h.get("sessionKey"));return new JSONObject("{fileListAO:{}}" );},store);
        assertTrue(session.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers("Referer",Provider.TIANYI.origin)).has("fileListAO"));session.request("https://cloud.189.cn/api/portal/getFileInfo.action",null,Net.headers());assertEquals(4,calls.get());assertEquals(1,store.writes);
    }
    @Test public void tianyiDoesNotRefreshUnrelatedErrorsOrRestoreLoggedOutSession()throws Exception{
        TianyiStore store=new TianyiStore();AtomicInteger calls=new AtomicInteger();TianyiSession forbidden=new TianyiSession((u,b,h)->{calls.incrementAndGet();throw new Net.ServiceException(403,"Forbidden");},store);assertThrows(Net.ServiceException.class,()->forbidden.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers()));assertEquals(1,calls.get());
        TianyiSession logout=new TianyiSession((u,b,h)->{if(!u.contains("getUserBriefInfo"))throw new Net.ServiceException(400,"InvalidSessionKey");AccountSessions.invalidate(Provider.TIANYI);return new JSONObject("{sessionKey:'new'}");},store);assertThrows(IOException.class,()->logout.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers()));assertEquals(0,store.writes);
    }
    @Test public void tianyiRestoresWebsiteBeforeReadingAndValidatingPortalKey()throws Exception{
        TianyiStore store=new TianyiStore();java.util.List<String> events=new java.util.ArrayList<>();
        TianyiSession session=new TianyiSession((u,b,h)->{if(u.contains("getUserBriefInfo")){events.add("portal");return new JSONObject("{res_code:0,sessionKey:'renewed'}");}events.add("file:"+h.get("sessionKey"));if("stale".equals(h.get("sessionKey")))throw new Net.ServiceException(400,"InvalidSessionKey");return new JSONObject("{res_code:0,fileListAO:{}}");},store,()->events.add("restore"));
        session.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers());assertEquals(java.util.Arrays.asList("file:stale","restore","portal","file:renewed"),events);assertEquals("renewed",store.value);
    }
    @Test public void tianyiFailedWebsiteRestorePreservesStoredKey()throws Exception{
        TianyiStore store=new TianyiStore();AtomicInteger calls=new AtomicInteger();TianyiSession session=new TianyiSession((u,b,h)->{calls.incrementAndGet();throw new Net.ServiceException(400,"InvalidSessionKey");},store,()->{throw new Net.ServiceException(200,"InvalidSessionKey");});
        assertThrows(Net.ServiceException.class,()->session.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers()));assertEquals(1,calls.get());assertEquals("stale",store.value);assertEquals(0,store.writes);
    }
    @Test public void tianyiRejectedRenewalDoesNotPoisonStoredSession()throws Exception{
        TianyiStore store=new TianyiStore();AtomicInteger calls=new AtomicInteger();
        TianyiSession session=new TianyiSession((u,b,h)->{calls.incrementAndGet();if(u.contains("getUserBriefInfo"))return new JSONObject("{res_code:0,sessionKey:'portal-only'}");throw new Net.ServiceException(400,"InvalidSessionKey");},store);
        assertThrows(Net.ServiceException.class,()->session.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers()));assertEquals(3,calls.get());assertEquals("stale",store.value);assertEquals(0,store.writes);
    }
    @Test public void tianyiDoesNotSaveRenewedKeyWhenFileApiReportsFailure()throws Exception{
        TianyiStore store=new TianyiStore();AtomicInteger calls=new AtomicInteger();TianyiSession session=new TianyiSession((u,b,h)->{int n=calls.incrementAndGet();if(n==1)throw new Net.ServiceException(400,"InvalidSessionKey");if(n==2)return new JSONObject("{res_code:0,sessionKey:'renewed'}");return new JSONObject("{res_code:9,errorCode:'Forbidden'}");},store);
        assertThrows(Net.ServiceException.class,()->session.request("https://cloud.189.cn/api/open/file/listFiles.action",null,Net.headers()));assertEquals("stale",store.value);assertEquals(0,store.writes);
    }
    @Test public void tianyiRejectsNullPlaceholderAndFailedPortalReplies()throws Exception{
        for(String raw:new String[]{"{res_code:0,sessionKey:null}","{res_code:0,sessionKey:'null'}","{res_code:0,sessionKey:'undefined'}","{res_code:0,sessionKey:42}","{res_code:1,sessionKey:'not-valid'}","{errorCode:'InvalidSessionKey',sessionKey:'not-valid'}"})assertThrows(Net.ServiceException.class,()->TianyiSession.portalKey(new JSONObject(raw)));
        assertEquals("valid",TianyiSession.portalKey(new JSONObject("{res_code:0,sessionKey:'valid'}")));
    }

}