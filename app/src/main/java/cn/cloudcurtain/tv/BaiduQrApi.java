package cn.cloudcurtain.tv;

import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** The public Baidu website QR protocol. Does not depend on a DOM, JS bridge, or NAS. */
final class BaiduQrApi implements AutoCloseable {
    static final String AUTH="https://passport.baidu.com", HOME="https://pan.baidu.com/disk/main";
    enum State { WAITING, SCANNED, CONFIRMED, CANCELLED, EXPIRED }
    interface Transport { byte[] get(String url)throws Exception; }
    private final Transport transport;private final Http http;private String sign,ticket;private boolean complete;
    private final String gid=UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    BaiduQrApi(){http=new Http();transport=http;}
    BaiduQrApi(Transport transport){this.transport=transport;http=null;}
    static boolean allowed(String url){return UrlRules.sameOrigin(AUTH,url)||UrlRules.sameOrigin("https://pan.baidu.com",url);}
    static String endpoint(String path,String... pairs){HttpUrl.Builder b=HttpUrl.get(AUTH+path).newBuilder();for(int i=0;i<pairs.length;i+=2)b.addQueryParameter(pairs[i],pairs[i+1]);return b.build().toString();}
    static JSONObject parse(byte[] bytes)throws Exception{
        String text=new String(bytes,"UTF-8").trim();
        if(text.startsWith("cloudCurtainQr(")){if(!text.endsWith(");")&&!text.endsWith(")"))throw new IOException("百度扫码响应不完整。");int end=text.lastIndexOf(')');text=text.substring("cloudCurtainQr(".length(),end);}
        org.json.JSONTokener parser=new org.json.JSONTokener(text);Object object=parser.nextValue();
        if(!(object instanceof JSONObject)||parser.nextClean()!=0)throw new IOException("百度扫码响应格式不正确。");
        return (JSONObject)object;
    }
    byte[] start()throws Exception{
        JSONObject response=parse(transport.get(endpoint("/v2/api/getqrcode","lp","pc","qrloginfrom","pc","tpl","netdisk","apiver","v3","gid",gid)));
        if(response.optInt("errno",-1)!=0)throw new IOException("百度暂时无法创建二维码（"+response.optInt("errno",-1)+"），请刷新或使用官网登录。");
        sign=response.optString("sign","");String image=response.optString("imgurl","");
        if(sign.isEmpty()||image.isEmpty())throw new IOException("百度未返回完整的二维码。");
        if(image.startsWith("//"))image="https:"+image;else if(!image.contains("://"))image="https://"+image;
        if(!UrlRules.sameOrigin(AUTH,image))throw new IOException("百度二维码图片来源不匹配。");
        return transport.get(image);
    }
    static JSONObject channel(JSONObject response)throws Exception{
        Object value=response.opt("channel_v");if(value instanceof JSONObject)return (JSONObject)value;
        if(value instanceof String&&!((String)value).isEmpty())return new JSONObject((String)value);return new JSONObject();
    }
    static State state(JSONObject response)throws Exception{
        int errno=response.optInt("errno",Integer.MIN_VALUE);
        if(errno==1)return State.WAITING;
        if(errno!=0)throw new IOException("百度扫码状态请求失败（"+(errno==Integer.MIN_VALUE?"无状态码":errno)+"），请刷新重试。");
        JSONObject c=channel(response);int status=c.optInt("status",Integer.MIN_VALUE);
        if(status==0){if(c.optString("v","").isEmpty())throw new IOException("百度未返回登录确认凭证。");return State.CONFIRMED;}
        if(status==1)return State.SCANNED;if(status==2)return State.CANCELLED;
        if(status==Integer.MIN_VALUE&&c.length()==0)return State.WAITING;
        throw new IOException("百度返回未知扫码状态，请刷新重试。");
    }
    State poll()throws Exception{
        if(sign==null)throw new IOException("请先获取二维码。");
        JSONObject response=parse(transport.get(endpoint("/channel/unicast","channel_id",sign,"gid",gid,"tpl","netdisk","_sdkFrom","1","apiver","v3")));
        State state=state(response);if(state==State.CONFIRMED)ticket=channel(response).getString("v");return state;
    }
    static void validateExchange(JSONObject response)throws IOException{
        JSONObject info=response.optJSONObject("errInfo");int error=info==null?Integer.MIN_VALUE:info.optInt("no",Integer.MIN_VALUE);
        if(error!=0)throw new IOException("百度登录尚未完成（"+(error==Integer.MIN_VALUE?"无状态码":error)+"）。请进入官网完成验证；本次不会标记为已登录。");
    }
    void complete()throws Exception{
        if(ticket==null)throw new IOException("手机尚未确认本次登录。");
        JSONObject response=parse(transport.get(endpoint("/v3/login/main/qrbdusslogin","v",Long.toString(System.currentTimeMillis()),
            "bduss",ticket,"u",URLEncoder.encode(HOME,"UTF-8"),"loginVersion","v4","qrcode","1","tpl","netdisk","apiver","v3","callback","cloudCurtainQr")));
        validateExchange(response);
        JSONObject data=response.optJSONObject("data");
        String landing=data==null?"":data.optString("u","");
        if(!landing.isEmpty()){
            for(int i=0;i<2&&!landing.startsWith("https://");i++)try{landing=java.net.URLDecoder.decode(landing,"UTF-8");}catch(Exception ignored){break;}
            if(landing.startsWith("https://")&&allowed(landing))transport.get(landing);
        }
        ticket=null;complete=true;
    }
    List<Cookie> cookies(){return !complete||http==null?Collections.emptyList():http.snapshot();}
    public void close(){ticket=null;sign=null;if(http!=null)http.close();}
    private static final class Http implements Transport,AutoCloseable {
        private final List<Cookie> cookies=new ArrayList<>();private final OkHttpClient client;private volatile boolean closed;
        Http(){client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(38,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).cookieJar(new CookieJar(){
                public void saveFromResponse(HttpUrl url,List<Cookie> list){synchronized(cookies){for(Cookie item:list){Iterator<Cookie> it=cookies.iterator();while(it.hasNext()){Cookie old=it.next();if(old.name().equals(item.name())&&old.domain().equals(item.domain())&&old.path().equals(item.path()))it.remove();}cookies.add(item);}}}
                public List<Cookie> loadForRequest(HttpUrl url){List<Cookie> list=new ArrayList<>();synchronized(cookies){for(Cookie item:cookies)if(item.matches(url)&&item.expiresAt()>System.currentTimeMillis())list.add(item);}return list;}
            }).build();}
        List<Cookie> snapshot(){synchronized(cookies){return new ArrayList<>(cookies);}}
        public byte[] get(String url)throws Exception{
            for(int redirects=0;redirects<8;redirects++){
            if(closed)throw new IOException("扫码已取消。");if(!allowed(url))throw new IOException("百度登录请求来源不匹配。");
            try(Response response=client.newCall(new Request.Builder().url(url).header("User-Agent",Net.UA).header("Referer","https://pan.baidu.com/").build()).execute()){
                if(response.isRedirect()){String location=response.header("Location");HttpUrl next=location==null?null:response.request().url().resolve(location);if(next==null||!allowed(next.toString()))throw new IOException("百度登录跳转来源不匹配。");url=next.toString();continue;}
                if(!response.isSuccessful()||response.body()==null)throw new IOException("百度接口返回 HTTP "+response.code()+"，请重试。");
                try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>1024*1024)throw new IOException("百度扫码响应过大。");out.write(buf,0,n);}return out.toByteArray();
                }
            }
            }throw new IOException("百度登录跳转次数过多。");
        }
        public synchronized void close(){if(closed)return;closed=true;QrHttpCleanup.release(client);}
    }
}
