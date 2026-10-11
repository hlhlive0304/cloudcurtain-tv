package cn.cloudcurtain.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.net.http.SslError;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Official-origin login. Credentials stay in the app; no password collection. */
public class CloudBrowserActivity extends Activity {
    private Provider provider;private WebView web;private Pointer pointer;private AdaptiveLayout.Flow toolbar;
    private TextView status;private Button playButton;private boolean pointerMode=true;private QrLoginController qrLogin;private BaiduLoginController baiduLogin;private DirectLoginController directLogin;
    private boolean checking,filesOpened,leaving,readingAliSession,aliSessionReady,aliFileErrorShown;private int accountGeneration;private final java.util.concurrent.ExecutorService accountWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,MediaSpec> candidates=new LinkedHashMap<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable scan=new Runnable(){public void run(){
        if(web==null||isFinishing()||leaving)return;
        captureAliSession();
        checkAccount();
        if(provider.allows(web.getUrl()))web.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('video')).map(v=>({url:v.currentSrc||v.src,title:document.title})))",s->{
            try { Object decoded=new JSONTokener(s).nextValue();if(!(decoded instanceof String))return;JSONArray list=new JSONArray((String)decoded);
                for(int i=0;i<list.length();i++){JSONObject v=list.getJSONObject(i);consider(v.optString("url"),null,v.optString("title"));}
            }catch(Exception ignored){}
        });handler.postDelayed(this,2500);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);provider=Provider.read(getIntent().getStringExtra("provider"));accountGeneration=AccountSessions.generation(provider);Ui.full(this);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,16),Ui.dp(this,10),Ui.dp(this,16),Ui.dp(this,6));setContentView(root);
        toolbar=new AdaptiveLayout.Flow(this);root.addView(toolbar);
        toolbar.addView(Ui.button(this,"返回",this::finish));
        toolbar.addView(Ui.button(this,"官网首页",()->{candidates.clear();updateCount();web.loadUrl(provider.loginUrl);setPointer(true);}));
        if(provider.hasNativeFiles())toolbar.addView(Ui.button(this,"文件模式",this::openFiles));
        toolbar.addView(Ui.button(this,"退出登录 / 更换账号",()->AccountSessions.ask(this,provider,this::stopLogin)));
        playButton=Ui.button(this,"原生播放 (0)",this::chooseStream);toolbar.addView(playButton);
        toolbar.addView(Ui.button(this,"网页操作",()->setPointer(true)));
        toolbar.addView(Ui.button(this,"向下翻页",()->{web.pageDown(false);setPointer(true);}));
        toolbar.addView(Ui.button(this,"向上翻页",()->{web.pageUp(false);setPointer(true);}));
        toolbar.addView(Ui.button(this,"刷新",()->{candidates.clear();updateCount();web.reload();setPointer(true);}));
        status=Ui.text(this,provider.title+" · 在官网扫码登录。方向键移动指针，确定点击；菜单键打开工具栏。",13,Ui.MUTED);status.setPadding(0,0,0,Ui.dp(this,8));root.addView(status);
        FrameLayout frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        try{web=new WebView(this);}catch(Exception e){Ui.message(this,"缺少网页组件","电视的 Android System WebView 不可用，需要先安装或更新系统网页组件。");return;}
        web.setBackgroundColor(Color.WHITE);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
        AccountSessions.register(web,provider);
        pointer=new Pointer();frame.addView(pointer,new FrameLayout.LayoutParams(-1,-1));
        WebSettings w=web.getSettings();w.setJavaScriptEnabled(true);w.setDomStorageEnabled(true);w.setUserAgentString(Net.UA);
        w.setLoadWithOverviewMode(true);w.setUseWideViewPort(true);w.setBuiltInZoomControls(true);w.setDisplayZoomControls(false);
        w.setAllowFileAccess(false);w.setAllowContentAccess(false);w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        w.setMediaPlaybackRequiresUserGesture(true);w.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int p){if(p<100)status.setText(provider.title+" · 正在加载 "+p+"% · 菜单键切换工具栏");else status.setText(provider.title+" · "+UrlRules.host(v.getUrl())+" · 方向键移动 / 确定点击 / 菜单键切换");}});
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                String url=req.getUrl().toString();
                if(!req.isForMainFrame())return false;
                if(UrlRules.mediaUrl(url)){consider(url,req.getRequestHeaders(),web.getTitle());return true;}
                if(!provider.allows(url)){status.setText("该跳转不属于当前网盘官网，已停止。请使用网页扫码登录。");return true;}return false;
            }
            @Override public void onPageStarted(WebView v,String u,Bitmap icon){AccountSessions.remember(provider,u);candidates.clear();updateCount();if(qrLogin!=null)qrLogin.pageStarted();}
            @Override public void onPageFinished(WebView v,String u){CookieManager.getInstance().flush();if(qrLogin!=null)qrLogin.pageFinished(u);}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();status.setText("网站证书验证失败。请检查电视时间和网络。");if(qrLogin!=null)qrLogin.fail("网站证书验证失败，请检查电视时间和网络。");}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())status.setText("官网加载失败，请检查电视联网后点击刷新。");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                AccountSessions.remember(provider,r.getUrl().toString());
                if("GET".equals(r.getMethod())&&UrlRules.mediaUrl(r.getUrl().toString())){
                    String u=r.getUrl().toString();Map<String,String> headers=new HashMap<>(r.getRequestHeaders());
                    handler.post(()->consider(u,headers,web==null?"网盘视频":web.getTitle()));
                }return null;
            }
        });
        web.setDownloadListener((url,ua,disposition,mime,length)->{
            if(UrlRules.isWeb(url)&&(UrlRules.mediaUrl(url)||(mime!=null&&mime.startsWith("video/")))){consider(url,Net.headers("User-Agent",ua),web.getTitle());chooseStream();}
            else Ui.toast(this,"此处只接收视频播放地址，不安装网站下载的软件。");
        });
        if(getIntent().getBooleanExtra("scan_login",false)){
            toolbar.setVisibility(View.GONE);status.setVisibility(View.GONE);setPointer(false);
            if(provider==Provider.BAIDU){web.setVisibility(View.INVISIBLE);baiduLogin=new BaiduLoginController(this,frame,this::showOfficial,this::openFiles);}
            else if(provider==Provider.TIANYI){web.setVisibility(View.INVISIBLE);directLogin=new DirectLoginController(this,provider,frame,this::showOfficial,this::openFiles);}
            else qrLogin=new QrLoginController(this,provider,web,frame,()->{toolbar.setVisibility(View.VISIBLE);status.setVisibility(View.VISIBLE);setPointer(true);});
        }else{web.loadUrl(provider.loginUrl);setPointer(true);}
    }
    private void showOfficial(){toolbar.setVisibility(View.VISIBLE);status.setVisibility(View.VISIBLE);web.setVisibility(View.VISIBLE);web.loadUrl(provider.loginUrl);setPointer(true);}
    private void openFiles(){if(filesOpened||leaving||isFinishing())return;filesOpened=true;handler.removeCallbacks(scan);if(qrLogin!=null)qrLogin.close();if(baiduLogin!=null)baiduLogin.close();if(directLogin!=null)directLogin.close();try{startActivity(new Intent(this,CloudFilesActivity.class).putExtra("provider",provider.name()));finish();}catch(RuntimeException e){filesOpened=false;LoginDiagnostics.record(this,provider,"打开文件页",e);status.setVisibility(View.VISIBLE);toolbar.setVisibility(View.VISIBLE);status.setText("文件页未能打开，请重试或查看登录诊断。");}}
    private void stopLogin(){leaving=true;handler.removeCallbacksAndMessages(null);if(qrLogin!=null)qrLogin.close();if(baiduLogin!=null)baiduLogin.close();if(directLogin!=null)directLogin.close();accountWorker.shutdownNow();if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.clearHistory();}}
    private void captureAliSession(){
        if(provider!=Provider.ALI||readingAliSession||leaving||filesOpened||web==null)return;
        String url=web.getUrl();if(!AliWebSession.allowed(url))return;
        readingAliSession=true;int generation=accountGeneration;
        web.evaluateJavascript(AliWebSession.SCRIPT,value->{
            readingAliSession=false;
            if(leaving||filesOpened||isFinishing()||generation!=AccountSessions.generation(provider)||!AliWebSession.allowed(web.getUrl()))return;
            try{
                JSONObject token=AliWebSession.parse(url,value);if(token==null)return;
                Vault vault=new Vault(this);String old=vault.get("ali_token");
                if(!old.isEmpty()&&new JSONObject(old).optString("access_token").equals(token.optString("access_token")))return;
                AliTokenSession.save(token);android.util.Log.i("CloudLoginState","ALI / TOKEN_SAVED");
                // A completed sign-in may enter files; explicitly opening the drive website remains there.
                if(qrLogin!=null&&qrLogin.visible()){aliSessionReady=true;qrLogin.awaitFiles();checkAccount();}
                else if(android.net.Uri.parse(url).getPath().startsWith("/sign/")){
                    accountWorker.execute(()->{try{new CloudApi(provider).list(provider.root(),1);handler.post(()->{if(!leaving&&generation==AccountSessions.generation(provider))openFiles();});}catch(Exception e){handler.post(()->{if(!leaving){LoginDiagnostics.record(this,provider,"官网登录/文件验证",e);status.setText(LoginErrors.friendly(e));if(qrLogin!=null&&qrLogin.visible())qrLogin.fileError(e);}});}});
                }
            }catch(Exception e){LoginDiagnostics.record(this,provider,"官网登录会话",e);status.setText(LoginErrors.friendly(e));if(qrLogin!=null&&qrLogin.visible())qrLogin.fileError(e);}
        });
    }
    private void checkAccount(){
        if(!(qrLogin!=null&&qrLogin.visible())&&!(baiduLogin!=null&&baiduLogin.visible())&&!(directLogin!=null&&directLogin.visible()))return;
        if(checking||filesOpened||leaving)return;checking=true;int generation=accountGeneration;
        accountWorker.execute(()->{boolean ok=false;try{new CloudApi(provider).list(provider.root(),1);ok=true;}catch(Exception e){if(provider==Provider.ALI&&aliSessionReady)handler.post(()->{if(!leaving&&!filesOpened&&!aliFileErrorShown){aliFileErrorShown=true;LoginDiagnostics.record(this,provider,"官网登录/文件验证",e);if(qrLogin!=null)qrLogin.fileError(e);}});}boolean success=ok;handler.post(()->{checking=false;if(success&&!leaving&&!isFinishing()&&generation==AccountSessions.generation(provider)&&hasWindowFocus())openFiles();});});
    }
    private void consider(String url,Map<String,String> headers,String title){
        if(!UrlRules.isWeb(url)||candidates.containsKey(url))return;
        // Only direct video URLs or an HTML video element's HTTP(S) source reach this method.
        MediaSpec m=new MediaSpec(url,title==null||title.isEmpty()?provider.title+"视频":title);m.provider=provider.name();
        m.headers.put("User-Agent",Net.UA);m.headers.put("Referer",provider.origin);
        if(headers!=null)for(Map.Entry<String,String> h:headers.entrySet()){
            if(Arrays.asList("user-agent","referer","origin","authorization").contains(h.getKey().toLowerCase(Locale.ROOT)))m.headers.put(h.getKey(),h.getValue());
        }
        if(candidates.size()>=30)candidates.remove(candidates.keySet().iterator().next());candidates.put(url,m);updateCount();
    }
    private void updateCount(){if(playButton!=null)playButton.setText("原生播放 ("+candidates.size()+")");}
    private void chooseStream(){
        if(candidates.isEmpty()){Ui.message(this,"还没有发现视频地址","请先在网盘官网点开一部电影并开始播放，再点这里。\n\n如果网页只使用 blob、DRM 或加密视频，本版本无法把它交给原生播放器；可继续用官网播放器观看。");return;}
        List<MediaSpec> all=new ArrayList<>(candidates.values());String[] names=new String[all.size()];
        for(int i=0;i<all.size();i++){MediaSpec m=all.get(i);String type=UrlRules.mediaMime(m.url);names[i]=(i+1)+". "+(type==null?"视频":type.contains("dash")?"DASH":"HLS")+" · "+UrlRules.host(m.url);}
        new AlertDialog.Builder(this).setTitle("选择已发现的视频流").setItems(names,(d,n)->{
            web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);
            PlayerActivity.open(this,all.get(n));
        }).setNegativeButton("取消",null).show();
    }
    private void setPointer(boolean on){pointerMode=on;if(pointer!=null)pointer.setVisibility(on?View.VISIBLE:View.GONE);if(on&&web!=null)web.requestFocus();else if(toolbar!=null)toolbar.getChildAt(0).requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if((qrLogin!=null&&qrLogin.visible())||(baiduLogin!=null&&baiduLogin.visible())||(directLogin!=null&&directLogin.visible()))return super.dispatchKeyEvent(e);
        if(web==null)return super.dispatchKeyEvent(e);
        if(e.getKeyCode()==KeyEvent.KEYCODE_MENU){if(e.getAction()==KeyEvent.ACTION_DOWN)setPointer(!pointerMode);return true;}
        if(pointerMode){int k=e.getKeyCode();
            if(k>=KeyEvent.KEYCODE_DPAD_UP&&k<=KeyEvent.KEYCODE_DPAD_RIGHT){
                if(e.getAction()==KeyEvent.ACTION_DOWN){int step=Ui.dp(this,e.getRepeatCount()>8?24:12);pointer.move(k==21?-step:k==22?step:0,k==19?-step:k==20?step:0);}return true;
            }
            if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){if(e.getAction()==KeyEvent.ACTION_DOWN){long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,pointer.x,pointer.y,0);MotionEvent up=MotionEvent.obtain(t,t+60,MotionEvent.ACTION_UP,pointer.x,pointer.y,0);web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();}return true;}
        }return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){if((qrLogin!=null&&qrLogin.visible())||(baiduLogin!=null&&baiduLogin.visible())||(directLogin!=null&&directLogin.visible())){finish();return;}if(web!=null&&web.canGoBack()){web.goBack();setPointer(true);}else finish();}
    @Override protected void onResume(){super.onResume();if(accountGeneration!=AccountSessions.generation(provider)){finish();return;}if(web!=null){web.onResume();if(qrLogin!=null)qrLogin.resume();if(baiduLogin!=null)baiduLogin.resume();if(directLogin!=null)directLogin.resume();handler.post(scan);}}
    @Override protected void onPause(){handler.removeCallbacks(scan);if(qrLogin!=null)qrLogin.pause();if(baiduLogin!=null)baiduLogin.pause();if(directLogin!=null)directLogin.pause();if(web!=null){web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);web.onPause();CookieManager.getInstance().flush();}super.onPause();}
    @Override protected void onDestroy(){leaving=true;accountWorker.shutdownNow();handler.removeCallbacksAndMessages(null);if(qrLogin!=null)qrLogin.close();if(baiduLogin!=null)baiduLogin.close();if(directLogin!=null)directLogin.close();if(web!=null){AccountSessions.unregister(web);web.stopLoading();if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
    private class Pointer extends View {
        float x=300,y=200;Paint paint=new Paint(3);
        Pointer(){super(CloudBrowserActivity.this);setClickable(false);setFocusable(false);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){x=w/2f;y=h/2f;}
        void move(int dx,int dy){x=Math.max(4,Math.min(getWidth()-4,x+dx));y=Math.max(4,Math.min(getHeight()-4,y+dy));invalidate();}
        @Override protected void onDraw(Canvas c){paint.setColor(0xCC102334);c.drawCircle(x,y,Ui.dp(getContext(),10),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setColor(Ui.ACCENT);c.drawCircle(x,y,Ui.dp(getContext(),9),paint);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(x,y,Ui.dp(getContext(),2),paint);}
    }
}
