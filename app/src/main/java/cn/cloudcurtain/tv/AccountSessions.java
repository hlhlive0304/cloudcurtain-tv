package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.webkit.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;

/** Per-provider removal: never uses removeAllCookies or deleteAllData. */
final class AccountSessions {
    private static final EnumMap<Provider,Integer> generations=new EnumMap<>(Provider.class);
    private static final Map<WebView,Provider> views=new WeakHashMap<>();
    static synchronized void register(WebView view,Provider provider){views.put(view,provider);}
    static synchronized void unregister(WebView view){views.remove(view);}
    static synchronized int generation(Provider p){return generations.containsKey(p)?generations.get(p):0;}
    static synchronized void invalidate(Provider p){generations.put(p,generation(p)+1);}
    static Provider owner(String url){for(Provider p:Provider.values())if(p.allows(url))return p;return null;}
    static void remember(Provider p,String url){
        if(p==null||!p.allows(url)||CloudApp.context()==null)return;
        HttpUrl u=HttpUrl.parse(url);if(u==null)return;String scope=u.scheme()+"://"+u.host()+u.encodedPath();
        android.content.SharedPreferences pref=CloudApp.context().getSharedPreferences("cloud_scopes",0);
        synchronized(AccountSessions.class){Set<String> all=new HashSet<>(pref.getStringSet(p.name(),Collections.emptySet()));if(all.size()<512){all.add(scope);pref.edit().putStringSet(p.name(),all).apply();}}
    }
    static Set<String> scopes(Provider p){
        Set<String> all=new HashSet<>();if(CloudApp.context()!=null)all.addAll(CloudApp.context().getSharedPreferences("cloud_scopes",0).getStringSet(p.name(),Collections.emptySet()));
        all.add(p.origin);all.add(p.loginUrl);all.addAll(LoginQr.origins(p));
        switch(p){
            case QUARK:all.add("https://drive.quark.cn/");all.add("https://quark.cn/");all.add("https://uop.quark.cn/cas/");break;
            case TIANYI:all.add("https://open.e.189.cn/api/logbox/oauth2/");all.add("https://189.cn/");break;
            case BAIDU:all.add("https://baidu.com/");all.add("https://passport.baidu.com/v3/login/main/");break;
            case ALI:all.add("https://api.aliyundrive.com/");all.add("https://alipan.com/");all.add("https://aliyundrive.com/");break;
        }return all;
    }
    static Map<String,Set<String>> cookiePaths(Provider p){
        Map<String,Set<String>> out=new HashMap<>();for(String scope:scopes(p)){HttpUrl u=HttpUrl.parse(scope);if(u==null||!p.allows(scope))continue;String path=u.encodedPath();Set<String> paths=out.get(u.host());if(paths==null){paths=new HashSet<>();out.put(u.host(),paths);}paths.add("/");paths.add(path);while(path.lastIndexOf('/')>0){path=path.substring(0,path.lastIndexOf('/'));paths.add(path);paths.add(path+"/");}}
        return out;
    }
    interface Cookies { String getCookie(String url);void setCookie(String url,String cookie,ValueCallback<Boolean> done);void flush(); }
    static void clear(Provider p,Runnable done){CookieManager cm=CookieManager.getInstance();clear(p,new Cookies(){public String getCookie(String u){return cm.getCookie(u);}public void setCookie(String u,String c,ValueCallback<Boolean> d){cm.setCookie(u,c,d);}public void flush(){cm.flush();}},done);}
    static void clear(Provider p,Cookies cm,Runnable done){
        synchronized(AccountSessions.class){for(Map.Entry<WebView,Provider> e:views.entrySet())if(e.getValue()==p){e.getKey().stopLoading();e.getKey().loadUrl("about:blank");e.getKey().clearHistory();}}
        invalidate(p);MovieLibrary.clear(CloudApp.context(),p);Tmdb.clearProvider(CloudApp.context(),p);PlaybackHistory.IO.execute(()->{try{new PlaybackHistory(CloudApp.context()).clearProvider(p);}catch(Exception error){LoginDiagnostics.record(CloudApp.context(),p,"清除播放记录",error);}});android.content.SharedPreferences favorites=CloudApp.context().getSharedPreferences("movie_favorites",0);Set<String> remaining=new HashSet<>(favorites.getStringSet("keys",Collections.emptySet()));for(Iterator<String> it=remaining.iterator();it.hasNext();)if(it.next().startsWith(p.name()+":"))it.remove();favorites.edit().putStringSet("keys",remaining).apply();Map<String,Set<String>> hosts=cookiePaths(p);Map<String,String> deletions=new LinkedHashMap<>();
        for(Map.Entry<String,Set<String>> host:hosts.entrySet())for(String path:host.getValue()){
            String url="https://"+host.getKey()+path;String raw=cm.getCookie(url);if(raw==null)continue;
            for(String part:raw.split(";")){int at=part.indexOf('=');if(at<=0)continue;String name=part.substring(0,at).trim();
                String suffix="=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path="+path+"; Secure; HttpOnly";
                deletions.put(url+"\n"+name+suffix,url);
                String domain=host.getKey();while(domain.contains(".")&&p.allows("https://"+domain+"/")){deletions.put(url+"\n"+name+suffix+"; Domain="+domain,url);deletions.put(url+"\n"+name+suffix+"; Domain=."+domain,url);domain=domain.substring(domain.indexOf('.')+1);}
            }
        }
        Runnable finish=()->{cm.flush();WebStorage storage=WebStorage.getInstance();for(String scope:scopes(p)){HttpUrl u=HttpUrl.parse(scope);if(u!=null&&p.allows(scope))storage.deleteOrigin(u.scheme()+"://"+u.host());}if(p==Provider.TIANYI)new Vault(CloudApp.context()).remove("tianyi_session");if(p==Provider.ALI){Vault vault=new Vault(CloudApp.context());vault.remove("ali_token");vault.remove("ali_device");}done.run();};
        if(deletions.isEmpty()){finish.run();return;}AtomicInteger pending=new AtomicInteger(deletions.size());for(Map.Entry<String,String> e:deletions.entrySet())cm.setCookie(e.getValue(),e.getKey().substring(e.getKey().indexOf('\n')+1),ok->{if(pending.decrementAndGet()==0)finish.run();});
    }
    static void ask(Activity a,Provider p,Runnable before){new AlertDialog.Builder(a).setTitle("退出"+p.title+"？").setMessage("清除本电视保存的该网盘登录，随后可扫码更换账号。网盘文件、其他网盘和字幕设置会保留。")
        .setNegativeButton("取消",null).setPositiveButton("退出并更换账号",(d,n)->{before.run();clear(p,()->{if(a.isFinishing())return;a.startActivity(new Intent(a,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("switch_provider",p.name()));a.finish();});}).show();}
}
