package cn.cloudcurtain.tv;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import androidx.media3.common.text.Cue;import androidx.media3.extractor.text.CuesWithTiming;import org.json.*;import java.util.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CaptionTranslationTest {
 @Test public void realCueTimeAxisAndOverlappingTracksRemainSeparate(){
  CaptionSamples samples=new CaptionSamples();samples.add("main:en",new CuesWithTiming(Collections.singletonList(new Cue.Builder().setText("Hello").build()),1000000,2000000));samples.add("main:fr",new CuesWithTiming(Collections.singletonList(new Cue.Builder().setText("Bonjour").build()),1000000,2000000));
  assertTrue(samples.current("main:en",999).isEmpty());assertEquals("Hello",samples.current("main:en",1000).get(0).text);assertEquals(3000,samples.current("main:en",2999).get(0).end);assertTrue(samples.current("main:en",3000).isEmpty());assertEquals("Bonjour",samples.current("main:fr",1500).get(0).text);
 }
 @Test public void translationRequestContainsWordsAndIdsButNoInventedTimeline()throws Exception{
  JSONObject body=Doubao.translationRequest(Doubao.DEFAULT_MODEL,Arrays.asList("Please come home.","We are waiting."));String data=body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");JSONObject payload=new JSONObject(data);assertEquals(2,payload.getJSONArray("subtitles").length());assertFalse(data.contains("time"));assertFalse(data.contains("url"));assertFalse(body.getBoolean("store"));
 }
 @Test public void incompleteOrDuplicateTranslationsAreRejected()throws Exception{
  assertEquals(Arrays.asList("你好","再见"),Doubao.parseTranslations("{translations:[{id:1,zh:'再见'},{id:0,zh:'你好'}]}",2));
  for(String invalid:new String[]{"{translations:[{id:0,zh:'你好'}]}","{translations:[{id:0,zh:'你好'},{id:0,zh:'再见'}]}","{translations:[{id:0,zh:'你好'},{id:2,zh:'再见'}]}","{translations:[{id:0,zh:''},{id:1,zh:'再见'}]}"})assertThrows(java.io.IOException.class,()->Doubao.parseTranslations(invalid,2));
 }

 @Test public void replacementCuesWithUnknownDurationUseNextRealCueAsTheirEnd(){
  CaptionSamples samples=new CaptionSamples();
  samples.add("en",new CuesWithTiming(Collections.singletonList(new Cue.Builder().setText("Hello").build()),1000000,androidx.media3.common.C.TIME_UNSET),true);
  assertEquals("Hello",samples.current("en",1200).get(0).text);
  samples.add("en",new CuesWithTiming(Collections.emptyList(),2500000,androidx.media3.common.C.TIME_UNSET),true);
  assertEquals(2500,samples.current("en",2499).get(0).end);assertTrue(samples.current("en",2500).isEmpty());
  samples.add("en",new CuesWithTiming(Collections.singletonList(new Cue.Builder().setText("Later").build()),3000000,androidx.media3.common.C.TIME_UNSET),true);
  assertEquals("Later",samples.current("en",3000).get(0).text);assertTrue(samples.current("en",2600).isEmpty());
 }

 @Test public void imageCaptionsRequireExplicitTrackOptInAndKeepOnlyTheCaptionCrop()throws Exception{
  CaptionSamples samples=new CaptionSamples();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(100,20,android.graphics.Bitmap.Config.ARGB_8888);
  Cue cue=new Cue.Builder().setBitmap(image).build();CuesWithTiming packet=new CuesWithTiming(Collections.singletonList(cue),1000000,androidx.media3.common.C.TIME_UNSET);
  samples.add("pgs",packet,true);assertTrue(samples.ahead("pgs",1000).isEmpty());
  samples.enableImages("pgs",1000);samples.add("pgs",packet,true);CaptionSamples.Line line=samples.current("pgs",1000).get(0);
  assertTrue(line.text.isEmpty());assertTrue(line.image.startsWith("data:image/png;base64,"));assertEquals(64,line.key.length());
  JSONObject request=Doubao.captionTranslationRequest(Doubao.DEFAULT_MODEL,Collections.singletonList(line));assertFalse(request.getBoolean("store"));assertFalse(request.toString().contains("time"));
  assertEquals("input_image",request.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type"));
  samples.disableImages();assertTrue(samples.ahead("pgs",1000).isEmpty());image.recycle();
 }
}
