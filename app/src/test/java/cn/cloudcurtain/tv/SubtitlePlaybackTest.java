package cn.cloudcurtain.tv;
import androidx.media3.common.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SubtitlePlaybackTest {
 @Test public void chineseLanguageAliasesChooseFullSupportedEmbeddedTrack(){
  Format ja=new Format.Builder().setSampleMimeType("application/x-subrip").setLanguage("jpn").build();
  Format zh=ja.buildUpon().setLanguage("chi").build();
  Format forced=zh.buildUpon().setSelectionFlags(C.SELECTION_FLAG_FORCED).build();
  TrackGroup group=new TrackGroup(ja,forced,zh,zh.buildUpon().setLanguage("zh-TW").build());
  Tracks tracks=new Tracks(com.google.common.collect.ImmutableList.of(new Tracks.Group(group,false,new int[]{C.FORMAT_HANDLED,C.FORMAT_HANDLED,C.FORMAT_HANDLED,C.FORMAT_UNSUPPORTED_TYPE},new boolean[]{true,false,false,false})));
  assertEquals(2,(int)SubtitlePolicy.bestChinese(tracks).trackIndices.get(0));
  for(String language:new String[]{"chi","zho","zh","zh-CN","zh_tw","chs","cht"})assertTrue(SubtitlePolicy.chinese(language,null));
  assertFalse(SubtitlePolicy.chinese("en","Doraemon"));assertNull(SubtitlePolicy.bestChinese(Tracks.EMPTY));
 }
 @Test public void sameFolderFileIsNotTrustedAsExactFileAndMetadataSurvivesSerialization()throws Exception{
  MediaSpec m=new MediaSpec("https://cdn.test/movie.mkv","电影");m.sourceWidth=3840;m.sourceHeight=2160;
  m.subtitles.add(new MediaSpec.Sub("cloud:other","中文·其他电影.srt","application/x-subrip"));
  assertNull(SubtitlePolicy.exactChinese(m.subtitles));
  m.subtitles.add(new MediaSpec.Sub("https://cdn.test/jp.vtt","同文件字幕·日语","text/vtt").exactFile("jpn"));
  MediaSpec.Sub chinese=new MediaSpec.Sub("https://cdn.test/ch.vtt","同文件字幕·中文","text/vtt").exactFile("chi");m.subtitles.add(chinese);
  assertSame(chinese,SubtitlePolicy.exactChinese(m.subtitles));
  MediaSpec copy=MediaSpec.parse(m.json());assertEquals(3840,copy.sourceWidth);assertEquals(2160,copy.sourceHeight);
  assertEquals("chi",SubtitlePolicy.exactChinese(copy.subtitles).language);assertFalse(copy.subtitles.get(0).sameFile);
  assertEquals("4K · 原文件",SubtitlePolicy.originalLabel(copy.sourceWidth,copy.sourceHeight));
 }
 @Test public void providerSubtitleReadDoesNotRequireChangingOriginalVideoTo720p()throws Exception{
  List<String> called=new ArrayList<>();CloudApi api=new CloudApi(Provider.ALI,(url,body,headers)->{
   called.add(url);
   if(url.endsWith("/v2/user/get"))return new JSONObject("{resource_drive_id:'r'}");
   if(url.endsWith("/get_download_url"))return new JSONObject("{url:'https://cdn.test/original.mkv'}");
   assertTrue(body.getBoolean("get_subtitle_info"));assertEquals("file",body.getString("file_id"));
   return new JSONObject("{video_preview_play_info:{meta:{width:3840,height:2160},live_transcoding_task_list:[{status:finished,template_id:HD,url:'https://cdn.test/720.m3u8'}],live_transcoding_subtitle_task_list:[{status:finished,language:chi,url:'https://cdn.test/ch.vtt'},{status:running,language:chi,url:'https://cdn.test/pending.vtt'},{status:finished,language:chi,preview_url:'https://cdn.test/trial.vtt'}]}}");
  },()->new JSONObject("{access_token:'test'}"));api.selectAliSpace("resource");
  CloudApi.Entry entry=new CloudApi.Entry("file","Unreliable.1080p.mkv",false,1);MediaSpec original=api.resolve(entry);
  assertEquals("https://cdn.test/original.mkv",original.url);CloudApi.FilePlaybackInfo info=api.filePlaybackInfo(entry);
  assertEquals(3840,info.width);assertEquals(2160,info.height);assertEquals(1,info.subtitles.size());assertTrue(info.subtitles.get(0).sameFile);
  assertEquals("original",original.quality);assertFalse(original.compatible);
  List<CloudApi.Quality> options=api.qualities(entry);assertEquals("4K · 原文件",options.get(0).label);assertEquals("720p · 在线转码",options.get(1).label);
 }
 @Test public void cropped4kOriginalIsVisibleEvenWhenOnlyLowTranscodeIsAuthorized()throws Exception{
  CloudApi api=new CloudApi(Provider.QUARK,(u,b,h)->new JSONObject("{code:0,data:{meta:{width:3838,height:1608},video_list:[{accessable:false,resolution:4k},{accessable:true,video_info:{url:'https://video.quark.cn/low.m3u8',width:360,height:202}}]}}"));
  CloudApi.Entry entry=new CloudApi.Entry("file","movie.mkv",false,0);List<CloudApi.Quality> options=api.qualities(entry);
  assertEquals(2,options.size());assertEquals("4K · 原文件",options.get(0).label);assertEquals(3838,options.get(0).width);assertEquals("quark:360x202",options.get(1).id);
  assertEquals(3838,api.filePlaybackInfo(entry).width);assertFalse(options.stream().anyMatch(q->q.id.equals("quark:3840x2160")));
 }
 @Test public void originalSubtitleFallbackPrefersEnglishAndStillPrefersChineseWhenPresent(){
  Format fr=new Format.Builder().setSampleMimeType("application/x-subrip").setLanguage("fr").build();Format en=fr.buildUpon().setLanguage("en").build();Format zh=fr.buildUpon().setLanguage("chi").build();
  TrackGroup group=new TrackGroup(fr,en,zh);Tracks languages=new Tracks(com.google.common.collect.ImmutableList.of(new Tracks.Group(group,false,new int[]{C.FORMAT_HANDLED,C.FORMAT_HANDLED,C.FORMAT_UNSUPPORTED_TYPE},new boolean[]{false,false,false})));
  assertEquals(1,(int)SubtitlePolicy.bestAvailable(languages).trackIndices.get(0));
  Tracks withChinese=new Tracks(com.google.common.collect.ImmutableList.of(new Tracks.Group(group,false,new int[]{C.FORMAT_HANDLED,C.FORMAT_HANDLED,C.FORMAT_HANDLED},new boolean[]{false,false,false})));assertEquals(2,(int)SubtitlePolicy.bestAvailable(withChinese).trackIndices.get(0));
 }
 @Test public void audioMenuContainsRequestedEffectsWithoutClaimingSurroundOnUnsupportedDevice(){
  assertArrayEquals(new String[]{"自动 · 高音质优先","普通音效","人声增强","超重低音","环绕音效"},SoundEffects.LABELS);
  SoundEffects effects=new SoundEffects();for(String id:SoundEffects.IDS){effects.attach(0,id,2);assertEquals("normal",effects.applied());}effects.close();
 }
}