package cn.cloudcurtain.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlRulesTest {
    @Test public void signedCdnQueryMatchesTransportWithoutChangingSignature(){
        String raw="https://cn-beijing-video-preview.aliyundrive.net/movie.ts?x-oss-process=video/convert,{tsid}&sign=A|B%2BC%2F";
        assertTrue(UrlRules.isWeb(raw));assertEquals("cn-beijing-video-preview.aliyundrive.net",UrlRules.host(raw));assertTrue(UrlRules.sameOrigin("https://cn-beijing-video-preview.aliyundrive.net/master.m3u8",raw));assertEquals("/movie.ts",UrlRules.path(raw));
        okhttp3.Request request=new okhttp3.Request.Builder().url(raw).build();assertEquals("x-oss-process=video/convert,{tsid}&sign=A|B%2BC%2F",request.url().encodedQuery());
    }
    @Test public void relaxedQueryDoesNotRelaxAuthority(){
        for(String unsafe:new String[]{"https://user:secret@pan.quark.cn/a?sig={x}","https://@pan.quark.cn/a?sig={x}","https://pan.quark.cn\\@evil.test/a","https://pan.quark.cn\n.evil.test/a","https://bad_host.quark.cn/a?sig={x}","javascript://pan.quark.cn/{x}"})assertFalse(unsafe,UrlRules.isWeb(unsafe));
        assertFalse(Provider.ALI.allows("https://alipan.com.evil.test/a?sig={x}"));
    }
    @Test public void credentialScopeIncludesSchemeAndPort(){assertTrue(UrlRules.sameOrigin("https://cdn.test/a","https://cdn.test:443/b"));assertFalse(UrlRules.sameOrigin("https://cdn.test/a","http://cdn.test/a"));assertFalse(UrlRules.sameOrigin("http://127.0.0.1:123/a","http://127.0.0.1:456/a"));assertFalse(UrlRules.sameOrigin("https://cdn.test/a","https://other.test/a"));}
    @Test public void credentialsAndJavascriptAreNotPlaybackUrls(){assertFalse(UrlRules.isWeb("javascript:alert(1)"));assertFalse(UrlRules.isWeb("https://user:secret@example.com/a"));assertFalse(UrlRules.isWeb("file:///etc/a"));assertTrue(UrlRules.isWeb("http://192.168.3.24:8000/test.mp4"));}
    @Test public void trustUsesDomainBoundaries(){assertTrue(Provider.BAIDU.allows("https://passport.baidu.com/login"));assertFalse(Provider.BAIDU.allows("https://baidu.com.attacker.test/login"));assertFalse(Provider.BAIDU.allows("http://pan.baidu.com/login"));assertFalse(Provider.BAIDU.allows("https://evilbaidu.com/"));}
    @Test public void ignoresSegmentsAndSharingPages(){assertFalse(UrlRules.mediaUrl("https://example.org/video/episode/2"));assertFalse(UrlRules.mediaUrl("https://cdn.test/0001.ts"));assertFalse(UrlRules.mediaUrl("https://cdn.test/init.m4s"));assertFalse(UrlRules.mediaUrl("blob:https://pan.quark.cn/id"));}
    @Test public void signedStreamAddressesWork(){assertTrue(UrlRules.mediaUrl("https://cdn.test/a.M3U8?token=abc"));assertTrue(UrlRules.mediaUrl("https://pan.baidu.com/api/streaming?type=M3U8_AUTO_480"));assertEquals("application/x-mpegURL",UrlRules.mediaMime("https://cdn.test/a.m3u8?sign=x"));}
    @Test public void subtitleMimeIgnoresQuery(){assertEquals("text/x-ssa",UrlRules.subtitleMime("https://cdn.test/a.ass?filename=srt"));assertEquals("text/vtt",UrlRules.subtitleMime("CAPTIONS.VTT"));assertEquals("application/x-subrip",UrlRules.subtitleMime("https://cdn.test/download?id=1"));}
}
