package cn.cloudcurtain.tv;

import android.content.*;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Separate client: never uses Net, CookieManager, cloud credentials or cloud redirect rules. */
final class Tmdb {
    static final String NOTICE="This product uses the TMDB API but is not endorsed or certified by TMDB.";
    static final String IMAGE="https://image.tmdb.org/t/p/w342";
    private final PublicPosters publicPosters=new PublicPosters();private final Context context;private final SharedPreferences cache;private final OkHttpClient client;
    Tmdb(Context c){context=c.getApplicationContext();cache=context.getSharedPreferences("movie_metadata",0);client=new OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();}
    boolean configured(){return !new Vault(context).get("tmdb_key").trim().isEmpty();}
    static boolean validCredential(String value){return value!=null&&value.matches("[A-Za-z0-9_.-]{20,2000}");}
    static Request request(String credential,String path,Map<String,String> query){
        if(!path.equals("/3/search/movie")&&!path.equals("/3/search/tv")&&!path.equals("/3/configuration"))throw new IllegalArgumentException("Unsupported TMDB endpoint");
        if(!validCredential(credential))throw new IllegalArgumentException("TMDB 密钥格式不正确，请填写官方 API Key 或 Read Access Token。");
        HttpUrl.Builder u=HttpUrl.get("https://api.themoviedb.org"+path).newBuilder();for(Map.Entry<String,String> e:query.entrySet())u.addQueryParameter(e.getKey(),e.getValue());
        Request.Builder r=new Request.Builder().header("Accept","application/json");if(credential.matches("[a-fA-F0-9]{32}"))u.addQueryParameter("api_key",credential);else r.header("Authorization","Bearer "+credential);return r.url(u.build()).build();
    }
    private JSONObject call(String path,Map<String,String> query)throws Exception{
        String key=new Vault(context).get("tmdb_key").trim();if(key.isEmpty())throw new IOException("请先在海报设置填写自己的 TMDB API Key 或 Read Access Token。");
        try(Response r=client.newCall(request(key,path,query)).execute()){
            if(!r.isSuccessful())throw new IOException(r.code()==401?"TMDB 密钥无效，请检查配置。":r.code()==429?"TMDB 请求较多，请稍后再试。":"TMDB 暂时无法访问（HTTP "+r.code()+"）。");
            if(r.body()==null)throw new IOException("TMDB 没有返回数据。");return new JSONObject(new String(readBounded(r.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    void verify()throws Exception{call("/3/configuration",Collections.emptyMap());}
    List<MovieMetadata> search(String title,String year,boolean television)throws Exception{
        if(title.trim().isEmpty()||title.length()>160)throw new IOException("请输入不超过 160 字的片名。");
        Map<String,String> q=new LinkedHashMap<>();q.put("query",title);q.put("language","zh-CN");q.put("include_adult","false");q.put("page","1");if(year.matches("(?:19|20)[0-9]{2}"))q.put(television?"first_air_date_year":"year",year);
        JSONArray a=call(television?"/3/search/tv":"/3/search/movie",q).optJSONArray("results");List<MovieMetadata> all=new ArrayList<>();if(a!=null)for(int i=0;i<a.length()&&all.size()<20;i++)all.add(MovieMetadata.read(a.getJSONObject(i),television));return all;
    }
    MovieMetadata cached(MovieLibrary.Movie movie){try{String raw=cache.getString("match:"+movie.key(),"");return raw.isEmpty()?null:MovieMetadata.read(new JSONObject(raw),movie.television);}catch(Exception e){return null;}}
    void save(MovieLibrary.Movie movie,MovieMetadata metadata)throws JSONException{cache.edit().putString("match:"+movie.key(),metadata.json().toString()).apply();}
    void remove(MovieLibrary.Movie movie){cache.edit().remove("match:"+movie.key()).remove("checked:"+movie.key()).apply();}
    MovieMetadata match(MovieLibrary.Movie movie)throws Exception{
        if(movie.personal||movie.title.equals("未识别影片"))return null;MovieMetadata existing=cached(movie);if(existing!=null)return existing;
        String checked="checked:"+movie.key();if(System.currentTimeMillis()-cache.getLong(checked,0)<7*86400000L)return null;
        int generation=AccountSessions.generation(movie.provider);List<MovieMetadata> candidates=configured()?search(movie.title,movie.year,movie.television):publicPosters.search(movie.title,movie.year,movie.television);MovieMetadata found=MovieMetadata.automatic(candidates,new MovieTitle(movie.title,movie.year,movie.television));
        synchronized(AccountSessions.class){if(generation!=AccountSessions.generation(movie.provider))return null;if(found!=null)save(movie,found);cache.edit().putLong(checked,System.currentTimeMillis()).apply();}return found;
    }
    void cancelPending(){client.dispatcher().cancelAll();publicPosters.cancelPending();}
    static void clearProvider(Context c,Provider p){SharedPreferences cache=c.getSharedPreferences("movie_metadata",0);SharedPreferences.Editor edit=cache.edit();for(String key:cache.getAll().keySet())if(key.startsWith("match:"+p.name()+":")||key.startsWith("checked:"+p.name()+":"))edit.remove(key);edit.apply();}
    static byte[] readBounded(InputStream stream,int max)throws IOException{ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int count;while((count=stream.read(bytes))!=-1){if(output.size()+count>max)throw new IOException("响应过大，请稍后重试。");output.write(bytes,0,count);}return output.toByteArray();}
}