package cn.cloudcurtain.tv;

import android.app.Instrumentation;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONObject;

/** Official public asset probe. Logs sizes and timings, never signed redirect queries. */
final class UpdateRangeLiveCheck {
    static void run(Instrumentation test,Bundle result)throws Exception {
        for(Proxy proxy:ProxySelector.getDefault().select(URI.create(UpdateManifest.API_FEED)))
            if(proxy.type()!=Proxy.Type.DIRECT)throw new IOException("Direct connection required");
        UpdateManifest release=new UpdateClient().check(()->false);
        OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(8,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).callTimeout(2,TimeUnit.MINUTES).build();
        String metadata;try(Response response=client.newCall(new Request.Builder().url(release.metadataUrl()).header("User-Agent","CloudCurtainTV-Updater").build()).execute()){metadata=response.body().string();}
        String address=release.assetUrl(metadata);long began=SystemClock.elapsedRealtime();
        for(int n=0;n<6;n++){
            if(!UpdateManifest.assetRedirect(HttpUrl.get(address)))throw new IOException("Untrusted redirect");
            Request request=new Request.Builder().url(address).header("User-Agent","CloudCurtainTV-Updater").header("Accept","application/octet-stream")
                .header("Accept-Encoding","identity").header("Range","bytes=0-1048575").build();
            try(Response response=client.newCall(request).execute()){
                if(response.isRedirect()){address=response.request().url().resolve(response.header("Location")).toString();continue;}
                result.putInt("range_http",response.code());result.putString("range_host",response.request().url().host());
                result.putString("accept_ranges",response.header("Accept-Ranges",""));result.putString("content_range",response.header("Content-Range",""));
                long total=0;byte[] buffer=new byte[65536];try(InputStream input=response.body().byteStream()){int count;while((count=input.read(buffer))!=-1){total+=count;if(total>1048576)throw new IOException("Range ignored");}}
                result.putLong("range_bytes",total);result.putLong("range_ms",SystemClock.elapsedRealtime()-began);
                if(response.code()!=206||total!=1048576)throw new IOException("Valid partial response not returned");return;
            }
        }
        throw new IOException("Too many redirects");
    }
}
