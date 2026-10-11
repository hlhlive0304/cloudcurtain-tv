package cn.cloudcurtain.tv;
import android.app.Instrumentation;import android.os.Bundle;import java.util.*;import org.json.*;
/** Read-only baseline discovery limited to the three directories requested by the user. */
final class BaiduFolderLiveCheck {
 static void run(Instrumentation test,Bundle out)throws Exception{
  CloudApi api=new CloudApi(Provider.BAIDU);CloudApi.Page root=api.list("/",1);out.putInt("root_entries",root.entries.size());
  ArrayDeque<MovieLibrary.Movie> queue=new ArrayDeque<>();
  for(String category:new String[]{"电影","漫剧","电视剧"})for(CloudApi.Entry e:root.entries)if(e.directory&&e.name.trim().equals(category)){
   MovieLibrary.Movie folder=new MovieLibrary.Movie();folder.id=e.id;folder.parents.add(category);queue.add(folder);out.putBoolean("root_"+category,true);
  }
  java.util.LinkedHashMap<String,MovieLibrary.Movie> indexed=new java.util.LinkedHashMap<>();
  for(MovieLibrary.Movie m:new MovieLibrary(test.getTargetContext()).movies())if(m.provider==Provider.BAIDU)indexed.put(m.key(),m);
  int pages=0,matches=0;Set<String> seen=new HashSet<>();
  while(!queue.isEmpty()&&pages<30){MovieLibrary.Movie folder=queue.remove();if(!seen.add(folder.id))continue;
   for(int page=1;page<=6&&pages<30;page++){
    Thread.sleep(1400);out.putString("last_folder_hash",Integer.toHexString(folder.id.hashCode()));out.putInt("last_page",page);
    CloudApi.Page rows=api.list(folder.id,page);pages++;
    for(CloudApi.Entry e:rows.entries){
     if(e.directory&&folder.parents.size()<10){MovieLibrary.Movie child=new MovieLibrary.Movie();child.id=e.id;child.parents.addAll(folder.parents);child.parents.add(e.name);queue.add(child);}
     else if(!e.directory&&UrlRules.video(e.name)){
      MovieLibrary.Movie m=new MovieLibrary.Movie();m.provider=Provider.BAIDU;m.id=e.id;m.name=e.name;m.size=e.size;m.folder=folder.id;m.parents.addAll(folder.parents);m.identify();indexed.put(m.key(),m);
      String name=(e.name+" "+String.join(" ",folder.parents)).toLowerCase(Locale.ROOT);
      if(name.contains("总动员")||name.contains("toy.story")||name.contains("toy story")){out.putString("requested_"+matches+"_hash",Integer.toHexString(m.playbackKey().hashCode()));out.putLong("requested_"+matches+"_bytes",m.size);matches++;}
     }
    }if(!rows.more)break;
   }
  }
  JSONArray data=new JSONArray();for(MovieLibrary.Movie m:indexed.values())data.put(m.json());test.getTargetContext().getSharedPreferences("movie_library",0).edit().putString("items:BAIDU",data.toString()).commit();
  out.putInt("requested_matches",matches);out.putInt("pages",pages);out.putInt("indexed",indexed.size());
 }
}
