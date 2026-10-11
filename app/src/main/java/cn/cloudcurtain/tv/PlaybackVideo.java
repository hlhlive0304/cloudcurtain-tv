package cn.cloudcurtain.tv;
import androidx.media3.common.*;
/** An advancing audio clock is not proof that a movie's video can be decoded. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackVideo {
 static boolean unsupported(Tracks tracks){boolean video=false;for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_VIDEO){video=true;if(g.isSupported())return false;}return video;}
 static boolean selected(Tracks tracks){for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_VIDEO&&g.isSelected())return true;return false;}
}
