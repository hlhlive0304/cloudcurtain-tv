package cn.cloudcurtain.tv;
import androidx.media3.common.*;
/** Prefer supported main audio, then channel count, sample rate and bitrate. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class AudioPolicy {
 static long score(Format f){
  long s=Math.max(0,f.channelCount)*10000000000L+Math.max(0,f.sampleRate)*10000L+Math.max(0,f.bitrate);
  if((f.roleFlags&(C.ROLE_FLAG_COMMENTARY|C.ROLE_FLAG_DESCRIBES_VIDEO))!=0)s-=1000000000000000L;
  if((f.roleFlags&C.ROLE_FLAG_MAIN)!=0)s+=100000000000000L;
  if((f.selectionFlags&C.SELECTION_FLAG_DEFAULT)!=0)s+=1000000L;return s;
 }
 static String identity(Format f){return f.language+":"+f.label+":"+f.sampleMimeType+":"+f.channelCount+":"+f.sampleRate+":"+f.bitrate;}
 static TrackSelectionOverride best(Tracks tracks,String manual){
  TrackSelectionOverride selected=null;long best=Long.MIN_VALUE;
  for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_AUDIO)for(int i=0;i<g.length;i++)if(g.isTrackSupported(i)){
   Format f=g.getTrackFormat(i);if(manual!=null&&manual.equals(identity(f)))return new TrackSelectionOverride(g.getMediaTrackGroup(),i);
   long v=score(f);if(v>best){best=v;selected=new TrackSelectionOverride(g.getMediaTrackGroup(),i);}
  }return selected;
 }
 static String label(Format f){String name=f.label!=null?f.label:f.language!=null?f.language:"音轨";return name+(f.channelCount>0?" · "+f.channelCount+" 声道":"")+(f.sampleRate>0?" · "+f.sampleRate/1000.0+" kHz":"")+(f.bitrate>0?" · "+f.bitrate/1000+" kbps":"");}
}