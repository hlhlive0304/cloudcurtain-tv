package cn.cloudcurtain.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackBufferTest {
 @Test public void smallTvHeapLeavesMemoryForUiAndDecoder(){assertEquals(48*1024*1024,PlaybackBuffer.targetBytes(192L*1024*1024));assertEquals(16*1024*1024,PlaybackBuffer.targetBytes(64L*1024*1024));}
 @Test public void highRamStillHasABoundedMediaAllocator(){assertEquals(96*1024*1024,PlaybackBuffer.targetBytes(1024L*1024*1024));}
}
