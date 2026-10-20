package cn.cloudcurtain.tv;

import androidx.media3.common.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=28)
public class VideoDecodeTest {
    private Format dv(String codecs){return new Format.Builder().setId("source-video").setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs(codecs).setWidth(3840).setHeight(2020).setFrameRate(23.976f).setInitializationData(Collections.singletonList(new byte[]{0,0,0,1,64,1})).setColorInfo(new ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT2020).setColorTransfer(C.COLOR_TRANSFER_ST2084).setColorRange(C.COLOR_RANGE_LIMITED).build()).build();}
    @Test public void onlyOptedInCompatibleDolbyProfilesUseOriginalHevcLayer(){for(String codecs:new String[]{"dvhe.08.06","dvhe.04.06"}){Format source=dv(codecs);assertTrue(codecs,VideoDecode.hasHevcBase(source));assertSame(source,VideoDecode.baseFormat(source,false));Format base=VideoDecode.baseFormat(source,true);assertEquals(MimeTypes.VIDEO_H265,base.sampleMimeType);assertNull(base.codecs);assertEquals(3840,base.width);assertEquals(2020,base.height);assertEquals(source.frameRate,base.frameRate,0);assertEquals(source.id,base.id);assertEquals(source.colorInfo,base.colorInfo);assertArrayEquals(source.initializationData.get(0),base.initializationData.get(0));assertEquals(MimeTypes.VIDEO_DOLBY_VISION,source.sampleMimeType);}}
    @Test public void incompatibleUnknownAndOrdinaryVideoAreNeverRelabelled(){for(Format source:new Format[]{dv("dvhe.05.06"),dv("dvhe.07.06"),dv("invalid"),dv(null),new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build(),new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()}){assertFalse(VideoDecode.hasHevcBase(source));assertSame(source,VideoDecode.baseFormat(source,true));}}
}
