package cn.cloudcurtain.tv;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/** Slow Baidu reads should retry while the player's reserve still contains video. */
@androidx.media3.common.util.UnstableApi
final class PlaybackNetwork {
    static OkHttpClient client(MediaSpec media){
        OkHttpClient client=Net.client(media);
        return PlaybackBuffer.baidu(media)?client.newBuilder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).build():client;
    }
}
