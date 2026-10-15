package cn.cloudcurtain.tv;

import java.util.*;
import java.io.IOException;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class EpisodeQueueTest {
    private MediaSpec media(Provider p,String id){MediaSpec m=new MediaSpec("https://video.example/current.mp4","Show.E02.mkv").inFolder("folder",Arrays.asList("电视剧","Show"));m.provider=p.name();m.key=p.name()+":"+id;m.space=p==Provider.ALI?"backup":"";return m;}
    private CloudApi.Entry video(String id,String name){return new CloudApi.Entry(id,name,false,100);}
    private CloudApi.Page page(boolean more,String marker,CloudApi.Entry... entries){CloudApi.Page p=new CloudApi.Page();p.entries.addAll(Arrays.asList(entries));p.more=more;p.nextMarker=marker;return p;}
    @Test public void folderContextRoundTripsWithoutSerializingPlaylistOrSignedNeighborUrls()throws Exception {
        MediaSpec original=media(Provider.ALI,"current");MediaSpec parsed=MediaSpec.parse(original.json());
        assertEquals("folder",parsed.folder);assertEquals(original.parents,parsed.parents);assertEquals("backup",parsed.space);assertTrue(EpisodeQueue.available(parsed));
        original.inFolder(original.folder,original.parents);assertEquals(2,original.parents.size());
        JSONObject legacy=new JSONObject(original.json());legacy.remove("folder");legacy.remove("parents");assertFalse(EpisodeQueue.available(MediaSpec.parse(legacy.toString())));
    }
    @Test public void allProvidersCollectWholeFolderAcrossPagesAndKeepSubtitleIds()throws Exception {
        for(Provider provider:Provider.values()){
            List<Integer> requested=new ArrayList<>();EpisodeQueue q=EpisodeQueue.load(media(provider,"two"),(folder,n,marker)->{
                assertEquals("folder",folder);requested.add(n);
                if(n==1){assertEquals("",marker);return page(true,"opaque",video("ten","Show.E10.mkv"),video("two","Show.E02.mkv"),new CloudApi.Entry("dir","Extras",true,0));}
                assertEquals("opaque",marker);return page(false,"",video("one","Show.E1.mkv"),video("two","Show.E02.mkv"),video("sub","简中.srt"),video("text","readme.txt"));
            });
            assertEquals(Arrays.asList(1,2),requested);assertEquals(3,q.videos.size());assertEquals("one",q.neighbor(-1).id);assertEquals("ten",q.neighbor(1).id);assertEquals(1,q.subtitles.size());assertEquals("cloud:sub",q.subtitles.get(0).url);
        }
    }
    @Test public void firstLastAndSingleEpisodeDoNotWrap()throws Exception {
        CloudApi.Page listing=page(false,"",video("a","第1集.mp4"),video("b","第2集.mp4"));
        EpisodeQueue first=EpisodeQueue.load(media(Provider.QUARK,"a"),(f,p,m)->listing),last=EpisodeQueue.load(media(Provider.QUARK,"b"),(f,p,m)->listing);
        assertNull(first.neighbor(-1));assertEquals("b",first.neighbor(1).id);assertNull(last.neighbor(1));assertEquals("a",last.neighbor(-1).id);assertNull(first.neighbor(0));
        EpisodeQueue single=EpisodeQueue.load(media(Provider.TIANYI,"a"),(f,p,m)->page(false,"",video("a","Movie.mkv")));assertNull(single.neighbor(1));assertNull(single.neighbor(-1));
    }
    @Test public void numericComparisonHandlesSeasonsPaddingChineseAndHugeNumbers(){
        for(String[] names:new String[][]{{"Show.S1E2.2160p.mkv","Show.S1E10.1080p.mkv"},{"Show.S2E12.mkv","Show.S10E1.mkv"},{"第2集.mkv","第10集.mkv"},{"０２.mp4","１０.mp4"},{"file999999999999999999999.mkv","file1000000000000000000000.mkv"}})assertTrue(EpisodeQueue.natural(names[0],names[1])<0);
        assertEquals(0,EpisodeQueue.natural("Show.E02.mkv","show.e2.mkv"));
    }
    @Test public void vanishedCurrentAndPartialListingsNeverProduceFalseBoundaries()throws Exception {
        assertThrows(IOException.class,()->EpisodeQueue.load(media(Provider.BAIDU,"missing"),(f,p,m)->page(false,"",video("other","1.mp4"))));
        assertThrows(IOException.class,()->EpisodeQueue.load(media(Provider.ALI,"a"),(f,p,m)->page(true,"same",video("a","1.mp4"))));
        assertThrows(IOException.class,()->EpisodeQueue.load(media(Provider.BAIDU,"a"),(f,p,m)->{throw new IOException("offline");}));
    }
    @Test public void directoryLimitIsBoundedAndDoesNotExposeTruncatedQueue(){
        List<Integer> calls=new ArrayList<>();assertThrows(IOException.class,()->EpisodeQueue.load(media(Provider.BAIDU,"1"),(f,p,m)->{calls.add(p);return page(true,"",video(""+p,p+".mp4"));}));assertEquals(100,calls.size());
    }
    @Test public void unknownOrFolderlessSourcesNeverReadAccountCatalog(){
        MediaSpec m=media(Provider.BAIDU,"a");m.provider="";assertFalse(EpisodeQueue.available(m));assertThrows(IOException.class,()->EpisodeQueue.load(m,(f,p,k)->{throw new AssertionError();}));
    }
}
