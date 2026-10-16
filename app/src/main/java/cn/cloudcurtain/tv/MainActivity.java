package cn.cloudcurtain.tv;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private LinearLayout historySection;private TextView historyStatus;private HistoryOpener historyOpener;private int historyRevision;private boolean closed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.full(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout page=new AdaptiveLayout.Page(this);scroll.addView(page,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        TextView brand=Ui.text(this,"云幕  /  TV",29,Ui.TEXT);brand.setTypeface(null,Typeface.BOLD);page.addView(brand);
        AdaptiveLayout.Flow header=new AdaptiveLayout.Flow(this);page.addView(header,new LinearLayout.LayoutParams(-1,-2));
        TextView caption=Ui.text(this,"你的网盘，你的大屏影院",14,Ui.MUTED);caption.setPadding(0,Ui.dp(this,8),Ui.dp(this,28),Ui.dp(this,16));header.addView(caption);
        TextView version=Ui.text(this,AppVersion.name(this)+" · 实验预览版",14,Ui.MUTED);version.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,16));header.addView(version);
        TextView hero=Ui.text(this,"今晚，选一部好电影。",34,Ui.TEXT);hero.setTag("home_hero");hero.setTypeface(null,Typeface.BOLD);
        hero.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,12));page.addView(hero);
        TextView guide=Ui.text(this,"选择网盘，手机扫码确认登录，选片后切换原生播放。无需电脑或 NAS 中转。",15,Ui.MUTED);guide.setTag("home_guide");page.addView(guide);
        AdaptiveLayout.Cards cards=new AdaptiveLayout.Cards(this);cards.setTag("home_cards");
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);clp.setMargins(0,Ui.dp(this,28),0,Ui.dp(this,32));page.addView(cards,clp);
        for(Provider p:Provider.values()) {
            LinearLayout card=Ui.column(this);card.setPadding(Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,16));card.setFocusable(true);card.setClickable(true);
            card.setBackground(Ui.bg(Ui.PANEL,0,this));cards.addView(card);
            TextView icon=Ui.text(this,p.shortTitle.substring(0,1),30,p.color);icon.setTypeface(null,Typeface.BOLD);card.addView(icon);
            TextView title=Ui.text(this,p.title,21,Ui.TEXT);title.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,8));card.addView(title);
            card.addView(Ui.text(this,p.hasNativeFiles()?"扫码登录 · 文件模式":"扫码登录 · 网页选片",12,Ui.MUTED));
            card.setOnFocusChangeListener((v,f)->card.setBackground(Ui.bg(f?0xFF223B4C:Ui.PANEL,f?p.color:0,this)));
            card.setOnClickListener(v->startActivity(new Intent(this,CloudBrowserActivity.class).putExtra("provider",p.name()).putExtra("scan_login",true)));
        }
        AdaptiveLayout.Flow actions=new AdaptiveLayout.Flow(this);actions.setTag("home_actions");page.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        actions.addView(Ui.button(this,"电影海报墙",()->startActivity(new Intent(this,MovieWallActivity.class))));
        Button historyButton=Ui.button(this,"历史播放记录",()->startActivity(new Intent(this,HistoryActivity.class)));historyButton.setId(View.generateViewId());historyButton.setTag("home_history_all");actions.addView(historyButton);
        actions.addView(Ui.button(this,"＋ 播放视频直链",()->Ui.ask(this,"播放视频直链","https://…/video.mp4 或 m3u8","",url->{
            if(!UrlRules.isWeb(url)){Ui.message(this,"地址不正确","请输入 HTTP 或 HTTPS 视频地址，普通分享网页不能直接播放。");return;}
            PlayerActivity.open(this,new MediaSpec(url,"网络视频"));
        })));
        actions.addView(Ui.button(this,"打开本地视频",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE);
            try{startActivityForResult(i,100);}catch(ActivityNotFoundException e){Ui.message(this,"没有系统文件选择器","这台电视未提供文件选择器。可以用支持分享文件的文件管理器，或使用视频直链。");}
        }));
        actions.addView(Ui.button(this,"字幕与设置",()->startActivity(new Intent(this,SettingsActivity.class))));
        actions.addView(Ui.button(this,"检查更新",()->startActivity(new Intent(this,UpdateActivity.class))));
        actions.addView(Ui.button(this,"使用说明",()->Ui.message(this,"第一部电影怎么播放","1. 选择网盘，在官网页面扫码或输入验证码登录。\n2. 遥控器方向键移动圆形指针，确定键点击。菜单键切换顶部工具栏。\n3. 手机确认后自动检查文件访问并进入个人空间。文件页和设置里可退出并更换账号。\n4. 播放中按菜单键，选择内嵌字幕、同目录字幕、本地字幕或在线字幕。\n\n当前为实验版：官网变化、验证码、受保护的视频、会员和编码限制都可能影响播放。海报墙可扫描已登录网盘并按片名选片，联网封面需填写自己的 TMDB 密钥。")));
        historyOpener=new HistoryOpener(this);historySection=Ui.column(this);historySection.setTag("home_history");historySection.setVisibility(View.GONE);page.addView(historySection,new LinearLayout.LayoutParams(-1,-2));
        historyStatus=Ui.text(this,"",13,Ui.MUTED);historyStatus.setMaxLines(2);page.addView(historyStatus);
        TextView foot=Ui.text(this,"个人网盘文件    ·    支持退出并更换账号    ·    不内置片源",12,Ui.MUTED);
        foot.setPadding(0,Ui.dp(this,18),0,0);page.addView(foot);
        cards.getChildAt(0).requestFocus();
        switchAccount(getIntent());
    }
    @Override protected void onResume(){super.onResume();int ticket=++historyRevision;PlaybackHistory.IO.execute(()->{try{java.util.List<PlaybackHistory.Entry> entries=new PlaybackHistory(this).entries();runOnUiThread(()->{if(!closed&&ticket==historyRevision)showHistory(entries);});}catch(Exception error){runOnUiThread(()->{if(!closed&&ticket==historyRevision)historyStatus.setText("播放记录暂未读取，可在历史播放记录中重试。");});}});}
    private void showHistory(java.util.List<PlaybackHistory.Entry> entries){
        String focused=getCurrentFocus()==null?"":String.valueOf(getCurrentFocus().getTag());historySection.removeAllViews();historySection.setVisibility(entries.isEmpty()?View.GONE:View.VISIBLE);historyStatus.setText("");
        findViewById(android.R.id.content).findViewWithTag("home_hero").setVisibility(entries.isEmpty()?View.VISIBLE:View.GONE);findViewById(android.R.id.content).findViewWithTag("home_guide").setVisibility(entries.isEmpty()?View.VISIBLE:View.GONE);
        View all=findViewById(android.R.id.content).findViewWithTag("home_history_all");all.setNextFocusDownId(View.NO_ID);
        TextView title=Ui.text(this,"最近观看 · 点击继续",19,Ui.TEXT);title.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,10));historySection.addView(title);AdaptiveLayout.Flow cards=new AdaptiveLayout.Flow(this);cards.setTag("home_history_cards");historySection.addView(cards,new LinearLayout.LayoutParams(-1,-2));View restore=null;
        for(int n=0;n<Math.min(3,entries.size());n++){PlaybackHistory.Entry entry=entries.get(n);HistoryCard card=new HistoryCard(this,entry,true,()->historyOpener.open(entry,false,historyStatus));ViewGroup.MarginLayoutParams lp=new ViewGroup.MarginLayoutParams(Ui.dp(this,260),-2);lp.setMargins(0,0,Ui.dp(this,12),Ui.dp(this,10));cards.addView(card,lp);if(focused.equals(card.getTag()))restore=card;}
        if(cards.getChildCount()>0){all.setNextFocusDownId(cards.getChildAt(0).getId());cards.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{for(int i=0;i<cards.getChildCount();i++)cards.getChildAt(i).setNextFocusUpId(cards.getChildAt(i).getTop()==cards.getChildAt(0).getTop()?all.getId():View.NO_ID);});}
        if(restore!=null)restore.requestFocus();else if(focused.startsWith("history:"))findViewById(android.R.id.content).findViewWithTag("home_history_all").requestFocus();
    }
    @Override protected void onStop(){historyRevision++;super.onStop();}
    @Override protected void onDestroy(){closed=true;historyRevision++;historyOpener.close();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);switchAccount(intent);}
    private void switchAccount(Intent intent){String provider=intent.getStringExtra("switch_provider");if(provider==null)return;intent.removeExtra("switch_provider");startActivity(new Intent(this,CloudBrowserActivity.class).putExtra("provider",provider).putExtra("scan_login",true));}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==100&&result==RESULT_OK&&data!=null&&data.getData()!=null){
        try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String name="本地视频";try(android.database.Cursor cursor=getContentResolver().query(data.getData(),new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(Exception ignored){}
        PlayerActivity.open(this,new MediaSpec(data.getData().toString(),name));
    }}
}
