package cn.cloudcurtain.tv;

import okhttp3.*;
import java.util.*;

/** Memory-only cookies belonging to one QR attempt, matched by RFC domain/path rules. */
final class LoginCookieJar implements CookieJar {
    private final List<Cookie> cookies=new ArrayList<>();
    @Override public synchronized void saveFromResponse(HttpUrl url,List<Cookie> added){
        for(Cookie next:added){for(Iterator<Cookie> it=cookies.iterator();it.hasNext();){Cookie old=it.next();if(old.name().equals(next.name())&&old.domain().equals(next.domain())&&old.path().equals(next.path()))it.remove();}if(next.expiresAt()>System.currentTimeMillis())cookies.add(next);}
    }
    @Override public synchronized List<Cookie> loadForRequest(HttpUrl url){
        List<Cookie> result=new ArrayList<>();for(Iterator<Cookie> it=cookies.iterator();it.hasNext();){Cookie c=it.next();if(c.expiresAt()<=System.currentTimeMillis())it.remove();else if(c.matches(url))result.add(c);}return result;
    }
}
