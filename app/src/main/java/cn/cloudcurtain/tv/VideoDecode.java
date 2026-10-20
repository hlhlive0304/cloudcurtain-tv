package cn.cloudcurtain.tv;

import androidx.media3.common.*;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.container.NalUnitUtil;
import java.io.IOException;
import java.util.List;

/** Explicit, per-file base-layer playback. Never changes resolution or downloads another video. */
@androidx.media3.common.util.UnstableApi
final class VideoDecode {
    static boolean hasHevcBase(Format format){
        return format!=null&&format.drmInitData==null&&MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)
            &&MimeTypes.VIDEO_H265.equals(MediaCodecUtil.getAlternativeCodecMimeType(format));
    }
    static Format baseFormat(Format format,boolean enabled){
        // Keep the original HEVC VPS/SPS/PPS and colour information. The decoder reads its profile
        // from that CSD; a Dolby Vision codec string must not be presented as a HEVC profile.
        if(!enabled||!hasHevcBase(format))return format;
        String codecs=null;try{codecs=NalUnitUtil.getH265BaseLayerCodecsString(format.initializationData);}catch(RuntimeException ignored){/* Some containers carry their parameter sets in-band. */}
        return format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs(codecs).build();
    }
    static ExtractorsFactory wrap(ExtractorsFactory factory,boolean enabled,Runnable applied){
        if(!enabled)return factory;
        return ()->{Extractor[] list=factory.createExtractors();for(int i=0;i<list.length;i++)list[i]=new Wrapped(list[i],applied);return list;};
    }
    private static final class Wrapped implements Extractor {
        final Extractor delegate;final Runnable applied;
        Wrapped(Extractor delegate,Runnable applied){this.delegate=delegate;this.applied=applied;}
        public boolean sniff(ExtractorInput input)throws IOException{return delegate.sniff(input);}
        public List<SniffFailure> getSniffFailureDetails(){return delegate.getSniffFailureDetails();}
        public Extractor getUnderlyingImplementation(){return delegate.getUnderlyingImplementation();}
        public void init(ExtractorOutput output){delegate.init(new ExtractorOutput(){
            public TrackOutput track(int id,int type){TrackOutput sink=output.track(id,type);if(type!=C.TRACK_TYPE_VIDEO)return sink;
                return new TrackOutput(){
                    public void format(Format format){Format selected=baseFormat(format,true);if(selected!=format)applied.run();sink.format(selected);}
                    public void durationUs(long duration){sink.durationUs(duration);}
                    public int sampleData(androidx.media3.common.DataReader input,int length,boolean allowEnd,int part)throws IOException{return sink.sampleData(input,length,allowEnd,part);}
                    public void sampleData(ParsableByteArray bytes,int length,int part){sink.sampleData(bytes,length,part);}
                    public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto){sink.sampleMetadata(time,flags,size,offset,crypto);}
                };
            }
            public void endTracks(){output.endTracks();}
            public void seekMap(SeekMap map){output.seekMap(map);}
        });}
        public int read(ExtractorInput input,PositionHolder holder)throws IOException{return delegate.read(input,holder);}
        public void seek(long position,long timeUs){delegate.seek(position,timeUs);}
        public void release(){delegate.release();}
    }
}
