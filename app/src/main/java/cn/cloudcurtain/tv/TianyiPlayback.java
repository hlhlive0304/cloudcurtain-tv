package cn.cloudcurtain.tv;

import java.io.IOException;
import okhttp3.*;

/** Resolve the authenticated portal hop once, then stream the issued CDN address. */
final class TianyiPlayback {
    static MediaSpec resolve(MediaSpec media)throws Exception{return resolve(media,Net.client(media));}
    static MediaSpec resolve(MediaSpec media,OkHttpClient client)throws Exception{
        if(!"TIANYI".equals(media.provider)||!Net.mediaHost(media,media.url))return media;
        try(Response response=client.newCall(new Request.Builder().url(media.url).header("Range","bytes=0-0").build()).execute()){
            if(!response.isSuccessful())throw new Net.ServiceException(response.code(),"");
            String issued=response.request().url().toString();
            if(!Net.playbackHeadersHost(media,issued)||!issued.startsWith("https://"))return media;
            if(response.code()!=206||!response.header("Content-Range","").matches("bytes 0-0/[1-9][0-9]*")||response.body()==null||response.body().byteStream().read()<0)return media;
            media.url=issued;
            return media;
        }
    }
}
