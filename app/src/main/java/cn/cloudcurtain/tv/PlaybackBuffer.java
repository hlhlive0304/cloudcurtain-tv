package cn.cloudcurtain.tv;
import androidx.media3.exoplayer.DefaultLoadControl;
/** Bound the selected-track allocator to leave room for the decoder, subtitles and TV UI. */
@androidx.media3.common.util.UnstableApi
final class PlaybackBuffer {
 static boolean largeOriginal(MediaSpec spec){return spec!=null&&"BAIDU".equals(spec.provider)&&!spec.compatible&&spec.sourceBytes>=1024L*1024*1024;}
 static int targetBytes(long heapBytes){return (int)Math.max(16L*1024*1024,Math.min(96L*1024*1024,heapBytes/4));}
 static int targetBytes(long heapBytes,MediaSpec spec){return largeOriginal(spec)?(int)Math.max(16L*1024*1024,Math.min(128L*1024*1024,heapBytes/3)):targetBytes(heapBytes);}
 static DefaultLoadControl create(){return create(null);}
 static DefaultLoadControl create(MediaSpec spec){boolean large=largeOriginal(spec);return new DefaultLoadControl.Builder().setBufferDurationsMs(large?30000:15000,large?120000:60000,large?6000:2000,large?12000:6000).setTargetBufferBytes(targetBytes(Runtime.getRuntime().maxMemory(),spec)).setPrioritizeTimeOverSizeThresholds(false).setBackBuffer(0,false).build();}
 static long averageBitsPerSecond(MediaSpec spec,long durationMs){if(spec==null||spec.compatible||spec.sourceBytes<=0||durationMs<=0||durationMs==androidx.media3.common.C.TIME_UNSET)return 0;return (long)(spec.sourceBytes*8000.0/durationMs);}
}
