package cn.cloudcurtain.tv;

import android.app.Activity;
import android.net.Uri;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Subtitles {
    public static final String API="https://api.opensubtitles.com/api/v1";
    public static class Result {
        public long id;public String label;
        Result(long id,String label){this.id=id;this.label=label;}
    }
    private final Activity context;private final Vault vault;
    public Subtitles(Activity context){this.context=context;vault=new Vault(context);}
    private Map<String,String> headers()throws IOException{
        String api=vault.get("os_api");if(api.isEmpty())throw new IOException("请先在“字幕与设置”填写你自己的 OpenSubtitles API Key。也可直接使用网盘同目录字幕或字幕链接。");
        Map<String,String> h=Net.headers("Api-Key",api,"User-Agent","CloudCurtainTV v0.1.14");String token=vault.get("os_token");if(!token.isEmpty())h.put("Authorization","Bearer "+token);return h;
    }
    public void login(String username,String password)throws Exception{
        JSONObject j=Net.json(API+"/login",new JSONObject().put("username",username).put("password",password),headers());
        String token=j.optString("token");if(token.isEmpty())throw new IOException("字幕服务未返回登录凭据，请检查账号。");vault.put("os_token",token);
    }
    public List<Result> search(String query)throws Exception{
        String lang=context.getSharedPreferences("settings",0).getString("sub_languages","zh-cn,zh-tw,en");
        JSONObject j=Net.json(Uri.parse(API+"/subtitles").buildUpon().appendQueryParameter("query",query).appendQueryParameter("languages",lang).appendQueryParameter("order_by","download_count").appendQueryParameter("order_direction","desc").build().toString(),null,headers());
        List<Result> results=new ArrayList<>();JSONArray data=j.optJSONArray("data");if(data==null)return results;
        for(int i=0;i<data.length()&&results.size()<50;i++){
            JSONObject a=data.getJSONObject(i).getJSONObject("attributes");JSONArray files=a.optJSONArray("files");if(files==null)continue;
            for(int k=0;k<files.length()&&results.size()<50;k++){JSONObject f=files.getJSONObject(k);String label=a.optString("language")+" · "+f.optString("file_name",a.optString("release"));if(a.optBoolean("hearing_impaired"))label+=" [听障字幕]";results.add(new Result(f.getLong("file_id"),label));}
        }return results;
    }
    public MediaSpec.Sub download(Result result)throws Exception{
        JSONObject j=Net.json(API+"/download",new JSONObject().put("file_id",result.id).put("sub_format","srt"),headers());
        String link=j.optString("link");if(!UrlRules.isWeb(link))throw new IOException("字幕下载不可用，请检查登录状态或下载额度。");
        return cache(new MediaSpec.Sub(link,result.label,"application/x-subrip"),null);
    }
    public MediaSpec.Sub cache(MediaSpec.Sub sub,MediaSpec media)throws Exception{
        String source=sub.url;MediaSpec scope=null;
        if(source.startsWith("cloud:")){
            if(media==null||media.provider.isEmpty())throw new IOException("没有字幕所属网盘");
            Provider p=Provider.read(media.provider);CloudApi api=new CloudApi(p);if(p==Provider.ALI)api.selectAliSpace(media.space);scope=api.resolve(new CloudApi.Entry(source.substring(6),sub.label,false,0));source=scope.url;
        }
        byte[] bytes;
        if(UrlRules.isWeb(source)){if(scope==null)scope=remoteScope(source,media);
            Request request=new Request.Builder().url(source).header("User-Agent",Net.UA).build();
            try(Response r=Net.client(scope).newCall(request).execute()){
                if(!r.isSuccessful()||r.body()==null)throw new IOException("字幕下载失败，HTTP "+r.code());
                try(InputStream input=r.body().byteStream()){bytes=bounded(input);}
            }
        }else if(source.startsWith("content:")){
            try(InputStream input=context.getContentResolver().openInputStream(Uri.parse(source))){if(input==null)throw new IOException("不能读取字幕");bytes=bounded(input);}
        }else throw new IOException("字幕地址不正确");
        String text=SubtitleText.decode(bytes);if(media!=null&&"ALI".equals(media.provider)&&"text/vtt".equals(sub.mime))text=SubtitleText.cleanAliVtt(text);String trimmed=text.trim().toLowerCase(Locale.ROOT);
        if(trimmed.startsWith("<!doctype html")||trimmed.startsWith("<html"))throw new IOException("这是网页而不是字幕文件，请使用字幕直链。");
        boolean valid=sub.mime.equals("text/x-ssa")?text.contains("[Events]"):sub.mime.equals("application/ttml+xml")?text.contains("<tt"):text.contains("-->");
        if(!valid)throw new IOException("没有识别到有效的字幕时间轴，请检查文件和字幕格式。");
        File dir=new File(context.getCacheDir(),"subtitles");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建字幕缓存");
        File[] old=dir.listFiles();if(old!=null&&old.length>30)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();
        File file=new File(dir,UUID.randomUUID().toString()+".txt");try(FileOutputStream out=new FileOutputStream(file)){out.write(text.getBytes(StandardCharsets.UTF_8));}
        MediaSpec.Sub cached=new MediaSpec.Sub(Uri.fromFile(file).toString(),sub.label,sub.mime);cached.sameFile=sub.sameFile;cached.language=sub.language;return cached;
    }
    static MediaSpec remoteScope(String source,MediaSpec media){
        MediaSpec scope=new MediaSpec(source,"");
        // Subtitle CDNs need public playback context, never the video/API credentials.
        boolean listed=false;if(media!=null)for(MediaSpec.Sub sub:media.subtitles)if(source.equals(sub.url)&&UrlRules.isWeb(source)){listed=true;break;}if(media!=null&&(listed||UrlRules.sameOrigin(source,media.url)||Net.mediaHost(media,source))){
            scope.provider=media.provider;
            for(String header:new String[]{"User-Agent","Referer"})if(media.headers.containsKey(header))scope.headers.put(header,media.headers.get(header));
        }
        return scope;
    }
    private byte[] bounded(InputStream input)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,total=0;
        while((n=input.read(buf))!=-1){total+=n;if(total>4*1024*1024)throw new IOException("字幕超过 4 MB，已停止读取");out.write(buf,0,n);}return out.toByteArray();
    }
}
