package cn.cloudcurtain.tv;
import androidx.media3.common.*;import androidx.media3.common.text.Cue;import androidx.media3.common.util.ParsableByteArray;import androidx.media3.extractor.*;import androidx.media3.extractor.text.*;import java.util.*;import java.io.*;
/** Captures decoded subtitle intervals. Image crops are retained only for the explicitly selected translation track. */
@androidx.media3.common.util.UnstableApi
final class CaptionSamples {
 static final class Line {final long start,end;final String text,image,key;Line(long start,long end,String text){this(start,end,text,"");}Line(long start,long end,String text,String image){this.start=start;this.end=end;this.text=text;this.image=image;this.key=image.isEmpty()?text:digest(image);}}
 volatile int packets,decoded,dropped,decodeFailures;volatile long lastDuration;
 private volatile String imageTrack="";private volatile long imagePosition;
 void enableImages(String track,long position){imageTrack=track;imagePosition=position;synchronized(this){tracks.remove(track);}}
 void imagePosition(long position){imagePosition=position;}
 synchronized void disableImages(){if(!imageTrack.isEmpty())tracks.remove(imageTrack);imageTrack="";}
 private final Map<String,List<Line>> external=new HashMap<>();private final Map<String,NavigableMap<Long,Line>> tracks=new LinkedHashMap<>();
 synchronized void add(String track,CuesWithTiming cues){add(track,cues,false);}
 synchronized void add(String track,CuesWithTiming cues,boolean replace){
  decoded++;lastDuration=cues.durationUs;if(cues.startTimeUs==C.TIME_UNSET||(cues.durationUs==C.TIME_UNSET&&!replace)||cues.durationUs<0&&cues.durationUs!=C.TIME_UNSET){dropped++;return;}
  StringBuilder text=new StringBuilder();for(Cue c:cues.cues)if(c.text!=null){if(text.length()>0)text.append('\n');text.append(c.text);}String value=text.toString().trim();if(value.length()>2000)return;String image="";long cuePosition=cues.startTimeUs/1000;if(track.equals(imageTrack)&&value.isEmpty()&&cuePosition>=imagePosition-60000&&cuePosition<=imagePosition+30000)image=captionImage(cues.cues);
  if(!tracks.containsKey(track)&&tracks.size()>=16)return;NavigableMap<Long,Line> lines=tracks.get(track);if(lines==null){lines=new TreeMap<>();tracks.put(track,lines);}
  long start=cues.startTimeUs/1000,end=cues.durationUs==C.TIME_UNSET?Long.MAX_VALUE:cues.endTimeUs/1000;
  if(replace){Map.Entry<Long,Line> previous=lines.lowerEntry(start);if(previous!=null&&previous.getValue().end>start){Line old=previous.getValue();lines.put(previous.getKey(),new Line(old.start,start,old.text,old.image));}Long next=lines.higherKey(start);if(next!=null)end=Math.min(end,next);}
  // Empty replacement cues clear the previous subtitle at their real timestamp.
  lines.put(start,new Line(start,end,value,image));while(lines.size()>400)lines.pollFirstEntry();if(track.equals(imageTrack))trimImages(lines);
 }

 static String digest(String image){try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(image.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(Locale.ROOT,"%02x",v));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
 private void trimImages(NavigableMap<Long,Line> lines){List<Line> images=new ArrayList<>();long chars=0;for(Line line:lines.values())if(!line.image.isEmpty()){images.add(line);chars+=line.image.length();}Collections.sort(images,(a,b)->Long.compare(Math.abs(b.start-imagePosition),Math.abs(a.start-imagePosition)));for(Line line:images){if(chars<=2*1024*1024)break;lines.put(line.start,new Line(line.start,line.end,line.text));chars-=line.image.length();}}
 /** User-enabled: encode only a subtitle bitmap, never movie frames. Runs off the UI thread. */
 static String captionImage(List<Cue> cues){
  android.graphics.Bitmap bitmap=null;for(Cue cue:cues)if(cue.bitmap!=null){bitmap=cue.bitmap;break;}if(bitmap==null||bitmap.isRecycled()||bitmap.getWidth()>8192||bitmap.getHeight()>2048)return "";
  float scale=Math.min(1f,Math.min(1600f/bitmap.getWidth(),320f/bitmap.getHeight()));int width=Math.max(1,Math.round(bitmap.getWidth()*scale)),height=Math.max(1,Math.round(bitmap.getHeight()*scale));
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width+32,height+80,android.graphics.Bitmap.Config.RGB_565);
  try{android.graphics.Canvas canvas=new android.graphics.Canvas(image);canvas.drawColor(android.graphics.Color.BLACK);canvas.drawBitmap(bitmap,null,new android.graphics.Rect(16,40,width+16,height+40),new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);if(bytes.size()>128*1024)return "";return "data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);}finally{image.recycle();}
 }
 synchronized List<Line> ahead(String track,long position){NavigableMap<Long,Line> lines=tracks.get(track);List<Line> file=external.get(track);if(lines==null&&file==null)return Collections.emptyList();List<Line> out=new ArrayList<>();for(Line line:file!=null?file:lines.values())if((!line.text.isEmpty()||!line.image.isEmpty())&&line.end>position&&line.start<=position+30000){out.add(line);if(out.size()==24)break;}return out;}
 synchronized List<Line> current(String track,long position){List<Line> out=new ArrayList<>();for(Line l:ahead(track,position))if(l.start<=position&&l.end>position)out.add(l);return out;}
 synchronized void clear(){tracks.clear();external.clear();}
 void readExternal(android.content.Context context,MediaSpec.Sub sub,String track)throws Exception{
  android.net.Uri uri=android.net.Uri.parse(sub.url);if(!"file".equals(uri.getScheme()))throw new IOException("请先加载字幕文件");
  File file=new File(uri.getPath()).getCanonicalFile(),root=context.getCacheDir().getCanonicalFile();
  if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.isFile()||file.length()>4L*1024*1024)throw new IOException("字幕文件不可用");
  byte[] bytes;try(InputStream in=new FileInputStream(file)){bytes=Tmdb.readBounded(in,4*1024*1024);}
  Format format=new Format.Builder().setSampleMimeType(sub.mime).build();DefaultSubtitleParserFactory factory=new DefaultSubtitleParserFactory();
  if(!factory.supportsFormat(format))throw new IOException("此字幕格式暂不能翻译");
  List<Line> result=new ArrayList<>();final int[] chars={0};
  factory.create(format).parse(bytes,SubtitleParser.OutputOptions.allCues(),cues->{
   if(cues.durationUs<=0||cues.durationUs==C.TIME_UNSET)return;StringBuilder text=new StringBuilder();for(Cue cue:cues.cues)if(cue.text!=null){if(text.length()>0)text.append('\n');text.append(cue.text);}String value=text.toString().trim();
   if(!value.isEmpty()&&value.length()<=2000&&result.size()<16000&&chars[0]<2*1024*1024){result.add(new Line(cues.startTimeUs/1000,cues.endTimeUs/1000,value));chars[0]+=value.length();}
  });
  if(result.isEmpty()||result.size()>=16000||chars[0]>=2*1024*1024)throw new IOException("字幕没有文字或超过翻译读取上限");
  synchronized(this){external.put(track,result);}
 }

 ExtractorsFactory wrap(ExtractorsFactory factory,String scope){return ()->{Extractor[] input=factory.createExtractors();for(int i=0;i<input.length;i++)input[i]=new Wrapped(input[i],scope);return input;};}
 private final class Wrapped implements Extractor{
  final Extractor delegate;final String scope;final List<Tap> taps=new ArrayList<>();Wrapped(Extractor delegate,String scope){this.delegate=delegate;this.scope=scope;}
  public boolean sniff(ExtractorInput input)throws IOException{return delegate.sniff(input);}
  public List<SniffFailure> getSniffFailureDetails(){return delegate.getSniffFailureDetails();}
  public Extractor getUnderlyingImplementation(){return delegate.getUnderlyingImplementation();}
  public void init(ExtractorOutput output){delegate.init(new ExtractorOutput(){public TrackOutput track(int id,int type){TrackOutput out=output.track(id,type);if(type!=C.TRACK_TYPE_TEXT)return out;Tap tap=new Tap(out,scope);taps.add(tap);return tap;}public void endTracks(){output.endTracks();}public void seekMap(SeekMap map){output.seekMap(map);}});}
  public int read(ExtractorInput input,PositionHolder holder)throws IOException{return delegate.read(input,holder);}
  public void seek(long position,long timeUs){for(Tap tap:taps)tap.reset();delegate.seek(position,timeUs);}
  public void release(){delegate.release();}
 }
 private final class Tap implements TrackOutput{
  final TrackOutput output;final String scope;final ByteArrayOutputStream pending=new ByteArrayOutputStream();final CueDecoder decoder=new CueDecoder();String track="";boolean capture;boolean replace;boolean overflow;
  Tap(TrackOutput output,String scope){this.output=output;this.scope=scope;}
  public void durationUs(long us){output.durationUs(us);}
  public void format(Format f){capture=MimeTypes.APPLICATION_MEDIA3_CUES.equals(f.sampleMimeType);replace=f.cueReplacementBehavior==Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;track=scope+":"+Objects.toString(f.id,"");output.format(f);}
  void reset(){pending.reset();overflow=false;}
  void record(byte[] bytes,int offset,int length,int part){if(!capture||overflow||part!=SAMPLE_DATA_PART_MAIN)return;if(pending.size()+length>512*1024){reset();overflow=true;return;}pending.write(bytes,offset,length);}
  public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException{if(!capture)return output.sampleData(input,length,allowEnd,part);byte[] bytes=new byte[Math.min(length,8192)];int n=input.read(bytes,0,bytes.length);if(n==-1){if(allowEnd)return -1;throw new EOFException();}record(bytes,0,n,part);output.sampleData(new ParsableByteArray(bytes),n,part);return n;}
  public void sampleData(ParsableByteArray bytes,int length,int part){record(bytes.getData(),bytes.getPosition(),length,part);output.sampleData(bytes,length,part);}
  public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto){if(capture&&!overflow&&crypto==null){byte[] bytes=pending.toByteArray();int begin=bytes.length-offset-size;if(begin>=0&&size>0)try{packets++;add(track,decoder.decode(time,bytes,begin,size),replace);}catch(RuntimeException ignored){decodeFailures++;}reset();if(offset>0&&offset<=bytes.length)pending.write(bytes,bytes.length-offset,offset);}else reset();output.sampleMetadata(time,flags,size,offset,crypto);}
 }
}
