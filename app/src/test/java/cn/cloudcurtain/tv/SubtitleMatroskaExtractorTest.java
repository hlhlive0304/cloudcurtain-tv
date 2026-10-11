package cn.cloudcurtain.tv;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.common.util.*;
import androidx.media3.datasource.*;
import androidx.media3.extractor.*;
import androidx.media3.extractor.mkv.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
/** Compare real fixture subtitle packets with the upstream extractor, while measuring skipped bytes. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SubtitleMatroskaExtractorTest {
 static final class Memory implements DataSource {
  final byte[] bytes;long at,read;Memory(byte[] bytes){this.bytes=bytes;}
  public long open(DataSpec s){at=s.position;return bytes.length-at;}
  public int read(byte[] out,int off,int n){n=(int)Math.min(n,bytes.length-at);if(n==0)return -1;System.arraycopy(bytes,(int)at,out,off,n);at+=n;read+=n;return n;}
  public Uri getUri(){return Uri.parse("memory:test");}public void close(){}public void addTransferListener(TransferListener t){}
 }
 static final class Track implements TrackOutput {
  final int type;Format format;int samples;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();final List<Long> times=new ArrayList<>();
  Track(int type){this.type=type;}
  public void format(Format f){format=f;}
  public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException{byte[] b=new byte[length];int n=input.read(b,0,length);if(n==-1&&!allowEnd)throw new EOFException();if(n>0&&type==C.TRACK_TYPE_TEXT)bytes.write(b,0,n);return n;}
  public void sampleData(ParsableByteArray b,int length,int part){if(type==C.TRACK_TYPE_TEXT)bytes.write(b.getData(),b.getPosition(),length);b.skipBytes(length);}
  public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto){samples++;times.add(time);}
 }
 static final class Output implements ExtractorOutput {
  final Map<Integer,Track> tracks=new TreeMap<>();SeekMap map;
  public TrackOutput track(int id,int type){return tracks.computeIfAbsent(id,k->new Track(type));}
  public void endTracks(){}public void seekMap(SeekMap s){map=s;}
 }
 static final class Result {Memory file;Output output;int seeks;}
 private Result run(Extractor extractor,byte[] bytes)throws Exception{
  Result r=new Result();r.file=new Memory(bytes);r.output=new Output();extractor.init(r.output);r.file.open(new DataSpec.Builder().setUri("memory:test").build());DefaultExtractorInput input=new DefaultExtractorInput(r.file,0,bytes.length);PositionHolder at=new PositionHolder();
  for(int n=0;n<100000;n++){int status=extractor.read(input,at);if(status==Extractor.RESULT_END_OF_INPUT){extractor.release();return r;}if(status==Extractor.RESULT_SEEK){assertTrue(at.position>=0&&at.position<=bytes.length);r.seeks++;r.file.open(new DataSpec.Builder().setUri("memory:test").setPosition(at.position).build());input=new DefaultExtractorInput(r.file,at.position,bytes.length);}}throw new AssertionError("Extractor did not terminate");
 }
 @Test public void indexedReaderPreservesEverySubtitleAndAvoidsVideoPayload()throws Exception{
  byte[] fixture;try(InputStream in=getClass().getResourceAsStream("/subtitle-skip-fixture.mkv")){assertNotNull(in);fixture=in.readAllBytes();}
  Result full=run(new MatroskaExtractor(new androidx.media3.extractor.text.DefaultSubtitleParserFactory()),fixture),text=run(new SubtitleMatroskaExtractor(new androidx.media3.extractor.text.DefaultSubtitleParserFactory()),fixture);assertEquals(full.output.tracks.keySet(),text.output.tracks.keySet());assertTrue("range seeks="+text.seeks,text.seeks>0);assertTrue("read="+text.file.read+" full="+full.file.read,text.file.read<full.file.read/4);assertTrue("seek map",text.output.map.isSeekable());int subtitles=0;
  for(int id:full.output.tracks.keySet()){Track original=full.output.tracks.get(id),skipped=text.output.tracks.get(id);assertEquals(original.format.sampleMimeType,skipped.format.sampleMimeType);if(original.type==C.TRACK_TYPE_TEXT){subtitles++;assertTrue(original.samples>0);assertEquals(original.times,skipped.times);assertArrayEquals(original.bytes.toByteArray(),skipped.bytes.toByteArray());}else{assertTrue(original.samples>0);assertEquals(0,skipped.samples);}}
  assertEquals(2,subtitles);
 }
 @Test public void completeRelativeIndexPreservesAllSubtitlesWithFewSeeks()throws Exception{
  byte[] fixture;try(InputStream in=getClass().getResourceAsStream("/subtitle-index-fixture.mkv")){fixture=in.readAllBytes();}
  Result full=run(new MatroskaExtractor(new androidx.media3.extractor.text.DefaultSubtitleParserFactory()),fixture);SubtitleMatroskaExtractor extractor=new SubtitleMatroskaExtractor(new androidx.media3.extractor.text.DefaultSubtitleParserFactory());Result indexed=run(extractor,fixture);
  assertEquals(2,extractor.indexedSubtitleTracks());assertTrue(extractor.indexedSubtitleBlocks()>=4);
  for(int id:full.output.tracks.keySet()){Track a=full.output.tracks.get(id),z=indexed.output.tracks.get(id);if(a.type==C.TRACK_TYPE_TEXT){assertEquals(a.times,z.times);assertArrayEquals(a.bytes.toByteArray(),z.bytes.toByteArray());}else{assertEquals(0,z.samples);assertTrue(indexed.seeks<a.samples/10);}}
  assertTrue(indexed.file.read<full.file.read/4);
 }
}