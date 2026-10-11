package cn.cloudcurtain.tv;

/** Error reporting must also work on Android's older ICU regex engines. */
final class LoginErrors {
    private LoginErrors(){}
    static String friendly(Exception error){
        if(error instanceof Net.ServiceException&&"InvalidSessionKey".equals(((Net.ServiceException)error).serviceCode))return "天翼文件会话已失效，请重新扫码登录。";
        if(error instanceof java.net.SocketTimeoutException)return "连接超时，请检查网络后重试。";
        if(error instanceof java.net.UnknownHostException)return "无法连接网盘服务器，请检查电视网络。";
        if(error instanceof javax.net.ssl.SSLException)return "安全连接失败，请检查电视日期、时间和系统更新。";
        String text=error.getMessage();
        if(error instanceof java.io.IOException&&text!=null&&text.length()<=240&&!text.contains("://")){
            for(int i=0;i<text.length();i++)if(text.charAt(i)>='\u3400'&&text.charAt(i)<='\u9fff')return text;
        }
        return "登录请求未完成，请重试；详细错误可在“字幕与设置 → 登录诊断”查看。";
    }
}
