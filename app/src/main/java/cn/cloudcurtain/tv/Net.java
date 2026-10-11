package cn.cloudcurtain.tv;

import android.webkit.CookieManager;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Net {
    public static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient BASE=new OkHttpClient.Builder().connectTimeout(18,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).connectionPool(new ConnectionPool(8,2,TimeUnit.MINUTES)).build();
    static final class ServiceException extends IOException {
        final String serviceCode;final int httpStatus;
        ServiceException(int status,String code){super("网盘接口返回 HTTP "+status+(safeCode(code).isEmpty()?"":"（"+safeCode(code)+"）")+"，请重试或完成官网登录验证。");serviceCode=safeCode(code);httpStatus=status;}
    }
    static final class ResponseFormatException extends IOException {
        final int httpStatus;final String responseKind;
        ResponseFormatException(int status,String body){super("网盘暂未返回文件数据，请在官网完成登录验证后重试。");httpStatus=status;
            String prefix=body==null?"":body.substring(0,Math.min(body.length(),256)).trim().toLowerCase(Locale.ROOT);
            responseKind=prefix.isEmpty()?"EMPTY":prefix.startsWith("<!doctype html")||prefix.startsWith("<html")?"HTML":prefix.startsWith("<")?"XML":"OTHER";
        }
    }
    private static String safeCode(String code){return code!=null&&code.length()<=80&&code.matches("-?[A-Za-z0-9][A-Za-z0-9_.]*")?code:"";}
    public static OkHttpClient client(MediaSpec spec) {
        final Map<Provider,Integer> epochs=new EnumMap<>(Provider.class);for(Provider p:Provider.values())epochs.put(p,AccountSessions.generation(p));
        return BASE.newBuilder()
            .addInterceptor(chain->{
                Response response=chain.proceed(chain.request());String secure=securePlaybackRedirect(spec,response.request().url().toString());
                if(secure==null)return response;
                // Upgrade only an observed provider CDN. Keep the Range and signed query unchanged.
                Request request=response.request().newBuilder().url(secure).build();response.close();return chain.proceed(request);
            })
            .addNetworkInterceptor(chain->{
                Request old=chain.request();String url=old.url().toString();Request.Builder b=old.newBuilder();
                Provider provider=AccountSessions.owner(url);if(provider!=null){if(epochs.get(provider)!=AccountSessions.generation(provider))throw new IOException("账号已退出，请重新登录。");AccountSessions.remember(provider,url);}
                // Network interceptor runs for every redirect. Never propagate credentials to a different host.
                b.removeHeader("Cookie");b.removeHeader("Authorization");b.removeHeader("Api-Key");
                b.removeHeader("X-Signature");b.removeHeader("X-Device-Id");b.removeHeader("sessionKey");
                b.removeHeader("Referer");b.removeHeader("Origin");
                String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null&&!cookie.isEmpty())b.header("Cookie",cookie);
                applyMediaHeaders(b,spec,url);
                Provider mediaOwner=spec!=null&&playbackHeadersHost(spec,url)?Provider.valueOf(spec.provider):null;
                if(mediaOwner!=null&&epochs.get(mediaOwner)!=AccountSessions.generation(mediaOwner))throw new IOException("账号已退出，请重新登录。");
                Response r=chain.proceed(b.build());
                if((provider!=null&&epochs.get(provider)!=AccountSessions.generation(provider))||(mediaOwner!=null&&epochs.get(mediaOwner)!=AccountSessions.generation(mediaOwner))){r.close();throw new IOException("账号已退出，请重新登录。");}
                for(String c:r.headers("Set-Cookie"))CookieManager.getInstance().setCookie(url,c);
                return r;
            }).build();
    }
    static String securePlaybackRedirect(MediaSpec spec,String url){
        if(spec==null||!"TIANYI".equals(spec.provider)||!UrlRules.isWeb(url)||!url.startsWith("http://"))return null;
        okhttp3.HttpUrl parsed=okhttp3.HttpUrl.get(url);if(parsed.port()!=80)return null;
        String secure=parsed.newBuilder().scheme("https").port(443).build().toString();return playbackHeadersHost(spec,secure)?secure:null;
    }
    static void applyMediaHeaders(Request.Builder b,MediaSpec spec,String url){
        if(spec==null)return;
        if(UrlRules.sameOrigin(url,spec.url))for(Map.Entry<String,String> h:spec.headers.entrySet())b.header(h.getKey(),h.getValue());
        if(playbackHeadersHost(spec,url)){
            // Reapply public playback headers to manifests, segments and redirected CDN hosts.
            for(String name:new String[]{"User-Agent","Referer"})if(spec.headers.containsKey(name))b.header(name,spec.headers.get(name));
            if(mediaHost(spec,url)&&"QUARK".equals(spec.provider)&&spec.headers.containsKey("Cookie"))b.header("Cookie",spec.headers.get("Cookie"));
        }
    }
    // Exact CDN hosts observed in authenticated local playback checks; public UA/Referer only.
    // These hosts never receive Cookie, Authorization, API keys or device signatures from this method.
    static boolean playbackHeadersHost(MediaSpec spec,String url){
        if(mediaHost(spec,url))return true;if(!url.startsWith("https://"))return false;
        String host=UrlRules.host(url);
        return "TIANYI".equals(spec.provider)&&(host.equals("qd-gxnnfy-tj-person01.tjtele.com")||host.equals("media-qhxn-fj-home.qh6oss.ctyunxs.cn"))
            ||"ALI".equals(spec.provider)&&host.equals("cdn-vp-cache-bj29.oss-cn-beijing.aliyuncs.com");
    }
    static boolean mediaHost(MediaSpec spec,String url){
        if(!url.startsWith("https://"))return false;String host=UrlRules.host(url);
        if("BAIDU".equals(spec.provider))return UrlRules.domain(host,"baidu.com")||UrlRules.domain(host,"baidupcs.com");
        if("QUARK".equals(spec.provider))return UrlRules.domain(host,"quark.cn")||UrlRules.domain(host,"uc.cn")||UrlRules.domain(host,"ucweb.com");
        if("TIANYI".equals(spec.provider))return UrlRules.domain(host,"189.cn")||UrlRules.domain(host,"21cn.com");
        if("ALI".equals(spec.provider))return UrlRules.domain(host,"alipan.com")||UrlRules.domain(host,"aliyundrive.com")||UrlRules.domain(host,"aliyundrive.net");
        return false;
    }
    public static JSONObject json(String url,JSONObject body,Map<String,String> headers) throws Exception {
        if(!UrlRules.isWeb(url))throw new IOException("地址无效");
        MediaSpec scope=new MediaSpec(url,"");if(headers!=null)scope.headers.putAll(headers);
        Request.Builder b=new Request.Builder().url(url).header("User-Agent",UA).header("Accept","application/json");
        if(body!=null)b.post(RequestBody.create(body.toString(),JSON));
        try(Response r=client(scope).newCall(b.build()).execute()){
            if(!r.isSuccessful()){String code="";try{JSONObject error=new JSONObject(r.peekBody(16384).string());code=error.optString("code",error.optString("errorCode",error.optString("res_code")));}catch(Exception ignored){}throw new ServiceException(r.code(),code);}
            if(r.body()==null)throw new ResponseFormatException(r.code(),"");
            String data=r.body().string();if(data.length()>8000000)throw new IOException("响应过大");
            try{return new JSONObject(data);}catch(JSONException e){
                if(UrlRules.sameOrigin(url,"https://cloud.189.cn"))try{return TianyiResponse.parse(data);}catch(Exception ignored){}
                throw new ResponseFormatException(r.code(),data);
            }
        }
    }
    public static Map<String,String> headers(String... values){Map<String,String> h=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)h.put(values[i],values[i+1]);return h;}
}
