package cn.cloudcurtain.tv;

import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.upstream.Allocation;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class BaiduBufferPolicyTest {
    private MediaSpec spec(String provider){MediaSpec m=new MediaSpec("https://media.test/video","");m.provider=provider;m.sourceBytes=880846424;return m;}
    private LoadControl.Parameters params(long milliseconds,boolean rebuffering){return new LoadControl.Parameters(PlayerId.UNSET,Timeline.EMPTY,new MediaPeriodId(new Object()),0,milliseconds*1000,1,true,rebuffering,C.TIME_UNSET,C.TIME_UNSET);}
    private DefaultLoadControl prepared(MediaSpec media){DefaultLoadControl control=PlaybackBuffer.create(media,64L*1024*1024);control.onPrepared(PlayerId.UNSET);return control;}
    @Test public void refillsBeforeReserveFallsBelowATransientNetworkDelay(){
        MediaSpec compatible=spec("QUARK");compatible.compatible=true;
        DefaultLoadControl baidu=prepared(spec("BAIDU")),other=prepared(compatible);
        assertFalse(baidu.shouldContinueLoading(params(181000,false)));assertTrue(baidu.shouldContinueLoading(params(45000,false)));
        assertFalse(other.shouldContinueLoading(params(61000,false)));assertFalse(other.shouldContinueLoading(params(45000,false)));assertTrue(other.shouldContinueLoading(params(14000,false)));
    }
    @Test public void depletedBufferBuildsAReserveBeforeResumingInsteadOfRepeatingSmallStalls(){
        DefaultLoadControl control=prepared(spec("BAIDU"));assertFalse(control.shouldStartPlayback(params(3000,false)));assertTrue(control.shouldStartPlayback(params(6000,false)));
        assertFalse(control.shouldStartPlayback(params(10000,true)));assertTrue(control.shouldStartPlayback(params(15000,true)));
    }
    @Test public void allocatorStillStopsAtTheTvMemoryBudget(){
        MediaSpec media=spec("BAIDU");DefaultLoadControl control=prepared(media);List<Allocation> allocations=new ArrayList<>();int budget=PlaybackBuffer.targetBytes(64L*1024*1024,media);
        try{while(control.getAllocator().getTotalBytesAllocated()<budget)allocations.add(control.getAllocator().allocate());assertFalse(control.shouldContinueLoading(params(200,false)));assertTrue(control.getAllocator().getTotalBytesAllocated()<budget+65536);}
        finally{for(Allocation allocation:allocations)control.getAllocator().release(allocation);control.onReleased(PlayerId.UNSET);}
    }
    @Test public void subGigabyteOriginalsAlsoReceiveTheBoundedReserve(){assertTrue(PlaybackBuffer.targetBytes(192L*1024*1024,spec("BAIDU"))>PlaybackBuffer.targetBytes(192L*1024*1024));MediaSpec local=spec("BAIDU");local.url="file:/video.mkv";assertFalse(PlaybackBuffer.baidu(local));assertFalse(PlaybackBuffer.baidu(spec("ALI")));}
    @Test public void onlyBaiduVideoReadsUseShorterTimeouts(){assertEquals(10000,PlaybackNetwork.client(spec("BAIDU")).readTimeoutMillis());assertEquals(8000,PlaybackNetwork.client(spec("BAIDU")).connectTimeoutMillis());assertEquals(30000,PlaybackNetwork.client(spec("QUARK")).readTimeoutMillis());}
}
