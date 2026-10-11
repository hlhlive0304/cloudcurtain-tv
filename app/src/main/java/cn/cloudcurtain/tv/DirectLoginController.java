package cn.cloudcurtain.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.*;
import okhttp3.Cookie;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Native QR for Tianyi and Ali; completion is verified by reading the user's file list. */
final class DirectLoginController {
    private final Activity activity;private final Provider provider;private final Runnable official,files;
    private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LoginSession session=new LoginSession();private final LinearLayout overlay;private final ImageView image;private final TextView status;
    private TianyiQrApi tianyi;private AliQrApi ali;private boolean closed,paused;private int errors;
    DirectLoginController(Activity a,Provider p,FrameLayout parent,Runnable official,Runnable files){
        activity=a;provider=p;this.official=official;this.files=files;
        overlay=Ui.column(a);overlay.setBackgroundColor(Ui.BG);overlay.setPadding(Ui.dp(a,24),Ui.dp(a,12),Ui.dp(a,24),0);overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.addView(Ui.text(a,p.title+" · 扫码登录",24,Ui.TEXT));TextView help=Ui.text(a,"打开手机"+p.title+" App 扫一扫，并确认登录",15,Ui.MUTED);help.setPadding(0,Ui.dp(a,10),0,Ui.dp(a,10));overlay.addView(help);
        FrameLayout area=new FrameLayout(a);overlay.addView(area,new LinearLayout.LayoutParams(-1,0,1));image=new ImageView(a){@Override protected void onMeasure(int w,int h){int s=Math.min(Ui.dp(getContext(),320),Math.min(MeasureSpec.getSize(w),MeasureSpec.getSize(h)));setMeasuredDimension(s,s);}};image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(p.title+"官方登录二维码");area.addView(image,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        status=Ui.text(a,"正在获取二维码…",15,Ui.MUTED);status.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,8));overlay.addView(status);
        AdaptiveLayout.Flow bar=new AdaptiveLayout.Flow(a);overlay.addView(bar);Button refresh=Ui.button(a,"刷新二维码",this::restart);bar.addView(refresh);bar.addView(Ui.button(a,"官网 / 完成验证",()->{close();official.run();}));bar.addView(Ui.button(a,"退出登录 / 更换账号",()->AccountSessions.ask(a,p,this::close)));bar.addView(Ui.button(a,"返回",a::finish));parent.addView(overlay,new FrameLayout.LayoutParams(-1,-1));restart();refresh.requestFocus();
    }
    private boolean current(String nonce){return !closed&&!paused&&!activity.isFinishing()&&session.accepts(nonce,SystemClock.elapsedRealtime());}
    private void cancel(){session.stop();handler.removeCallbacksAndMessages(null);if(tianyi!=null)tianyi.close();if(ali!=null)ali.close();image.setImageDrawable(null);}
    private void restart(){if(closed)return;cancel();errors=0;String nonce=session.start(SystemClock.elapsedRealtime());TianyiQrApi t=provider==Provider.TIANYI?new TianyiQrApi():null;AliQrApi l=provider==Provider.ALI?new AliQrApi():null;tianyi=t;ali=l;status.setText("正在向"+provider.title+"获取二维码…");
        handler.postDelayed(()->{if(current(nonce)&&image.getDrawable()==null)fail("获取二维码超时，请刷新。可检查电视网络或进入官网。");},45000);
        handler.postDelayed(()->{if(!closed&&session.expired(SystemClock.elapsedRealtime()))fail("二维码已过期，请刷新二维码。");},LoginSession.MAX_AGE_MS);
        worker.execute(()->{try{Bitmap b=LoginQr.decode(t!=null?t.start():l.start());handler.post(()->{if(!current(nonce)){b.recycle();return;}image.setImageBitmap(b);status.setText("请扫码，手机确认后会自动进入文件列表。");schedule(nonce,t,l);});}catch(Exception e){handler.post(()->{if(current(nonce))fail("获取二维码失败："+message(e));});}});
    }
    private String message(Exception e){LoginDiagnostics.record(activity,provider,"扫码/文件验证",e);return LoginErrors.friendly(e);}
    private void fail(String text){cancel();status.setText(text);}
    private void schedule(String n,TianyiQrApi t,AliQrApi l){handler.postDelayed(()->{if(current(n))poll(n,t,l);},1800);}
    private void poll(String n,TianyiQrApi t,AliQrApi l){worker.execute(()->{try{
        String state=t!=null?t.poll().name():l.poll().name();
        if(state.equals("CONFIRMED")){
            if(t!=null){t.complete();List<Cookie> cookies=t.cookies();handler.post(()->{if(current(n)){try{new Vault(activity).put("tianyi_session",t.sessionKey());save(n,cookies);}catch(Exception e){fail("无法安全保存天翼会话。"+message(e));}}});}
            else{String token=l.token().toString();handler.post(()->{if(!current(n))return;try{new Vault(activity).put("ali_token",token);verify(n);}catch(Exception e){LoginDiagnostics.record(activity,provider,"保存登录",e);fail("无法安全保存阿里登录凭证。");}});}return;
        }
        handler.post(()->{if(!current(n))return;errors=0;if(state.equals("CANCELLED")){message(new Net.ServiceException(200,"QR_CANCELLED"));fail("本次二维码会话已被服务器取消，请刷新后重新扫码。");return;}if(state.equals("EXPIRED")||state.equals("VERIFICATION_REQUIRED")){fail("二维码已过期或需额外验证，请刷新或进入官网。");return;}status.setText(state.equals("SCANNED")?"已扫码，请在手机确认登录。":"等待手机扫码…");schedule(n,t,l);});
    }catch(Exception e){handler.post(()->{if(!current(n))return;if(++errors>=3){fail(message(e));return;}status.setText("正在重新检查扫码状态…");schedule(n,t,l);});}});}
    private void save(String n,List<Cookie> cookies){if(cookies.isEmpty()){fail("天翼未返回登录会话，请重新扫码。");return;}status.setText("正在保存登录状态…");CookieManager cm=CookieManager.getInstance();AtomicInteger remaining=new AtomicInteger(cookies.size());AtomicBoolean failed=new AtomicBoolean();for(Cookie c:cookies){String url="https://"+c.domain()+c.path();AccountSessions.remember(provider,url);cm.setCookie(url,c.toString(),ok->{if(!Boolean.TRUE.equals(ok))failed.set(true);if(remaining.decrementAndGet()==0&&current(n)){cm.flush();if(failed.get())fail("电视未能保存登录状态，请重试。");else verify(n);}});}}
    private void verify(String n){status.setText("手机已确认，正在读取文件列表…");worker.execute(()->{try{new CloudApi(provider).list(provider.root(),1);handler.post(()->{if(current(n)){close();files.run();}});}catch(Exception e){handler.post(()->{if(current(n))fail("登录确认后文件读取失败："+message(e));});}});}
    boolean visible(){return !closed;}
    void pause(){if(closed)return;paused=true;cancel();}
    void resume(){if(paused&&!closed){paused=false;restart();}}
    void close(){if(closed)return;closed=true;cancel();overlay.setVisibility(View.GONE);worker.shutdownNow();}
}
