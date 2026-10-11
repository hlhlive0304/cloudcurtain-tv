package cn.cloudcurtain.tv;

import okhttp3.OkHttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TLS shutdown can perform network I/O, including when evicting an idle socket.
 * This executor outlives a QR controller's worker, which is stopped on navigation.
 */
final class QrHttpCleanup {
    private static final ExecutorService IO=Executors.newFixedThreadPool(2,r->{
        Thread t=new Thread(r,"qr-network-cleanup");t.setDaemon(true);return t;
    });
    private QrHttpCleanup(){}
    static void release(OkHttpClient client){
        IO.execute(()->{
            try{try{client.dispatcher().cancelAll();}finally{client.connectionPool().evictAll();}}
            catch(RuntimeException e){LoginDiagnostics.record(CloudApp.context(),null,"后台清理登录连接",e);}
        });
    }
}
