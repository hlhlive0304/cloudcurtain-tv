package cn.cloudcurtain.tv;

import android.content.Context;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;
import java.util.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaybackHistoryTest {
    static final class Memory implements PlaybackHistory.Store {String value="{migrated:true,items:[]}";public String read(){return value;}public void write(String v){value=v;}}
    private Context context;private Memory memory;private PlaybackHistory history;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();MovieLibrary.clearAll(context);context.getSharedPreferences("progress",0).edit().clear().commit();memory=new Memory();history=new PlaybackHistory(context,memory);}
    private MediaSpec media(Provider provider,String id){MediaSpec m=new MediaSpec("https://cdn.example/old.mkv?sign=expired","Show.S01E02.1080p.mkv").inFolder("series",Arrays.asList("电视剧","Show"));m.provider=provider.name();m.space=provider==Provider.ALI?"backup":"";m.key=provider.name()+":"+id;m.matchTitle="Show 2025";m.sourceBytes=1024;return m;}
    private PlaybackHistory.Entry entry(String id,long updated)throws Exception{return PlaybackHistory.snapshot(media(Provider.BAIDU,id),123000,360000,false,updated);}
    @Test public void nativeCloudHistoryRetainsReferencesAndNeverOldLinksOrCredentials()throws Exception{
        MediaSpec m=media(Provider.QUARK,"file");m.headers.put("Cookie","private-fixture-cookie");m.headers.put("Authorization","private-fixture-authorization");m.subtitles.add(new MediaSpec.Sub("https://cdn.example/srt?secret=old","external","text/vtt"));m.subtitles.add(new MediaSpec.Sub("cloud:srt-id","简中.srt","application/x-subrip"));history.save(PlaybackHistory.snapshot(m,123000,360000,false,10));
        assertFalse(memory.value.contains("cdn.example"));assertFalse(memory.value.contains("private-fixture"));assertEquals(1,history.entries().get(0).source.subtitles.size());assertEquals("cloud:srt-id",history.entries().get(0).source.subtitles.get(0).url);assertTrue(history.entries().get(0).source.headers.isEmpty());assertEquals("",history.entries().get(0).source.url);
    }
    @Test public void allFourProvidersReResolveFileIdentityAndKeepFolderAndAliSpace()throws Exception{
        for(Provider p:Provider.values()){
            PlaybackHistory.Entry e=PlaybackHistory.snapshot(media(p,"original-id"),180000,360000,false,1);int[] calls={0};MediaSpec fresh=PlaybackHistory.resolve(e,(provider,space,file)->{calls[0]++;assertSame(p,provider);assertEquals(p==Provider.ALI?"backup":"",space);assertEquals("original-id",file.id);assertEquals(1024,file.size);MediaSpec m=media(p,file.id);m.url="https://cdn.example/new.mkv?sign=fresh";return m;});
            assertEquals(1,calls[0]);assertTrue(fresh.url.contains("sign=fresh"));assertEquals("series",fresh.folder);assertEquals(e.source.parents,fresh.parents);assertEquals("Show 2025",fresh.matchTitle);assertEquals(180000,e.resumePosition());
        }
    }
    @Test public void sameFileUpdatesPositionAndMovesToFrontWithoutDuplicates()throws Exception{history.save(entry("one",1));history.save(entry("two",2));PlaybackHistory.Entry e=entry("one",3);e.position=240000;history.save(e);List<PlaybackHistory.Entry> list=history.entries();assertEquals(2,list.size());assertEquals(e.id(),list.get(0).id());assertEquals(240000,list.get(0).position);}
    @Test public void retentionDropsOldestAfterOneHundredUniqueVideos()throws Exception{for(int n=0;n<105;n++)history.save(entry(""+n,n));List<PlaybackHistory.Entry> list=history.entries();assertEquals(100,list.size());assertEquals(entry("104",0).id(),list.get(0).id());assertEquals(entry("5",0).id(),list.get(99).id());}
    @Test public void legacyProgressImportsOnceWithoutInventingWatchDate()throws Exception{
        MovieLibrary.Movie m=new MovieLibrary.Movie();m.provider=Provider.BAIDU;m.id="/Show/E01.mkv";m.folder="/Show";m.name="Show.E01.mkv";m.parents.add("漫剧");m.identify();context.getSharedPreferences("movie_library",0).edit().putString("items:BAIDU",new JSONArray().put(m.json()).toString()).commit();context.getSharedPreferences("progress",0).edit().putLong(Integer.toHexString(m.playbackKey().hashCode()),99000).commit();memory.value="";
        List<PlaybackHistory.Entry> imported=history.entries();assertEquals(1,imported.size());assertEquals(99000,imported.get(0).position);assertEquals(0,imported.get(0).updated);history.remove(imported.get(0).id());assertTrue(history.entries().isEmpty());assertEquals(99000,context.getSharedPreferences("progress",0).getLong(Integer.toHexString(m.playbackKey().hashCode()),0));
    }
    @Test public void providerRemovalAndClearDoNotReimportLegacyRecords()throws Exception{history.save(entry("b",1));history.save(PlaybackHistory.snapshot(media(Provider.ALI,"a"),1000,2000,false,2));history.clearProvider(Provider.BAIDU);assertEquals(1,history.entries().size());assertEquals("ALI",history.entries().get(0).source.provider);history.clear();assertTrue(history.entries().isEmpty());assertTrue(new JSONObject(memory.value).getBoolean("migrated"));}
    @Test public void completedEntriesStayVisibleAndRestartFromZero()throws Exception{PlaybackHistory.Entry e=PlaybackHistory.snapshot(media(Provider.BAIDU,"done"),360000,360000,true,1);history.save(e);e=history.entries().get(0);assertTrue(e.completed);assertEquals(0,e.resumePosition());assertTrue(e.detail().contains("已看完"));}
    @Test public void aliSpacesAndDifferentProvidersHaveIndependentHistoryIdentity()throws Exception{MediaSpec one=media(Provider.ALI,"same"),two=media(Provider.ALI,"same");two.space="resource";assertNotEquals(PlaybackHistory.identity(one),PlaybackHistory.identity(two));assertNotEquals(PlaybackHistory.identity(one),PlaybackHistory.identity(media(Provider.BAIDU,"same")));}
    @Test public void damagedRowsAreSkippedWithoutLosingValidEntry()throws Exception{PlaybackHistory.Entry valid=entry("good",1);memory.value=new JSONObject().put("migrated",true).put("items",new JSONArray().put(new JSONObject().put("source","bad")).put(valid.json())).toString();assertEquals(1,history.entries().size());assertEquals(valid.id(),history.entries().get(0).id());}
    @Test public void localAndDirectSourcesCanResumeWithoutCloudRequests()throws Exception{for(String uri:new String[]{"content://fixture/video/1","file:///storage/video.mp4","https://example.com/video.mp4"}){MediaSpec m=new MediaSpec(uri,"Video.mp4");PlaybackHistory.Entry e=PlaybackHistory.snapshot(m,10000,20000,false,1);history.save(e);MediaSpec restored=PlaybackHistory.resolve(e,(p,s,f)->{throw new AssertionError("Not a cloud source");});assertEquals(uri,restored.url);}assertEquals(3,history.entries().size());}
    @Test public void episodeLabelAndLongDurationsRemainReadable()throws Exception{PlaybackHistory.Entry e=entry("show",1);assertEquals("S01E02 · Show 2025",e.title());assertEquals("1:01:01",PlaybackHistory.time(3661000));assertEquals("00:00",PlaybackHistory.time(-1));}
}
