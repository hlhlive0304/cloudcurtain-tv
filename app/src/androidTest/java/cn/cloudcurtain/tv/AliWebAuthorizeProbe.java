package cn.cloudcurtain.tv;
import android.app.Instrumentation;import android.os.Bundle;import okhttp3.*;import org.json.*;import java.io.IOException;import java.util.concurrent.TimeUnit;
/** Reproduce the official sign page's token_login -> callback -> token/get flow. No credentials leave Alipan. */
final class AliWebAuthorizeProbe {
 static void run(Instrumentation test,Bundle result,String hash)throws Exception{
  MovieLibrary.Movie movie=null;for(MovieLibrary.Movie m:new MovieLibrary(test.getTargetContext()).movies())if(m.provider==Provider.ALI&&(hash.isEmpty()||Integer.toHexString(m.playbackKey().hashCode()).equals(hash))){movie=m;break;}if(movie==null)throw new IOException("Movie missing");
  CloudApi api=new CloudApi(Provider.ALI);api.selectAliSpace(movie.space);JSONObject old=new AliTokenSession(Net::json).read();java.lang.reflect.Method driveMethod=CloudApi.class.getDeclaredMethod("aliDrive",JSONObject.class);driveMethod.setAccessible(true);String drive=(String)driveMethod.invoke(api,old);String state=new JSONObject().put("origin","https://www.alipan.com").put("nonce",java.util.UUID.randomUUID().toString()).toString();
  String clientId="25dzX3vbYqktVxyX",auth="https://auth.alipan.com";
  OkHttpClient client=new OkHttpClient.Builder().cookieJar(new LoginCookieJar()).followRedirects(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).build();
  try{
   HttpUrl start=HttpUrl.get(auth+"/v2/oauth/authorize").newBuilder().addQueryParameter("client_id",clientId).addQueryParameter("redirect_uri","https://www.alipan.com/sign/callback").addQueryParameter("response_type","code").addQueryParameter("login_type","custom").addQueryParameter("state",state).build();
   try(Response response=client.newCall(new Request.Builder().url(start).header("User-Agent",Net.UA).build()).execute()){result.putInt("authorize_http",response.code());if(!response.isSuccessful())throw new IOException("Official authorize initialization failed");}
   JSONObject login=post(client,auth+"/v2/oauth/token_login",new JSONObject().put("token",old.getString("access_token")),auth,result,"token_login");HttpUrl callback=HttpUrl.parse(login.optString("goto"));
   if(callback==null||!UrlRules.sameOrigin("https://www.alipan.com",callback.toString())||!"/sign/callback".equals(callback.encodedPath()))throw new IOException("Unexpected official callback");
   String code=callback.queryParameter("code");if(code==null||code.isEmpty()||!state.equals(callback.queryParameter("state")))throw new IOException("Official callback state mismatch");result.putBoolean("callback_valid",true);
   JSONObject fresh=post(client,AliDeviceSession.API+"/token/get",new JSONObject().put("client_id",clientId).put("code",code),"https://www.alipan.com",result,"token_exchange");result.putString("token_keys",CloudLiveInstrumentation.keys(fresh));
   if(fresh.optString("access_token").isEmpty()||!old.optString("user_id").equals(fresh.optString("user_id")))throw new IOException("Authorized account mismatch");result.putBoolean("same_owner",true);synchronized(AccountSessions.class){if(!old.optString("user_id").equals(new JSONObject(new Vault(test.getTargetContext()).get("ali_token")).optString("user_id")))throw new IOException("Account changed during probe");new Vault(test.getTargetContext()).put("ali_web_probe_token",fresh.toString());}
   JSONObject request=new JSONObject().put("drive_id",drive).put("file_id",movie.id).put("expire_sec",14400);
   JSONObject reply=new AliDeviceSession(Net::json).request("/v2/file/get_download_url",request,fresh);result.putBoolean("original_available",UrlRules.isWeb(reply.optString("url"))||UrlRules.isWeb(reply.optString("cdn_url")));result.putString("download_keys",CloudLiveInstrumentation.keys(reply));
   // The authorized token is retained only if this same account obtains a usable original URL.
   if(result.getBoolean("original_available")){AliTokenSession.save(fresh);result.putBoolean("same_account_saved",true);}
  }finally{QrHttpCleanup.release(client);}
 }
 static JSONObject post(OkHttpClient client,String url,JSONObject body,String origin,Bundle result,String tag)throws Exception{
  Request request=new Request.Builder().url(url).header("User-Agent",Net.UA).header("Origin",origin).header("Referer",origin+"/").header("x-requested-with","XMLHttpRequest").post(RequestBody.create(body.toString(),MediaType.get("application/json"))).build();
  try(Response response=client.newCall(request).execute()){result.putInt(tag+"_http",response.code());JSONObject json=response.body()==null?new JSONObject():new JSONObject(response.body().string());String code=json.optString("code");if(code.matches("[A-Za-z0-9_.-]{0,80}"))result.putString(tag+"_code",code);if(!response.isSuccessful()||!code.isEmpty())throw new IOException("Official authorization failed at "+tag);return json;}
 }
}
