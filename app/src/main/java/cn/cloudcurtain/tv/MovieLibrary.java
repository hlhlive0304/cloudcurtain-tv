package cn.cloudcurtain.tv;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Private read-only index. Signed media URLs, cookies and credentials are never indexed. */
final class MovieLibrary {
    static final Provider[] ORDER={Provider.BAIDU,Provider.QUARK,Provider.ALI,Provider.TIANYI};
    static final List<String> CATEGORIES=Arrays.asList("电影","漫剧","电视剧");
    private static final int SCAN_SCHEMA=1;
    static String categoryName(String name){String value=name==null?"":name.trim().replaceFirst("^[（(]","").replaceFirst("[）)]$","").trim();return CATEGORIES.contains(value)?value:"";}
    static final class Movie {
        Provider provider;String id,name,folder,space="",title,year;long size;boolean television,personal;
        final List<String> parents=new ArrayList<>();final List<MediaSpec.Sub> subtitles=new ArrayList<>();
        String key(){return provider.name()+":"+space+":"+id;}
        String playbackKey(){return provider.name()+":"+id;}
        CloudApi.Entry entry(){return new CloudApi.Entry(id,name,false,size);}
        JSONObject json()throws JSONException{
            JSONArray subs=new JSONArray();for(MediaSpec.Sub s:subtitles)subs.put(new JSONObject().put("id",s.url.substring(6)).put("name",s.label));
            return new JSONObject().put("provider",provider.name()).put("id",id).put("name",name).put("folder",folder).put("space",space).put("size",size).put("parents",new JSONArray(parents)).put("subs",subs);
        }
        static Movie read(JSONObject j)throws JSONException{
            Movie m=new Movie();m.provider=Provider.valueOf(j.getString("provider"));m.id=j.getString("id");m.name=j.getString("name");m.folder=j.getString("folder");m.space=j.optString("space");m.size=j.optLong("size");
            JSONArray parents=j.optJSONArray("parents");if(parents!=null)for(int i=0;i<parents.length();i++)m.parents.add(parents.getString(i));
            JSONArray subs=j.optJSONArray("subs");if(subs!=null)for(int i=0;i<subs.length()&&i<8;i++){JSONObject s=subs.getJSONObject(i);m.subtitles.add(new MediaSpec.Sub("cloud:"+s.getString("id"),s.getString("name"),UrlRules.subtitleMime(s.getString("name"))));}
            m.identify();return m;
        }
        String category(){return parents.isEmpty()?"":categoryName(parents.get(0));}
        void identify(){MovieTitle t=MovieTitle.parse(name,parents);title=t.title;year=t.year;String category=category();television=category.isEmpty()?t.television:!category.equals("电影");personal=MovieTitle.personal(name);}
    }
    interface Listing {CloudApi.Page list(String folder,int page)throws Exception;default CloudApi.Page list(String folder,int page,String marker)throws Exception{return list(folder,page);}}
    interface Progress {void update(Provider provider,int pages,int movies);}
    static final class Result {int pages,movies;boolean complete;final Set<String> categories=new LinkedHashSet<>();}
    private static final class Folder {
        final String id,space;final List<String> parents;int page=1;String marker="";
        Folder(String id,String space,List<String> parents){this.id=id;this.space=space;this.parents=new ArrayList<>(parents);}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("space",space).put("parents",new JSONArray(parents)).put("page",page).put("marker",marker);}
        static Folder read(JSONObject j)throws JSONException{List<String> names=new ArrayList<>();JSONArray a=j.getJSONArray("parents");for(int i=0;i<a.length();i++)names.add(a.getString(i));Folder f=new Folder(j.getString("id"),j.optString("space"),names);f.page=Math.max(1,j.optInt("page",1));f.marker=j.optString("marker");return f;}
    }
    private final Context context;private final SharedPreferences store;
    MovieLibrary(Context context){this.context=context.getApplicationContext();store=this.context.getSharedPreferences("movie_library",0);}
    synchronized List<Movie> movies(){
        List<Movie> all=new ArrayList<>();for(Provider p:ORDER)try{JSONArray a=new JSONArray(store.getString("items:"+p.name(),"[]"));for(int i=0;i<a.length();i++){Movie m=Movie.read(a.getJSONObject(i));if(!m.category().isEmpty())all.add(m);}}catch(Exception ignored){}
        return all;
    }
    static void clear(Context c,Provider p){synchronized(AccountSessions.class){c.getSharedPreferences("movie_library",0).edit().remove("items:"+p.name()).remove("queue:"+p.name()).remove("complete:"+p.name()).remove("schema:"+p.name()).apply();}}
    static void clearAll(Context c){synchronized(AccountSessions.class){SharedPreferences pref=c.getSharedPreferences("movie_library",0);long revision=pref.getLong("revision",0)+1;pref.edit().clear().putLong("revision",revision).apply();}}
    boolean incomplete(Provider p){return !store.getBoolean("complete:"+p.name(),false);}
    boolean needsMigration(){for(Provider p:ORDER)if(store.getInt("schema:"+p.name(),0)!=SCAN_SCHEMA)return true;return false;}
    Result scan(Provider p,boolean restart,Progress progress)throws Exception{
        CloudApi api=new CloudApi(p);String space=p==Provider.ALI?context.getSharedPreferences("cloud_preferences",0).getString("ali_space","resource"):"";
        return scan(p,space,restart,new Listing(){public CloudApi.Page list(String folder,int page)throws Exception{return api.list(folder,page);}public CloudApi.Page list(String folder,int page,String marker)throws Exception{return api.list(folder,page,marker);}},progress,40,p==Provider.BAIDU?1200:350);
    }
    Result scan(Provider p,String space,boolean restart,Listing listing,Progress progress,int budget,long delay)throws Exception{
        final int generation=AccountSessions.generation(p);final long revision=store.getLong("revision",0);LinkedHashMap<String,Movie> found=new LinkedHashMap<>();
        restart=restart||store.getInt("schema:"+p.name(),0)!=SCAN_SCHEMA;
        for(Movie m:movies())if(m.provider==p)found.put(m.key(),m);
        ArrayDeque<Folder> queue=new ArrayDeque<>();
        if(!restart)try{JSONArray a=new JSONArray(store.getString("queue:"+p.name(),"[]"));for(int i=0;i<a.length();i++)queue.add(Folder.read(a.getJSONObject(i)));}catch(Exception ignored){}
        if(queue.isEmpty())queue.add(new Folder(p.root(),space,Collections.emptyList()));
        Set<String> visited=new HashSet<>();Result result=new Result();boolean first=true;
        for(Folder f:queue)if(!f.parents.isEmpty())result.categories.add(f.parents.get(0));for(Movie m:found.values())result.categories.add(m.category());
        while(!queue.isEmpty()&&result.pages<budget){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            Folder folder=queue.remove();if(!folder.space.equals(space)){queue.clear();queue.add(new Folder(p.root(),space,Collections.emptyList()));continue;}
            if(!visited.add(folder.id))continue;
            try{
                while(result.pages<budget){
                    if(Thread.currentThread().isInterrupted()||AccountSessions.generation(p)!=generation)throw new InterruptedException();
                    CloudApi.Page page=listing.list(folder.id,folder.page,folder.marker);result.pages++;
                    if(first&&restart){found.clear();first=false;}else first=false;
                    List<CloudApi.Entry> subtitles=new ArrayList<>();for(CloudApi.Entry e:page.entries)if(!e.directory&&UrlRules.subtitle(e.name))subtitles.add(e);
                    for(CloudApi.Entry e:page.entries){
                        if(e.directory&&folder.parents.size()<12){String category=categoryName(e.name);if(folder.parents.isEmpty()&&category.isEmpty())continue;if(folder.parents.isEmpty())result.categories.add(category);List<String> parents=new ArrayList<>(folder.parents);parents.add(folder.parents.isEmpty()?category:e.name);queue.add(new Folder(e.id,space,parents));}
                        else if(!folder.parents.isEmpty()&&!e.directory&&UrlRules.video(e.name)&&found.size()<10000){Movie m=new Movie();m.provider=p;m.space=space;m.id=e.id;m.name=e.name;m.size=e.size;m.folder=folder.id;m.parents.addAll(folder.parents);for(CloudApi.Entry s:subtitles)if(m.subtitles.size()<8)m.subtitles.add(new MediaSpec.Sub("cloud:"+s.id,s.name,UrlRules.subtitleMime(s.name)));m.identify();found.put(m.key(),m);}
                    }
                    if(folder.parents.isEmpty()&&!page.more){List<Folder> roots=new ArrayList<>(queue);Collections.sort(roots,(a,b)->Integer.compare(CATEGORIES.indexOf(a.parents.get(0)),CATEGORIES.indexOf(b.parents.get(0))));queue.clear();queue.addAll(roots);}
                    boolean more=page.more;if(more){folder.page++;folder.marker=page.nextMarker;}if(more&&result.pages>=budget)queue.addFirst(folder);
                    ArrayDeque<Folder> checkpoint=new ArrayDeque<>(queue);if(more&&result.pages<budget)checkpoint.addFirst(folder);save(p,generation,revision,found.values(),checkpoint,false);result.movies=found.size();progress.update(p,result.pages,result.movies);
                    if(!more)break;if(delay>0)Thread.sleep(delay);
                }
            }catch(Exception e){queue.addFirst(folder);save(p,generation,revision,found.values(),queue,false);throw e;}
            if(delay>0&&!queue.isEmpty())Thread.sleep(delay);
        }
        result.complete=queue.isEmpty();result.movies=found.size();save(p,generation,revision,found.values(),queue,result.complete);return result;
    }
    private void save(Provider p,int generation,long revision,Collection<Movie> movies,Collection<Folder> folders,boolean complete)throws Exception{
        JSONArray a=new JSONArray(),q=new JSONArray();for(Movie m:movies)a.put(m.json());for(Folder f:folders)q.put(f.json());
        synchronized(AccountSessions.class){if(AccountSessions.generation(p)!=generation||store.getLong("revision",0)!=revision)throw new InterruptedException();store.edit().putString("items:"+p.name(),a.toString()).putString("queue:"+p.name(),q.toString()).putBoolean("complete:"+p.name(),complete).putInt("schema:"+p.name(),SCAN_SCHEMA).commit();}
    }
    MediaSpec resolve(Movie m,boolean fromStart)throws Exception{
        int generation=AccountSessions.generation(m.provider);CloudApi api=new CloudApi(m.provider);if(m.provider==Provider.ALI)api.selectAliSpace(m.space);
        MediaSpec media=api.resolveSelected(m.entry()).inFolder(m.folder,m.parents);media.matchTitle=m.title+" "+m.year;media.subtitles.addAll(m.subtitles);
        if(generation!=AccountSessions.generation(m.provider))throw new IOException("账号已退出，请重新登录并扫描。");
        SharedPreferences.Editor edit=context.getSharedPreferences("progress",0).edit().putBoolean("compatible:"+Integer.toHexString(media.key.hashCode()),media.compatible);if(fromStart)edit.remove(Integer.toHexString(media.key.hashCode()));edit.apply();return media;
    }
    long position(Movie m){return context.getSharedPreferences("progress",0).getLong(Integer.toHexString(m.playbackKey().hashCode()),0);}
}
