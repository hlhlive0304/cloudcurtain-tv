package cn.cloudcurtain.tv;

import org.junit.Test;
import okhttp3.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=28)
public class TianyiPlaybackTest {
 private MediaSpec media(){MediaSpec m=new MediaSpec("https://cloud.189.cn/api/portal/download?fileId=fixture","");m.provider="TIANYI";m.key="TIANYI:fixture";m.quality="original";m.headers.put("Referer","https://cloud.189.cn/web/main");return m;}
 private OkHttpClient client(String issued,int status,String range){return new OkHttpClient.Builder().addInterceptor(c->{assertEquals("GET",c.request().method());assertEquals("bytes=0-0",c.request().header("Range"));return new Response.Builder().request(c.request().newBuilder().url(issued).build()).protocol(Protocol.HTTP_1_1).code(status).message("fixture").header("Content-Range",range).body(ResponseBody.create(new byte[]{1},MediaType.get("video/x-matroska"))).build();}).build();}
 @Test public void issuedSignedCdnIsRetainedWithoutChangingQualityOrQuery()throws Exception{MediaSpec m=media();String issued="https://media-qhxn-fj-home.qh6oss.ctyunxs.cn/video?Signature=a%2Bb&Expires=9999999";assertSame(m,TianyiPlayback.resolve(m,client(issued,206,"bytes 0-0/76315188624")));assertEquals(issued,m.url);assertEquals("original",m.quality);assertEquals("TIANYI:fixture",m.key);assertFalse(m.headers.containsKey("Cookie"));assertFalse(m.headers.containsKey("Authorization"));}
 @Test public void unverifiedOrPlainHttpTargetsCannotReplacePortalAddress()throws Exception{for(String issued:new String[]{"https://ctyunxs.cn.evil.test/video","https://outside.test/video","http://media-qhxn-fj-home.qh6oss.ctyunxs.cn/video"}){MediaSpec m=media();String start=m.url;TianyiPlayback.resolve(m,client(issued,206,"bytes 0-0/999"));assertEquals(start,m.url);}}
 @Test public void ignoredMalformedOrEmptyRangesCannotBeUsedAsVideo()throws Exception{for(String range:new String[]{"","bytes 1-1/999","bytes 0-0/*"}){MediaSpec m=media();String start=m.url;TianyiPlayback.resolve(m,client("https://media-qhxn-fj-home.qh6oss.ctyunxs.cn/video",206,range));assertEquals(start,m.url);}MediaSpec m=media();String start=m.url;TianyiPlayback.resolve(m,client("https://media-qhxn-fj-home.qh6oss.ctyunxs.cn/video",200,"bytes 0-0/999"));assertEquals(start,m.url);}
 @Test public void permissionErrorIsPropagatedWithoutReturningBrokenAddress()throws Exception{MediaSpec m=media();String start=m.url;try{TianyiPlayback.resolve(m,client("https://cloud.189.cn/error",403,""));fail();}catch(Net.ServiceException e){assertEquals(403,e.httpStatus);}assertEquals(start,m.url);}
}
