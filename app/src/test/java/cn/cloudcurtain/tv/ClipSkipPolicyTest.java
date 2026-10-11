package cn.cloudcurtain.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClipSkipPolicyTest {
 @Test public void defaultAndPausedPlaybackNeverSkip(){ClipSkipPolicy p=new ClipSkipPolicy(0,0);assertEquals(ClipSkipPolicy.Action.NONE,p.check(0,100000,true,true));p.configure(30,10);assertEquals(ClipSkipPolicy.Action.NONE,p.check(0,100000,false,true));assertEquals(ClipSkipPolicy.Action.NONE,p.check(0,100000,true,false));}
 @Test public void explicitIntroSkipRunsOnceAndKeepsUserSeekBack(){ClipSkipPolicy p=new ClipSkipPolicy(30,10);assertEquals(ClipSkipPolicy.Action.INTRO,p.check(1000,100000,true,true));assertEquals(ClipSkipPolicy.Action.NONE,p.check(1000,100000,true,true));assertEquals(ClipSkipPolicy.Action.OUTRO,p.check(90000,100000,true,true));assertEquals(ClipSkipPolicy.Action.NONE,p.check(93000,100000,true,true));}
 @Test public void resumePastIntroAndInvalidDurationsArePreserved(){ClipSkipPolicy p=new ClipSkipPolicy(30,10);assertEquals(ClipSkipPolicy.Action.NONE,p.check(60000,100000,true,true));assertEquals(ClipSkipPolicy.Action.NONE,p.check(0,100000,true,true));p.configure(90,30);assertEquals(ClipSkipPolicy.Action.NONE,p.check(0,100000,true,true));assertEquals(ClipSkipPolicy.Action.NONE,p.check(0,-1,true,true));}
}