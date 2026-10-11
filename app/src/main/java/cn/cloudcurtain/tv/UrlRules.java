package cn.cloudcurtain.tv;

import java.net.URI;
import java.util.Locale;
import okhttp3.HttpUrl;

public final class UrlRules {
    private UrlRules() {}
    private static HttpUrl web(String value){
        if(value==null||value.indexOf(92)>=0)return null;
        for(int i=0;i<value.length();i++)if(Character.isWhitespace(value.charAt(i))||Character.isISOControl(value.charAt(i)))return null;
        int separator=value.indexOf("://");if(separator<0)return null;
        String scheme=value.substring(0,separator);if(!scheme.equalsIgnoreCase("https")&&!scheme.equalsIgnoreCase("http"))return null;
        int end=value.length();for(char c:new char[]{'/','?','#'}){int at=value.indexOf(c,separator+3);if(at>=0)end=Math.min(end,at);}
        try{
            // Validate the authority strictly, then parse signed paths/queries exactly as OkHttp does.
            // Java URI rejects literal query characters used by real CDN signatures.
            URI authority=new URI(value.substring(0,end));if(authority.getHost()==null||authority.getUserInfo()!=null)return null;
            return HttpUrl.parse(value);
        }catch(Exception error){return null;}
    }
    public static boolean isWeb(String value) {return web(value)!=null;}
    public static String host(String value) {HttpUrl url=web(value);return url==null?"":url.host().toLowerCase(Locale.ROOT);}
    public static boolean domain(String host, String root) {
        return host.equals(root) || host.endsWith("." + root);
    }
    public static boolean sameOrigin(String first,String second) {
        HttpUrl a=web(first),b=web(second);return a!=null&&b!=null&&a.scheme().equals(b.scheme())&&a.host().equals(b.host())&&a.port()==b.port();
    }
    public static String path(String value) {
        HttpUrl url=web(value);if(url==null)return "";try{return url.uri().getPath().toLowerCase(Locale.ROOT);}catch(Exception e){return "";}
    }
    public static boolean video(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|mov|m4v|avi|ts|m2ts|flv|wmv|m3u8|mpd)$");
    }
    public static boolean subtitle(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(srt|vtt|ass|ssa|ttml|xml)$");
    }
    public static boolean mediaUrl(String url) {
        if (!isWeb(url)) return false;
        String p = path(url);
        return p.matches(".*\\.(mp4|mkv|webm|mov|m4v|m3u8|mpd)$")
            || (p.endsWith("/streaming") && url.toUpperCase(Locale.ROOT).contains("M3U8"));
    }
    public static String subtitleMime(String url) {
        String p = isWeb(url) ? path(url) : url.toLowerCase(Locale.ROOT);
        if (p.endsWith(".vtt")) return "text/vtt";
        if (p.endsWith(".ass") || p.endsWith(".ssa")) return "text/x-ssa";
        if (p.endsWith(".ttml") || p.endsWith(".xml")) return "application/ttml+xml";
        return "application/x-subrip";
    }
    public static String mediaMime(String url) {
        if (path(url).endsWith(".m3u8") || (path(url).endsWith("/streaming") && url.toUpperCase(Locale.ROOT).contains("M3U8"))) return "application/x-mpegURL";
        if (path(url).endsWith(".mpd")) return "application/dash+xml";
        return null;
    }
    public static String size(long size) {
        if(size < 1024) return size + " B";
        if(size < 1048576) return String.format(Locale.ROOT, "%.1f KB", size/1024.0);
        if(size < 1073741824L) return String.format(Locale.ROOT, "%.1f MB", size/1048576.0);
        return String.format(Locale.ROOT, "%.1f GB", size/1073741824.0);
    }
}
