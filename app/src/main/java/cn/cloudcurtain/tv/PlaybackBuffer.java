package cn.cloudcurtain.tv;
import androidx.media3.exoplayer.DefaultLoadControl;
/** Bound the selected-track allocator to leave room for the decoder, subtitles and TV UI. */
@androidx.media3.common.util.UnstableApi
final class PlaybackBuffer {
 static boolean baidu(MediaSpec spec){return spec!=null&&"BAIDU".equals(spec.provider)&&UrlRules.isWeb(spec.url);}
 static boolean largeOriginal(MediaSpec spec){return spec!=null&&UrlRules.isWeb(spec.url)&&("BAIDU".equals(spec.provider)||"TIANYI".equals(spec.provider)||"QUARK".equals(spec.provider))&&!spec.compatible&&spec.sourceBytes>=256L*1024*1024;}
 static int targetBytes(long heapBytes){return (int)Math.max(16L*1024*1024,Math.min(96L*1024*1024,heapBytes/4));}
 static int targetBytes(long heapBytes,MediaSpec spec){return largeOriginal(spec)?(int)Math.max(16L*1024*1024,Math.min(128L*1024*1024,heapBytes/3)):targetBytes(heapBytes);}
 static DefaultLoadControl create(){return create(null);}
 static DefaultLoadControl create(MediaSpec spec){return create(spec,Runtime.getRuntime().maxMemory());}
 static DefaultLoadControl create(MediaSpec spec,long heapBytes){boolean reserve=baidu(spec)||largeOriginal(spec);return new DefaultLoadControl.Builder().setBufferDurationsMs(reserve?60000:15000,reserve?180000:60000,reserve?6000:2000,reserve?15000:6000).setTargetBufferBytes(targetBytes(heapBytes,spec)).setPrioritizeTimeOverSizeThresholds(false).setBackBuffer(0,false).build();}
 static long averageBitsPerSecond(MediaSpec spec,long durationMs){if(spec==null||spec.compatible||spec.sourceBytes<=0||durationMs<=0||durationMs==androidx.media3.common.C.TIME_UNSET)return 0;return (long)(spec.sourceBytes*8000.0/durationMs);}
}
