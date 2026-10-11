package cn.cloudcurtain.tv;

import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import static org.junit.Assert.*;

/** Real pooled TCP connections, with the TV's main-thread close prohibition.
 * Empty clients cannot reproduce the crash reported by the user's Android 11 TV.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class QrCloseRegressionTest {
    @Test public void baiduCanCloseIdleAndPollingConnectionsFromUi()throws Exception{exercise(Provider.BAIDU,false);exercise(Provider.BAIDU,true);}
    @Test public void tianyiCanCloseIdleAndPollingConnectionsFromUi()throws Exception{exercise(Provider.TIANYI,false);exercise(Provider.TIANYI,true);}
    @Test public void aliCanCloseIdleAndPollingConnectionsFromUi()throws Exception{exercise(Provider.ALI,false);exercise(Provider.ALI,true);}
    private Object field(Object owner,String name)throws Exception{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void exercise(Provider p,boolean active)throws Exception{
        assertSame(Looper.getMainLooper(),Looper.myLooper());
        AutoCloseable api=p==Provider.BAIDU?new BaiduQrApi():p==Provider.TIANYI?new TianyiQrApi():new AliQrApi();
        OkHttpClient original=(OkHttpClient)field(p==Provider.ALI?api:field(api,"http"),"client");
        List<Socket> sockets=Collections.synchronizedList(new ArrayList<>());CountDownLatch closed=new CountDownLatch(1);AtomicInteger uiCloses=new AtomicInteger();
        SocketFactory factory=new SocketFactory(){
            @Override public Socket createSocket(){Socket s=new Socket(){@Override public synchronized void close()throws IOException{
                if(Looper.myLooper()==Looper.getMainLooper()){uiCloses.incrementAndGet();throw new NetworkOnMainThreadException();}
                super.close();closed.countDown();
            }};sockets.add(s);return s;}
            @Override public Socket createSocket(String host,int port)throws IOException{Socket s=createSocket();s.connect(new InetSocketAddress(host,port));return s;}
            @Override public Socket createSocket(InetAddress host,int port)throws IOException{return createSocket(host.getHostAddress(),port);}
            @Override public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{Socket s=createSocket();s.bind(new InetSocketAddress(local,localPort));s.connect(new InetSocketAddress(host,port));return s;}
            @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return createSocket(host.getHostAddress(),port,local,localPort);}
        };
        ExecutorService io=Executors.newFixedThreadPool(2);
        try(MockWebServer server=new MockWebServer()){
            server.start();server.enqueue(new MockResponse().setBody("ready"));
            // newBuilder retains the QR API's actual connection pool and dispatcher.
            OkHttpClient client=original.newBuilder().socketFactory(factory).build();
            io.submit(()->{try(Response r=client.newCall(new Request.Builder().url(server.url("/warm")).build()).execute()){r.body().string();}return null;}).get(5,TimeUnit.SECONDS);
            assertNotNull(server.takeRequest(5,TimeUnit.SECONDS));assertEquals(1,original.connectionPool().idleConnectionCount());
            Future<?> pending=null;
            if(active){server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));pending=io.submit(()->{try(Response r=client.newCall(new Request.Builder().url(server.url("/poll")).build()).execute()){r.body().string();}return null;});assertNotNull(server.takeRequest(5,TimeUnit.SECONDS));}
            api.close();api.close();
            assertTrue("Cleanup must run after the UI returns",closed.await(5,TimeUnit.SECONDS));assertEquals("No socket shutdown on UI",0,uiCloses.get());
            if(pending!=null){Future<?> result=pending;assertThrows(ExecutionException.class,()->result.get(5,TimeUnit.SECONDS));}
        }finally{
            io.submit(()->{original.dispatcher().cancelAll();original.connectionPool().evictAll();synchronized(sockets){for(Socket s:sockets)try{s.close();}catch(IOException ignored){}}}).get(5,TimeUnit.SECONDS);io.shutdownNow();
        }
    }
}
