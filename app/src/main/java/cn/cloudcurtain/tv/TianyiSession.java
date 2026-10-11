package cn.cloudcurtain.tv;

import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

/** The portal sessionKey is separate from the QR/SSO cookies. */
final class TianyiSession {
    private static final Object LOCK=new Object();
    interface Store {String read();void write(String value)throws Exception;}
    interface Renewal {void restore()throws Exception;}
    private final CloudApi.Transport transport;private final Store store;private final Renewal renewal;private final int generation;
    TianyiSession(CloudApi.Transport transport){this(transport,new Store(){public String read(){return new Vault(CloudApp.context()).get("tianyi_session");}public void write(String s)throws Exception{new Vault(CloudApp.context()).put("tianyi_session",s);}},TianyiWebSession::restore);}
    TianyiSession(CloudApi.Transport transport,Store store){this(transport,store,()->{});}
    TianyiSession(CloudApi.Transport transport,Store store,Renewal renewal){this.transport=transport;this.store=store;this.renewal=renewal;generation=AccountSessions.generation(Provider.TIANYI);}
    private void active()throws IOException{if(generation!=AccountSessions.generation(Provider.TIANYI))throw new IOException("账号已退出，请重新登录。");}
    JSONObject request(String url,JSONObject body,Map<String,String> headers)throws Exception{
        active();String key=store.read();try{return send(url,body,headers,key);}catch(Net.ServiceException e){if(!"InvalidSessionKey".equals(e.serviceCode))throw e;}
        synchronized(LOCK){active();String current=store.read();if(current.isEmpty()||current.equals(key)){
            renewal.restore();active();
            JSONObject session=transport.request("https://cloud.189.cn/api/portal/v2/getUserBriefInfo.action?noCache="+System.currentTimeMillis(),null,Net.headers("Accept","application/json;charset=UTF-8","Referer","https://cloud.189.cn/web/main","Origin","https://cloud.189.cn"));
            current=portalKey(session);
            // A portal reply alone does not prove the file API accepts this session.
            JSONObject reply=send(url,body,headers,current);
            synchronized(AccountSessions.class){active();store.write(current);}
            return reply;
        }return send(url,body,headers,current);}
    }
    static String portalKey(JSONObject session)throws Net.ServiceException {
        String error=session.optString("errorCode","");String result=session.optString("res_code","0");
        Object value=session.opt("sessionKey");String key=value instanceof String?((String)value).trim():"";
        if(!error.isEmpty()||!"0".equals(result)||key.isEmpty()||key.equals("null")||key.equals("undefined"))throw new Net.ServiceException(200,error.isEmpty()?"InvalidSessionKey":error);
        return key;
    }
    private JSONObject send(String url,JSONObject body,Map<String,String> headers,String key)throws Exception{Map<String,String> actual=new LinkedHashMap<>(headers);if(!key.isEmpty())actual.put("sessionKey",key);JSONObject data=transport.request(url,body,actual);active();String error=data.optString("errorCode","");String result=data.optString("res_code","0");if(!error.isEmpty()||!"0".equals(result))throw new Net.ServiceException(200,error.isEmpty()?result:error);return data;}
}