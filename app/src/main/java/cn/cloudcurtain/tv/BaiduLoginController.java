package cn.cloudcurtain.tv;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Cookie;

/** Native QR and state polling: no hidden login page and no JavaScript dependency. */
final class BaiduLoginController {
    private final Activity activity;private final Runnable enterWeb,enterFiles;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LoginSession session=new LoginSession();
    private final LinearLayout overlay;private final ImageView image;private final TextView status;
    private BaiduQrApi api;private boolean closed,paused;private int errors;
    BaiduLoginController(Activity activity,FrameLayout parent,Runnable enterWeb,Runnable enterFiles){
        this.activity=activity;this.enterWeb=enterWeb;this.enterFiles=enterFiles;
        overlay=Ui.column(activity);overlay.setBackgroundColor(Ui.BG);overlay.setPadding(Ui.dp(activity,24),Ui.dp(activity,16),Ui.dp(activity,24),0);overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.addView(Ui.text(activity,"百度网盘 · 手机扫码登录",24,Ui.TEXT));
        TextView help=Ui.text(activity,"打开百度网盘 App 扫一扫，在手机上确认登录",15,Ui.MUTED);help.setGravity(Gravity.CENTER);help.setPadding(0,Ui.dp(activity,10),0,Ui.dp(activity,10));overlay.addView(help);
        FrameLayout area=new FrameLayout(activity);overlay.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        image=new ImageView(activity){@Override protected void onMeasure(int w,int h){int side=Math.max(0,Math.min(Ui.dp(getContext(),320),Math.min(MeasureSpec.getSize(w),MeasureSpec.getSize(h))));setMeasuredDimension(side,side);}};
        image.setContentDescription("百度网盘当前登录二维码");image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.WHITE);int pad=Ui.dp(activity,12);image.setPadding(pad,pad,pad,pad);image.setVisibility(View.INVISIBLE);area.addView(image,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        status=Ui.text(activity,"正在向百度获取二维码…",14,Ui.MUTED);status.setGravity(Gravity.CENTER);status.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8));overlay.addView(status);
        AdaptiveLayout.Flow actions=new AdaptiveLayout.Flow(activity);overlay.addView(actions);
        Button refresh=Ui.button(activity,"刷新二维码",this::restart);actions.addView(refresh);
        actions.addView(Ui.button(activity,"官网 / 完成验证",this::official));actions.addView(Ui.button(activity,"返回",activity::finish));
        actions.addView(Ui.button(activity,"退出登录 / 更换账号",()->AccountSessions.ask(activity,Provider.BAIDU,this::close)));
        parent.addView(overlay,new FrameLayout.LayoutParams(-1,-1));restart();refresh.requestFocus();
    }
    private long now(){return SystemClock.elapsedRealtime();}
    private boolean current(String nonce){return !closed&&!paused&&!activity.isFinishing()&&session.accepts(nonce,now());}
    private void clear(){image.setImageDrawable(null);image.setVisibility(View.INVISIBLE);}
    private void cancelAttempt(){session.stop();handler.removeCallbacksAndMessages(null);if(api!=null)api.close();clear();}
    private void restart(){
        if(closed)return;cancelAttempt();String nonce=session.start(now());BaiduQrApi attempt=new BaiduQrApi();api=attempt;errors=0;
        status.setText("正在向百度获取二维码…");
        handler.postDelayed(()->{if(current(nonce)&&image.getVisibility()!=View.VISIBLE)fail("获取二维码超时，请检查电视网络并刷新重试。");},25_000);
        handler.postDelayed(()->{if(!closed&&session.expired(now()))fail("二维码已过期，请按“刷新二维码”。");},LoginSession.MAX_AGE_MS);
        worker.execute(()->{
            try{Bitmap bitmap=LoginQr.decode(attempt.start());handler.post(()->{
                if(!current(nonce)){bitmap.recycle();return;}
                image.setImageBitmap(bitmap);image.setVisibility(View.VISIBLE);status.setText("请用百度网盘 App 扫码，手机确认后会自动继续。");schedulePoll(attempt,nonce);
            });}catch(Exception e){handler.post(()->{if(current(nonce))fail("获取二维码失败："+friendly(e));});}
        });
    }
    private String friendly(Exception e){LoginDiagnostics.record(activity,Provider.BAIDU,"扫码/文件验证",e);return LoginErrors.friendly(e);}
    private void schedulePoll(BaiduQrApi attempt,String nonce){handler.postDelayed(()->{if(current(nonce))poll(attempt,nonce);},1800);}
    private void poll(BaiduQrApi attempt,String nonce){
        worker.execute(()->{
            boolean confirming=false;
            try{
                BaiduQrApi.State state=attempt.poll();
                if(state==BaiduQrApi.State.CONFIRMED){confirming=true;attempt.complete();List<Cookie> cookies=attempt.cookies();
                    boolean loginCookie=false;for(Cookie cookie:cookies)if((cookie.name().equals("BDUSS")||cookie.name().equals("BDUSS_BFESS"))&&!cookie.value().isEmpty())loginCookie=true;
                    if(!loginCookie)throw new IOException("百度尚未建立登录会话，请到官网完成验证。");
                    handler.post(()->{if(current(nonce))saveCookies(cookies,nonce);});return;
                }
                handler.post(()->{
                    if(!current(nonce))return;errors=0;
                    if(state==BaiduQrApi.State.CANCELLED||state==BaiduQrApi.State.EXPIRED){fail("本次扫码已取消或过期，请刷新二维码。");return;}
                    status.setText(state==BaiduQrApi.State.SCANNED?"已扫码，请在手机上确认登录。":"等待手机扫码…");schedulePoll(attempt,nonce);
                });
            }catch(Exception e){boolean confirmationFailed=confirming;handler.post(()->{
                if(!current(nonce))return;
                if(confirmationFailed){fail("手机确认后登录未完成："+friendly(e));return;}
                if(e instanceof java.net.SocketTimeoutException||e instanceof java.io.InterruptedIOException){schedulePoll(attempt,nonce);return;}
                if(++errors>=3){fail("扫码登录未完成："+friendly(e));return;}
                status.setText("正在重新检查扫码状态…");schedulePoll(attempt,nonce);
            });}
        });
    }
    private void saveCookies(List<Cookie> cookies,String nonce){
        status.setText("手机已确认，正在保存登录状态…");CookieManager manager=CookieManager.getInstance();AtomicInteger remaining=new AtomicInteger(cookies.size());java.util.concurrent.atomic.AtomicBoolean failed=new java.util.concurrent.atomic.AtomicBoolean();
        for(Cookie cookie:cookies){String host=cookie.domain();if(host.startsWith("."))host=host.substring(1);String url="https://"+host+cookie.path();
            AccountSessions.remember(Provider.BAIDU,url);
            manager.setCookie(url,cookie.toString(),ok->{if(!Boolean.TRUE.equals(ok))failed.set(true);if(remaining.decrementAndGet()==0&&current(nonce)){if(failed.get()){fail("电视未能保存登录状态，请到官网重新登录。");return;}manager.flush();verifyFiles(nonce);}});
        }
    }
    private void verifyFiles(String nonce){status.setText("手机已确认，正在读取百度文件…");worker.execute(()->{try{new CloudApi(Provider.BAIDU).list("/",1);handler.post(()->{if(current(nonce)){close();enterFiles.run();}});}catch(Exception e){handler.post(()->{if(current(nonce))fail("百度未能读取文件："+friendly(e));});}});}
    private void fail(String message){cancelAttempt();status.setText(message);}
    private void official(){if(closed)return;close();enterWeb.run();}
    boolean visible(){return !closed;}
    void pause(){if(closed)return;paused=true;cancelAttempt();}
    void resume(){if(paused&&!closed){paused=false;restart();}}
    void close(){if(closed)return;closed=true;cancelAttempt();overlay.setVisibility(View.GONE);worker.shutdownNow();}
}
