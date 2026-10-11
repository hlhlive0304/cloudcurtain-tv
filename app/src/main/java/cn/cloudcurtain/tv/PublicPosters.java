package cn.cloudcurtain.tv;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Public article artwork. Separate HTTP client; no drive cookies or API keys. */
final class PublicPosters {
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    static boolean imageUrl(String value){
        HttpUrl u=HttpUrl.parse(value);if(u==null||!u.scheme().equals("https")||u.port()!=443||!u.username().isEmpty()||!u.password().isEmpty()||u.query()!=null)return false;return ((u.host().equals("upload.wikimedia.org")||u.host().equals("thumb.wikimedia.org"))&&u.encodedPath().startsWith("/wikipedia/"))||(u.host().matches("img[1-9]\\.doubanio\\.com")&&u.encodedPath().matches("/view/photo/[A-Za-z0-9_-]+/public/p[0-9]+\\.(jpg|webp|png)"));
    }
    static boolean articleUrl(String value){
        HttpUrl u=HttpUrl.parse(value);return u!=null&&u.scheme().equals("https")&&u.port()==443&&u.username().isEmpty()&&u.password().isEmpty()&&(((u.host().equals("zh.wikipedia.org")||u.host().equals("en.wikipedia.org"))&&u.encodedPath().startsWith("/wiki/"))||(u.host().equals("movie.douban.com")&&u.encodedPath().matches("/subject/[0-9]+/")));
    }
    static Request request(String language,String title){
        if(!language.equals("zh")&&!language.equals("en"))throw new IllegalArgumentException();
        if(title.trim().isEmpty()||title.length()>180)throw new IllegalArgumentException("请输入准确片名");
        HttpUrl u=HttpUrl.get("https://"+language+".wikipedia.org/w/api.php").newBuilder().addQueryParameter("action","query").addQueryParameter("format","json").addQueryParameter("generator","search").addQueryParameter("gsrsearch",title).addQueryParameter("gsrlimit","8").addQueryParameter("gsrnamespace","0").addQueryParameter("prop","pageimages|extracts|info").addQueryParameter("pilicense","any").addQueryParameter("piprop","thumbnail|name").addQueryParameter("pithumbsize","400").addQueryParameter("exintro","1").addQueryParameter("explaintext","1").addQueryParameter("exchars","800").addQueryParameter("inprop","url").build();
        return new Request.Builder().url(u).header("Accept","application/json").header("User-Agent","CloudCurtainTV/0.1.14 (personal movie library)").build();
    }
    static List<MovieMetadata> parse(JSONObject reply,boolean television){
        List<MovieMetadata> result=new ArrayList<>();JSONObject q=reply.optJSONObject("query"),pages=q==null?null:q.optJSONObject("pages");if(pages==null)return result;
        for(Iterator<String> keys=pages.keys();keys.hasNext();){
            JSONObject p=pages.optJSONObject(keys.next());if(p==null)continue;JSONObject thumb=p.optJSONObject("thumbnail");
            String summary=p.optString("extract"),image=thumb==null?"":thumb.optString("source"),article=p.optString("fullurl");
            if(!imageUrl(image)||!articleUrl(article)||!summary.matches("(?is).*(电影|電影|film|电视剧|電視劇|television|动画|動畫).*"))continue;
            MovieMetadata m=new MovieMetadata();m.id=p.optLong("pageid");m.source="WIKIPEDIA";m.sourceUrl=article;m.imagePage=article.substring(0,article.indexOf("/wiki/")+6)+"File:"+android.net.Uri.encode(p.optString("pageimage"));m.poster=image;m.television=television;m.title=p.optString("title").replaceAll("\\s*[（(](?:电影|電影|film|[0-9]{4} film)[）)]$","").trim();m.original=m.title;m.overview=summary;
            java.util.regex.Matcher year=java.util.regex.Pattern.compile("(?:19|20)[0-9]{2}").matcher(summary.substring(0,Math.min(160,summary.length())));if(year.find())m.year=year.group();result.add(m);
        }return result;
    }
    static List<MovieMetadata> parseDouban(JSONArray rows,boolean television){
        List<MovieMetadata> result=new ArrayList<>();for(int i=0;i<rows.length()&&result.size()<10;i++){JSONObject row=rows.optJSONObject(i);if(row==null||!row.optString("type").equals("movie")||!imageUrl(row.optString("img"))||!row.optString("id").matches("[0-9]+"))continue;MovieMetadata m=new MovieMetadata();m.id=row.optLong("id");m.title=row.optString("title");m.original=row.optString("sub_title",m.title);m.year=row.optString("year");if(!m.year.matches("(?:19|20)[0-9]{2}"))m.year="";m.television=television;m.source="DOUBAN";m.poster=row.optString("img");m.sourceUrl="https://movie.douban.com/subject/"+row.optString("id")+"/";m.imagePage=m.sourceUrl;m.overview="封面来自豆瓣电影。可通过“封面图片来源”查看影片介绍。";result.add(m);}return result;
    }
    private List<MovieMetadata> douban(String title,boolean television)throws Exception{
        HttpUrl url=HttpUrl.get("https://movie.douban.com/j/subject_suggest").newBuilder().addQueryParameter("q",title).build();
        try(Response response=client.newCall(new Request.Builder().url(url).header("User-Agent","CloudCurtainTV/0.1.14 (personal movie library)").header("Accept","application/json").build()).execute()){if(!response.isSuccessful()||response.body()==null)throw new IOException("网络封面搜索暂时不可用（HTTP "+response.code()+"）");return parseDouban(new JSONArray(new String(Tmdb.readBounded(response.body().byteStream(),65536),java.nio.charset.StandardCharsets.UTF_8)),television);}
    }
    void cancelPending(){client.dispatcher().cancelAll();}
    List<MovieMetadata> search(String title,String year,boolean television)throws Exception{
        List<MovieMetadata> found=new ArrayList<>();Exception failure=null;if(title.trim().isEmpty()||title.length()>180)throw new IOException("请输入准确片名");try{for(MovieMetadata m:douban(title,television))if(year.isEmpty()||m.year.equals(year))found.add(m);if(!found.isEmpty())return found;}catch(Exception e){failure=e;}
        for(String language:new String[]{"zh","en"}){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("已取消封面搜索");
            try(Response response=client.newCall(request(language,title)).execute()){
                if(!response.isSuccessful()||response.body()==null)throw new IOException("网络封面搜索暂时不可用（HTTP "+response.code()+"）");
                List<MovieMetadata> pages=parse(new JSONObject(new String(Tmdb.readBounded(response.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8)),television);
                for(MovieMetadata m:pages)if(year.isEmpty()||m.year.equals(year))found.add(m);
                if(!found.isEmpty())break;
            }catch(Exception e){failure=e;}
        }
        if(found.isEmpty()&&failure!=null)throw new IOException("网络封面服务暂时无法连接，请稍后重试。",failure);return found;
    }
}