package cn.cloudcurtain.tv;

import okhttp3.*;
import java.io.IOException;

/** Reestablish the official portal cookie session with the user's existing SSO cookies. */
final class TianyiWebSession {
    static void restore()throws Exception {
        String url=TianyiQrApi.START;MediaSpec scope=new MediaSpec(url,"");scope.headers.put("Referer","https://cloud.189.cn/web/main");
        OkHttpClient client=Net.client(scope).newBuilder().followRedirects(false).followSslRedirects(false).build();
        for(int redirects=0;redirects<8;redirects++){
            if(!TianyiQrApi.allowed(url))throw new IOException("天翼会话跳转来源不匹配。");
            try(Response response=client.newCall(new Request.Builder().url(url).header("User-Agent",Net.UA).build()).execute()){
                if(response.isRedirect()){
                    String location=response.header("Location");HttpUrl next=location==null?null:response.request().url().resolve(location);
                    if(next==null||!TianyiQrApi.allowed(next.toString()))throw new IOException("天翼会话跳转来源不匹配。");
                    url=next.toString();continue;
                }
                if(!response.isSuccessful())throw new Net.ServiceException(response.code(),"InvalidSessionKey");
                if(!UrlRules.sameOrigin("https://cloud.189.cn",url)||!response.request().url().encodedPath().equals("/web/main"))throw new Net.ServiceException(200,"InvalidSessionKey");
                return;
            }
        }
        throw new Net.ServiceException(200,"InvalidSessionKey");
    }
}
