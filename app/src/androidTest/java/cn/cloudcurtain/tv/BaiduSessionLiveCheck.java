package cn.cloudcurtain.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.webkit.CookieManager;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** In-memory cookie omissions reproduce partial sessions without changing the retained login. */
final class BaiduSessionLiveCheck {
    static void actual(Instrumentation test,Bundle result)throws Exception {
        android.content.Context context=test.getTargetContext();CookieManager manager=CookieManager.getInstance();String origin=Provider.BAIDU.origin;
        String original=cookie(manager.getCookie(origin),"STOKEN"),identity=cookie(manager.getCookie(origin),"BDUSS");
        if(original.isEmpty())throw new IOException("A complete baseline session is required");
        java.util.concurrent.CountDownLatch removed=new java.util.concurrent.CountDownLatch(5);
        test.runOnMainSync(()->{for(String domain:new String[]{"","baidu.com",".baidu.com","pan.baidu.com",".pan.baidu.com"})manager.setCookie(origin,
            "STOKEN=; Max-Age=0; Path=/; Secure; HttpOnly"+(domain.isEmpty()?"":"; Domain="+domain),ok->removed.countDown());});
        if(!removed.await(5,TimeUnit.SECONDS))throw new IOException("Cookie callback timed out");
        android.app.Activity activity=null;
        try {
            if(!cookie(manager.getCookie(origin),"STOKEN").isEmpty())throw new IOException("Partial session was not reproduced");
            result.putInt("partial_session_root_entries",new CloudApi(Provider.BAIDU).list("/",1).entries.size());
            String vars="https://pan.baidu.com/api/gettemplatevariable?web=1&fields="+android.net.Uri.encode("[\"sign1\",\"sign3\",\"timestamp\",\"bdstoken\"]");
            int errno=Net.json(vars,null,Net.headers("Referer",origin)).optInt("errno",-999);result.putInt("partial_session_variables_errno",errno);if(errno!=-6)throw new IOException("Expected partial-session error missing");
            JSONObject selected=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(context.getCacheDir(),"baidu-subtitle-selection.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
            activity=test.startActivitySync(new android.content.Intent(context,UpdateActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            MediaSpec media=new MediaSpec("https://pan.baidu.com/fixture","test");media.provider="BAIDU";
            MediaSpec.Sub cached=new Subtitles(activity).cache(new MediaSpec.Sub("cloud:"+selected.getString("id"),selected.getString("name"),"application/x-subrip"),media);
            File file=new File(android.net.Uri.parse(cached.url).getPath());String text=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);
            result.putLong("automatically_cached_bytes",file.length());result.putBoolean("automatic_chinese_timeline",text.contains("-->")&&text.matches("(?s).*[\\u4e00-\\u9fff].*"));
            result.putBoolean("download_session_restored",!cookie(manager.getCookie(origin),"STOKEN").isEmpty());result.putBoolean("main_login_retained",identity.equals(cookie(manager.getCookie(origin),"BDUSS")));
            if(!result.getBoolean("automatic_chinese_timeline")||!result.getBoolean("download_session_restored")||!result.getBoolean("main_login_retained"))throw new IOException("Actual subtitle session recovery failed");
        }finally {
            if(activity!=null){android.app.Activity screen=activity;test.runOnMainSync(screen::finish);}
            if(cookie(manager.getCookie(origin),"STOKEN").isEmpty())manager.setCookie(origin,"STOKEN="+original+"; Path=/; Secure; HttpOnly");manager.flush();
        }
    }
    private static String cookie(String raw,String name){if(raw!=null)for(String part:raw.split(";")){int at=part.indexOf('=');if(at>0&&part.substring(0,at).trim().equals(name))return part.substring(at+1);}return "";}
    static void run(Instrumentation test,Bundle result)throws Exception {
        File selection=new File(test.getTargetContext().getCacheDir(),"baidu-subtitle-selection.json");
        String path=new JSONObject(new String(java.nio.file.Files.readAllBytes(selection.toPath()),java.nio.charset.StandardCharsets.UTF_8)).getString("id");
        String meta="https://pan.baidu.com/api/filemetas?web=1&target="+android.net.Uri.encode(new JSONArray().put(path).toString());
        String vars="https://pan.baidu.com/api/gettemplatevariable?web=1&fields="+android.net.Uri.encode("[\"sign1\",\"sign3\",\"timestamp\",\"bdstoken\"]");
        for(String missing:new String[]{"NONE","STOKEN","BDUSS_BFESS","BDUSS"}){
            List<Cookie> added=new ArrayList<>();OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(20,TimeUnit.SECONDS)
                .addInterceptor(chain->{Request original=chain.request();String raw=CookieManager.getInstance().getCookie(original.url().toString());Map<String,String> values=new LinkedHashMap<>();
                    if(raw!=null)for(String part:raw.split(";")){int at=part.indexOf('=');if(at>0){String name=part.substring(0,at).trim();if(!name.equals(missing))values.put(name,part.substring(at+1));}}
                    for(Cookie cookie:added)if(cookie.matches(original.url()))values.put(cookie.name(),cookie.value());StringBuilder joined=new StringBuilder();for(Map.Entry<String,String> e:values.entrySet()){if(joined.length()>0)joined.append("; ");joined.append(e.getKey()).append('=').append(e.getValue());}
                    Response response=chain.proceed(original.newBuilder().header("Cookie",joined.toString()).build());added.addAll(Cookie.parseAll(original.url(),response.headers()));return response;}).build();
            JSONObject m=json(client,meta),v=json(client,vars);result.putInt(missing+"_metadata_errno",m.optInt("errno",-999));result.putInt(missing+"_variables_errno",v.optInt("errno",-999));
            JSONObject data=v.optJSONObject("result");if(data!=null){String url=HttpUrl.get("https://pan.baidu.com/api/download").newBuilder().addQueryParameter("web","1").addQueryParameter("type","dlink")
                    .addQueryParameter("fidlist","["+m.getJSONArray("info").getJSONObject(0).getLong("fs_id")+"]").addQueryParameter("sign",BaiduSubtitleDownload.sign(data.optString("sign3"),data.optString("sign1")))
                    .addQueryParameter("timestamp",data.optString("timestamp")).addQueryParameter("bdstoken",data.optString("bdstoken")).build().toString();
                result.putInt(missing+"_download_errno",json(client,url).optInt("errno",-999));
            }
            if(!missing.equals("NONE")){
                if(missing.equals("STOKEN")){
                    int[] refreshes={0};CloudApi.Entry chosen=new CloudApi.Entry(path,"简中.srt",false,0);
                    MediaSpec recovered=BaiduSubtitleDownload.resolve(chosen,url->json(client,url),()->{refreshes[0]++;BaiduWebSession.refresh(url->client.newCall(request(url)).execute());});
                    result.putInt("automatic_refreshes",refreshes[0]);result.putBoolean("recovered_download_address",BaiduSubtitleDownload.trusted(recovered.url));
                    String download=recovered.url;for(int n=0;n<6;n++){
                        if(!BaiduSubtitleDownload.trusted(download))throw new IOException("Untrusted subtitle redirect");
                        try(Response response=client.newCall(request(download)).execute()){
                            if(response.isRedirect()){download=response.request().url().resolve(response.header("Location")).toString();continue;}
                            result.putInt("recovered_subtitle_http",response.code());byte[] subtitle=response.peekBody(4*1024*1024).bytes();String text=SubtitleText.decode(subtitle);
                            result.putLong("recovered_subtitle_bytes",subtitle.length);result.putBoolean("recovered_chinese_timeline",text.contains("-->")&&text.matches("(?s).*[\\u4e00-\\u9fff].*"));
                            if(response.code()!=200||!result.getBoolean("recovered_chinese_timeline")||refreshes[0]!=1)throw new IOException("Short-lived download session did not recover");break;
                        }
                    }
                }
                String address=BaiduQrApi.HOME;for(int n=0;n<6;n++){if(!BaiduQrApi.allowed(address))throw new IOException("Untrusted refresh");try(Response response=client.newCall(request(address)).execute()){
                    if(response.isRedirect()){address=response.request().url().resolve(response.header("Location")).toString();continue;}result.putInt(missing+"_refresh_http",response.code());result.putString(missing+"_refresh_host",response.request().url().host());break;}}
                result.putInt(missing+"_refreshed_metadata_errno",json(client,meta).optInt("errno",-999));result.putInt(missing+"_refreshed_variables_errno",json(client,vars).optInt("errno",-999));
                result.putBoolean(missing+"_server_set_cookies",!added.isEmpty());
            }
        }
        result.putBoolean("retained_cookies_unchanged",true);
    }
    private static Request request(String address){return new Request.Builder().url(address).header("User-Agent",Net.UA).header("Referer",Provider.BAIDU.origin).build();}
    private static JSONObject json(OkHttpClient client,String address)throws Exception{try(Response response=client.newCall(request(address)).execute()){if(response.body()==null)throw new IOException("Empty response");return new JSONObject(response.body().string());}}
}
