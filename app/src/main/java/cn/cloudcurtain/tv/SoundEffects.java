package cn.cloudcurtain.tv;
import android.media.audiofx.*;
/** Session-bound Android effects. Unsupported effects remain off. */
public final class SoundEffects implements AutoCloseable {
 public static final String[] IDS={"auto","normal","voice","bass","surround"};
 public static final String[] LABELS={"自动 · 高音质优先","普通音效","人声增强","超重低音","环绕音效"};
 private Equalizer eq;private BassBoost bass;private Virtualizer surround;private int session=-1;
 private String applied="normal",status="等待音频轨道";
 public String status(){return status;}public String applied(){return applied;}
 public void attach(int id,String mode,int channels){
  if(id<=0)return;if(session!=id){close();session=id;try{eq=new Equalizer(0,id);}catch(RuntimeException ignored){}try{bass=new BassBoost(0,id);}catch(RuntimeException ignored){}try{surround=new Virtualizer(0,id);}catch(RuntimeException ignored){}}
  try{if(eq!=null)eq.setEnabled(false);if(bass!=null)bass.setEnabled(false);if(surround!=null)surround.setEnabled(false);}catch(RuntimeException ignored){}
  applied="normal";if("auto".equals(mode)||"normal".equals(mode)){status="原声 · 优先最高可解码音轨";return;}
  try{
   if("voice".equals(mode)&&eq!=null&&eq.hasControl()){
    short[] range=eq.getBandLevelRange();for(short band=0;band<eq.getNumberOfBands();band++){
     int hz=eq.getCenterFreq(band)/1000,gain=hz>=700&&hz<=4500?250:hz<250?-150:0;
     eq.setBandLevel(band,(short)Math.max(range[0],Math.min(range[1],gain)));
    }eq.setEnabled(true);if(eq.getEnabled())applied=mode;
   }else if("bass".equals(mode)&&bass!=null&&bass.hasControl()&&bass.getStrengthSupported()){
    bass.setStrength((short)800);bass.setEnabled(true);if(bass.getEnabled())applied=mode;
   }else if("surround".equals(mode)&&surround!=null&&surround.hasControl()&&surround.getStrengthSupported()){
    surround.setStrength((short)500);surround.setEnabled(true);if(surround.getEnabled())applied=mode;
   }
  }catch(RuntimeException ignored){}
  status=applied.equals(mode)?LABELS[java.util.Arrays.asList(IDS).indexOf(mode)]+"已启用":"设备不支持此音效，保持原声";
 }
 @Override public void close(){if(eq!=null){try{eq.release();}catch(RuntimeException ignored){}eq=null;}if(bass!=null){try{bass.release();}catch(RuntimeException ignored){}bass=null;}if(surround!=null){try{surround.release();}catch(RuntimeException ignored){}surround=null;}session=-1;}
}