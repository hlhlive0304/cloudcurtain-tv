package cn.cloudcurtain.tv;
import java.util.*;import java.text.*;
/** Filtering is local to the loaded page. Directories remain navigable. */
final class FilePresentation {
 static final String[] TYPES={"video","subtitle","document","image","audio","torrent","archive","other"};
 static final String[] TYPE_LABELS={"视频","字幕","文档","图片","音频","BT 种子","压缩包","其他"};
 static final String[] SOURCES={"scanned","shared","unzipped","backup","uploaded","favorites","unknown"};
 static final String[] SOURCE_LABELS={"我扫描的","来自分享","云解压","备份","我上传的","云收藏","来源未提供"};
 static final String[] SORT_LABELS={"综合排序","按文件名","按文件类型","按修改时间","按创建时间","按文件大小"};
 static final String[] VIEW_LABELS={"分栏","列表","图标","大图"};
 static final class Options {
  Set<String> types=new HashSet<>(),sources=new HashSet<>();int sort=0,view=1;boolean descending=false;String query="";
  Options copy(){Options c=new Options();c.types.addAll(types);c.sources.addAll(sources);c.sort=sort;c.view=view;c.descending=descending;c.query=query;return c;}
 }
 static String extension(String name){int dot=name.lastIndexOf('.');return dot>=0?name.substring(dot+1).toLowerCase(Locale.ROOT):"";}
 static String type(CloudApi.Entry e){
  if(e.directory)return "folder";if(UrlRules.video(e.name))return "video";if(UrlRules.subtitle(e.name))return "subtitle";
  String ext=extension(e.name);
  if(Arrays.asList("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","md","csv","epub").contains(ext))return "document";
  if(Arrays.asList("jpg","jpeg","png","webp","bmp","gif","heic").contains(ext))return "image";
  if(Arrays.asList("mp3","aac","flac","wav","m4a","ogg","opus","ape","dts").contains(ext))return "audio";
  if(ext.equals("torrent"))return "torrent";if(Arrays.asList("zip","rar","7z","tar","gz","bz2","iso").contains(ext))return "archive";return "other";
 }
 static String label(String id,String[] values,String[] labels){for(int i=0;i<values.length;i++)if(values[i].equals(id))return labels[i];return "来源未提供";}
 static String typeLabel(CloudApi.Entry e){return e.directory?"文件夹":label(type(e),TYPES,TYPE_LABELS);}
 static String sourceLabel(CloudApi.Entry e){return label(e.source,SOURCES,SOURCE_LABELS);}
 static String icon(CloudApi.Entry e){String type=type(e);return type.equals("folder")?"▸":type.equals("video")?"▶":type.equals("subtitle")?"字":type.equals("image")?"▧":type.equals("audio")?"♫":"□";}
 static String detail(CloudApi.Entry e){String info=typeLabel(e)+(e.directory?"":" · "+UrlRules.size(e.size));if(e.modified>0)info+=" · "+new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(e.modified));return info;}
 static List<CloudApi.Entry> select(List<CloudApi.Entry> entries,Options options){
  List<CloudApi.Entry> out=new ArrayList<>();String query=options.query.toLowerCase(Locale.ROOT);
  for(CloudApi.Entry e:entries)if(e.directory||((options.types.isEmpty()||options.types.contains(type(e)))&&(options.sources.isEmpty()||options.sources.contains(e.source))&&e.name.toLowerCase(Locale.ROOT).contains(query)))out.add(e);
  final Collator names=Collator.getInstance(Locale.CHINA);
  Collections.sort(out,(a,b)->{
   if(a.directory!=b.directory)return a.directory?-1:1;
   if(options.sort==0)return 0;
   long av=options.sort==3?a.modified:options.sort==4?a.created:options.sort==5?a.size:0,bv=options.sort==3?b.modified:options.sort==4?b.created:options.sort==5?b.size:0;
   // Missing dates stay last in both directions instead of being mistaken for recent files.
   if((options.sort==3||options.sort==4)&&((av==0)!=(bv==0)))return av==0?1:-1;
   int comparison=options.sort==1?names.compare(a.name,b.name):options.sort==2?type(a).compareTo(type(b)):Long.compare(av,bv);
   if(comparison==0)comparison=names.compare(a.name,b.name);return options.descending?-comparison:comparison;
  });
  if(options.sort==0&&options.descending){List<CloudApi.Entry> folders=new ArrayList<>(),files=new ArrayList<>();for(CloudApi.Entry e:out)(e.directory?folders:files).add(e);Collections.reverse(folders);Collections.reverse(files);out.clear();out.addAll(folders);out.addAll(files);}
  return out;
 }
}