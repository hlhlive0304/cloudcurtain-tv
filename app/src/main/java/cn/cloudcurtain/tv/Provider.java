package cn.cloudcurtain.tv;

public enum Provider {
    TIANYI("天翼云盘", "天翼", "https://cloud.189.cn/web/login.html", "https://cloud.189.cn/", 0xFFFFB875, "189.cn", "21cn.com", "dlife.cn", "e.189.cn"),
    BAIDU("百度网盘", "百度", "https://pan.baidu.com/disk/main", "https://pan.baidu.com/", 0xFF87B7FF, "baidu.com"),
    QUARK("夸克网盘", "夸克", "https://pan.quark.cn/", "https://pan.quark.cn/", 0xFFAFA3FF, "quark.cn", "uc.cn", "ucweb.com"),
    ALI("阿里云盘", "阿里", "https://www.alipan.com/sign/in?ad__pass__q__desktopLogin=true&back=%2Fdrive", "https://www.alipan.com/", 0xFFFFAA91, "alipan.com", "aliyundrive.com", "aliyun.com", "taobao.com");
    public final String title, shortTitle, loginUrl, origin;
    public final int color;
    private final String[] domains;
    Provider(String title,String shortTitle,String loginUrl,String origin,int color,String... domains) {
        this.title=title;this.shortTitle=shortTitle;this.loginUrl=loginUrl;this.origin=origin;this.color=color;this.domains=domains;
    }
    public boolean allows(String url) {
        if (!UrlRules.isWeb(url) || !url.startsWith("https://")) return false;
        for (String root:domains) if (UrlRules.domain(UrlRules.host(url),root)) return true;
        return false;
    }
    public boolean hasNativeFiles() { return true; }
    public String root() { return this==TIANYI ? "-11" : this==BAIDU?"/":this==ALI?"root":"0"; }
    public static Provider read(String value) { try { return valueOf(value); } catch(Exception e) { return TIANYI; } }
}
