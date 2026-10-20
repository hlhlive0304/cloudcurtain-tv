package cn.cloudcurtain.tv;

import androidx.media3.common.PlaybackException;

/** Separate bounded budgets for an expired address and an unsupported original video. */
final class PlaybackRecovery {
    enum Action { NONE, REFRESH, COMPATIBLE }
    static final long STALL_MS=45000;
    private final boolean addressEnabled,compatibleEnabled;
    private boolean addressUsed,compatibleUsed;
    PlaybackRecovery(String provider){compatibleEnabled="ALI".equals(provider)||"QUARK".equals(provider)||"BAIDU".equals(provider);addressEnabled=compatibleEnabled||"TIANYI".equals(provider);}
    boolean error(Throwable error,int code,boolean compatible){
        int status=PlaybackErrors.httpStatus(error);
        if(status==401||status==403||status==404||status==410)return claimAddress();
        boolean unsupported=!compatible&&(code==PlaybackException.ERROR_CODE_DECODING_FAILED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||code==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED||code==PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED);
        return claimCompatible(unsupported);
    }
    boolean stalled(long waitingMs){return stalled(waitingMs,false,false);}
    boolean stalled(long waitingMs,boolean originalAuto,boolean rendered){
        if(waitingMs<(originalAuto&&rendered?15000:STALL_MS))return false;
        return originalAuto&&compatibleEnabled?claimCompatible(true):claimAddress();
    }
    private boolean claimAddress(){if(!addressEnabled||addressUsed)return false;addressUsed=true;return true;}
    private boolean claimCompatible(boolean eligible){if(!compatibleEnabled||compatibleUsed||!eligible)return false;compatibleUsed=true;return true;}
    boolean used(){return addressUsed||compatibleUsed;}
    Action onStall(long waitingMs,MediaSpec media,boolean rendered){
        boolean auto="auto".equals(media.quality)&&!media.compatible;
        if(waitingMs<(auto&&rendered&&compatibleEnabled?15000:STALL_MS))return Action.NONE;
        if(auto&&compatibleEnabled)return claimCompatible(true)?Action.COMPATIBLE:Action.NONE;
        return claimAddress()?Action.REFRESH:Action.NONE;
    }
    Action onError(Throwable error,int code,MediaSpec media){
        int status=PlaybackErrors.httpStatus(error);
        if(status==401||status==403||status==404||status==410)return claimAddress()?Action.REFRESH:Action.NONE;
        if(!"auto".equals(media.quality)||media.compatible)return Action.NONE;
        return error(error,code,false)?Action.COMPATIBLE:Action.NONE;
    }
}
