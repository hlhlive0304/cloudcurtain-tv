package cn.cloudcurtain.tv;

import android.content.Context;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.SubtitleView;

/** Reads only original subtitle tracks while the main player uses a provider's compatible video. */
@androidx.annotation.OptIn(markerClass=UnstableApi.class)
final class OriginalSubtitles implements AutoCloseable {
    private final CaptionSamples captions;private ExoPlayer source;private final ExoPlayer main;private final Context context;private final MediaSpec original;private final MediaItem item;private final SubtitleView view;private final Handler handler=new Handler(Looper.getMainLooper());
    volatile String failureCode="",failureFrames="";volatile long transferredBytes,requestPosition,lastByteTime;volatile int transferStarts;private boolean closed,manual,lastMainPlaying;private String preferredIdentity;
    OriginalSubtitles(Context context,MediaSpec original,ExoPlayer main,SubtitleView view,String preferredIdentity){this(context,original,main,view,preferredIdentity,null);}
    OriginalSubtitles(Context context,MediaSpec original,ExoPlayer main,SubtitleView view,String preferredIdentity,CaptionSamples captions){this.captions=captions;this.preferredIdentity=preferredIdentity;manual=preferredIdentity!=null;
        this.main=main;this.context=context;this.original=original;this.view=view;lastMainPlaying=main.isPlaying();item=new MediaItem.Builder().setUri(original.url).build();startSource(main.getCurrentPosition());main.addListener(mainEvents);handler.postDelayed(sync,500);
    }
    private void startSource(long position){
        if(source!=null){ExoPlayer previous=source;source=null;previous.release();}
        OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(Net.client(original)).setTransferListener(new TransferListener(){public void onTransferInitializing(DataSource s,DataSpec d,boolean network){}public void onTransferStart(DataSource s,DataSpec d,boolean network){transferStarts++;requestPosition=d.position;}public void onBytesTransferred(DataSource s,DataSpec d,boolean network,int n){transferredBytes+=n;lastByteTime=SystemClock.elapsedRealtime();}public void onTransferEnd(DataSource s,DataSpec d,boolean network){}});
        androidx.media3.extractor.ExtractorsFactory extractors=()->{
            androidx.media3.extractor.Extractor[] normal=new androidx.media3.extractor.DefaultExtractorsFactory().createExtractors();
            java.util.List<androidx.media3.extractor.Extractor> selected=new java.util.ArrayList<>();
            selected.add(new androidx.media3.extractor.mkv.SubtitleMatroskaExtractor(new androidx.media3.extractor.text.DefaultSubtitleParserFactory()));
            for(androidx.media3.extractor.Extractor e:normal)if(!(e instanceof androidx.media3.extractor.mkv.MatroskaExtractor))selected.add(e);
            return selected.toArray(new androidx.media3.extractor.Extractor[0]);
        };
        source=new ExoPlayer.Builder(context)
            .setLoadControl(new androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(5000,15000,0,0).setTargetBufferBytes(4*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build())
            .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context,http),captions==null?extractors:captions.wrap(extractors,"original"))).build();
        source.setTrackSelectionParameters(source.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).setPreferredTextLanguages("zh","en").build());
        final ExoPlayer reader=source;source.addListener(new Player.Listener(){
            @Override public void onTracksChanged(Tracks tracks){
                if(closed||source!=reader)return;TrackSelectionOverride choice=null;if(preferredIdentity!=null){for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<g.length;i++)if(g.isTrackSupported(i)&&preferredIdentity.equals(AudioPolicy.identity(g.getTrackFormat(i))))choice=new TrackSelectionOverride(g.getMediaTrackGroup(),i);}else if(!manual)choice=SubtitlePolicy.bestAvailable(tracks);
                if(choice!=null&&!choice.equals(source.getTrackSelectionParameters().overrides.get(choice.mediaTrackGroup)))source.setTrackSelectionParameters(source.getTrackSelectionParameters().buildUpon().setOverrideForType(choice).build());
            }
            @Override public void onCues(CueGroup group){if(!closed&&source==reader)view.setCues(group.cues);}
            @Override public void onPlayerError(PlaybackException error){if(closed||source!=reader)return;failureCode=error.getErrorCodeName();StringBuilder frames=new StringBuilder();for(Throwable cause=error;cause!=null;cause=cause.getCause()){frames.append(cause.getClass().getSimpleName()).append(":");int n=0;for(StackTraceElement frame:cause.getStackTrace())if(n++<6)frames.append(frame.getClassName()).append(".").append(frame.getMethodName()).append(":").append(frame.getLineNumber()).append(",");}failureFrames=frames.toString();view.setCues(java.util.Collections.emptyList());LoginDiagnostics.record(context,Provider.read(original.provider),"读取原文件内嵌字幕",error);handler.removeCallbacks(sync);}
        });
        source.setMediaItem(item,position);source.prepare();source.setPlayWhenReady(main.isPlaying());
    }
    private final Player.Listener mainEvents=new Player.Listener(){
        @Override public void onPositionDiscontinuity(Player.PositionInfo old,Player.PositionInfo now,int reason){
            if(closed||source==null||reason!=Player.DISCONTINUITY_REASON_SEEK&&reason!=Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)return;
            if(source.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))return;
            view.setCues(java.util.Collections.emptyList());startSource(now.positionMs);
        }
    };
    private final Runnable sync=new Runnable(){@Override public void run(){
        if(closed)return;long pos=main.getCurrentPosition();
        if(source.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)){source.pause();handler.postDelayed(this,500);return;}
        boolean mainPlaying=main.isPlaying(),paused=lastMainPlaying&&!mainPlaying;lastMainPlaying=mainPlaying;
        if(paused||(source.getPlaybackState()==Player.STATE_READY&&source.getBufferedPosition()>=pos+1000&&Math.abs(source.getCurrentPosition()-pos)>1500)){view.setCues(java.util.Collections.emptyList());source.seekTo(pos);}
        source.setPlaybackSpeed(main.getPlaybackParameters().speed);source.setPlayWhenReady(main.isPlaying());handler.postDelayed(this,500);
    }};
    ExoPlayer player(){return source;}
    void select(TrackSelectionOverride choice){manual=true;preferredIdentity=choice==null?null:AudioPolicy.identity(choice.mediaTrackGroup.getFormat(choice.trackIndices.get(0)));TrackSelectionParameters.Builder b=source.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,choice==null);if(choice!=null)b.setOverrideForType(choice);source.setTrackSelectionParameters(b.build());if(choice==null){source.pause();view.setCues(java.util.Collections.emptyList());}else{source.seekTo(main.getCurrentPosition());source.setPlayWhenReady(main.isPlaying());}}
    @Override public void close(){if(closed)return;closed=true;main.removeListener(mainEvents);handler.removeCallbacksAndMessages(null);source.release();view.setCues(java.util.Collections.emptyList());}
}