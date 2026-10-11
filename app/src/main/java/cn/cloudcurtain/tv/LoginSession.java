package cn.cloudcurtain.tv;

import java.util.UUID;

/** A QR belongs to one visible login attempt; late callbacks may not revive an old code. */
final class LoginSession {
    static final long MAX_AGE_MS=180_000;
    String nonce="";private long started;private boolean active;
    String start(long now){nonce=UUID.randomUUID().toString();started=now;active=true;return nonce;}
    boolean accepts(String value,long now){return active&&nonce.equals(value)&&now-started<MAX_AGE_MS;}
    boolean expired(long now){return active&&now-started>=MAX_AGE_MS;}
    void stop(){active=false;}
}
