package cn.cloudcurtain.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import android.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AliFlowTest {
    @Test public void scanConfirmationRequiresServerTokens()throws Exception{
        AtomicInteger calls=new AtomicInteger();String ext=Base64.encodeToString("{pds_login_result:{access_token:'test-access',refresh_token:'test-refresh'}}".getBytes("UTF-8"),Base64.NO_WRAP);
        try(AliQrApi api=new AliQrApi((u,form)->{
            if(calls.getAndIncrement()==0)return new JSONObject("{content:{success:true,data:{t:'123',ck:'qr-ticket',codeContent:'https://www.alipan.com/login/qr/test'}}}");
            assertTrue(u.endsWith("query.do?appName=aliyun_drive&fromSite=52"));assertEquals("123",form.get("t"));assertEquals("qr-ticket",form.get("ck"));return new JSONObject().put("content",new JSONObject().put("success",true).put("data",new JSONObject().put("qrCodeStatus","CONFIRMED").put("bizExt",ext)));
        })){assertThrows(java.io.IOException.class,api::token);LoginQr.decode(api.start()).recycle();assertEquals(AliQrApi.State.CONFIRMED,api.poll());assertEquals("test-access",api.token().getString("access_token"));}
    }
    @Test public void aliListUsesOwnTokenAndOpaquePagination()throws Exception{
        AtomicInteger page=new AtomicInteger();CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{assertEquals("Bearer access",h.get("Authorization"));assertEquals("drive",b.getString("drive_id"));if(page.getAndIncrement()==0){assertEquals("",b.getString("marker"));return new JSONObject("{items:[{file_id:'folder',name:'电影',type:'folder'}],next_marker:'opaque'}");}assertEquals("opaque",b.getString("marker"));return new JSONObject("{items:[],next_marker:''}");},()->new JSONObject("{access_token:'access',default_drive_id:'drive'}"));
        assertTrue(api.list("root",1).more);assertFalse(api.list("root",2).more);assertThrows(java.io.IOException.class,()->api.list("new-folder",2));
    }
    @Test public void resourceAndBackupUseTheirOwnDriveAndKeepPaginationSeparate()throws Exception{
        JSONObject token=new JSONObject("{access_token:'access',default_drive_id:'legacy',resource_drive_id:'resource',backup_drive_id:'backup'}");
        CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{if(u.equals("https://user.aliyundrive.com/v2/user/get"))return token;String drive=b.getString("drive_id");assertTrue(drive.equals("resource")||drive.equals("backup"));assertEquals("",b.getString("marker"));return new JSONObject("{items:[],next_marker:'next-page'}");},()->token);
        api.selectAliSpace("resource");assertTrue(api.list("root",1).more);api.selectAliSpace("backup");assertThrows(java.io.IOException.class,()->api.list("root",2));assertTrue(api.list("root",1).more);
        assertEquals("resource",CloudApi.chooseAliDrive(new JSONObject("{default_drive_id:'legacy'}"),new JSONObject("{resource_drive_id:'resource',backup_drive_id:'backup'}"),"resource"));
        assertEquals("legacy",CloudApi.chooseAliDrive(new JSONObject("{default_drive_id:'legacy'}"),new JSONObject(),"resource"));
    }    @Test public void currentUserServiceWinsStaleTokenDriveAndPlaybackUsesSameSpace()throws Exception{
        JSONObject token=new JSONObject("{access_token:'access',default_drive_id:'legacy',resource_drive_id:'stale-resource'}");AtomicInteger accountCalls=new AtomicInteger();
        CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{
            if(u.endsWith("/v2/user/get")){assertEquals("https://user.aliyundrive.com/v2/user/get",u);accountCalls.incrementAndGet();return new JSONObject("{resource_drive_id:'current-resource',backup_drive_id:'current-backup'}");}
            assertEquals("current-resource",b.getString("drive_id"));if(u.endsWith("/file/list"))return new JSONObject("{items:[{file_id:'video',name:'test.mp4',type:'file',size:123}],next_marker:''}");
            assertTrue(u.endsWith("/file/get_download_url"));assertEquals("video",b.getString("file_id"));return new JSONObject("{url:'https://cdn.example.com/test.mp4'}");
        },()->token);api.selectAliSpace("resource");CloudApi.Entry file=api.list("root",1).entries.get(0);assertEquals("https://cdn.example.com/test.mp4",api.resolve(file).url);assertEquals(1,accountCalls.get());
    }
    @Test public void deviceSessionRoutesAccountLookupToOfficialUserService()throws Exception{
        AliDeviceSession session=new AliDeviceSession((u,b,h)->{assertEquals("https://user.aliyundrive.com/v2/user/get",u);assertEquals("Bearer access",h.get("Authorization"));return new JSONObject("{user_id:'user',resource_drive_id:'resource'}");},new AliDeviceSession.Store(){String value="";public String read(){return value;}public void write(String s){value=s;}});
        assertEquals("resource",session.request("/v2/user/get",new JSONObject(),new JSONObject("{access_token:'access',user_id:'user'}")).getString("resource_drive_id"));
    }    @Test public void emptyAliDownloadFallsBackToAvailableStreamAndExposesOnlineSubtitles()throws Exception{
        CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{
            assertEquals("drive",b.getString("drive_id"));assertEquals("video",b.getString("file_id"));
            if(u.endsWith("get_download_url"))return new JSONObject("{url:'',cdn_url:'',punish_flag:0}");
            assertTrue(u.endsWith("get_video_preview_play_info"));assertTrue(b.getString("category").equals("quick_video")||b.getString("category").equals("live_transcoding"));
            return new JSONObject("{video_preview_play_info:{live_transcoding_task_list:[{template_id:'SD',status:'finished',url:'https://cdn.example.com/sd.m3u8'},{template_id:'HD',status:'finished',url:'https://cdn.example.com/hd.m3u8'},{template_id:'FHD',status:'finished',url:''},{template_id:'FHD',status:'running',url:'https://cdn.example.com/pending.m3u8'}],live_transcoding_subtitle_task_list:[{status:'finished',language:'zh',url:'https://cdn.example.com/sub.vtt'},{status:'running',url:'https://cdn.example.com/pending.vtt'}]}}");
        },()->new JSONObject("{access_token:'access',default_drive_id:'drive'}"));
        MediaSpec m=MediaSpec.parse(api.resolve(new CloudApi.Entry("video","test.mp4",false,1)).json());assertEquals("https://cdn.example.com/hd.m3u8",m.url);assertTrue(m.compatible);assertEquals("ALI:video",m.key);assertEquals(1,m.subtitles.size());assertEquals("text/vtt",m.subtitles.get(0).mime);
    }
    @Test public void aliNeverUsesUnavailablePreviewUrlOrCallsVideoApiForSubtitle()throws Exception{
        AtomicInteger previews=new AtomicInteger();CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{
            if(u.endsWith("get_download_url"))return new JSONObject("{url:''}");previews.incrementAndGet();return new JSONObject("{video_preview_play_info:{live_transcoding_task_list:[{template_id:'FHD',status:'finished',url:'',preview_url:'https://cdn.example.com/trailer.m3u8'},{template_height:2160,status:'finished',url:'https://cdn.example.com/4k.m3u8'}]}}");
        },()->new JSONObject("{access_token:'access',default_drive_id:'drive'}"));
        assertThrows(java.io.IOException.class,()->api.resolve(new CloudApi.Entry("video","test.mp4",false,1)));assertEquals(2,previews.get());
        assertThrows(java.io.IOException.class,()->api.resolve(new CloudApi.Entry("sub","test.srt",false,1)));assertEquals(2,previews.get());
    }

    @Test public void quickPreviewPreservesActualDimensionsAndIgnoresUnavailableHigherQuality()throws Exception{
        AtomicInteger calls=new AtomicInteger();CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{
            if(u.endsWith("get_download_url"))return new JSONObject("{url:''}");
            calls.incrementAndGet();assertEquals("quick_video",b.getString("category"));assertEquals("high_res",b.getString("mode"));assertTrue(b.getBoolean("get_subtitle_info"));
            return new JSONObject("{video_preview_play_info:{meta:{width:3840,height:1608},quick_video_list:[{template_id:'HD',template_width:1280,template_height:720,status:'finished',url:'https://cdn.example.com/720.m3u8'},{template_id:'QHD',template_width:2560,template_height:1440,status:'finished',url:''}],quick_video_subtitle_list:[{language:'zh',status:'finished',url:'https://cdn.example.com/zh.vtt'}]}}");
        },()->new JSONObject("{access_token:'access',default_drive_id:'drive'}"));
        MediaSpec media=api.resolve(new CloudApi.Entry("video","test.mp4",false,1));
        assertEquals(1,calls.get());assertEquals("720p · 在线转码",media.qualityLabel);assertEquals(3840,media.sourceWidth);assertEquals(1608,media.sourceHeight);assertEquals(1,media.subtitles.size());assertEquals("https://cdn.example.com/720.m3u8",media.url);
    }
}
