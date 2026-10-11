package cn.cloudcurtain.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackBufferTest {
 @Test public void smallTvHeapLeavesMemoryForUiAndDecoder(){assertEquals(48*1024*1024,PlaybackBuffer.targetBytes(192L*1024*1024));assertEquals(16*1024*1024,PlaybackBuffer.targetBytes(64L*1024*1024));}
 @Test public void highRamStillHasABoundedMediaAllocator(){assertEquals(96*1024*1024,PlaybackBuffer.targetBytes(1024L*1024*1024));}
 @Test public void largeBaiduFilesHaveMoreReserveWithoutUsingMostOfTvHeap(){MediaSpec m=new MediaSpec("https://media.test/movie","");m.provider="BAIDU";m.sourceBytes=54L*1024*1024*1024;for(long heap:new long[]{64L*1024*1024,192L*1024*1024,1024L*1024*1024}){int cap=PlaybackBuffer.targetBytes(heap,m);assertTrue(cap>=PlaybackBuffer.targetBytes(heap));assertTrue(cap<=heap/3);assertTrue(cap<=128*1024*1024);}m.compatible=true;assertEquals(PlaybackBuffer.targetBytes(192L*1024*1024),PlaybackBuffer.targetBytes(192L*1024*1024,m));}
 @Test public void averageRateUsesOriginalBytesAndDurationRatherThanResolution(){MediaSpec m=new MediaSpec("","");m.sourceBytes=100_000_000;assertEquals(8_000_000,PlaybackBuffer.averageBitsPerSecond(m,100_000));assertEquals(0,PlaybackBuffer.averageBitsPerSecond(m,0));m.compatible=true;assertEquals(0,PlaybackBuffer.averageBitsPerSecond(m,100_000));}
}
