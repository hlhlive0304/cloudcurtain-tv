package cn.cloudcurtain.tv;

import java.io.IOException;
import java.util.Locale;
import okhttp3.HttpUrl;
import org.json.JSONObject;

/** The public feed can select a release, but cannot change the trusted package or signing key. */
final class UpdateManifest {
    static final String REPOSITORY="https://github.com/hlhlive0304/cloudcurtain-tv";
    static final String FEED="https://raw.githubusercontent.com/hlhlive0304/cloudcurtain-tv/main/update.json";
    static final String API="https://api.github.com/repos/hlhlive0304/cloudcurtain-tv";
    static final String API_FEED=API+"/contents/update.json?ref=main";
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
        return cleanHttps(u)&&(releaseUrl(u.toString())||apiAsset(u.toString())||u.host().equals("release-assets.githubusercontent.com")
            ||u.host().equals("objects.githubusercontent.com")||u.host().equals("objects-origin.githubusercontent.com"));
    }
    static boolean apiAsset(String value){
        try{HttpUrl u=HttpUrl.get(value);return cleanHttps(u)&&u.host().equals("api.github.com")&&u.query()==null
            &&u.encodedPath().matches("/repos/hlhlive0304/cloudcurtain-tv/releases/assets/[1-9][0-9]*");}
        catch(Exception ignored){return false;}
    }
    String metadataUrl(){return API+"/releases/tags/"+HttpUrl.get(apkUrl).pathSegments().get(4);}
    String assetUrl(String text)throws IOException{
        try{
            JSONObject j=new JSONObject(text);String tag=HttpUrl.get(apkUrl).pathSegments().get(4);
            if(j.optBoolean("draft",true)||!tag.equals(j.getString("tag_name")))throw new IOException("更新版本的发布信息不一致。");
            org.json.JSONArray assets=j.getJSONArray("assets");
            for(int i=0;i<assets.length();i++){
                JSONObject asset=assets.getJSONObject(i);if(!apkUrl.equals(asset.optString("browser_download_url")))continue;
                String url=asset.getString("url"),digest=asset.optString("digest","");
                if(!apiAsset(url)||!url.equals(API+"/releases/assets/"+asset.getLong("id"))||asset.getLong("size")!=apkSize
                    ||!"uploaded".equals(asset.getString("state"))||(!digest.isEmpty()&&!digest.equals("sha256:"+sha256)))
                    throw new IOException("更新安装包的发布信息不一致。");
                return url;
            }
            throw new IOException("更新版本尚未提供对应的安装包。");
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("更新版本的发布信息格式不正确。");}
    }
    boolean newerThan(long installed){return versionCode>installed;}
}
