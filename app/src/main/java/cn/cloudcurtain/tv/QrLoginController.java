package cn.cloudcurtain.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;
import java.util.concurrent.*;

/** Native TV QR presentation; the official WebView retains the login/verification session. */
final class QrLoginController {
    private final Activity activity;private final Provider provider;private final WebView web;private final FrameLayout webHost;
    private final Runnable showOfficial;private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2);private final LoginSession session=new LoginSession();
    private final LinearLayout overlay;private final ImageView qr;private final TextView status;
    private ScriptHandler scriptHandle;private boolean documentStartSupported,pixelProbe,fallbackShown,reparented,supported,closed,paused,probing,decoding,officialMode;private String seen="",script="";
    private long lastProbe;private Button refresh;
    QrLoginController(Activity activity,Provider provider,WebView web,FrameLayout frame,Runnable showOfficial){
        this.activity=activity;this.provider=provider;this.web=web;this.webHost=frame;this.showOfficial=showOfficial;
        overlay=Ui.column(activity);overlay.setGravity(Gravity.CENTER_HORIZONTAL);overlay.setBackgroundColor(Ui.BG);overlay.setPadding(Ui.dp(activity,16),Ui.dp(activity,12),Ui.dp(activity,16),0);
        TextView title=Ui.text(activity,provider.title+" · 扫码登录",24,Ui.TEXT);overlay.addView(title);
        TextView help=Ui.text(activity,"打开手机上的"+provider.title+" App 扫一扫，在手机上确认登录",14,Ui.MUTED);help.setGravity(Gravity.CENTER);help.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8));overlay.addView(help);
        FrameLayout picture=new FrameLayout(activity);overlay.addView(picture,new LinearLayout.LayoutParams(-1,0,1));
        qr=new ImageView(activity){@Override protected void onMeasure(int w,int h){int side=Math.max(0,Math.min(Ui.dp(getContext(),320),Math.min(MeasureSpec.getSize(w),MeasureSpec.getSize(h))));setMeasuredDimension(side,side);}};
        qr.setContentDescription("当前网盘的官方登录二维码");qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));qr.setBackgroundColor(Color.WHITE);qr.setVisibility(View.INVISIBLE);
        picture.addView(qr,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        status=Ui.text(activity,"正在获取官网登录二维码…",14,Ui.MUTED);status.setGravity(Gravity.CENTER);status.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8));overlay.addView(status);
        AdaptiveLayout.Flow actions=new AdaptiveLayout.Flow(activity);overlay.addView(actions);
        refresh=Ui.button(activity,"刷新二维码",this::restart);actions.addView(refresh);
        actions.addView(Ui.button(activity,"已确认，继续",this::manualContinue));
        actions.addView(Ui.button(activity,"官网 / 完成验证",this::official));
        actions.addView(Ui.button(activity,"返回",activity::finish));
        actions.addView(Ui.button(activity,"退出登录 / 更换账号",()->AccountSessions.ask(activity,provider,this::close)));
        frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        try{
            supported=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);documentStartSupported=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
            if(supported)WebViewCompat.addWebMessageListener(web,"CloudCurtainQr",LoginQr.origins(provider),(view,message,origin,mainFrame,reply)->{
                String value=message.getData();if(!LoginQr.originAllowed(provider,origin.toString())||value==null||value.length()>1_400_000)return;
                receive(value);
            });
        }catch(RuntimeException e){supported=false;}
        if(provider==Provider.ALI&&!documentStartSupported){
            ((ViewGroup)web.getParent()).removeView(web);picture.addView(web,0,new FrameLayout.LayoutParams(-1,-1));qr.bringToFront();reparented=true;
        }
        restart();refresh.requestFocus();
    }
    private long now(){return SystemClock.elapsedRealtime();}
    private boolean current(String nonce){return !closed&&!paused&&!activity.isFinishing()&&session.accepts(nonce,now());}
    private void clearCode(){qr.setImageDrawable(null);qr.setVisibility(View.INVISIBLE);seen="";}
    void restart(){
        if(closed)return;handler.removeCallbacks(tick);clearCode();fallbackShown=false;session.start(now());lastProbe=0;
        if(scriptHandle!=null){scriptHandle.remove();scriptHandle=null;}
        status.setText(supported?"正在获取官网登录二维码…":"电视网页组件不支持二维码提取，请选择“官网 / 完成验证”扫码。");
        try{
            script=LoginQr.script(activity,session.nonce);
            if(supported&&documentStartSupported)scriptHandle=WebViewCompat.addDocumentStartJavaScript(web,script,LoginQr.origins(provider));
        }catch(Exception e){status.setText("二维码组件加载失败，请使用官网扫码。");}
        web.loadUrl(provider.loginUrl);handler.postDelayed(tick,1000);
        String attempt=session.nonce;handler.postDelayed(()->{if(current(attempt)&&seen.isEmpty()&&!fallbackShown)fail("未能从官网取得二维码，请选择“官网 / 完成验证”，或刷新重试。");},25_000);
    }
    void pageFinished(String url){if(!closed&&supported&&LoginQr.originAllowed(provider,url))web.evaluateJavascript(script,null);}
    void pageStarted(){if(!closed)clearCode();}
    void fail(String message){if(!closed){session.stop();clearCode();handler.removeCallbacks(tick);status.setText(message);}}
    private void receive(String raw){
        try{
            JSONObject object=new JSONObject(raw);String nonce=object.optString("nonce");if(!current(nonce))return;
            String type=object.optString("type");
            if(type.equals("expired")){fail("二维码已过期，请按“刷新二维码”。");return;}
            if(!type.equals("qr")||decoding)return;
            String value=object.optString("image");if(value.isEmpty()||value.equals(seen))return;
            if(!value.startsWith("data:image/png;base64,")&&!LoginQr.imageUrlAllowed(provider,value))return;
            decoding=true;
            worker.execute(()->{
                Bitmap bitmap=null;try{bitmap=LoginQr.image(provider,value);}catch(Exception ignored){}
                Bitmap result=bitmap;handler.post(()->{
                    decoding=false;if(!current(nonce)){if(result!=null)result.recycle();return;}
                    if(result==null){status.setText("二维码暂时无法显示，可刷新或到官网完成扫码。");return;}
                    seen=value;qr.setImageBitmap(result);qr.setVisibility(View.VISIBLE);status.setText("请用手机扫码并确认。若官网要求验证码，选择“官网 / 完成验证”。");
                });
            });
        }catch(JSONException ignored){}
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(closed||paused)return;
        if(session.expired(now())){fail("本次二维码已停止使用，请按“刷新二维码”重新获取。");return;}
        String nonce=session.nonce;
        if(current(nonce)&&now()-lastProbe>=4000){lastProbe=now();if(provider==Provider.ALI&&!documentStartSupported)probeAliPixels(nonce);
            if(provider.hasNativeFiles()){ /* File access is watched by CloudBrowserActivity, independently of QR expiry. */ }
            else if(LoginQr.originAllowed(provider,web.getUrl())){
                // Only the official main document's visible file list can finish website login.
                web.evaluateJavascript("(function(){function visible(e){return !!e&&e.getBoundingClientRect().height>0&&getComputedStyle(e).visibility!=='hidden';}var list=document.querySelector('[class*=\"file-list\"],.wp-s-pan-table,.nd-main-list');var login=document.querySelector('[class*=\"login-panel\"],[class*=\"login-modal\"],#passport-login-pop');return visible(list)&&!visible(login);})()",result->{if(current(nonce)&&"true".equals(result))official();});
            }
        }
        if(current(nonce)&&seen.isEmpty()&&!fallbackShown)status.setText(supported?"正在等待官网二维码；若迟迟不出现，请选“官网 / 完成验证”。":"电视网页组件不支持二维码提取，请选择“官网 / 完成验证”扫码。");
        handler.postDelayed(this,1000);
    }};
    private void probeAliPixels(String nonce){
        if(pixelProbe||!AliWebSession.allowed(web.getUrl())||web.getWidth()==0||web.getHeight()==0)return;
        pixelProbe=true;
        web.evaluateJavascript(AliQrPixels.FRAME,raw->{
            if(!current(nonce)||!AliWebSession.allowed(web.getUrl())){pixelProbe=false;return;}
            try{
                android.graphics.Rect area=AliQrPixels.bounds(raw,web.getWidth(),web.getHeight());if(area==null){pixelProbe=false;return;}fallbackShown=true;status.setText("请用阿里云盘 App 扫码并确认，登录后自动读取文件。");
                Bitmap frame=AliQrPixels.capture(web,area);
                worker.execute(()->{
                    Bitmap code=null;String value="";try{value=AliQrPixels.decode(frame);if(!value.isEmpty())code=AliQrPixels.qr(value);}catch(Exception ignored){}finally{frame.recycle();}
                    Bitmap result=code;String signature=value;
                    handler.post(()->{pixelProbe=false;if(!current(nonce)){if(result!=null)result.recycle();return;}if(result!=null){if(!signature.equals(seen)){seen=signature;qr.setImageBitmap(result);qr.setVisibility(View.VISIBLE);status.setText("请用阿里云盘 App 扫码并确认，登录后自动读取文件。");}else result.recycle();}});
                });
            }catch(Exception ignored){pixelProbe=false;}
        });
    }
    private void probeFiles(boolean manual){
        if(probing||closed)return;probing=true;String nonce=session.nonce;
        worker.execute(()->{
            boolean success=false;try{new CloudApi(provider).list(provider.root(),1);success=true;}catch(Exception ignored){}
            boolean ok=success;handler.post(()->{
                probing=false;if(!current(nonce))return;
                if(ok){CookieManager.getInstance().flush();close();activity.startActivity(new Intent(activity,CloudFilesActivity.class).putExtra("provider",provider.name()));}
                else if(manual)status.setText("尚未验证到文件访问权限。请在手机确认，或到官网完成验证后再试。");
            });
        });
    }
    void awaitFiles(){session.stop();clearCode();handler.removeCallbacks(tick);status.setText("手机已确认登录，正在读取文件列表…");}
    void fileError(Exception e){status.setText("已取得登录会话，但文件读取失败："+LoginErrors.friendly(e));}
    private void manualContinue(){status.setText("正在检查文件访问权限，手机确认后会自动进入文件列表。");}
    private void official(){
        if(closed)return;
        CookieManager.getInstance().flush();
        close();showOfficial.run();
    }
    void pause(){paused=true;session.stop();handler.removeCallbacks(tick);clearCode();}
    void resume(){if(paused&&!closed){paused=false;restart();}}
    boolean visible(){return !closed&&overlay.getVisibility()==View.VISIBLE;}
    void close(){if(closed)return;closed=true;session.stop();handler.removeCallbacksAndMessages(null);clearCode();overlay.setVisibility(View.GONE);if(reparented){((ViewGroup)web.getParent()).removeView(web);webHost.addView(web,0,new FrameLayout.LayoutParams(-1,-1));reparented=false;}if(scriptHandle!=null)scriptHandle.remove();if(supported)WebViewCompat.removeWebMessageListener(web,"CloudCurtainQr");worker.shutdownNow();}
}
