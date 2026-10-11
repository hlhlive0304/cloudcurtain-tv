package cn.cloudcurtain.tv;

import okhttp3.*;
import org.json.JSONObject;
import android.graphics.Bitmap;
import android.util.Base64;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Public Alipan website QR flow. Tokens remain in this application's encrypted store. */
final class AliQrApi implements AutoCloseable {
    static final String AUTH="https://passport.aliyundrive.com";
    static final String LOGIN=AUTH+"/mini_login.htm?appName=aliyun_drive&fromSite=52&appEntrance=web&lang=zh_CN";
    private final Map<String,String> loginForm=new LinkedHashMap<>();
    enum State { WAITING, SCANNED, CONFIRMED, EXPIRED, CANCELLED }
    interface Transport { JSONObject request(String url,Map<String,String> form)throws Exception; }
    private final OkHttpClient client;private final Transport transport;private String t,ck;private JSONObject token;private volatile boolean closed;
    AliQrApi(){client=new OkHttpClient.Builder().cookieJar(new LoginCookieJar()).connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).followRedirects(false).build();transport=(url,form)->{
        if(closed)throw new IOException("本次扫码已取消。");
        Request.Builder b=new Request.Builder().url(url).header("User-Agent",Net.UA).header("Referer",LOGIN).header("Origin",AUTH);
        if(form!=null){FormBody.Builder f=new FormBody.Builder();for(Map.Entry<String,String> p:form.entrySet())f.add(p.getKey(),p.getValue());b.post(f.build());}
        try(Response r=client.newCall(b.build()).execute()){if(!r.isSuccessful()||r.body()==null)throw new IOException("阿里扫码接口 HTTP "+r.code());return new JSONObject(r.body().string());}
    };}
    AliQrApi(Transport transport){this.transport=transport;client=null;}
    static JSONObject data(JSONObject response)throws IOException{
        JSONObject c=response.optJSONObject("content");JSONObject d=c==null?null:c.optJSONObject("data");
        if(c==null||!c.optBoolean("success",false)||d==null)throw new IOException("阿里暂未返回扫码数据，请刷新或使用官网登录。");return d;
    }
    byte[] start()throws Exception{
        if(closed)throw new IOException("本次扫码已取消。");
        if(client!=null){
            try(Response r=client.newCall(new Request.Builder().url(LOGIN).header("User-Agent",Net.UA).build()).execute()){
                if(!r.isSuccessful()||r.body()==null)throw new IOException("阿里登录初始化失败。");
                loginForm.clear();loginForm.putAll(parseLoginForm(r.body().string()));
            }
        }
        HttpUrl.Builder url=HttpUrl.get(AUTH+"/newlogin/qrcode/generate.do").newBuilder();for(Map.Entry<String,String> e:params().entrySet())url.addQueryParameter(e.getKey(),e.getValue());
        JSONObject d=data(transport.request(url.build().toString(),null));t=d.optString("t");ck=d.optString("ck");String text=d.optString("codeContent");
        if(t.isEmpty()||ck.isEmpty()||!Provider.ALI.allows(text))throw new IOException("阿里二维码数据不完整或来源不匹配。");
        BitMatrix matrix=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,480,480,Collections.singletonMap(EncodeHintType.MARGIN,4));
        Bitmap bitmap=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);int[] pixels=new int[480*480];for(int y=0;y<480;y++)for(int x=0;x<480;x++)pixels[y*480+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        bitmap.setPixels(pixels,0,480,0,0,480,480);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray();
    }
    static Map<String,String> parseLoginForm(String html)throws Exception{
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("window\\.viewData\\s*=\\s*(\\{[^\\r\\n]+\\})\\s*;").matcher(html);
        if(!match.find())throw new IOException("阿里登录页面已变化，请使用官网完成验证。");
        JSONObject form=new JSONObject(match.group(1)).getJSONObject("loginFormData");Map<String,String> out=new LinkedHashMap<>();
        // Only parameters supplied by this attempt's official login page; never execute page scripts.
        for(String key:new String[]{"appName","fromSite","appEntrance","_csrf_token","umidToken","hsiz","bizParams","mainPage","isMobile","lang","returnUrl"})if(form.has(key))out.put(key,form.getString(key));
        if(!"aliyun_drive".equals(out.get("appName"))||!"52".equals(out.get("fromSite")))throw new IOException("阿里登录页面来源不匹配。");
        return out;
    }
    private Map<String,String> params(){Map<String,String> p=Net.headers("appName","aliyun_drive","fromSite","52","appEntrance","web","isMobile","false","lang","zh_CN","returnUrl","","bizParams","taobaoBizLoginFrom=web");p.putAll(loginForm);return p;}
    State poll()throws Exception{
        if(closed)throw new IOException("本次扫码已取消。");
        if(t==null)throw new IOException("请先获取二维码。");Map<String,String> form=params();form.put("t",t);form.put("ck",ck);
        JSONObject d=data(transport.request(AUTH+"/newlogin/qrcode/query.do?appName=aliyun_drive&fromSite=52",form));String state=d.optString("qrCodeStatus");if(Arrays.asList("NEW","SCANED","SCANNED","CONFIRMED","EXPIRED","CANCELED").contains(state))android.util.Log.i("CloudLoginState","ALI / "+state);
        if(state.equals("NEW"))return State.WAITING;if(state.equals("SCANED")||state.equals("SCANNED"))return State.SCANNED;
        if(state.equals("EXPIRED"))return State.EXPIRED;if(state.equals("CANCELED"))return State.CANCELLED;
        if(state.equals("CONFIRMED")){
            String ext=d.optString("bizExt");JSONObject result=new JSONObject(new String(Base64.decode(ext,Base64.DEFAULT),"UTF-8"));token=result.optJSONObject("pds_login_result");
            if(token==null||token.optString("access_token").isEmpty()||token.optString("refresh_token").isEmpty())throw new IOException("阿里未返回完整登录凭证，请到官网完成验证。");
            return State.CONFIRMED;
        }
        throw new IOException("阿里登录需要额外验证，请选择官网登录。");
    }
    JSONObject token()throws IOException{if(token==null)throw new IOException("手机尚未确认登录。");return token;}
    public synchronized void close(){if(closed)return;closed=true;token=null;t=null;ck=null;if(client!=null)QrHttpCleanup.release(client);}
}
