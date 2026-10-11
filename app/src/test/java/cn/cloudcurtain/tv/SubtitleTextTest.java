package cn.cloudcurtain.tv;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.*;
public class SubtitleTextTest {
    @Test public void detectsChineseEncoding()throws Exception{String text="1\n00:00:01,000 --> 00:00:03,000\n你好，世界。";assertEquals(text,SubtitleText.decode(text.getBytes(StandardCharsets.UTF_8)));assertEquals(text,SubtitleText.decode(text.getBytes(Charset.forName("GB18030"))));}
    @Test public void stripsUtf8Bom()throws Exception{assertEquals("字幕",SubtitleText.decode(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF,(byte)0xE5,(byte)0xAD,(byte)0x97,(byte)0xE5,(byte)0xB9,(byte)0x95}));}
    @Test public void detectsUtf16Bom()throws Exception{String value="中文字幕";assertEquals(value,SubtitleText.decode(value.getBytes(StandardCharsets.UTF_16)));}
    @Test public void aliVttRemovesTranscodeNumberingButPreservesTimelineAndLiteralBraces(){
        String vtt="WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n{=670=671=672}中文字幕 {2026}\n";
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n中文字幕 {2026}\n",SubtitleText.cleanAliVtt(vtt));
    }
}