package cn.cloudcurtain.tv;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CloudPlaybackRegressionTest {
    @Test public void quarkAutoDoesNotSilentlyDowngradeToLowDefinition()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->{
            if(u.contains("/file/download?"))throw new Net.ServiceException(400,"32003");
            return new JSONObject("{code:0,data:{video_list:[{accessable:true,video_info:{url:'https://video.quark.cn/low.mp4',width:480,height:202}},{accessable:false,video_info:{url:'https://video.quark.cn/4k.mp4',width:3840,height:1608}}]}}");
        });CloudApi.Entry entry=new CloudApi.Entry("movie","test.mkv",false,500);
        assertThrows(CloudApi.LowQualityException.class,()->api.resolveQuality(entry,"auto"));
        assertThrows(CloudApi.LowQualityException.class,()->api.resolveAutomaticCompatible(entry));
        // Manual selection remains available; the user sees the actual resolution.
        MediaSpec manual=api.resolveCompatible(entry);assertTrue(manual.compatible);assertEquals("202p · 在线转码",manual.qualityLabel);
    }
    @Test public void quarkAutoAcceptsWideScreenHdWithoutLetterboxPadding()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->new JSONObject("{code:0,data:{video_list:[{video_info:{url:'https://video.quark.cn/hd.mp4',width:1280,height:536}}]}}"));
        assertEquals("https://video.quark.cn/hd.mp4",api.resolveAutomaticCompatible(new CloudApi.Entry("movie","test.mkv",false,500)).url);
    }
    @Test public void quarkDownload400FallsBackToAuthorizedVideoStream()throws Exception{
        AtomicInteger calls=new AtomicInteger();CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->{
            assertEquals("https://pan.quark.cn",h.get("Origin"));
            if(calls.getAndIncrement()==0){assertTrue(u.contains("/file/download?"));assertEquals("movie",b.getJSONArray("fids").getString(0));throw new Net.ServiceException(400,"32003");}
            assertTrue(u.contains("/file/v2/play?"));assertEquals(CloudApi.QUARK_UA,h.get("User-Agent"));assertEquals("movie",b.getString("fid"));
            return new JSONObject("{code:0,data:{video_list:[{video_info:{url:'https://video.quark.cn/low.m3u8',width:1280,height:720}},{accessable:false,video_info:{url:'https://video.quark.cn/locked.m3u8',width:3840,height:2160}},{video_info:{url:'https://video.quark.cn/high.m3u8',width:1920,height:1080,success:true,finish:true}}]}}");
        });MediaSpec m=api.resolve(new CloudApi.Entry("movie","test.mkv",false,500));assertEquals(2,calls.get());assertEquals("https://video.quark.cn/high.m3u8",m.url);assertTrue(m.compatible);
    }
    @Test public void quarkNeverLaunchesUnavailableTranscodeOrUsesVideoApiForSubtitle()throws Exception{
        AtomicInteger count=new AtomicInteger();CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->{count.incrementAndGet();if(u.contains("/file/download?"))throw new Net.ServiceException(400,"32003");return new JSONObject("{code:0,data:{video_list:[{video_info:{url:'https://video.quark.cn/pending.m3u8',finish:false}}]}}");});
        assertThrows(java.io.IOException.class,()->api.resolve(new CloudApi.Entry("1","x.mkv",false,1)));assertEquals(2,count.get());
        assertThrows(java.io.IOException.class,()->api.resolve(new CloudApi.Entry("2","x.srt",false,1)));assertEquals(3,count.get());
    }
    @Test public void compatibleQuarkUsesAuthorized1080pStreamAndPreservesProgressKey()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->{
            assertTrue(u.contains("/file/v2/play?"));assertFalse(b.getString("resolutions").contains("4k"));
            return new JSONObject("{code:0,data:{video_list:[{video_info:{url:'https://video.quark.cn/4k.m3u8',width:3840,height:2160}},{video_info:{url:'https://video.quark.cn/1080.m3u8',width:1920,height:1080}},{video_info:{url:'https://video.quark.cn/720.m3u8',width:1280,height:720}}]}}");
        });MediaSpec m=api.resolveCompatible(new CloudApi.Entry("movie","video.mkv",false,1));assertEquals("https://video.quark.cn/1080.m3u8",m.url);assertEquals("QUARK:movie",m.key);assertEquals(CloudApi.QUARK_UA,m.headers.get("User-Agent"));assertTrue(m.compatible);MediaSpec restored=MediaSpec.parse(m.json());assertTrue(restored.compatible);assertEquals(m.key,restored.key);
    }
    @Test public void compatibleQuarkDoesNotFallBackTo4kOrUnreadyStream()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->new JSONObject("{code:0,data:{video_list:[{video_info:{url:'https://video.quark.cn/4k.m3u8',width:3840,height:2160}},{video_info:{url:'https://video.quark.cn/1080.m3u8',width:1920,height:1080,finish:false}}]}}"));
        assertThrows(java.io.IOException.class,()->api.resolveCompatible(new CloudApi.Entry("movie","video.mkv",false,1)));
    }
    @Test public void verifiedCdnGetsOnlyPublicPlaybackHeaders(){
        MediaSpec m=new MediaSpec("https://cloud.189.cn/video","");m.provider="TIANYI";m.headers.put("Referer",Provider.TIANYI.origin);m.headers.put("User-Agent",Net.UA);
        m.headers.put("Cookie","private");m.headers.put("Authorization","Bearer private");m.headers.put("X-Signature","private");m.headers.put("sessionKey","private");
        Request.Builder r=new Request.Builder().url("https://qd-gxnnfy-tj-person01.tjtele.com/video");
        Net.applyMediaHeaders(r,m,r.build().url().toString());assertEquals(Net.UA,r.build().header("User-Agent"));assertEquals(Provider.TIANYI.origin,r.build().header("Referer"));
        for(String secret:new String[]{"Cookie","Authorization","X-Signature","sessionKey"})assertNull(r.build().header(secret));
        for(String wrong:new String[]{"https://qd-gxnnfy-tj-person01.tjtele.com.evil.test/a","https://unknown.tjtele.com/a","http://qd-gxnnfy-tj-person01.tjtele.com/a","https://outside.example/a"}){
            Request.Builder outside=new Request.Builder().url(wrong);Net.applyMediaHeaders(outside,m,wrong);assertNull(outside.build().header("Referer"));assertFalse(Net.playbackHeadersHost(m,wrong));
        }
    }
    @Test public void explicitOriginalNeverSilentlyReturnsCompatibleVideo()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->{throw new Net.ServiceException(400,"32003");});
        assertThrows(java.io.IOException.class,()->api.resolveQuality(new CloudApi.Entry("film","Film.mkv",false,1),"original"));
    }
    @Test public void baiduKeepsRequiredUaAndStableIdentity()throws Exception{
        CloudApi api=new CloudApi(Provider.BAIDU,(u,b,h)->{assertTrue(u.contains("origin=dlna"));return new JSONObject("{errno:0,info:[{dlink:'https://d.pcs.baidu.com/file?ticket=test'}]}");});
        MediaSpec m=MediaSpec.parse(api.resolve(new CloudApi.Entry("/电影.mkv","电影.mkv",false,1)).json());assertEquals("pan.baidu.com",m.headers.get("User-Agent"));assertEquals("BAIDU:/电影.mkv",m.key);
    }
    @Test public void mediaRedirectPreservesRangeUaButNotCredentials()throws Exception{
        try(MockWebServer start=new MockWebServer();MockWebServer cdn=new MockWebServer()){
            start.start();cdn.start();start.enqueue(new MockResponse().setResponseCode(302).addHeader("Location",cdn.url("/video")));cdn.enqueue(new MockResponse().setResponseCode(206).addHeader("Content-Range","bytes 8-11/12").setBody("data"));
            MediaSpec m=new MediaSpec(start.url("/file").toString(),"video");m.headers.put("User-Agent","pan.baidu.com");m.headers.put("Cookie","session=test");
            androidx.media3.datasource.okhttp.OkHttpDataSource source=new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(Net.client(m)).setUserAgent(m.headers.get("User-Agent")).createDataSource();
            try{source.open(new androidx.media3.datasource.DataSpec.Builder().setUri(m.url).setPosition(8).build());byte[] buffer=new byte[4];assertEquals(4,source.read(buffer,0,4));assertEquals("data",new String(buffer,"UTF-8"));}finally{source.close();}
            RecordedRequest initial=start.takeRequest(),redirect=cdn.takeRequest();assertEquals("bytes=8-",initial.getHeader("Range"));assertEquals("bytes=8-",redirect.getHeader("Range"));assertEquals("pan.baidu.com",redirect.getHeader("User-Agent"));assertNull(redirect.getHeader("Cookie"));
        }
    }

    @Test public void observedTianyiCdnReceivesOnlyPublicHeaders(){
        MediaSpec media=new MediaSpec("https://cloud.189.cn/file","");media.provider="TIANYI";
        media.headers.put("Referer","https://cloud.189.cn/");media.headers.put("User-Agent",Net.UA);
        media.headers.put("Cookie","private=session");media.headers.put("Authorization","Bearer private");media.headers.put("sessionKey","private");
        String exact="https://media-qhxn-fj-home.qh6oss.ctyunxs.cn/file";Request.Builder request=new Request.Builder().url(exact);Net.applyMediaHeaders(request,media,exact);
        assertEquals(Net.UA,request.build().header("User-Agent"));assertEquals("https://cloud.189.cn/",request.build().header("Referer"));
        assertNull(request.build().header("Cookie"));assertNull(request.build().header("Authorization"));assertNull(request.build().header("sessionKey"));
        for(String unsafe:new String[]{"http://media-qhxn-fj-home.qh6oss.ctyunxs.cn/file","https://media-qhxn-fj-home.qh6oss.ctyunxs.cn.evil.test/file","https://other.ctyunxs.cn/file"})assertFalse(Net.playbackHeadersHost(media,unsafe));
    }
    @Test public void trustedCdnRulesRejectLookalikesAndDowngrade(){
        MediaSpec m=new MediaSpec("https://pan.baidu.com/file","");m.provider="BAIDU";assertTrue(Net.mediaHost(m,"https://d.baidupcs.com/file"));assertFalse(Net.mediaHost(m,"https://baidupcs.com.evil.test/file"));assertFalse(Net.mediaHost(m,"http://d.baidupcs.com/file"));
        m.provider="QUARK";assertTrue(Net.mediaHost(m,"https://download.quark.cn/file"));assertFalse(Net.mediaHost(m,"https://unknown.test/file"));
    }
    @Test public void aliCrossCdnSegmentsKeepPublicHeadersButNeverApiSecrets(){
        MediaSpec media=new MediaSpec("https://cn-beijing-video-preview.aliyundrive.net/movie.m3u8","");media.provider="ALI";media.headers.put("Referer","https://www.alipan.com/");media.headers.put("User-Agent",Net.UA);media.headers.put("Authorization","Bearer private");media.headers.put("Cookie","private=test");media.headers.put("X-Signature","private-signature");
        String url="https://cn-shanghai-video-preview.aliyundrive.net/segment.ts?x-oss-process=video,{tsid}&sign=public|test";Request.Builder builder=new Request.Builder().url(url);Net.applyMediaHeaders(builder,media,url);Request request=builder.build();
        assertEquals("https://www.alipan.com/",request.header("Referer"));assertEquals(Net.UA,request.header("User-Agent"));assertNull(request.header("Authorization"));assertNull(request.header("Cookie"));assertNull(request.header("X-Signature"));
        for(String unsafe:new String[]{"https://aliyundrive.net.evil.test/file","http://cn-beijing-video-preview.aliyundrive.net/file","https://outside.example/file"}){assertFalse(Net.mediaHost(media,unsafe));Request.Builder other=new Request.Builder().url(unsafe);Net.applyMediaHeaders(other,media,unsafe);assertNull(other.build().header("Referer"));}
    }
    @Test public void tianyiXmlRepeatedAndSingletonItemsPreserveIds()throws Exception{
        JSONObject data=TianyiResponse.parse("<?xml version='1.0'?><listFiles><fileListAO><folderList><id>12345678901234567</id><name>电影</name></folderList><fileList><id>2</id><name>x.mkv</name><size>22300000000</size></fileList><fileList><id>3</id><name>x.srt</name><size>500</size></fileList></fileListAO></listFiles>");
        CloudApi api=new CloudApi(Provider.TIANYI,(u,b,h)->{assertEquals("application/json;charset=UTF-8",h.get("Accept"));return data;});CloudApi.Page page=api.list("-11",1);assertEquals(3,page.entries.size());assertEquals("12345678901234567",page.entries.get(0).id);assertEquals(22300000000L,page.entries.get(1).size);
        JSONObject nested=TianyiResponse.parse("<listFiles><fileListAO><fileList><file><id>1</id><name>a.mkv</name></file></fileList></fileListAO></listFiles>");assertEquals(1,TianyiResponse.items(nested.getJSONObject("fileListAO"),"fileList").length());
    }
    @Test public void tianyiXmlRejectsHtmlAndEntitiesAndPropagatesSessionErrors()throws Exception{
        assertThrows(Exception.class,()->TianyiResponse.parse("<html><body>login</body></html>"));
        assertThrows(Exception.class,()->TianyiResponse.parse("<!DOCTYPE listFiles [<!ENTITY x SYSTEM 'file:///private'>]><listFiles>&x;</listFiles>"));
        JSONObject error=TianyiResponse.parse("<error><errorCode>InvalidSessionKey</errorCode></error>");assertThrows(Net.ServiceException.class,()->new CloudApi(Provider.TIANYI,(u,b,h)->error).list("-11",1));
    }
    @Test public void tianyiCompletesPortalSessionAfterSsoRedirect()throws Exception{
        AtomicInteger completed=new AtomicInteger();
        TianyiQrApi api=new TianyiQrApi((u,f,h)->{
            if(u.equals("https://cloud.189.cn/login-test"))return new TianyiQrApi.Reply("https://cloud.189.cn/web/main?sessionKey=test%2Bkey",new byte[0]);
            assertTrue(u.startsWith("https://cloud.189.cn/api/portal/v2/getUserBriefInfo.action?"));assertEquals("test+key",HttpUrl.get(u).queryParameter("sessionKey"));assertEquals("application/json;charset=UTF-8",h.get("Accept"));completed.incrementAndGet();return new TianyiQrApi.Reply(u,"{sessionKey:'initialized'}".getBytes("UTF-8"));
        });
        java.lang.reflect.Field redirect=TianyiQrApi.class.getDeclaredField("redirect");redirect.setAccessible(true);redirect.set(api,"https://cloud.189.cn/login-test");api.complete();assertEquals(1,completed.get());
    }
    @Test public void aliBootstrapKeepsAttemptFieldsAndCookiesSeparate()throws Exception{
        Map<String,String> form=AliQrApi.parseLoginForm("window.viewData = {\"loginFormData\":{\"appName\":\"aliyun_drive\",\"fromSite\":52,\"_csrf_token\":\"test-csrf\",\"hsiz\":\"test-session\",\"isMobile\":false,\"bizParams\":\"taobaoBizLoginFrom=web\"}};");
        assertEquals("test-csrf",form.get("_csrf_token"));assertEquals("false",form.get("isMobile"));
        HttpUrl u=HttpUrl.get(AliQrApi.AUTH+"/mini_login.htm");LoginCookieJar jar=new LoginCookieJar();jar.saveFromResponse(u,Collections.singletonList(Cookie.parse(u,"loginSession=test; Path=/; Secure; HttpOnly")));assertEquals(1,jar.loadForRequest(HttpUrl.get(AliQrApi.AUTH+"/newlogin/qrcode/query.do")).size());assertTrue(jar.loadForRequest(HttpUrl.get("https://other.test/")).isEmpty());assertTrue(new LoginCookieJar().loadForRequest(u).isEmpty());
    }
    @Test public void subtitleDownloadUsesPublicPlaybackRefererWithoutApiCredentials()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();server.enqueue(new MockResponse().setBody("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n字幕"));
            MediaSpec video=new MediaSpec(server.url("/video.m3u8").toString(),"");video.headers.put("Referer","https://www.alipan.com/");video.headers.put("User-Agent",Net.UA);video.headers.put("Authorization","Bearer test");video.headers.put("Cookie","session=test");
            String subtitle=server.url("/subtitle.vtt").toString();try(Response response=Net.client(Subtitles.remoteScope(subtitle,video)).newCall(new Request.Builder().url(subtitle).build()).execute()){assertEquals(200,response.code());}
            RecordedRequest request=server.takeRequest();assertEquals("https://www.alipan.com/",request.getHeader("Referer"));assertEquals(Net.UA,request.getHeader("User-Agent"));assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"));
            MediaSpec external=Subtitles.remoteScope("https://outside.example.com/sub.vtt",video);assertFalse(external.headers.containsKey("Referer"));video.subtitles.add(new MediaSpec.Sub("https://subtitle.example.com/sub.vtt","在线字幕","text/vtt"));MediaSpec listed=Subtitles.remoteScope("https://subtitle.example.com/sub.vtt",video);assertEquals("https://www.alipan.com/",listed.headers.get("Referer"));assertFalse(listed.headers.containsKey("Authorization"));assertFalse(listed.headers.containsKey("Cookie"));
        }
    }
 @org.junit.Test public void tianyiOnlyUpgradesObservedPlaybackCdnAndRetainsSignedRangeTarget(){
  MediaSpec media=new MediaSpec("https://cloud.189.cn/stream","");media.provider="TIANYI";
  String source="http://media-qhxn-fj-home.qh6oss.ctyunxs.cn/a/b?Expires=123&signature=a%2Bb%2Fc";
  org.junit.Assert.assertEquals(source.replace("http://","https://"),Net.securePlaybackRedirect(media,source));
  org.junit.Assert.assertNull(Net.securePlaybackRedirect(media,source.replace("ctyunxs.cn","ctyunxs.cn.invalid")));
  org.junit.Assert.assertNull(Net.securePlaybackRedirect(media,source.replace(".cn/",".cn:8080/")));
  org.junit.Assert.assertNull(Net.securePlaybackRedirect(media,source.replace("http://","https://")));
  media.provider="ALI";org.junit.Assert.assertNull(Net.securePlaybackRedirect(media,source));
 }
}
