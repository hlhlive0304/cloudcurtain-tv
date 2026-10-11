package cn.cloudcurtain.tv;

import org.json.JSONObject;
import java.io.IOException;
import java.text.*;
import java.util.*;

/** Rotating official tokens remain local; an in-flight refresh cannot undo logout. */
final class AliTokenSession {
    private static final Object LOCK=new Object();
    interface Store {String read();void write(String value)throws Exception;}
    interface Clock {long now();}
    private final CloudApi.Transport transport;private final Store store;private final Clock clock;private final int generation;
    AliTokenSession(CloudApi.Transport transport){this(transport,new Store(){public String read(){return new Vault(CloudApp.context()).get("ali_token");}public void write(String s)throws Exception{new Vault(CloudApp.context()).put("ali_token",s);}},System::currentTimeMillis);}
    AliTokenSession(CloudApi.Transport transport,Store store,Clock clock){this.transport=transport;this.store=store;this.clock=clock;generation=AccountSessions.generation(Provider.ALI);}
    private void active()throws IOException{if(generation!=AccountSessions.generation(Provider.ALI))throw new IOException("账号已退出，请重新登录。");}
    private JSONObject stored()throws Exception{active();String value=store.read();if(value.isEmpty())throw new IOException("请先扫码登录阿里云盘。");return new JSONObject(value);}
    JSONObject read()throws Exception{synchronized(LOCK){JSONObject token=stored();return expiresSoon(token,clock.now())?renew(token):token;}}
    JSONObject refresh(JSONObject failed)throws Exception{synchronized(LOCK){JSONObject current=stored();return current.optString("access_token").equals(failed.optString("access_token"))?renew(current):current;}}
    private JSONObject renew(JSONObject old)throws Exception{
        String refresh=old.optString("refresh_token");if(refresh.isEmpty())throw new IOException("阿里登录已过期，请重新扫码。");
        JSONObject reply=transport.request(AliDeviceSession.API+"/token/refresh",new JSONObject().put("refresh_token",refresh),Net.headers("Referer",Provider.ALI.origin,"Origin","https://www.alipan.com","x-requested-with","XMLHttpRequest"));
        active();if(!reply.optString("code").isEmpty())throw new Net.ServiceException(200,reply.optString("code"));
        if(reply.optString("access_token").isEmpty())throw new IOException("阿里未返回有效续期凭证，请重新扫码。");
        if(!reply.optString("user_id").isEmpty()&&!reply.optString("user_id").equals(old.optString("user_id")))throw new IOException("阿里续期账号不匹配，请重新扫码。");
        JSONObject next=new JSONObject(old.toString());next.remove("expire_time");
        for(String key:new String[]{"access_token","refresh_token","user_id","token_type","expire_time","expires_in","default_drive_id","resource_drive_id","backup_drive_id"})if(reply.has(key)&&!reply.isNull(key)&&!reply.optString(key).isEmpty())next.put(key,reply.get(key));
        next.put("_saved_at",clock.now());if(!reply.has("expires_in"))next.put("expires_in",600);
        synchronized(AccountSessions.class){active();store.write(next.toString());}return next;
    }
    static boolean expiresSoon(JSONObject token,long now){
        String expiry=token.optString("expire_time");if(!expiry.isEmpty()){
            String normalized=expiry.replaceAll("Z$","+0000").replaceAll("([+-][0-9]{2}):([0-9]{2})$","$1$2");
            for(String pattern:new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSZ","yyyy-MM-dd'T'HH:mm:ssZ"}){SimpleDateFormat format=new SimpleDateFormat(pattern,Locale.US);format.setLenient(false);ParsePosition pos=new ParsePosition(0);Date date=format.parse(normalized,pos);if(date!=null&&pos.getIndex()==normalized.length())return date.getTime()-now<=300000;}
        }
        long saved=token.optLong("_saved_at",0),seconds=token.optLong("expires_in",0);return saved<=0||seconds<=0||seconds>86400||now-saved>=seconds*1000-300000;
    }
    static void save(JSONObject token)throws Exception{synchronized(AccountSessions.class){JSONObject copy=new JSONObject(token.toString());copy.put("_saved_at",System.currentTimeMillis());new Vault(CloudApp.context()).put("ali_token",copy.toString());}}
}