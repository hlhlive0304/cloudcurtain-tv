package cn.cloudcurtain.tv;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Revisit the normal website landing page to refresh short-lived STOKEN cookies. */
final class BaiduWebSession {
    interface Fetch {Response get(String url)throws Exception;}
    static void refresh()throws Exception {
        OkHttpClient client=Net.client(null).newBuilder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).build();
        refresh(url->client.newCall(new Request.Builder().url(url).header("User-Agent",Net.UA).header("Referer",Provider.BAIDU.origin).build()).execute());
        android.webkit.CookieManager.getInstance().flush();
    }
    static void refresh(Fetch fetch)throws Exception {
        String address=BaiduQrApi.HOME;
        for(int n=0;n<6;n++){
            if(!BaiduQrApi.allowed(address))throw new IOException("百度下载会话跳转来源不匹配。");
            try(Response response=fetch.get(address)){
                if(response.isRedirect()){
                    String location=response.header("Location");HttpUrl next=location==null?null:HttpUrl.get(address).resolve(location);
                    if(next==null||!BaiduQrApi.allowed(next.toString()))throw new IOException("百度下载会话跳转来源不匹配。");address=next.toString();continue;
                }
                if(!response.isSuccessful()||response.body()==null)throw new IOException("百度网页会话刷新未完成，请进入官网完成验证。");
                response.peekBody(1024);return;
            }
        }
        throw new IOException("百度下载会话跳转次数过多。");
    }
}
