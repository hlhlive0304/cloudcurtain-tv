package cn.cloudcurtain.tv;

import org.json.*;
import java.io.IOException;

/** Read the completed website session only from Alipan's top-level HTTPS document. */
final class AliWebSession {
    static final String SCRIPT="(function(){try{var v=sessionStorage.getItem('token')||localStorage.getItem('token');return v?JSON.stringify(JSON.parse(v)):null;}catch(e){return null;}})()";
    static boolean allowed(String url){
        return UrlRules.sameOrigin("https://www.alipan.com",url)||UrlRules.sameOrigin("https://www.aliyundrive.com",url);
    }
    static JSONObject parse(String url,String evaluated)throws Exception{
        if(!allowed(url)||evaluated==null||evaluated.length()>65536)return null;
        Object value=new JSONTokener(evaluated).nextValue();if(!(value instanceof String))return null;
        JSONObject source=new JSONObject((String)value);
        if(source.optString("access_token").isEmpty()||source.optString("refresh_token").isEmpty()||source.optString("user_id").isEmpty())return null;
        if(source.optBoolean("deviceAmountLimit",false))throw new IOException("阿里要求管理登录设备，请在官网完成后继续。");
        JSONObject token=new JSONObject();
        for(String key:new String[]{"access_token","refresh_token","user_id","token_type","expire_time","expires_in","default_drive_id","resource_drive_id","backup_drive_id"}){
            if(source.has(key)&&!source.isNull(key))token.put(key,source.get(key));
        }
        return token;
    }
}