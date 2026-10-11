package cn.cloudcurtain.tv;

import androidx.media3.datasource.HttpDataSource;

final class PlaybackErrors {
    static int httpStatus(Throwable error){for(int i=0;error!=null&&i<8;i++,error=error.getCause())if(error instanceof HttpDataSource.InvalidResponseCodeException)return ((HttpDataSource.InvalidResponseCodeException)error).responseCode;return 0;}
    static String describe(Throwable error,String code){
        int status=httpStatus(error);if(status==0){if(code!=null&&(code.contains("DECODING")||code.contains("DECODER")))return code+"\n当前设备解码此视频失败，可尝试其他清晰度或兼容视频。";return code+"\n可重试播放；仍失败时请查看设置中的登录诊断。";}
        String reason=status==401||status==403?"服务器拒绝了播放请求，请重新获取地址；若仍失败，请确认网盘登录和该文件的播放权限。":status==404||status==410?"播放地址已失效或文件不可用，请重新获取地址。":status==429?"网盘请求过于频繁，请稍后重试。":"视频服务器未能返回可播放的数据，请稍后重试。";
        return "视频请求返回 HTTP "+status+"\n"+reason;
    }
}
