package cn.cloudcurtain.tv;

import android.app.*;import android.content.*;import android.os.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.zip.*;

/** Local synthetic HLS with a delayed read when the buffer next requests a segment. */
final class BufferOutageLiveCheck {
    private static java.lang.reflect.Field field(String name)throws Exception{java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    static void run(Instrumentation test,Bundle result,boolean expectProtected)throws Exception {
        Map<String,byte[]> files=new HashMap<>();try(ZipInputStream archive=new ZipInputStream(test.getContext().getAssets().open("buffer-fixture.zip"))){ZipEntry entry;while((entry=archive.getNextEntry())!=null){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=archive.read(bytes))!=-1)data.write(bytes,0,n);files.put(entry.getName(),data.toByteArray());}}
        ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));ExecutorService sockets=Executors.newCachedThreadPool();AtomicBoolean injected=new AtomicBoolean();List<Socket> open=Collections.synchronizedList(new ArrayList<>());long began=SystemClock.elapsedRealtime();
        sockets.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();open.add(socket);sockets.submit(()->{try(Socket connection=socket){connection.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));String first=reader.readLine();if(first==null)return;String path=first.split(" ")[1].substring(1);String header;while((header=reader.readLine())!=null&&!header.isEmpty()){}
            byte[] body=files.get(path);if(body==null)body=new byte[0];
            if(path.endsWith(".ts")&&SystemClock.elapsedRealtime()-began>35000&&injected.compareAndSet(false,true)){result.putLong("delayed_read_at_ms",SystemClock.elapsedRealtime()-began);Thread.sleep(22000);}
            String response="HTTP/1.1 200 OK\r\nContent-Type: "+(path.endsWith(".m3u8")?"application/vnd.apple.mpegurl":"video/mp2t")+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";
            connection.getOutputStream().write(response.getBytes(java.nio.charset.StandardCharsets.US_ASCII));connection.getOutputStream().write(body);connection.getOutputStream().flush();
        }catch(Exception ignored){}finally{open.remove(socket);}});}catch(IOException ignored){}});
        PlayerActivity activity=null;String key="fixture:baidu-buffer-outage";
        try {
            MediaSpec media=new MediaSpec("http://127.0.0.1:"+server.getLocalPort()+"/index.m3u8","缓冲波动测试");media.provider="BAIDU";media.key=key;media.compatible=true;media.mime="application/x-mpegURL";media.quality="baidu:1080";
            try(okhttp3.Response response=Net.client(media).newCall(new okhttp3.Request.Builder().url(media.url).build()).execute()){
                result.putInt("fixture_http",response.code());if(response.code()!=200||!response.body().string().startsWith("#EXTM3U"))throw new IOException("Local HLS fixture did not respond");
            }
            activity=(PlayerActivity)test.startActivitySync(new Intent(test.getTargetContext(),PlayerActivity.class).putExtra("media",media.json()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlayerActivity screen=activity;boolean started=false;int waiting=0,playing=0;long maximumReserve=0;boolean[] state={false,false};long[] sampled={0,0};
            long end=began+155000;while(SystemClock.elapsedRealtime()<end){Thread.sleep(500);test.runOnMainSync(()->{try{ExoPlayer player=(ExoPlayer)field("player").get(screen);state[0]=player.isPlaying();state[1]=player.getPlaybackState()==Player.STATE_BUFFERING;sampled[0]=player.getTotalBufferedDuration();sampled[1]=player.getCurrentPosition();}catch(Exception e){throw new RuntimeException(e);}});if(state[0]){started=true;playing++;}if(started&&state[1])waiting++;maximumReserve=Math.max(maximumReserve,sampled[0]);}
            result.putBoolean("network_delay_injected",injected.get());result.putInt("buffering_samples_after_start",waiting);result.putInt("playing_samples",playing);result.putLong("maximum_reserve_ms",maximumReserve);result.putLong("final_position_ms",sampled[1]);
            if(!injected.get()||playing<220)throw new IOException("Outage scenario was not exercised");
            if(expectProtected&&waiting!=0)throw new IOException("Playback ran out during the delayed read");if(!expectProtected&&waiting<5)throw new IOException("Baseline did not reproduce buffering");
        }finally {
            if(activity!=null){PlayerActivity screen=activity;test.runOnMainSync(screen::finish);}server.close();synchronized(open){for(Socket socket:open)try{socket.close();}catch(IOException ignored){}}sockets.shutdownNow();
            test.getTargetContext().getSharedPreferences("progress",0).edit().remove(Integer.toHexString(key.hashCode())).commit();
        }
    }
}
