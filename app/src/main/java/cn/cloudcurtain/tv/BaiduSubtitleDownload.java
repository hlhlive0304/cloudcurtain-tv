package cn.cloudcurtain.tv;

import android.net.Uri;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.HttpUrl;
import org.json.*;

/** Small sidecar files use the signed download offered to the current website session. */
final class BaiduSubtitleDownload {
    interface Request {JSONObject get(String url)throws Exception;}
    static MediaSpec resolve(CloudApi.Entry entry,Request request)throws Exception{
        if(!entry.id.startsWith("/")||entry.directory||!UrlRules.subtitle(entry.name))throw new IOException("百度字幕文件无效。");
        String target=new JSONArray().put(entry.id).toString();
        JSONObject metadata=request.get("https://pan.baidu.com/api/filemetas?web=1&target="+Uri.encode(target));
        JSONArray files=metadata.optJSONArray("info");
        if(files==null||files.length()!=1)throw new IOException("百度未返回字幕文件信息。");
        JSONObject file=files.getJSONObject(0);long id=file.optLong("fs_id"),size=file.optLong("size",-1);
        if(file.optInt("errno",0)!=0||file.optInt("isdir",1)!=0||id<=0||!entry.id.equals(file.optString("path")))throw new IOException("百度字幕文件信息与所选文件不一致。");
        if(size<0||size>4*1024*1024)throw new IOException("字幕超过 4 MB 或文件大小无效，已停止读取。");
        JSONObject variables=request.get("https://pan.baidu.com/api/gettemplatevariable?web=1&fields="+Uri.encode("[\"sign1\",\"sign3\",\"timestamp\",\"bdstoken\"]"));
        JSONObject session=variables.optJSONObject("result");if(session==null)throw new IOException("百度未提供字幕下载会话，请重新登录后重试。");
        String one=session.optString("sign1"),three=session.optString("sign3"),token=session.optString("bdstoken");long timestamp=session.optLong("timestamp");
        if(!ascii(one)||!ascii(three)||token.isEmpty()||token.length()>4096||timestamp<=0)throw new IOException("百度字幕下载会话不完整，请重新登录后重试。");
        String url=Uri.parse("https://pan.baidu.com/api/download").buildUpon().appendQueryParameter("web","1").appendQueryParameter("type","dlink")
            .appendQueryParameter("fidlist","["+id+"]").appendQueryParameter("sign",sign(three,one)).appendQueryParameter("timestamp",String.valueOf(timestamp)).appendQueryParameter("bdstoken",token).build().toString();
        JSONObject download=request.get(url);JSONArray links=download.optJSONArray("dlink");
        if(links==null||links.length()!=1)throw new IOException("百度未返回字幕下载地址，请重新登录后重试。");
        JSONObject link=links.getJSONObject(0);String address=link.optString("dlink");
        if(link.has("fs_id")&&link.optLong("fs_id")!=id)throw new IOException("百度字幕下载文件不一致。");
        if(!trusted(address))throw new IOException("百度字幕下载地址无效。");
        MediaSpec scope=new MediaSpec(address,entry.name);scope.provider="BAIDU";scope.key="BAIDU:"+entry.id;scope.sourceBytes=size;
        scope.headers.put("User-Agent",Net.UA);scope.headers.put("Referer",Provider.BAIDU.origin);return scope;
    }
    private static boolean ascii(String value){return value.matches("[\\x20-\\x7E]{1,4096}");}
    static boolean trusted(String address){
        try{HttpUrl url=HttpUrl.get(address);return url.isHttps()&&url.port()==443&&url.username().isEmpty()&&url.password().isEmpty()&&url.fragment()==null
            &&(UrlRules.domain(url.host(),"baidu.com")||UrlRules.domain(url.host(),"baidupcs.com"));}catch(Exception e){return false;}
    }
    /** RC4/base64 transform used by the web download session; no server-supplied JavaScript is executed. */
    static String sign(String key,String value)throws IOException{
        if(!ascii(key)||!ascii(value))throw new IOException("百度字幕下载签名参数无效。");
        byte[] k=key.getBytes(StandardCharsets.US_ASCII),data=value.getBytes(StandardCharsets.US_ASCII);int[] state=new int[256];
        for(int i=0;i<256;i++)state[i]=i;
        for(int i=0,j=0;i<256;i++){j=(j+state[i]+(k[i%k.length]&255))&255;int temp=state[i];state[i]=state[j];state[j]=temp;}
        for(int n=0,i=0,j=0;n<data.length;n++){i=(i+1)&255;j=(j+state[i])&255;int temp=state[i];state[i]=state[j];state[j]=temp;data[n]^=(byte)state[(state[i]+state[j])&255];}
        return android.util.Base64.encodeToString(data,android.util.Base64.NO_WRAP);
    }
}
