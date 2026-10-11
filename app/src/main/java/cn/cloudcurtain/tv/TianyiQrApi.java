package cn.cloudcurtain.tv;

import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Uses the current Tianyi website's QR protocol, without a WebView or another app's credentials. */
final class TianyiQrApi implements AutoCloseable {
    static final String AUTH="https://open.e.189.cn";
    static final String START="https://cloud.189.cn/api/portal/loginUrl.action?redirectURL=https%3A%2F%2Fcloud.189.cn%2Fweb%2Fmain";
    enum State { WAITING, SCANNED, CONFIRMED, EXPIRED, VERIFICATION_REQUIRED }
    static final class Reply {
        final String url;final byte[] data;
        Reply(String url,byte[] data){this.url=url;this.data=data;}
        JSONObject json()throws Exception{return new JSONObject(new String(data,"UTF-8"));}
    }
    interface Transport { Reply request(String url,Map<String,String> form,Map<String,String> headers)throws Exception; }
    private final Transport transport;private final Http http;
    private JSONObject config,code;private String lt,reqId,loginReqId,appId,redirect,sessionKey;
    TianyiQrApi(){http=new Http();transport=http;}
    TianyiQrApi(Transport transport){this.transport=transport;http=null;}
    static boolean allowed(String url){return UrlRules.sameOrigin(AUTH,url)||UrlRules.sameOrigin("https://cloud.189.cn",url);}
    static String required(JSONObject object,String key)throws IOException{
        String value=object.optString(key,"");if(value.isEmpty())throw new IOException("天翼接口缺少 "+key+"，请重试或使用官网登录。");return value;
    }
    private Map<String,String> headers(){return Net.headers("lt",lt,"REQID",reqId,"Referer",AUTH+"/","Origin",AUTH);}
    byte[] start()throws Exception{
        Reply page=transport.request(START,null,Collections.emptyMap());
        if(!allowed(page.url))throw new IOException("天翼登录跳转来源不匹配。");
        HttpUrl url=HttpUrl.get(page.url);lt=url.queryParameter("lt");reqId=url.queryParameter("reqId");loginReqId=reqId;appId=url.queryParameter("appId");
        if(lt==null||reqId==null||appId==null)throw new IOException("天翼未返回扫码初始化参数，请重试。");
        JSONObject conf=transport.request(AUTH+"/api/logbox/oauth2/appConf.do",Net.headers("version","2.0","appKey",appId),headers()).json();
        if(conf.optInt("result",-1)!=0||conf.optJSONObject("data")==null)throw new IOException("天翼登录初始化失败，请刷新重试。");
        config=conf.getJSONObject("data");appId=required(config,"appKey");reqId=required(config,"reqId");required(config,"returnUrl");required(config,"paramId");
        Map<String,String> uuidHeaders=headers();uuidHeaders.put("REQID",loginReqId);
        code=transport.request(AUTH+"/api/logbox/oauth2/getUUID.do",Net.headers("appId",appId),uuidHeaders).json();
        if(code.optInt("result",-1)!=0)throw new IOException("天翼暂时不能创建二维码，请刷新重试。");
        required(code,"uuid");required(code,"encryuuid");String encoded=required(code,"encodeuuid");
        // encodeuuid is already URL-encoded by Tianyi. Encoding '%' again makes
        // the image refer to a different ticket while polling the original one.
        String image=imageUrl(encoded,reqId);
        return transport.request(image,null,headers()).data;
    }
    static String imageUrl(String encoded,String requestId){return HttpUrl.get(AUTH+"/api/logbox/oauth2/image.do").newBuilder().addEncodedQueryParameter("uuid",encoded).addQueryParameter("REQID",requestId).build().toString();}
    static State state(JSONObject result)throws IOException{
        int status=result.optInt("status",Integer.MIN_VALUE);
        if(result.optInt("result",0)==-20099||status==-11001)return State.EXPIRED;
        if(status==-106)return State.WAITING;
        if(status==-11002)return State.SCANNED;
        if(status==-134)return State.VERIFICATION_REQUIRED;
        if(status==0){String url=required(result,"redirectUrl");if(!allowed(url))throw new IOException("天翼确认跳转地址不属于官网。");return State.CONFIRMED;}
        throw new IOException("天翼扫码状态异常（"+(status==Integer.MIN_VALUE?"无状态码":status)+"），请刷新或到官网完成验证。");
    }
    State poll()throws Exception{
        if(code==null||config==null)throw new IOException("请先获取二维码。");
        String date=new SimpleDateFormat("yyyy-MM-ddhh:mm:ss",Locale.US).format(new Date())+new Random().nextInt(24);
        Map<String,String> form=Net.headers("appId",appId,"encryuuid",required(code,"encryuuid"),"uuid",required(code,"uuid"),
            "date",date,"returnUrl",URLEncoder.encode(required(config,"returnUrl"),"UTF-8"),"clientType","1",
            "timeStamp",Long.toString(System.currentTimeMillis()),"cb_SaveName","3","isOauth2",config.optString("isOauth2","false"),
            "state",config.optString("state",""),"paramId",required(config,"paramId"));
        JSONObject result=transport.request(AUTH+"/api/logbox/oauth2/qrcodeLoginState.do",form,headers()).json();
        State state=state(result);if(state==State.CONFIRMED)redirect=result.getString("redirectUrl");return state;
    }
    void complete()throws Exception{
        if(redirect==null||!allowed(redirect))throw new IOException("手机尚未确认本次登录。");
        Reply landing=transport.request(redirect,null,Collections.emptyMap());
        // The official main page performs this session handoff before loading files.
        // Following HTTP redirects alone does not execute that JavaScript request.
        if(!allowed(landing.url))throw new IOException("天翼登录跳转来源不匹配。");
        String key=HttpUrl.get(landing.url).queryParameter("sessionKey");
        HttpUrl.Builder url=HttpUrl.get("https://cloud.189.cn/api/portal/v2/getUserBriefInfo.action").newBuilder().addQueryParameter("noCache",Long.toString(System.currentTimeMillis()));
        if(key!=null&&!key.isEmpty())url.addQueryParameter("sessionKey",key);
        JSONObject session=transport.request(url.build().toString(),null,Net.headers("Accept","application/json;charset=UTF-8","Referer","https://cloud.189.cn/web/main","Origin","https://cloud.189.cn")).json();
        sessionKey=TianyiSession.portalKey(session);
    }
    String sessionKey(){return sessionKey==null?"":sessionKey;} List<Cookie> cookies(){return http==null?Collections.emptyList():http.snapshot();}
    @Override public void close(){if(http!=null)http.close();}

    private static final class Http implements Transport,AutoCloseable {
        final List<Cookie> cookies=new ArrayList<>();final OkHttpClient client;volatile boolean closed;
        Http(){client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).cookieJar(new CookieJar(){
                @Override public void saveFromResponse(HttpUrl url,List<Cookie> added){synchronized(cookies){for(Cookie next:added){Iterator<Cookie> it=cookies.iterator();while(it.hasNext()){Cookie old=it.next();if(old.name().equals(next.name())&&old.domain().equals(next.domain())&&old.path().equals(next.path()))it.remove();}cookies.add(next);}}}
                @Override public List<Cookie> loadForRequest(HttpUrl url){List<Cookie> out=new ArrayList<>();synchronized(cookies){for(Cookie cookie:cookies)if(cookie.matches(url)&&cookie.expiresAt()>System.currentTimeMillis())out.add(cookie);}return out;}
            }).build();}
        List<Cookie> snapshot(){synchronized(cookies){return new ArrayList<>(cookies);}}
        public Reply request(String url,Map<String,String> form,Map<String,String> headers)throws Exception{
            for(int redirects=0;redirects<8;redirects++){
                if(closed)throw new IOException("本次扫码已取消。");if(!allowed(url))throw new IOException("已停止非天翼官网的登录跳转。");
                Request.Builder builder=new Request.Builder().url(url).header("User-Agent",Net.UA);
                for(Map.Entry<String,String> h:headers.entrySet())builder.header(h.getKey(),h.getValue());
                if(form!=null){FormBody.Builder body=new FormBody.Builder();for(Map.Entry<String,String> pair:form.entrySet())body.add(pair.getKey(),pair.getValue());builder.post(body.build());}
                try(Response response=client.newCall(builder.build()).execute()){
                    if(response.isRedirect()){
                        String location=response.header("Location");HttpUrl next=location==null?null:response.request().url().resolve(location);
                        if(next==null)throw new IOException("天翼返回了无效跳转。");url=next.toString();headers=Collections.emptyMap();
                        if(response.code()!=307&&response.code()!=308)form=null;continue;
                    }
                    if(!response.isSuccessful()||response.body()==null)throw new IOException("天翼接口返回 HTTP "+response.code()+"，请稍后重试。");
                    try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("天翼响应过大。");out.write(bytes,0,n);}return new Reply(url,out.toByteArray());
                    }
                }
            }throw new IOException("天翼登录跳转次数过多。");
        }
        public synchronized void close(){if(closed)return;closed=true;QrHttpCleanup.release(client);}
    }
}
