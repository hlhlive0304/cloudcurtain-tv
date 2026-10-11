package cn.cloudcurtain.tv;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.*;

public class PosterSettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=Ui.page(this,"电影海报设置","海报墙仅展示你网盘里的视频，默认按片名匹配网络封面，豆包可以辅助识别；TMDB 为可选来源。");ScrollView scroll=new ScrollView(this);LinearLayout box=Ui.column(this);scroll.addView(box);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView heading=Ui.text(this,"TMDB · API Key / Read Access Token",21,Ui.TEXT);box.addView(heading);TextView help=Ui.text(this,"在 themoviedb.org 注册账号，进入账号设置 → API 申请自己的密钥。\n可填写 v3 API Key 或 API Read Access Token。填好后，影片库会自动匹配封面、年份、评分和简介。\n\n只发送清理后的片名和年份；网盘账号、文件路径、Cookie 和播放地址不会发送给 TMDB。影片重名或年份不符时保留占位封面，在详情里手动选择。",14,Ui.MUTED);help.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,18));box.addView(help);
        Vault vault=new Vault(this);EditText key=Ui.edit(this,"你的 TMDB 密钥（本机加密保存）",vault.get("tmdb_key"),true);box.addView(key);TextView status=Ui.text(this,new Tmdb(this).configured()?"已配置密钥，尚未进行本次连接测试":"未配置 TMDB · 网络封面匹配仍可使用",14,Ui.MUTED);status.setPadding(0,0,0,Ui.dp(this,12));box.addView(status);
        AdaptiveLayout.Flow actions=new AdaptiveLayout.Flow(this);box.addView(actions);actions.addView(Ui.button(this,"保存密钥",()->{String value=key.getText().toString().trim();if(!Tmdb.validCredential(value)){key.setError("请填写有效的 API Key 或 Read Access Token");return;}try{vault.put("tmdb_key",value);status.setText("密钥已加密保存 · 返回影片库后自动匹配");Ui.toast(this,"TMDB 密钥已保存");}catch(Exception e){Ui.message(this,"保存失败","系统加密存储不可用，密钥未保存。");}}));
        Button test=Ui.button(this,"测试连接",()->{});actions.addView(test);test.setOnClickListener(v->{if(!new Tmdb(this).configured()){Ui.toast(this,"请先填写并保存密钥");return;}test.setEnabled(false);status.setText("正在连接 TMDB 官方 API…");worker.execute(()->{try{new Tmdb(this).verify();runOnUiThread(()->{if(isFinishing())return;status.setText("TMDB 连接成功，可以匹配海报");test.setEnabled(true);});}catch(Exception e){runOnUiThread(()->{if(isFinishing())return;status.setText(e instanceof java.io.IOException?e.getMessage():"连接失败，请检查密钥和网络后重试。");test.setEnabled(true);});}});});
        actions.addView(Ui.button(this,"清除密钥",()->{vault.remove("tmdb_key");key.setText("");status.setText("密钥已清除，已缓存的海报会保留");}));
        box.addView(Ui.button(this,"重新尝试自动匹配",()->{android.content.SharedPreferences cache=getSharedPreferences("movie_metadata",0);android.content.SharedPreferences.Editor edit=cache.edit();for(String name:cache.getAll().keySet())if(name.startsWith("checked:"))edit.remove(name);edit.apply();PosterQueryCache.clear(this);Ui.toast(this,"返回影片库后将重试未匹配的影片");}));
        box.addView(Ui.button(this,"清除本机影片索引",()->new AlertDialog.Builder(this).setTitle("清除影片库？").setMessage("移除本机的影片列表和海报匹配记录，下次可重新扫描。网盘文件、网盘登录和播放进度会保留。") .setNegativeButton("取消",null).setPositiveButton("清除",(d,n)->{MovieLibrary.clearAll(this);getSharedPreferences("movie_metadata",0).edit().clear().apply();PosterQueryCache.clear(this);getSharedPreferences("movie_favorites",0).edit().clear().apply();Ui.toast(this,"本机影片索引已清除");}).show()));
        TextView credits=Ui.text(this,"关于海报数据",20,Ui.TEXT);credits.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,12));box.addView(credits);ImageView logo=new ImageView(this);logo.setImageResource(cn.cloudcurtain.tv.R.drawable.tmdb_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("TMDB");box.addView(logo,new LinearLayout.LayoutParams(Ui.dp(this,93),Ui.dp(this,67)));TextView notice=Ui.text(this,Tmdb.NOTICE+"\n\n默认网络封面：豆瓣电影；补充来源：Wikipedia / Wikimedia。详情的“封面图片来源”可查看原图页面与许可。\\n可选 Metadata and poster images: TMDB · https://www.themoviedb.org\n影片封面与简介不代表云幕 TV 提供影片内容或播放权益。",13,Ui.MUTED);notice.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,18));box.addView(notice);box.addView(Ui.button(this,"返回影片库",this::finish));
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
