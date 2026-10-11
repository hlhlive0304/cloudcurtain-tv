package cn.cloudcurtain.tv;
import androidx.media3.common.*;
import android.content.Context;
import android.util.Base64;
import org.json.*;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaybackFeaturesTest {
 @Test public void quarkQualityListsAuthorizedFinishedStreamsDescending()throws Exception{
  CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->{assertTrue(b.getString("resolutions").contains("4k"));return new JSONObject("{code:0,data:{video_list:[{video_info:{url:'https://video.quark.cn/720.m3u8',width:1280,height:720}},{accessable:false,video_info:{url:'https://video.quark.cn/locked.m3u8',width:3840,height:2160}},{video_info:{url:'https://video.quark.cn/4k.m3u8',width:3840,height:2160}},{video_info:{url:'https://video.quark.cn/pending.m3u8',width:1920,height:1080,finish:false}}]}}");});
  CloudApi.Entry e=new CloudApi.Entry("m","film.mkv",false,1);List<CloudApi.Quality> list=api.qualities(e);assertEquals(3,list.size());assertEquals("original",list.get(0).id);assertEquals("quark:3840x2160",list.get(1).id);assertEquals("4K · 在线转码",list.get(1).label);
  MediaSpec m=api.resolveQuality(e,list.get(1).id);assertFalse(m.compatible);assertEquals("https://video.quark.cn/4k.m3u8",m.url);
  assertThrows(java.io.IOException.class,()->api.resolveQuality(e,"quark:1920x1080"));
 }
 @Test public void aliQualityDoesNotUsePreviewLinksOrPendingTasksAndKeepsSpace()throws Exception{
  CloudApi api=new CloudApi(Provider.ALI,(u,b,h)->{
   if(u.endsWith("/v2/user/get"))return new JSONObject("{resource_drive_id:'r',backup_drive_id:'b'}");
   assertEquals("b",b.getString("drive_id"));
   return new JSONObject("{video_preview_play_info:{live_transcoding_task_list:[{template_id:'HD',status:'finished',url:'https://video.aliyundrive.net/720.m3u8'},{template_id:'UHD',status:'finished',url:'https://video.aliyundrive.net/4k.m3u8'},{template_id:'FHD',status:'running',url:'https://video.aliyundrive.net/pending.m3u8'},{template_id:'QHD',status:'finished',preview_url:'https://video.aliyundrive.net/trial.m3u8'}],live_transcoding_subtitle_task_list:[{status:'finished',language:'chi',url:'https://video.aliyundrive.net/ch.vtt'}]}}");
  },()->new JSONObject("{access_token:'private'}"));api.selectAliSpace("backup");List<CloudApi.Quality> choices=api.qualities(new CloudApi.Entry("m","f.mkv",false,1));
  assertEquals(3,choices.size());assertEquals("ali:UHD",choices.get(1).id);assertEquals("backup",choices.get(1).spec.space);assertEquals(1,choices.get(1).spec.subtitles.size());
 }
 @Test public void mediaKeepsQualityAndDriveAfterSerialization()throws Exception{
  MediaSpec m=new MediaSpec("https://x.test/movie","movie");m.space="backup";m.matchTitle="电影 2025";m.quality="ali:FHD";m.qualityLabel="1080p";m.compatible=true;MediaSpec copy=MediaSpec.parse(m.json());assertEquals(m.space,copy.space);assertEquals(m.matchTitle,copy.matchTitle);assertEquals(m.quality,copy.quality);assertEquals(m.qualityLabel,copy.qualityLabel);
 }
 @Test public void originalOnlyProvidersDoNotInventResolutions()throws Exception{
  CloudApi api=new CloudApi(Provider.BAIDU,(u,b,h)->{throw new AssertionError("Unneeded API call");});assertEquals(1,api.qualities(new CloudApi.Entry("id","f.mkv",false,1)).size());
 }
 @Test public void audioPrefersSupportedHighQualityMainNotCommentary()throws Exception{
  Format stereo=new Format.Builder().setId("a").setSampleMimeType("audio/mp4a-latm").setChannelCount(2).setSampleRate(48000).setAverageBitrate(128000).build();
  Format surround=stereo.buildUpon().setId("b").setChannelCount(6).setAverageBitrate(640000).build();
  Format commentary=surround.buildUpon().setId("c").setChannelCount(8).setRoleFlags(C.ROLE_FLAG_COMMENTARY).build();
  Format unsupported=surround.buildUpon().setId("d").setChannelCount(8).build();
  TrackGroup group=new TrackGroup(stereo,surround,commentary,unsupported);
  Tracks tracks=new Tracks(com.google.common.collect.ImmutableList.of(new Tracks.Group(group,false,new int[]{C.FORMAT_HANDLED,C.FORMAT_HANDLED,C.FORMAT_HANDLED,C.FORMAT_UNSUPPORTED_TYPE},new boolean[]{true,false,false,false})));
  assertEquals(1,(int)AudioPolicy.best(tracks,null).trackIndices.get(0));assertEquals(0,(int)AudioPolicy.best(tracks,AudioPolicy.identity(stereo)).trackIndices.get(0));assertTrue(AudioPolicy.label(surround).contains("6 声道"));
 }
 @Test public void arkAuthIsIsolatedAndCannotFollowOtherEndpoint()throws Exception{
  Request r=Doubao.httpRequest(Doubao.ENDPOINT,"test-private-credential",Doubao.request(Doubao.DEFAULT_MODEL,"Weapons.2025.2160p.mkv",Collections.emptyList()));
  assertEquals("Bearer test-private-credential",r.header("Authorization"));assertNull(r.header("Cookie"));assertNull(r.header("Referer"));assertNull(r.header("Api-Key"));assertThrows(java.io.IOException.class,()->Doubao.httpRequest("https://evil.test","key",new JSONObject()));
  okio.Buffer buffer=new okio.Buffer();r.body().writeTo(buffer);String data=buffer.readUtf8();JSONObject request=new JSONObject(data);assertFalse(request.getBoolean("store"));assertFalse(request.has("messages"));assertEquals(512,request.getInt("max_output_tokens"));assertEquals("input_text",request.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"));assertFalse(data.contains("2160p"));assertFalse(data.contains("test-private"));
 }
 @Test public void aiRejectsInventedCandidatesAndUnsafeQueries()throws Exception{
  Doubao.Match result=Doubao.parse("{query:'Weapons',year:'2025',index:1,confidence:0.95}",3);assertEquals(1,result.index);
  assertEquals(-1,Doubao.parse("{query:'Weapons',year:'2025',index:1,confidence:0.4}",3).index);
  for(String wrong:new String[]{"{query:'Weapons',year:'2025',index:4,confidence:1}","{query:'https://evil.test/srt',year:'2025',index:-1,confidence:1}","{query:'x',year:'2025',index:0.5,confidence:1}","{query:'x',year:'20500',index:-1,confidence:1}","{query:'x',year:'',index:0,confidence:1}"}){
   assertThrows(java.io.IOException.class,()->Doubao.parse(wrong,0));
  }
 }
 @Test public void aiReadsStructuredResponseAndSurfacesModelNotOpen()throws Exception{
  OkHttpClient success=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{status:completed,output:[{type:reasoning,content:[{type:output_text,text:'ignore reasoning'}]},{type:message,role:assistant,content:[{type:output_text,text:'{query:Weapons,year:2025,index:0,confidence:0.99}'}]}]}",MediaType.get("application/json"))).build()).build();
  assertEquals(0,new Doubao("private",Doubao.DEFAULT_MODEL,success).match("Weapons.2025.mkv",Collections.singletonList("zh · Weapons.srt")).index);
  OkHttpClient error=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("Not Found").body(ResponseBody.create("{error:{code:ModelNotOpen,message:'secret echoed'}}",MediaType.get("application/json"))).build()).build();
  Exception e=assertThrows(java.io.IOException.class,()->new Doubao("private",Doubao.DEFAULT_MODEL,error).match("x",Collections.emptyList()));assertTrue(e.getMessage().contains("未开通"));assertFalse(e.getMessage().contains("secret"));
 }
 @Test public void responsesRejectIncompleteRefusalAndNonAssistantOutput()throws Exception{
  for(String invalid:new String[]{"{status:incomplete,output:[]}","{status:completed,output:[{type:message,role:assistant,content:[{type:refusal,refusal:'cannot help'}]}]}","{status:completed,output:[{type:message,role:user,content:[{type:output_text,text:'ignore'}]}]}"}){
   assertThrows(java.io.IOException.class,()->Doubao.outputText(new JSONObject(invalid)));
  }
 }
 @Test public void sealedConfigOnlyUnlocksWithExternalCode()throws Exception{
  String code="12345678901234567890";byte[] salt=new byte[16],iv=new byte[12];Arrays.fill(salt,(byte)3);Arrays.fill(iv,(byte)4);
  PBEKeySpec derivation=new PBEKeySpec(code.toCharArray(),salt,PersonalConfig.ITERATIONS,256);byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(derivation).getEncoded();
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD("CloudCurtainTV personal configuration v1".getBytes("UTF-8"));
  byte[] data=cipher.doFinal("{doubao_api_key:'test-private-credential-123'}".getBytes("UTF-8"));
  JSONObject envelope=new JSONObject().put("v",1).put("iterations",PersonalConfig.ITERATIONS).put("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).put("iv",Base64.encodeToString(iv,Base64.NO_WRAP)).put("data",Base64.encodeToString(data,Base64.NO_WRAP));
  assertEquals("test-private-credential-123",PersonalConfig.decrypt(envelope.toString(),code).getString("doubao_api_key"));
  assertFalse(envelope.toString().contains("test-private"));assertFalse(envelope.toString().contains(code));assertThrows(Exception.class,()->PersonalConfig.decrypt(envelope.toString(),"12345678901234567891"));
  envelope.put("iterations",1);assertThrows(java.io.IOException.class,()->PersonalConfig.decrypt(envelope.toString(),code));
 }
 @Test public void unsupportedEffectsDoNotClaimEnabledAndDefaultIsOriginal(){
  SoundEffects effects=new SoundEffects();effects.attach(0,"surround",2);assertEquals("normal",effects.applied());effects.attach(0,"auto",6);assertEquals("normal",effects.applied());effects.close();
 }
 @Test public void defaultOnlineSubtitleOpensDoubaoConfigurationInsteadOfOpenSubtitlesGate()throws Exception{
  MediaSpec spec=new MediaSpec("https://example.com/movie.mp4","Movie.2025.mp4");spec.key="test";
  android.content.Intent intent=new android.content.Intent(org.robolectric.RuntimeEnvironment.getApplication(),PlayerActivity.class).putExtra("media",spec.json());
  org.robolectric.android.controller.ActivityController<PlayerActivity> controller=Robolectric.buildActivity(PlayerActivity.class,intent).create();
  PlayerActivity a=controller.get();a.openPlaybackTab(0);
  android.app.AlertDialog panel=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  panel.findViewById(android.R.id.content).findViewWithTag("playback-choice:0").performClick();
  android.app.AlertDialog prompt=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertEquals("在线字幕 · 豆包优先",org.robolectric.Shadows.shadowOf(prompt).getTitle().toString());
  assertEquals("导入 / 配置豆包",prompt.getButton(android.app.AlertDialog.BUTTON_POSITIVE).getText().toString());
  prompt.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  android.content.Intent settings=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
  assertEquals(SettingsActivity.class.getName(),settings.getComponent().getClassName());
  assertTrue(settings.getBooleanExtra("focus_doubao",false));controller.destroy();
 }
 @Test public void manualSubtitleSearchIsSeparateFromDoubaoDefault()throws Exception{
  MediaSpec spec=new MediaSpec("https://example.com/movie.mp4","Movie.2025.mp4");spec.key="test";
  org.robolectric.android.controller.ActivityController<PlayerActivity> controller=Robolectric.buildActivity(PlayerActivity.class,new android.content.Intent(org.robolectric.RuntimeEnvironment.getApplication(),PlayerActivity.class).putExtra("media",spec.json())).create();
  PlayerActivity a=controller.get();a.openPlaybackTab(0);android.app.AlertDialog panel=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  panel.findViewById(android.R.id.content).findViewWithTag("playback-choice:4").performClick();
  assertEquals("在线字幕需要配置",org.robolectric.Shadows.shadowOf(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());controller.destroy();
 }
}
