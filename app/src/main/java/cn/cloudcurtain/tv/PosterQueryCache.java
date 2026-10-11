package cn.cloudcurtain.tv;
import android.content.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import org.json.*;

/** Reuses public metadata across episode files. Contains no drive IDs, paths or credentials. */
final class PosterQueryCache {
 private final SharedPreferences store;
 PosterQueryCache(Context context){store=context.getSharedPreferences("poster_queries",0);}
 private static String key(MovieTitle query){try{return UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest((MovieTitle.normalized(query.title)+"|"+query.year+"|"+query.television).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 MovieMetadata find(MovieTitle query){try{String raw=store.getString(key(query),"");if(raw.isEmpty())return null;MovieMetadata metadata=MovieMetadata.read(new JSONObject(raw),query.television);return MovieMetadata.automatic(java.util.Collections.singletonList(metadata),query);}catch(Exception e){return null;}}
 void seed(SharedPreferences legacy)throws JSONException{
  if(store.getBoolean("seeded",false))return;
  java.util.Map<String,MovieTitle> queries=new java.util.LinkedHashMap<>();java.util.Map<String,java.util.Map<String,MovieMetadata>> groups=new java.util.LinkedHashMap<>();
  for(java.util.Map.Entry<String,?> entry:legacy.getAll().entrySet())if(entry.getKey().startsWith("match:")&&entry.getValue() instanceof String)try{
   JSONObject json=new JSONObject((String)entry.getValue());MovieMetadata data=MovieMetadata.read(json,json.optString("media_type").equals("tv"));if(data.id<=0||data.poster.isEmpty())continue;
   java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();names.add(data.title);names.add(data.original);if(!data.year.isEmpty())for(String part:data.title.split("[:：]",2))names.add(part);
   for(String name:names){if(name.trim().length()<2)continue;MovieTitle query=new MovieTitle(name,data.year,data.television);String key=key(query);if(!groups.containsKey(key)){if(groups.size()>=512)continue;groups.put(key,new java.util.LinkedHashMap<>());queries.put(key,query);}groups.get(key).put(data.source+":"+data.id,data);}
  }catch(JSONException ignored){}
  for(String key:groups.keySet()){MovieTitle query=queries.get(key);MovieMetadata unique=MovieMetadata.automatic(new java.util.ArrayList<>(groups.get(key).values()),query);if(unique!=null)remember(query,unique);else store.edit().remove(key).apply();}
  store.edit().putBoolean("seeded",true).apply();
 }
 boolean recentMiss(MovieTitle query){return System.currentTimeMillis()-store.getLong("miss:"+key(query),0)<15*60*1000L;}
 void remember(MovieTitle query,MovieMetadata metadata)throws JSONException{if(store.getAll().size()>512)store.edit().clear().apply();String key=key(query);SharedPreferences.Editor edit=store.edit();if(metadata==null)edit.putLong("miss:"+key,System.currentTimeMillis());else edit.putString(key,metadata.json().toString()).remove("miss:"+key);edit.apply();}
 static void clear(Context context){context.getSharedPreferences("poster_queries",0).edit().clear().apply();}
}
