package cn.cloudcurtain.tv;
import org.json.JSONObject;import java.text.*;import java.util.*;
/** Optional provider metadata; unknown dates and provenance stay unknown. */
final class CloudFileMetadata {
 static CloudApi.Entry apply(CloudApi.Entry entry,JSONObject raw){
  entry.created=date(raw,"created_at","l_created_at","server_ctime","createDate","created","ctime");
  entry.modified=date(raw,"updated_at","l_updated_at","server_mtime","lastOpTime","modified","mtime");
  for(String key:new String[]{"source_name","source","origin_type"}){Object value=raw.opt(key);if(value instanceof String){String source=source((String)value);if(!source.equals("unknown")){entry.source=source;break;}}}
  return entry;
 }
 static long date(JSONObject raw,String... keys){for(String key:keys){long value=parse(raw.opt(key));if(value>0)return value;}return 0;}
 static long parse(Object value){
  if(value==null||value==JSONObject.NULL)return 0;String text=value.toString().trim();
  try{long n=Long.parseLong(text);return n>0&&n<100000000000L?n*1000:n>0?n:0;}catch(NumberFormatException ignored){}
  for(String pattern:new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd"}){
   SimpleDateFormat format=new SimpleDateFormat(pattern,Locale.ROOT);format.setLenient(false);format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));ParsePosition pos=new ParsePosition(0);Date parsed=format.parse(text,pos);
   if(parsed!=null&&pos.getIndex()==text.length())return Math.max(0,parsed.getTime());
  }return 0;
 }
 static String source(String value){
  switch(value.trim().toLowerCase(Locale.ROOT)){
   case "scanned":case "我扫描的":return "scanned";
   case "shared":case "share":case "来自分享":case "来自：分享":return "shared";
   case "unzip":case "unzipped":case "云解压":return "unzipped";
   case "backup":case "备份":return "backup";
   case "upload":case "uploaded":case "我上传的":return "uploaded";
   case "favorite":case "favorites":case "云收藏":return "favorites";
   default:return "unknown";
  }
 }
}