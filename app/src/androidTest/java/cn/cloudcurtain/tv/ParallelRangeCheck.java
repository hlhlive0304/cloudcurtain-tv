package cn.cloudcurtain.tv;
import android.app.Instrumentation;import android.os.*;import java.util.*;import java.util.concurrent.*;import java.io.*;import okhttp3.*;
final class ParallelRangeCheck {
 static void run(Instrumentation test,Bundle out,String provider,String hash)throws Exception{
  MovieLibrary.Movie chosen=null;for(MovieLibrary.Movie m:new MovieLibrary(test.getTargetContext()).movies())if(m.provider.name().equals(provider)&&Integer.toHexString(m.playbackKey().hashCode()).equals(hash))chosen=m;if(chosen==null)throw new IOException("Movie missing");
  CloudApi api=new CloudApi(chosen.provider);if(chosen.provider==Provider.ALI)api.selectAliSpace(chosen.space);MediaSpec spec=api.resolveOriginal(chosen.entry());OkHttpClient client=Net.client(spec).newBuilder().callTimeout(25,TimeUnit.SECONDS).build();ExecutorService pool=Executors.newFixedThreadPool(3);List<Future<Long>> futures=new ArrayList<>();long started=SystemClock.elapsedRealtime();
  try{for(int lane=0;lane<3;lane++){final long pos=lane*2097152L;futures.add(pool.submit(()->{long bytes=0;try(Response r=client.newCall(new Request.Builder().url(spec.url).header("Range","bytes="+pos+"-"+(pos+2097151)).build()).execute()){if(r.code()!=206||!r.header("Content-Range","").startsWith("bytes "+pos+"-")||r.body()==null)throw new IOException("Range was not honored");try(InputStream in=r.body().byteStream()){byte[] b=new byte[32768];int n;while(bytes<2097152&&(n=in.read(b,0,(int)Math.min(b.length,2097152-bytes)))!=-1){bytes+=n;if(SystemClock.elapsedRealtime()-started>20000)break;}}}return bytes;}));}
   long total=0;for(int i=0;i<futures.size();i++){long n=futures.get(i).get(27,TimeUnit.SECONDS);total+=n;out.putLong("lane_"+i+"_bytes",n);}long elapsed=SystemClock.elapsedRealtime()-started;out.putLong("sample_bytes",total);out.putLong("sample_ms",elapsed);out.putLong("aggregate_kib_per_second",total*1000/Math.max(1,elapsed)/1024);
  }finally{for(Future<Long> f:futures)f.cancel(true);pool.shutdownNow();}
 }
}
