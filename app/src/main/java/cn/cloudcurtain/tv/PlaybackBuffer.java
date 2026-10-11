package cn.cloudcurtain.tv;
import androidx.media3.exoplayer.DefaultLoadControl;
/** Bound the selected-track allocator to leave room for the decoder, subtitles and TV UI. */
@androidx.media3.common.util.UnstableApi
final class PlaybackBuffer {
 static int targetBytes(long heapBytes){return (int)Math.max(16L*1024*1024,Math.min(96L*1024*1024,heapBytes/4));}
 static DefaultLoadControl create(){return new DefaultLoadControl.Builder().setBufferDurationsMs(15000,60000,2000,6000).setTargetBufferBytes(targetBytes(Runtime.getRuntime().maxMemory())).setPrioritizeTimeOverSizeThresholds(false).setBackBuffer(0,false).build();}
}
