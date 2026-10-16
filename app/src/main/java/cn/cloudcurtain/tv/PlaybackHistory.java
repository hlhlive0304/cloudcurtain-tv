package cn.cloudcurtain.tv;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Bounded, encrypted local history. Native cloud entries retain file references, never signed URLs. */
final class PlaybackHistory {
    static final int LIMIT=100;
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Object LOCK=new Object();
    interface Store {String read();void write(String value)throws Exception;}
    interface Resolver {MediaSpec resolve(Provider provider,String space,CloudApi.Entry entry)throws Exception;}
    private final Context context;private final Store store;
    PlaybackHistory(Context context){this(context,new Store(){
        final Vault vault=new Vault(context,"playback_history");
        public String read(){return vault.get("items");}
        public void write(String value)throws Exception{vault.putDurable("items",value);}
    });}
    PlaybackHistory(Context context,Store store){this.context=context.getApplicationContext();this.store=store;}
    static final class Entry {
        MediaSpec source;long position,duration,updated;boolean completed;
        String id(){return identity(source);}
        long resumePosition(){return completed?0:position;}
        String title(){
            MovieTitle movie=MovieTitle.parse(source.title,source.parents);String label=source.matchTitle.trim();if(label.isEmpty())label=movie.title+(movie.year.isEmpty()?"":" "+movie.year);
            Matcher episode=Pattern.compile("(?i)(?<![A-Za-z0-9])(?:S[0-9]{1,2}E[0-9]{1,3}|EP?[ ._-]?[0-9]{1,3})(?![A-Za-z0-9])|第[一二三四五六七八九十百0-9]+[集话回]").matcher(source.title);
            return (episode.find()?episode.group().toUpperCase(Locale.ROOT)+" · ":"")+label;
        }
        String detail(){String origin=source.provider.isEmpty()?(UrlRules.isWeb(source.url)?"网络视频":"本地视频"):Provider.read(source.provider).shortTitle;return origin+" · "+(completed?"已看完":time(position))+(duration>0?" / "+time(duration):"");}
        JSONObject json()throws JSONException{return new JSONObject().put("source",new JSONObject(source.json())).put("position",position).put("duration",duration).put("updated",updated).put("completed",completed);}
        static Entry read(JSONObject json)throws Exception{Entry e=new Entry();e.source=MediaSpec.parse(json.getJSONObject("source").toString());e.position=Math.max(0,json.optLong("position"));e.duration=Math.max(0,json.optLong("duration"));e.updated=Math.max(0,json.optLong("updated"));e.completed=json.optBoolean("completed");if(!cloud(e.source)&&!validUri(e.source.url))throw new IOException("历史来源不可用");return e;}
    }
    static boolean cloud(MediaSpec source){if(!source.key.startsWith(source.provider+":"))return false;try{Provider.valueOf(source.provider);return true;}catch(Exception e){return false;}}
    private static boolean validUri(String uri){return UrlRules.isWeb(uri)||"content".equals(Uri.parse(uri).getScheme())||"file".equals(Uri.parse(uri).getScheme());}
    static String identity(MediaSpec source){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest((source.provider+"\n"+source.space+"\n"+source.key).getBytes(StandardCharsets.UTF_8));StringBuilder text=new StringBuilder();for(byte b:digest)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static String time(long millis){long seconds=Math.max(0,millis)/1000;return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    static Entry snapshot(MediaSpec media,long position,long duration,boolean completed,long updated)throws Exception {
        Entry entry=new Entry();entry.position=Math.max(0,position);entry.duration=Math.max(0,duration);entry.completed=completed;entry.updated=updated;
        if(cloud(media)){
            MediaSpec ref=new MediaSpec("",media.title).inFolder(media.folder,media.parents);ref.provider=media.provider;ref.key=media.key;ref.space=media.space;ref.matchTitle=media.matchTitle;ref.sourceBytes=media.sourceBytes;
            for(MediaSpec.Sub sub:media.subtitles)if(ref.subtitles.size()<32&&sub.url.startsWith("cloud:"))ref.subtitles.add(new MediaSpec.Sub(sub.url,sub.label,sub.mime));entry.source=ref;
        }else{if(!validUri(media.url))throw new IOException("历史来源不可用");entry.source=MediaSpec.parse(media.json());}
        return entry;
    }
    private static final class State {boolean migrated;final List<Entry> entries=new ArrayList<>();}
    private State read()throws Exception {
        State state=new State();String raw=store.read();if(!raw.isEmpty())try{JSONObject root=new JSONObject(raw);state.migrated=root.optBoolean("migrated");JSONArray rows=root.optJSONArray("items");if(rows!=null)for(int n=0;n<rows.length()&&state.entries.size()<LIMIT;n++)try{state.entries.add(Entry.read(rows.getJSONObject(n)));}catch(Exception ignored){}}catch(JSONException ignored){}
        if(!state.migrated){
            state.migrated=true;Set<String> seen=new HashSet<>();for(Entry e:state.entries)seen.add(e.id());
            for(MovieLibrary.Movie movie:new MovieLibrary(context).movies()){
                if(state.entries.size()>=LIMIT)break;long position=context.getSharedPreferences("progress",0).getLong(Integer.toHexString(movie.playbackKey().hashCode()),0);if(position<=0)continue;
                MediaSpec ref=new MediaSpec("",movie.name).inFolder(movie.folder,movie.parents);ref.provider=movie.provider.name();ref.space=movie.space;ref.key=movie.playbackKey();ref.sourceBytes=movie.size;ref.matchTitle=movie.title+(movie.year.isEmpty()?"":" "+movie.year);ref.subtitles.addAll(movie.subtitles);
                Entry older=snapshot(ref,position,0,false,0);if(seen.add(older.id()))state.entries.add(older);
            }
            if(!state.entries.isEmpty())write(state);
        }
        return state;
    }
    private void write(State state)throws Exception {JSONArray rows=new JSONArray();for(Entry e:state.entries)rows.put(e.json());store.write(new JSONObject().put("schema",1).put("migrated",true).put("items",rows).toString());}
    List<Entry> entries()throws Exception {synchronized(LOCK){State state=read();Collections.sort(state.entries,(a,b)->Long.compare(b.updated,a.updated));return new ArrayList<>(state.entries);}}
    void save(Entry entry)throws Exception {synchronized(LOCK){State state=read();for(Iterator<Entry> it=state.entries.iterator();it.hasNext();)if(it.next().id().equals(entry.id()))it.remove();state.entries.add(0,entry);Collections.sort(state.entries,(a,b)->Long.compare(b.updated,a.updated));while(state.entries.size()>LIMIT)state.entries.remove(state.entries.size()-1);write(state);}}
    void remove(String id)throws Exception{synchronized(LOCK){State state=read();for(Iterator<Entry> it=state.entries.iterator();it.hasNext();)if(it.next().id().equals(id))it.remove();write(state);}}
    void clear()throws Exception{synchronized(LOCK){State state=new State();state.migrated=true;write(state);}}
    void clearProvider(Provider provider)throws Exception{synchronized(LOCK){State state=read();for(Iterator<Entry> it=state.entries.iterator();it.hasNext();)if(it.next().source.provider.equals(provider.name()))it.remove();write(state);}}
    static MediaSpec resolve(Entry entry)throws Exception{return resolve(entry,(provider,space,file)->{CloudApi api=new CloudApi(provider);if(provider==Provider.ALI)api.selectAliSpace(space);return api.resolveSelected(file);});}
    static MediaSpec resolve(Entry entry,Resolver resolver)throws Exception {
        MediaSpec source=entry.source;if(!cloud(source))return MediaSpec.parse(source.json());
        MediaSpec fresh=resolver.resolve(Provider.valueOf(source.provider),source.space,new CloudApi.Entry(source.key.substring(source.provider.length()+1),source.title,false,source.sourceBytes)).inFolder(source.folder,source.parents);
        fresh.matchTitle=source.matchTitle;fresh.subtitles.addAll(source.subtitles);return fresh;
    }
}
