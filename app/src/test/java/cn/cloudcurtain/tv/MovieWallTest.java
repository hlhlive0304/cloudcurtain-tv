package cn.cloudcurtain.tv;

import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import okhttp3.Request;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="land-mdpi")
public class MovieWallTest {
    Context c;
    @Before public void clear(){c=RuntimeEnvironment.getApplication();MovieLibrary.clearAll(c);c.getSharedPreferences("movie_metadata",0).edit().clear().commit();}
    @Test public void releaseNamesAndParentFallbackDoNotSendCodecWords(){
        MovieTitle a=MovieTitle.parse("Weapons.2025.2160p.iTunes.WEB-DL.DDP5.1.Atmos.HDR10+.H.265-ETHEL.mkv",Collections.emptyList());assertEquals("Weapons",a.title);assertEquals("2025",a.year);assertFalse(a.television);
        MovieTitle b=MovieTitle.parse("(2026) -1080p.BluRay REMUX.AVC.TrueHD7.1 Atmos3Audio.mkv",Arrays.asList("来自分享","电影","哆啦A梦：新·大雄的海底鬼岩城（2026）1080P REMUX"));assertEquals("哆啦A梦:新·大雄的海底鬼岩城",b.title);assertEquals("2026",b.year);
        MovieTitle d=MovieTitle.parse("S01E01.2026.2160p.HamiVideo.WEB-DL.H.264.AAC.mp4",Arrays.asList("电影","B）绑｜架｜游｜戏（2026）","Season1"));assertEquals("绑架游戏",d.title);assertTrue(d.television);
    }
    @Test public void yearsInsideMovieTitlesAreNotMistakenForReleaseYears(){
        MovieTitle m=MovieTitle.parse("1917.2019.1080p.BluRay.mkv",Collections.emptyList());assertEquals("1917",m.title);assertEquals("2019",m.year);
        MovieTitle n=MovieTitle.parse("2001.A.Space.Odyssey.1968.1080p.mkv",Collections.emptyList());assertEquals("2001 A Space Odyssey",n.title);assertEquals("1968",n.year);
        assertTrue(MovieTitle.personal("2022-07-06 15.54 IMG_0001.MOV"));assertFalse(MovieTitle.personal("Weapons.2025.mkv"));
    }
    @Test public void theatricalDateNumberAndFansubTagsAreNotMovieNames(){MovieTitle movie=MovieTitle.parse("[1995.04.15][剧场版03 云黑斋的野心][独偶字幕组].mp4",Arrays.asList("电影","蜡笔小新_剧场版","1-10"));assertEquals("云黑斋的野心",movie.title);assertEquals("1995",movie.year);assertFalse(movie.television);}
    @Test public void titlelessTheatricalFilesUseNamedParentAndKeepReleaseYear(){MovieTitle movie=MovieTitle.parse("[2004.04.17][剧场版12.mp4",Arrays.asList("电影","蜡笔小新_剧场版","11-20"));assertEquals("蜡笔小新",movie.title);assertEquals("2004",movie.year);MovieTitle date=MovieTitle.parse("1995-07[压制Y][微信公众号 example].mkv",Arrays.asList("电影","回魂夜"));assertEquals("回魂夜",date.title);assertEquals("1995",date.year);}
    @Test public void artworkQueriesRemoveQualityTagsAndResolveShortAliases(){assertEquals("逃出绝命街",MovieTitle.parse("「逃出绝命街」[4KSDR高码].mp4",Collections.emptyList()).title);MovieTitle alias=MovieTitle.parse("M&M.2026.1080p中英字幕.mp4",Arrays.asList("电影","小黄人与大怪兽"));assertEquals("小黄人与大怪兽",alias.title);assertEquals("2026",alias.year);}
    @Test public void uniqueColonTitleAliasesStillRejectWrongYearsAndAmbiguity()throws Exception{MovieMetadata a=MovieMetadata.read(new JSONObject().put("title","蜡笔小新：云黑斋的野心").put("release_date","1995"),false);assertSame(a,MovieMetadata.automatic(Collections.singletonList(a),new MovieTitle("云黑斋的野心","1995",false)));assertSame(a,MovieMetadata.automatic(Collections.singletonList(a),new MovieTitle("蜡笔小新","1995",false)));assertNull(MovieMetadata.automatic(Collections.singletonList(a),new MovieTitle("蜡笔小新","2004",false)));MovieMetadata b=MovieMetadata.read(new JSONObject().put("title","蜡笔小新：另一部电影").put("release_date","2004"),false);assertNull(MovieMetadata.automatic(Arrays.asList(a,b),new MovieTitle("蜡笔小新","",false)));}
    @Test public void ambiguousRemakesAndWrongYearsRequireHumanChoice()throws Exception{
        MovieMetadata first=MovieMetadata.read(new JSONObject().put("id",1).put("title","The Thing").put("release_date","1982-01-01"),false),second=MovieMetadata.read(new JSONObject().put("id",2).put("title","The Thing").put("release_date","2011-01-01"),false);
        assertNull(MovieMetadata.automatic(Arrays.asList(first,second),new MovieTitle("The Thing","",false)));assertSame(first,MovieMetadata.automatic(Arrays.asList(first,second),new MovieTitle("The Thing","1982",false)));assertNull(MovieMetadata.automatic(Arrays.asList(first,second),new MovieTitle("The Thing","2026",false)));
        assertNull(MovieMetadata.automatic(Collections.singletonList(first),new MovieTitle("Other Film","1982",false)));
    }
    @Test public void metadataUrlsCannotInjectOtherHostsAndCredentialsStayAtOfficialApi()throws Exception{
        MovieMetadata m=MovieMetadata.read(new JSONObject().put("poster_path","//attacker.test/cover.jpg"),false);assertTrue(m.poster.isEmpty());
        Map<String,String> query=new LinkedHashMap<>();query.put("query","影片 & 2026");Request r=Tmdb.request("test-token.only-fixture","/3/search/movie",query);assertEquals("api.themoviedb.org",r.url().host());assertEquals("https",r.url().scheme());assertFalse(Tmdb.validCredential("token-with-newline\nsecret"));assertFalse(Tmdb.validCredential("密钥字符串012345678901234567890"));assertNull(r.header("Cookie"));assertNull(r.header("Referer"));assertEquals("Bearer test-token.only-fixture",r.header("Authorization"));assertEquals("影片 & 2026",r.url().queryParameter("query"));
        Request api=Tmdb.request("0123456789abcdef0123456789abcdef","/3/search/movie",query);assertNull(api.header("Authorization"));assertNotNull(api.url().queryParameter("api_key"));
        try{Tmdb.request("fixture","https://attacker.test/",query);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void boundedRecursiveScanResumesAndRetainsSiblingSubtitles()throws Exception{
        MovieLibrary library=new MovieLibrary(c);MovieLibrary.Listing listing=(folder,page)->{CloudApi.Page result=new CloudApi.Page();if(folder.equals("/"))result.entries.add(new CloudApi.Entry("/films","电影",true,0));else{result.entries.add(new CloudApi.Entry("/films/test.mp4","Test.2025.1080p.mp4",false,10));result.entries.add(new CloudApi.Entry("/films/test.srt","test.srt",false,2));result.entries.add(new CloudApi.Entry("/films/readme.txt","readme.txt",false,1));}return result;};
        assertFalse(library.scan(Provider.BAIDU,"",false,listing,(p,pages,movies)->{},1,0).complete);assertEquals(0,library.movies().size());assertTrue(library.scan(Provider.BAIDU,"",false,listing,(p,pages,movies)->{},2,0).complete);assertEquals(1,library.movies().size());MovieLibrary.Movie movie=library.movies().get(0);assertEquals("Test",movie.title);assertEquals(1,movie.subtitles.size());assertEquals("cloud:/films/test.srt",movie.subtitles.get(0).url);assertEquals(Collections.singletonList("电影"),movie.parents);
        assertFalse(c.getSharedPreferences("movie_library",0).getString("items:BAIDU","").contains("download_url"));
    }
    @Test public void logoutDuringListingNeverRestoresOldAccountIndex()throws Exception{
        MovieLibrary library=new MovieLibrary(c);MovieLibrary.Listing listing=(folder,page)->{AccountSessions.invalidate(Provider.QUARK);MovieLibrary.clear(c,Provider.QUARK);CloudApi.Page result=new CloudApi.Page();result.entries.add(new CloudApi.Entry("private-file-id","Test.mp4",false,1));return result;};
        try{library.scan(Provider.QUARK,"",false,listing,(p,pages,movies)->{},1,0);fail();}catch(InterruptedException expected){}assertEquals(0,library.movies().size());
    }
    @Test public void scansOnlyRequestedCategoriesInOrderAcrossRootPages()throws Exception{
        MovieLibrary library=new MovieLibrary(c);List<String> folders=new ArrayList<>();MovieLibrary.Listing listing=(folder,page)->{CloudApi.Page r=new CloudApi.Page();folders.add(folder+":"+page);if(folder.equals("/")){if(page==1){r.entries.add(new CloudApi.Entry("tv","电视剧",true,0));r.entries.add(new CloudApi.Entry("course","课程",true,0));r.entries.add(new CloudApi.Entry("loose","Lecture.mp4",false,1));r.more=true;}else{r.entries.add(new CloudApi.Entry("animation","漫剧",true,0));r.entries.add(new CloudApi.Entry("film","电影",true,0));}}else if(folder.equals("tv")){r.entries.add(new CloudApi.Entry("show","庆余年（2024）",true,0));}else r.entries.add(new CloudApi.Entry(folder+"-video",folder.equals("show")?"EP01.1080p.mp4":"Test.2025.mp4",false,1));return r;};
        assertFalse(library.scan(Provider.BAIDU,"",false,listing,(p,n,m)->{},1,0).complete);
        assertTrue(library.scan(Provider.BAIDU,"",false,listing,(p,n,m)->{},10,0).complete);
        assertEquals(Arrays.asList("/:1","/:2","film:1","animation:1","tv:1","show:1"),folders);assertEquals(3,library.movies().size());
        MovieLibrary.Movie series=library.movies().stream().filter(m->m.id.equals("show-video")).findFirst().get();assertEquals("庆余年",series.title);assertEquals("2024",series.year);assertTrue(series.television);assertEquals("电视剧",series.category());
    }
    @Test public void oldWholeDriveCheckpointIsDiscardedButProgressIsKept()throws Exception{
        MovieLibrary.Movie unrelated=new MovieLibrary.Movie();unrelated.provider=Provider.BAIDU;unrelated.id="lecture";unrelated.name="Lecture.mp4";unrelated.folder="course";unrelated.parents.add("课程");unrelated.identify();
        c.getSharedPreferences("movie_library",0).edit().putString("items:BAIDU",new JSONArray().put(unrelated.json()).toString()).putString("queue:BAIDU","[{\"id\":\"course\",\"parents\":[\"课程\"],\"space\":\"\"}]").commit();c.getSharedPreferences("progress",0).edit().putLong("fixture",60000).commit();
        MovieLibrary library=new MovieLibrary(c);assertTrue(library.needsMigration());assertTrue(library.movies().isEmpty());
        library.scan(Provider.BAIDU,"",false,(folder,page)->{assertEquals("/",folder);return new CloudApi.Page();},(p,n,m)->{},1,0);assertEquals(60000,c.getSharedPreferences("progress",0).getLong("fixture",0));
    }
    @Test public void changedParentTitleInvalidatesAutomaticPosterButKeepsManualChoice()throws Exception{
        MovieLibrary.Movie m=new MovieLibrary.Movie();m.provider=Provider.QUARK;m.id="fixture";m.folder="films";m.name="01.mp4";m.parents.add("漫剧");m.parents.add("旧片名");m.identify();
        MovieMetadata old=new MovieMetadata();old.title="旧片名";old.year="";Tmdb metadata=new Tmdb(c);metadata.save(m,old);assertNotNull(metadata.cached(m));
        c.getSharedPreferences("movie_metadata",0).edit().putBoolean("manual:"+m.key(),false).commit();m.parents.set(1,"新片名");m.identify();assertNull(metadata.cached(m));metadata.save(m,old);assertNotNull(metadata.cached(m));
    }
    @Test public void providerRemovalPreservesOtherLibrariesAndMetadata()throws Exception{
        MovieLibrary library=new MovieLibrary(c);for(Provider p:new Provider[]{Provider.ALI,Provider.BAIDU})library.scan(p,p==Provider.ALI?"resource":"",false,(folder,page)->{CloudApi.Page result=new CloudApi.Page();if(folder.equals(p.root()))result.entries.add(new CloudApi.Entry("films","电影",true,0));else result.entries.add(new CloudApi.Entry("fixture","Test.mp4",false,1));return result;},(provider,pages,movies)->{},2,0);
        c.getSharedPreferences("movie_metadata",0).edit().putString("match:ALI:resource:fixture","ali").putString("match:BAIDU::fixture","baidu").commit();MovieLibrary.clear(c,Provider.ALI);Tmdb.clearProvider(c,Provider.ALI);assertEquals(1,library.movies().size());assertEquals(Provider.BAIDU,library.movies().get(0).provider);assertEquals("baidu",c.getSharedPreferences("movie_metadata",0).getString("match:BAIDU::fixture",""));assertFalse(c.getSharedPreferences("movie_metadata",0).contains("match:ALI:resource:fixture"));
    }
    @Test public void pageCheckpointContinuesWithoutReplayingTheFirstPage()throws Exception{
        MovieLibrary library=new MovieLibrary(c);List<Integer> requested=new ArrayList<>();MovieLibrary.Listing listing=(folder,page)->{CloudApi.Page result=new CloudApi.Page();if(folder.equals(Provider.QUARK.root())){result.entries.add(new CloudApi.Entry("films","电影",true,0));return result;}requested.add(page);result.entries.add(new CloudApi.Entry("item"+page,"Film "+page+".mp4",false,1));result.more=page<5;return result;};
        assertFalse(library.scan(Provider.QUARK,"",false,listing,(p,pages,movies)->{},2,0).complete);assertFalse(library.scan(Provider.QUARK,"",false,listing,(p,pages,movies)->{},2,0).complete);assertTrue(library.scan(Provider.QUARK,"",false,listing,(p,pages,movies)->{},2,0).complete);assertEquals(Arrays.asList(1,2,3,4,5),requested);assertEquals(5,library.movies().size());
    }
    @Test public void oversizedMetadataResponsesAndTitlelessFilesRemainSafe()throws Exception{
        try{Tmdb.readBounded(new java.io.ByteArrayInputStream(new byte[9]),8);fail();}catch(java.io.IOException expected){}
        assertEquals("未识别影片",MovieTitle.parse("2026.1080p.mkv",Collections.emptyList()).title);
        MovieMetadata m=MovieMetadata.read(new JSONObject().put("media_type","tv").put("name","Fixture TV").put("first_air_date","2026"),false);assertTrue(m.television);assertEquals("Fixture TV",m.title);
    }
    @Test public void posterGridResizesWithoutOverlapAndHasExplicitRemoteNavigation(){
        PosterGrid grid=new PosterGrid(c);grid.leftFocus=100;grid.topFocus=101;for(int i=0;i<17;i++){MovieLibrary.Movie m=new MovieLibrary.Movie();m.provider=Provider.QUARK;m.id=""+i;m.title="长电影片名与大字号展示测试 "+i;m.name="movie.mp4";m.year="2026";grid.addView(new PosterCard(c,m,6000,()->{}));}
        for(int width:new int[]{400,720,1200}){grid.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));grid.layout(0,0,width,grid.getMeasuredHeight());assertTrue(grid.columns()>=2);for(int i=0;i<grid.getChildCount();i++){View a=grid.getChildAt(i);assertTrue(a.getRight()<=width);assertTrue(a.getBottom()<=grid.getHeight());a.requestFocus();assertEquals(1f,a.getScaleX(),0);assertEquals(1f,a.getScaleY(),0);for(int j=i+1;j<grid.getChildCount();j++){View b=grid.getChildAt(j);assertFalse(Rect.intersects(new Rect(a.getLeft(),a.getTop(),a.getRight(),a.getBottom()),new Rect(b.getLeft(),b.getTop(),b.getRight(),b.getBottom())));}assertEquals(i<grid.columns()?101:grid.getChildAt(i-grid.columns()).getId(),a.getNextFocusUpId());}}
    }
}
