package cn.cloudcurtain.tv;
import android.os.*;import androidx.media3.exoplayer.ExoPlayer;import androidx.media3.common.Player;import androidx.media3.common.text.Cue;import androidx.media3.ui.SubtitleView;import java.util.*;import java.util.concurrent.*;
/** Explicit opt-in: translated words reuse real source intervals; pending lines remain in the original language. */
@androidx.media3.common.util.UnstableApi
final class LiveTranslation implements AutoCloseable{
 private final ExoPlayer player;private final CaptionSamples samples;private final String track;private final SubtitleView view;private final Doubao doubao;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Map<String,String> cache=new LinkedHashMap<>();interface Failure {void accept(String message);}private final Failure failure;
 private boolean busy,closed;private int revision;volatile int translatedLines,calls;volatile String failureCode="",failureType="";
 LiveTranslation(ExoPlayer player,CaptionSamples samples,String track,SubtitleView view,Doubao doubao,Failure failure){this.player=player;this.samples=samples;this.track=track;this.view=view;this.doubao=doubao;this.failure=failure;player.addListener(events);main.post(tick);}
 private final Player.Listener events=new Player.Listener(){@Override public void onPositionDiscontinuity(Player.PositionInfo old,Player.PositionInfo now,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK||reason==Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT){revision++;doubao.cancel();busy=false;view.setCues(Collections.emptyList());}}};
 private final Runnable tick=new Runnable(){public void run(){
  if(closed)return;long position=player.getCurrentPosition();samples.imagePosition(position);List<CaptionSamples.Line> current=samples.current(track,position);List<Cue> cues=new ArrayList<>();
  for(CaptionSamples.Line line:current){String zh=cache.get(line.key);if(line.image.isEmpty())cues.add(new Cue.Builder().setText(zh==null?line.text:zh+"\n"+line.text).build());else if(zh!=null)cues.add(new Cue.Builder().setText(zh).setLine(.74f,Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END).build());}view.setCues(cues);
  if(!busy&&failureCode.isEmpty()){
   List<CaptionSamples.Line> batch=new ArrayList<>();int chars=0;List<CaptionSamples.Line> ahead=player.getPlayWhenReady()?samples.ahead(track,position):current;
   for(CaptionSamples.Line line:ahead)if(!cache.containsKey(line.key)&&!contains(batch,line.key)){if(batch.size()>=4||chars+line.text.length()>6000)break;batch.add(line);chars+=line.text.length();}
   if(!batch.isEmpty()){busy=true;int ticket=revision;calls++;worker.execute(()->{
    try{List<String> result=doubao.translateCaptions(batch);main.post(()->{if(closed||ticket!=revision)return;busy=false;for(int i=0;i<batch.size();i++)cache.put(batch.get(i).key,result.get(i));translatedLines+=batch.size();while(cache.size()>400)cache.remove(cache.keySet().iterator().next());});}
    catch(Exception e){main.post(()->{if(closed||ticket!=revision)return;busy=false;failureType=e.getClass().getSimpleName();failureCode=e instanceof java.io.InterruptedIOException?"豆包字幕请求超时，请稍后重试。":e instanceof org.json.JSONException?"豆包字幕翻译返回格式不正确，请重试。":LoginErrors.friendly(e);failure.accept(failureCode);});}
   });}
  }
  main.postDelayed(this,250);
 }};
 private boolean contains(List<CaptionSamples.Line> lines,String key){for(CaptionSamples.Line line:lines)if(line.key.equals(key))return true;return false;}
 @Override public void close(){if(closed)return;closed=true;revision++;doubao.cancel();worker.shutdownNow();main.removeCallbacksAndMessages(null);player.removeListener(events);view.setCues(Collections.emptyList());cache.clear();samples.disableImages();}
}
