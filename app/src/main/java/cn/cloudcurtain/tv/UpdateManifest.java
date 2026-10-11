package cn.cloudcurtain.tv;

import java.io.IOException;
import java.util.Locale;
import okhttp3.HttpUrl;
import org.json.JSONObject;

/** The public feed can select a release, but cannot change the trusted package or signing key. */
final class UpdateManifest {
    static final String REPOSITORY="https://github.com/hlhlive0304/cloudcurtain-tv";
    static final String FEED="https://raw.githubusercontent.com/hlhlive0304/cloudcurtain-tv/main/update.json";
    static final String PACKAGE="cn.cloudcurtain.tv";
    static final String SIGNER="bd876cc0b82d02864e8d7af31bc5a11c90e6c907906e906ab1f931a38d5be576";
    static final long MAX_APK_BYTES=512L*1024*1024;
    final long versionCode,apkSize;
    final int minSdk;
    final String versionName,apkUrl,sha256,notes;

    private UpdateManifest(long code,String name,int minSdk,String url,long size,String hash,String notes){
        this.versionCode=code;this.versionName=name;this.minSdk=minSdk;this.apkUrl=url;this.apkSize=size;this.sha256=hash;this.notes=notes;
    }
    static UpdateManifest parse(String text)throws IOException {
        if(text==null||text.length()>65536)throw new IOException("更新信息过大或为空。");
        try{
            JSONObject j=new JSONObject(text);
            long code=j.getLong("versionCode"),size=j.getLong("apkSize");
            String name=j.getString("versionName"),url=j.getString("apkUrl"),hash=j.getString("sha256").toLowerCase(Locale.ROOT),notes=j.optString("notes","");
            int sdk=j.getInt("minSdk");
            if(j.getInt("schemaVersion")!=1||!PACKAGE.equals(j.getString("packageName"))||code<1||code>Integer.MAX_VALUE||sdk<23||sdk>1000
                ||name.isEmpty()||name.length()>80||name.matches("(?s).*\\p{Cntrl}.*")||notes.length()>12000
                ||size<1||size>MAX_APK_BYTES||!hash.matches("[0-9a-f]{64}")||!releaseUrl(url))throw new IOException("更新信息不符合此应用的发布规则。");
            return new UpdateManifest(code,name,sdk,url,size,hash,notes);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("更新信息格式不正确。");}
    }
    static boolean releaseUrl(String value){
        try{
            HttpUrl u=HttpUrl.get(value);
            return cleanHttps(u)&&u.host().equals("github.com")&&u.query()==null
                &&u.encodedPath().matches("/hlhlive0304/cloudcurtain-tv/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk");
        }catch(Exception ignored){return false;}
    }
    static boolean cleanHttps(HttpUrl u){return u.isHttps()&&u.port()==443&&u.username().isEmpty()&&u.password().isEmpty()&&u.fragment()==null;}
    static boolean assetRedirect(HttpUrl u){
        return cleanHttps(u)&&(releaseUrl(u.toString())||u.host().equals("release-assets.githubusercontent.com")
            ||u.host().equals("objects.githubusercontent.com")||u.host().equals("objects-origin.githubusercontent.com"));
    }
    boolean newerThan(long installed){return versionCode>installed;}
}
