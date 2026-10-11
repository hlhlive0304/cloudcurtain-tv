package cn.cloudcurtain.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CloudApiTest {
    @Test public void quarkListsFoldersBeforeFilesAndKeepsPagination()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(url,body,h)->{
            assertTrue(url.contains("pdir_fid=0"));assertNull(body);assertEquals("https://pan.quark.cn/",h.get("Referer"));
            return new JSONObject("{\"code\":0,\"status\":200,\"data\":{\"list\":[{\"fid\":\"v\",\"file_name\":\"video.mkv\",\"file\":true,\"size\":100},{\"fid\":\"d\",\"file_name\":\"Movies\",\"file\":false}]},\"metadata\":{\"_total\":101}}");
        });CloudApi.Page p=api.list("0",1);assertTrue(p.more);assertEquals("d",p.entries.get(0).id);assertEquals("video.mkv",p.entries.get(1).name);
    }
    @Test public void tianyiParsesNumericIdsWithoutTruncation()throws Exception{
        CloudApi api=new CloudApi(Provider.TIANYI,(u,b,h)->new JSONObject("{\"res_code\":0,\"fileListAO\":{\"count\":2,\"folderList\":[{\"id\":12345678901234567,\"name\":\"电影\"}],\"fileList\":[{\"id\":\"2\",\"name\":\"中文.srt\",\"size\":42}]}}"));
        CloudApi.Page p=api.list("-11",1);assertEquals("12345678901234567",p.entries.get(0).id);assertFalse(p.more);assertEquals(42,p.entries.get(1).size);
    }
    @Test public void sessionErrorsDoNotLookLikeEmptyFolders()throws Exception{
        CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->new JSONObject("{\"code\":401,\"status\":401}"));
        assertThrows(java.io.IOException.class,()->api.list("0",1));
    }
    @Test public void missingLinkDoesNotLaunchPlayer()throws Exception{
        CloudApi api=new CloudApi(Provider.TIANYI,(u,b,h)->new JSONObject("{\"res_code\":0}"));
        assertThrows(java.io.IOException.class,()->api.resolve(new CloudApi.Entry("1","v.mp4",false,1)));
    }
    @Test public void normalizedLinkKeepsStableFileIdentity()throws Exception{
        CloudApi api=new CloudApi(Provider.TIANYI,(u,b,h)->new JSONObject("{\"downloadUrl\":\"//cdn.example/v.mp4?a=1&amp;b=2\"}"));
        MediaSpec m=api.resolve(new CloudApi.Entry("12","电影.mp4",false,10));assertEquals("https://cdn.example/v.mp4?a=1&b=2",m.url);assertEquals("TIANYI:12",m.key);
    }
}
