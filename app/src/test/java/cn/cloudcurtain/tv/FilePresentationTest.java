package cn.cloudcurtain.tv;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import org.json.*;import java.util.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FilePresentationTest{
 private CloudApi.Entry entry(String name,long size){return new CloudApi.Entry(name,name,false,size);}
 @Test public void metadataUsesRealDatesAndNeverGuessesProvenanceFromNamesOrNumericCodes()throws Exception{
  CloudApi.Entry e=CloudFileMetadata.apply(entry("来自分享.mp4",1),new JSONObject("{server_mtime:1700000000,source:1,created_at:'2024-01-01T00:00:00.000Z'}"));
  assertEquals(1700000000000L,e.modified);assertEquals(1704067200000L,e.created);assertEquals("unknown",e.source);
  assertEquals(0,CloudFileMetadata.parse("2024-13-40"));assertEquals(0,CloudFileMetadata.parse("2024-01-01garbage"));
  assertEquals("shared",CloudFileMetadata.apply(entry("x",1),new JSONObject("{source_name:'来自分享'}")).source);
 }
 @Test public void filteringKeepsFoldersAndSeparatesTypesSources(){
  CloudApi.Entry folder=new CloudApi.Entry("dir","folder",true,0),video=entry("movie.mkv",3),sub=entry("movie.ass",1),other=entry("book.pdf",2);video.source="shared";
  FilePresentation.Options o=new FilePresentation.Options();o.types.add("video");o.sources.add("shared");
  assertEquals(Arrays.asList(folder,video),FilePresentation.select(Arrays.asList(other,folder,sub,video),o));o.query="missing";assertEquals(Collections.singletonList(folder),FilePresentation.select(Arrays.asList(folder,video),o));
  assertEquals("subtitle",FilePresentation.type(sub));assertEquals("document",FilePresentation.type(other));assertEquals("archive",FilePresentation.type(entry("x.7z",1)));assertEquals("torrent",FilePresentation.type(entry("x.torrent",1)));
 }
 @Test public void reverseSortKeepsUnknownDatesLastAndFoldersFirst(){
  CloudApi.Entry folder=new CloudApi.Entry("dir","folder",true,0),a=entry("a.mp4",1),b=entry("b.mp4",2),unknown=entry("c.mp4",3);a.modified=10;b.modified=20;
  FilePresentation.Options o=new FilePresentation.Options();o.sort=3;o.descending=true;
  assertEquals(Arrays.asList(folder,b,a,unknown),FilePresentation.select(Arrays.asList(unknown,a,folder,b),o));o.descending=false;
  assertEquals(Arrays.asList(folder,a,b,unknown),FilePresentation.select(Arrays.asList(unknown,a,folder,b),o));
  o.sort=5;o.descending=true;assertEquals(Arrays.asList(folder,unknown,b,a),FilePresentation.select(Arrays.asList(a,b,folder,unknown),o));
 }
}
