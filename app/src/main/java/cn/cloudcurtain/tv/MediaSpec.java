package cn.cloudcurtain.tv;

import org.json.*;
import java.util.*;

public class MediaSpec {
    public long sourceBytes;
    public String url,title,provider="",key="",space="",quality="original",qualityLabel="原文件",matchTitle="",mime=""; public boolean compatible; public int sourceWidth,sourceHeight;
    public Map<String,String> headers=new LinkedHashMap<>();
    public List<Sub> subtitles=new ArrayList<>();
    public MediaSpec(String url,String title){this.url=url;this.title=title;this.key=url;}
    public static class Sub {
        public String url,label,mime,language=""; public boolean sameFile;
        public Sub(String u,String l,String m){url=u;label=l;mime=m;}
        public Sub exactFile(String language){this.sameFile=true;this.language=language==null?"":language;return this;}
        JSONObject json() throws JSONException {return new JSONObject().put("url",url).put("label",label).put("mime",mime).put("sameFile",sameFile).put("language",language);}
    }
    public String json() {
        try {
            JSONArray a=new JSONArray();for(Sub s:subtitles)a.put(s.json());
            return new JSONObject().put("sourceBytes",sourceBytes).put("mime",mime).put("sourceWidth",sourceWidth).put("sourceHeight",sourceHeight).put("url",url).put("title",title).put("matchTitle",matchTitle).put("provider",provider).put("key",key).put("compatible",compatible).put("space",space).put("quality",quality).put("qualityLabel",qualityLabel).put("headers",new JSONObject(headers)).put("subtitles",a).toString();
        }catch(Exception e){throw new IllegalArgumentException(e);}
    }
    public static MediaSpec parse(String s) throws JSONException {
        JSONObject j=new JSONObject(s);MediaSpec m=new MediaSpec(j.getString("url"),j.optString("title","视频"));m.mime=j.optString("mime");if(!m.mime.equals("application/x-mpegURL")&&!m.mime.equals("application/dash+xml"))m.mime="";m.sourceWidth=j.optInt("sourceWidth");m.sourceHeight=j.optInt("sourceHeight");m.matchTitle=j.optString("matchTitle");m.provider=j.optString("provider");m.key=j.optString("key",m.url);m.compatible=j.optBoolean("compatible",false);m.space=j.optString("space");m.quality=j.optString("quality",m.compatible?"compatible":"original");m.qualityLabel=j.optString("qualityLabel",m.compatible?"兼容播放":"原文件");
        m.sourceBytes=Math.max(0,j.optLong("sourceBytes"));JSONObject h=j.optJSONObject("headers");if(h!=null)for(Iterator<String> it=h.keys();it.hasNext();){String k=it.next();m.headers.put(k,h.getString(k));}
        JSONArray a=j.optJSONArray("subtitles");if(a!=null)for(int i=0;i<a.length();i++){JSONObject z=a.getJSONObject(i);Sub sub=new Sub(z.getString("url"),z.getString("label"),z.getString("mime"));sub.sameFile=z.optBoolean("sameFile");sub.language=z.optString("language");m.subtitles.add(sub);}
        return m;
    }
}
