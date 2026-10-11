package cn.cloudcurtain.tv;

/** Per-video opt-in. Explicit seeks back into an intro are not repeatedly overridden. */
final class ClipSkipPolicy {
    enum Action { NONE, INTRO, OUTRO }
    private int introSeconds,outroSeconds;private boolean introDone,outroDone;
    ClipSkipPolicy(int intro,int outro){configure(intro,outro);}
    void configure(int intro,int outro){introSeconds=Math.max(0,Math.min(1800,intro));outroSeconds=Math.max(0,Math.min(1800,outro));introDone=outroDone=false;}
    int intro(){return introSeconds;}int outro(){return outroSeconds;}
    Action check(long position,long duration,boolean playing,boolean ready){
        if(!playing||!ready||duration<=0||((long)introSeconds+outroSeconds)*1000+1000>=duration)return Action.NONE;
        if(introSeconds>0&&!introDone){introDone=true;if(position<introSeconds*1000L)return Action.INTRO;}
        if(outroSeconds>0&&!outroDone&&position>=duration-outroSeconds*1000L){outroDone=true;return Action.OUTRO;}
        return Action.NONE;
    }
}