package cn.cloudcurtain.tv;

import android.os.Bundle;
import android.webkit.CookieManager;
import okhttp3.*;
import org.json.JSONObject;
import java.util.*;

/** Read-only comparison with the authenticated official portal protocol. No credential output. */
final class TianyiPortalLiveCheck {
    static void run(Bundle result)throws Exception {
        String origin="https://cloud.189.cn", list=origin+"/api/open/file/listFiles.action?folderId=-11&pageNum=1&pageSize=60&mediaType=0&iconOption=5&orderBy=filename&descending=false";
        Map<String,String> browser=Net.headers("User-Agent",Net.UA,"Accept","application/json;charset=UTF-8","Referer",origin+"/web/main","Origin",origin,"Sign-Type","1","Browser-Id",UUID.randomUUID().toString().replace("-",""));
        JSONObject fresh=Net.json(origin+"/api/portal/v2/getUserBriefInfo.action?noCache="+System.currentTimeMillis(),null,browser);String key=fresh.optString("sessionKey","");
        result.putBoolean("portal_authenticated",fresh.optInt("res_code",-1)==0&&!key.isEmpty());
        String[] variants={"website_cookies","session_header","session_query","capital_header","portal_list"};boolean any=false;
        for(int i=0;i<variants.length;i++) {
            Map<String,String> headers=new LinkedHashMap<>(browser);String url=list+"&noCache="+System.nanoTime();
            if(i==1)headers.put("sessionKey",key);if(i==2)url+="&sessionKey="+android.net.Uri.encode(key);if(i==3)headers.put("SessionKey",key);
            if(i==4)url=url.replace("/api/open/file/listFiles.action","/api/portal/listFiles.action");
            try {JSONObject data=Net.json(url,null,headers);boolean loaded=data.optJSONObject("fileListAO")!=null;result.putBoolean(variants[i]+"_loaded",loaded);result.putString(variants[i]+"_keys",CloudLiveInstrumentation.keys(data));any|=loaded;}
            catch(Net.ServiceException e){result.putInt(variants[i]+"_http",e.httpStatus);result.putString(variants[i]+"_code",e.serviceCode);}
        }
        // An isolated jar applies each Set-Cookie synchronously, as the browser does.
        List<Cookie> cookies=new ArrayList<>();HttpUrl host=HttpUrl.get(origin+"/");String raw=CookieManager.getInstance().getCookie(origin+"/");
        if(raw!=null)for(String piece:raw.split(";")){Cookie c=Cookie.parse(host,piece.trim());if(c!=null)cookies.add(c);}
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(18,java.util.concurrent.TimeUnit.SECONDS).readTimeout(30,java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).cookieJar(new CookieJar(){
            public void saveFromResponse(HttpUrl u,List<Cookie> added){for(Cookie next:added){cookies.removeIf(old->old.name().equals(next.name())&&old.domain().equals(next.domain())&&old.path().equals(next.path()));cookies.add(next);}}
            public List<Cookie> loadForRequest(HttpUrl u){List<Cookie> matched=new ArrayList<>();for(Cookie c:cookies)if(c.matches(u)&&c.expiresAt()>System.currentTimeMillis())matched.add(c);return matched;}
        }).build();
        Request.Builder brief=new Request.Builder().url(origin+"/api/portal/v2/getUserBriefInfo.action?noCache="+System.nanoTime());for(Map.Entry<String,String> h:browser.entrySet())brief.header(h.getKey(),h.getValue());
        try(Response r=client.newCall(brief.build()).execute()){result.putInt("jar_portal_http",r.code());JSONObject data=new JSONObject(r.body().string());key=data.optString("sessionKey","");List<String> set=new ArrayList<>();for(String value:r.headers("Set-Cookie")){int at=value.indexOf('=');if(at>0)set.add(value.substring(0,at));}result.putString("jar_cookie_names",String.join(",",set));}
        Request.Builder files=new Request.Builder().url(list+"&noCache="+System.nanoTime());for(Map.Entry<String,String> h:browser.entrySet())files.header(h.getKey(),h.getValue());
        try(Response r=client.newCall(files.build()).execute()){JSONObject data=new JSONObject(r.body().string());boolean loaded=data.optJSONObject("fileListAO")!=null;result.putInt("jar_list_http",r.code());result.putBoolean("jar_list_loaded",loaded);String error=data.optString("errorCode",data.optString("res_code"));if(error.matches("[A-Za-z0-9_.-]{0,80}"))result.putString("jar_list_code",error);any|=loaded;}
        result.putString("status",any?"PASS":"FAIL");
    }
}
