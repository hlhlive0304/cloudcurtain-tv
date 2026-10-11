package cn.cloudcurtain.tv;
import androidx.media3.common.*;import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class PlaybackVideoTest {
 private Tracks.Group group(String mime,boolean selected,int support){return new Tracks.Group(new TrackGroup(new Format.Builder().setSampleMimeType(mime).build()),false,new int[]{support},new boolean[]{selected});}
 @Test public void audioClockDoesNotCountAsVideoSuccess(){Tracks t=new Tracks(Arrays.asList(group("audio/aac",true,C.FORMAT_HANDLED),group("video/dolby-vision",false,C.FORMAT_UNSUPPORTED_SUBTYPE)));assertTrue(PlaybackVideo.unsupported(t));assertFalse(PlaybackVideo.selected(t));}
 @Test public void supportedAlternativeVideoAndAudioOnlyDoNotTriggerUnsupported(){assertFalse(PlaybackVideo.unsupported(new Tracks(Collections.singletonList(group("audio/aac",true,C.FORMAT_HANDLED)))));Tracks t=new Tracks(Arrays.asList(group("video/dolby-vision",false,C.FORMAT_UNSUPPORTED_SUBTYPE),group("video/avc",true,C.FORMAT_HANDLED)));assertFalse(PlaybackVideo.unsupported(t));assertTrue(PlaybackVideo.selected(t));}
}
