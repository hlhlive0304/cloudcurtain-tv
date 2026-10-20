package cn.cloudcurtain.tv;
import org.junit.Test;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.DataSpec;
import android.net.Uri;
import java.util.Collections;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=28)
public class PlaybackRecoveryTest {
    private Throwable http(int code){return new HttpDataSource.InvalidResponseCodeException(code,"denied",null,Collections.emptyMap(),new DataSpec(Uri.parse("https://media.example/movie")),new byte[0]);}
    @Test public void deniedAddressRecoversOnceAndDoesNotLoop(){PlaybackRecovery policy=new PlaybackRecovery("ALI");assertTrue(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,true));assertFalse(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,true));}
    @Test public void unsupportedOriginalCanSwitchButCompatibleFailureStops(){assertTrue(new PlaybackRecovery("QUARK").error(null,PlaybackException.ERROR_CODE_DECODING_FAILED,false));assertFalse(new PlaybackRecovery("QUARK").error(null,PlaybackException.ERROR_CODE_DECODING_FAILED,true));}
    @Test public void stallWaitsForDeadlineAndUsesOneBudget(){PlaybackRecovery policy=new PlaybackRecovery("QUARK");assertFalse(policy.stalled(44999));assertTrue(policy.stalled(45000));assertFalse(policy.stalled(90000));assertFalse(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,false));}
    @Test public void tianyiRenewsTheSameQualityWithoutClaimingACompatibleStream(){PlaybackRecovery policy=new PlaybackRecovery("TIANYI");assertFalse(policy.error(null,PlaybackException.ERROR_CODE_DECODING_FAILED,false));assertTrue(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,false));assertFalse(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,false));assertFalse(policy.stalled(90000));}
    @Test public void compatibleFallbackDoesNotSpendTheLaterExpiryRefresh(){PlaybackRecovery policy=new PlaybackRecovery("QUARK");assertTrue(policy.error(null,PlaybackException.ERROR_CODE_DECODING_FAILED,false));assertTrue(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,true));assertFalse(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,true));assertFalse(policy.error(null,PlaybackException.ERROR_CODE_DECODING_FAILED,false));}
    @Test public void tianyiTimeoutRefreshesOnceWithoutSwitchingQuality(){PlaybackRecovery policy=new PlaybackRecovery("TIANYI");assertFalse(policy.stalled(44999,false,false));assertTrue(policy.stalled(45000,false,false));assertFalse(policy.stalled(90000,false,false));}
    @Test public void healthyProvidersAndRateLimitsAreNotChanged(){for(String p:new String[]{""}){PlaybackRecovery policy=new PlaybackRecovery(p);assertFalse(policy.error(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,false));assertFalse(policy.stalled(90000));}PlaybackRecovery policy=new PlaybackRecovery("ALI");assertFalse(policy.error(http(429),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,false));assertFalse(policy.used());}
    @Test public void explicitOriginalAndExplicitFourKRefreshWithoutDegrading(){
        for(String provider:new String[]{"QUARK","TIANYI","BAIDU"})for(String quality:new String[]{"original","quark:3840x1608"}){
            MediaSpec media=new MediaSpec("https://example.test/video","");media.provider=provider;media.quality=quality;
            PlaybackRecovery policy=new PlaybackRecovery(provider);
            assertEquals(PlaybackRecovery.Action.NONE,policy.onStall(44999,media,true));
            assertEquals(PlaybackRecovery.Action.REFRESH,policy.onStall(45000,media,true));
            assertEquals(PlaybackRecovery.Action.NONE,policy.onStall(90000,media,true));
            assertEquals(PlaybackRecovery.Action.NONE,policy.onError(null,PlaybackException.ERROR_CODE_DECODING_FAILED,media));
            assertEquals(quality,media.quality);assertFalse(media.compatible);
        }
    }
    @Test public void autoFallbackAndAddressRenewalHaveIndependentBoundedBudgets(){
        MediaSpec media=new MediaSpec("https://example.test/video","");media.quality="auto";PlaybackRecovery policy=new PlaybackRecovery("QUARK");
        assertEquals(PlaybackRecovery.Action.COMPATIBLE,policy.onStall(15000,media,true));
        assertEquals(PlaybackRecovery.Action.NONE,policy.onStall(90000,media,true));
        assertEquals(PlaybackRecovery.Action.REFRESH,policy.onError(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,media));
        assertEquals(PlaybackRecovery.Action.NONE,policy.onError(http(403),PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,media));
    }
}
