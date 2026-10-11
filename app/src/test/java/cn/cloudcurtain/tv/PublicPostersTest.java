package cn.cloudcurtain.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import okhttp3.Request;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PublicPostersTest {
    private JSONObject page(String image,String summary)throws Exception{return new JSONObject().put("pageid",1).put("title","流浪地球 (电影)").put("extract",summary).put("fullurl","https://zh.wikipedia.org/wiki/Film").put("pageimage","Film.jpg").put("thumbnail",new JSONObject().put("source",image));}
    @Test public void publicRequestsDoNotCarryCloudOrAiCredentials(){
        Request r=PublicPosters.request("zh","流浪地球 & 2019");assertEquals("流浪地球 & 2019",r.url().queryParameter("gsrsearch"));assertEquals("zh.wikipedia.org",r.url().host());for(String header:new String[]{"Cookie","Authorization","Referer"})assertNull(r.header(header));
        assertFalse(PublicPosters.imageUrl("https://upload.wikimedia.org.attacker.test/wikipedia/a.jpg"));assertFalse(PublicPosters.imageUrl("http://upload.wikimedia.org/wikipedia/a.jpg"));assertFalse(PublicPosters.imageUrl("https://user:secret@upload.wikimedia.org/wikipedia/a.jpg"));assertFalse(PublicPosters.imageUrl("https://upload.wikimedia.org/private/file"));assertTrue(PublicPosters.imageUrl("https://upload.wikimedia.org/wikipedia/zh/a/a1/Film.jpg"));
    }
    @Test public void onlyMoviePagesWithTrustedImagesBecomeCandidates()throws Exception{
        JSONObject pages=new JSONObject().put("one",page("https://upload.wikimedia.org/wikipedia/zh/a/Film.jpg","《流浪地球》是2019年上映的中国大陆电影。")).put("two",page("https://attacker.test/poster.jpg","2019 film")).put("three",page("https://upload.wikimedia.org/wikipedia/a.jpg","2019 novel"));
        List<MovieMetadata> result=PublicPosters.parse(new JSONObject().put("query",new JSONObject().put("pages",pages)),false);assertEquals(1,result.size());assertEquals("流浪地球",result.get(0).title);assertEquals("2019",result.get(0).year);assertEquals("WIKIPEDIA",result.get(0).source);
    }
    @Test public void publicPosterCacheRetainsSourceAndRejectsInjectedUrls()throws Exception{
        MovieMetadata m=PublicPosters.parse(new JSONObject().put("query",new JSONObject().put("pages",new JSONObject().put("one",page("https://upload.wikimedia.org/wikipedia/zh/a/Film.jpg","2019电影")))),false).get(0);
        MovieMetadata restored=MovieMetadata.read(m.json(),false);assertEquals(m.poster,restored.poster);assertEquals(m.sourceUrl,restored.sourceUrl);assertTrue(PublicPosters.articleUrl(restored.imagePage));JSONObject poison=m.json().put("poster_path","https://attacker.test/a.jpg");assertTrue(MovieMetadata.read(poison,false).poster.isEmpty());
    }
    @Test public void ambiguousYearsNeverAutomaticallyApplyWrongPoster(){
        MovieMetadata first=new MovieMetadata(),second=new MovieMetadata();first.title=second.title="Film";first.year="2019";second.year="2023";assertSame(first,MovieMetadata.automatic(Arrays.asList(first,second),new MovieTitle("Film","2019",false)));assertNull(MovieMetadata.automatic(Arrays.asList(first,second),new MovieTitle("Film","",false)));
    }
    @Test public void publicMovieSuggestionsExcludePeopleAndRetainExactYear()throws Exception{
        JSONArray rows=new JSONArray().put(new JSONObject().put("type","movie").put("id","26266893").put("title","流浪地球").put("year","2019").put("img","https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2545472803.jpg")).put(new JSONObject().put("type","person").put("id","123").put("title","Actor").put("img","https://img3.doubanio.com/view/photo/s_ratio_poster/public/p123.jpg")).put(new JSONObject().put("type","movie").put("id","1").put("title","Film").put("img","https://img3.doubanio.com.attacker.test/view/photo/s_ratio_poster/public/p123.jpg"));
        List<MovieMetadata> matches=PublicPosters.parseDouban(rows,false);assertEquals(1,matches.size());assertEquals("2019",matches.get(0).year);MovieMetadata restored=MovieMetadata.read(matches.get(0).json(),false);assertEquals("DOUBAN",restored.source);assertEquals("https://movie.douban.com/subject/26266893/",restored.sourceUrl);assertTrue(PublicPosters.imageUrl(restored.poster));
    }
    @Test public void explicitStreamingMimeSurvivesSerialization()throws Exception{
        MediaSpec hls=new MediaSpec("https://example.test/opaque","video");hls.mime="application/x-mpegURL";assertEquals(hls.mime,MediaSpec.parse(hls.json()).mime);hls.mime="text/html";assertTrue(MediaSpec.parse(hls.json()).mime.isEmpty());
    }
}