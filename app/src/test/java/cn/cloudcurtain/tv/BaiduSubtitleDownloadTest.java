package cn.cloudcurtain.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.io.IOException;
import java.util.*;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BaiduSubtitleDownloadTest {
    private CloudApi.Entry entry(){return new CloudApi.Entry("/电影/测试/简中.srt","简中.srt",false,100);}
    private JSONObject metadata()throws Exception{return new JSONObject().put("errno",0).put("info",new JSONArray().put(new JSONObject().put("path",entry().id).put("fs_id",42).put("size",100).put("isdir",0)));}
    private JSONObject session()throws Exception{return new JSONObject("{errno:0,result:{sign1:Plaintext,sign3:Key,timestamp:1791979200,bdstoken:test-session}}");}
    private JSONObject link()throws Exception{return new JSONObject("{errno:0,dlink:[{fs_id:42,dlink:'https://download.baidupcs.com/subtitle?sign=test'}]}");}
    @Test public void downloadsSelectedSmallFileWithCurrentSessionInsteadOfDlnaVideoAddress()throws Exception{
        List<HttpUrl> calls=new ArrayList<>();CloudApi api=new CloudApi(Provider.BAIDU,(u,b,h)->{
            assertNull(b);assertEquals(Provider.BAIDU.origin,h.get("Referer"));HttpUrl url=HttpUrl.get(u);calls.add(url);
            switch(calls.size()){
                case 1:assertEquals("/api/filemetas",url.encodedPath());assertEquals("1",url.queryParameter("web"));assertNull(url.queryParameter("origin"));assertEquals(entry().id,new JSONArray(url.queryParameter("target")).getString(0));return metadata();
                case 2:assertEquals("/api/gettemplatevariable",url.encodedPath());return session();
                default:assertEquals("/api/download",url.encodedPath());assertEquals("[42]",url.queryParameter("fidlist"));assertEquals("test-session",url.queryParameter("bdstoken"));assertEquals(BaiduSubtitleDownload.sign("Key","Plaintext"),url.queryParameter("sign"));assertEquals("1791979200",url.queryParameter("timestamp"));assertNull(url.queryParameter("vip"));return link();
            }
        });
        MediaSpec scope=api.resolveSubtitle(entry());assertEquals(3,calls.size());assertEquals("BAIDU",scope.provider);assertEquals(Net.UA,scope.headers.get("User-Agent"));assertEquals(100,scope.sourceBytes);assertEquals(2,scope.headers.size());assertFalse(scope.url.contains("test-session"));
    }
    @Test public void signingMatchesTheKnownRc4VectorAndRejectsInvalidInput()throws Exception{
        assertEquals("u/MW6NlArwrT",BaiduSubtitleDownload.sign("Key","Plaintext"));
        for(String key:new String[]{"","\n","密钥"})assertThrows(IOException.class,()->BaiduSubtitleDownload.sign(key,"Plaintext"));
    }
    @Test public void metadataForAnotherFileDirectoryOrOversizedSubtitleStopsBeforeSigning()throws Exception{
        for(String field:new String[]{"path","fs_id","size","isdir","errno"}){
            JSONObject meta=metadata(),file=meta.getJSONArray("info").getJSONObject(0);
            file.put(field,field.equals("path")?"/other.srt":field.equals("fs_id")?0:field.equals("size")?4194305:1);
            final int[] calls={0};assertThrows(IOException.class,()->BaiduSubtitleDownload.resolve(entry(),u->{calls[0]++;return meta;}));assertEquals(1,calls[0]);
        }
    }
    @Test public void incompleteSigningSessionDoesNotRequestDownload()throws Exception{
        for(String key:new String[]{"sign1","sign3","timestamp","bdstoken"}){
            JSONObject vars=session();vars.getJSONObject("result").remove(key);final int[] calls={0};
            assertThrows(IOException.class,()->BaiduSubtitleDownload.resolve(entry(),u->++calls[0]==1?metadata():vars));assertEquals(2,calls[0]);
        }
    }
    @Test public void refusesUnknownDownloadHostsWrongIdentityAndEmptyLink()throws Exception{
        List<String> addresses=Arrays.asList("http://download.baidupcs.com/a","https://download.baidupcs.com.evil.test/a","https://user:password@pan.baidu.com/a","https://pan.baidu.com:8443/a","https://pan.baidu.com/a#fragment");
        for(String address:addresses){assertFalse(BaiduSubtitleDownload.trusted(address));JSONObject reply=link();reply.getJSONArray("dlink").getJSONObject(0).put("dlink",address);final int[] calls={0};assertThrows(IOException.class,()->BaiduSubtitleDownload.resolve(entry(),u->++calls[0]==1?metadata():calls[0]==2?session():reply));}
        JSONObject wrong=link();wrong.getJSONArray("dlink").getJSONObject(0).put("fs_id",43);final int[] calls={0};assertThrows(IOException.class,()->BaiduSubtitleDownload.resolve(entry(),u->++calls[0]==1?metadata():calls[0]==2?session():wrong));
    }
    @Test public void originalVideoAndOtherProviderSubtitleRoutesKeepExistingBehavior()throws Exception{
        final String[] called={""};CloudApi baidu=new CloudApi(Provider.BAIDU,(u,b,h)->{called[0]=u;return new JSONObject("{errno:0,info:[{dlink:'https://d.pcs.baidu.com/video'}]}");});
        baidu.resolveOriginal(new CloudApi.Entry("/movie.mkv","movie.mkv",false,5000000000L));assertTrue(called[0].contains("origin=dlna"));assertTrue(called[0].contains("web=5"));
        CloudApi quark=new CloudApi(Provider.QUARK,(u,b,h)->{called[0]=u;return new JSONObject("{code:0,data:[{download_url:'https://download.quark.cn/subtitle'}]}");});
        assertEquals("https://download.quark.cn/subtitle",quark.resolveSubtitle(new CloudApi.Entry("file","简中.srt",false,100)).url);assertTrue(called[0].contains("/file/download?"));
    }
}
