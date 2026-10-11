package cn.cloudcurtain.tv;

import android.app.*;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import java.util.concurrent.*;

public class SettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){super.onCreate(state);
        LinearLayout page=Ui.page(this,"字幕与设置","内嵌字幕 / 同目录字幕 / SRT、ASS、VTT、TTML · 中文编码自动识别");
        ScrollView scroll=new ScrollView(this);LinearLayout box=Ui.column(this);scroll.addView(box);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        box.addView(Ui.button(this,"检查应用更新 · "+AppVersion.name(this),()->startActivity(new android.content.Intent(this,UpdateActivity.class))));
        TextView accounts=Ui.text(this,"网盘账号",21,Ui.TEXT);accounts.setPadding(0,0,0,Ui.dp(this,12));box.addView(accounts);
        for(Provider p:Provider.values())box.addView(Ui.button(this,p.title+" · 退出登录 / 更换账号",()->AccountSessions.ask(this,p,()->{})));
        box.addView(Ui.button(this,"电影海报 · TMDB 设置",()->startActivity(new android.content.Intent(this,PosterSettingsActivity.class))));
        Vault vault=new Vault(this);
        TextView doubaoTitle=Ui.text(this,"豆包 · 在线字幕默认匹配",21,Ui.TEXT);doubaoTitle.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,10));box.addView(doubaoTitle);
        TextView doubaoHelp=Ui.text(this,"使用火山方舟官方接口识别片名并推荐实际字幕候选。在线字幕默认优先使用豆包；只在主动点击匹配时发送片名和字幕标签。\n匹配网盘字幕无需 OpenSubtitles；搜索外部字幕文件仍需字幕服务配置。模型需在火山引擎开通。",14,Ui.MUTED);doubaoHelp.setPadding(0,0,0,Ui.dp(this,14));box.addView(doubaoHelp);
        EditText doubaoKey=Ui.edit(this,"豆包 API Key",vault.get("doubao_api_key"),true);box.addView(doubaoKey);
        EditText doubaoModel=Ui.edit(this,"模型 ID / ep- 接入点 ID",vault.get("doubao_model").isEmpty()?Doubao.DEFAULT_MODEL:vault.get("doubao_model"),false);box.addView(doubaoModel);
        TextView doubaoStatus=Ui.text(this,doubaoKey.getText().length()>0?"密钥已加密保存 · 可测试模型连接":"尚未配置豆包密钥",13,Ui.MUTED);doubaoStatus.setPadding(0,0,0,Ui.dp(this,12));box.addView(doubaoStatus);
        AdaptiveLayout.Flow doubaoActions=new AdaptiveLayout.Flow(this);box.addView(doubaoActions);
        if(PersonalConfig.available(this))doubaoActions.addView(Ui.button(this,"导入我的豆包配置",()->{
            LinearLayout form=Ui.column(this);form.setPadding(Ui.dp(this,22),Ui.dp(this,12),Ui.dp(this,22),0);EditText code=Ui.edit(this,"输入随安装包提供的 20 位配置码","",true);code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);form.addView(code);
            new AlertDialog.Builder(this).setTitle("一次导入 · 本机加密保存").setMessage("配置已加密封装；输入配置码后即可保存到本电视，之后无需重复输入。配置码与安装包分开保管。").setView(form).setNegativeButton("取消",null).setPositiveButton("导入",(d,n)->{String value=code.getText().toString().trim();worker.execute(()->{try{PersonalConfig.importPreset(this,value);runOnUiThread(()->{if(!isFinishing()){doubaoKey.setText(vault.get("doubao_api_key"));doubaoModel.setText(vault.get("doubao_model"));doubaoStatus.setText("个人密钥已导入 · 请测试模型连接");Ui.toast(this,"豆包密钥已加密保存到本电视");}});}catch(Exception e){runOnUiThread(()->{if(!isFinishing())Ui.message(this,"导入失败","配置码不正确或系统加密存储不可用，密钥未导入。");});}});}).show();
        }));
        doubaoActions.addView(Ui.button(this,"保存豆包配置",()->{
            String value=doubaoKey.getText().toString().trim(),model=doubaoModel.getText().toString().trim();if(value.isEmpty()){doubaoKey.setError("请输入豆包 API Key");return;}if(!model.matches("[A-Za-z0-9._-]{1,120}")){doubaoModel.setError("请输入模型 ID");return;}
            try{vault.put("doubao_api_key",value);vault.put("doubao_model",model);doubaoStatus.setText("密钥和模型已保存 · 可测试连接");Ui.toast(this,"豆包配置已加密保存");}catch(Exception e){Ui.message(this,"保存失败","系统加密存储不可用，配置未保存。");}
        }));
        Button testDoubao=Ui.button(this,"测试豆包连接",()->{});testDoubao.setOnClickListener(v->{if(vault.get("doubao_api_key").isEmpty()){Ui.toast(this,"先导入或保存配置");return;}testDoubao.setEnabled(false);worker.execute(()->{
            try{Doubao.Match result=new Doubao(this).match("流浪地球.2019.1080p.mkv",java.util.Collections.emptyList());runOnUiThread(()->{if(!isFinishing()){doubaoStatus.setText("模型连接成功 · 智能字幕匹配可用");testDoubao.setEnabled(true);}});}
            catch(Exception e){runOnUiThread(()->{if(!isFinishing()){doubaoStatus.setText("连接未通过 · 请检查模型开通状态");Ui.message(this,"豆包连接未成功",e.getMessage());testDoubao.setEnabled(true);}});}
        });});doubaoActions.addView(testDoubao);
        doubaoActions.addView(Ui.button(this,"清除豆包配置",()->{vault.remove("doubao_api_key");vault.remove("doubao_model");doubaoKey.setText("");doubaoStatus.setText("尚未配置豆包密钥");Ui.toast(this,"豆包配置已清除");}));

        box.addView(Ui.text(this,"外部字幕搜索 · OpenSubtitles（可选）",21,Ui.TEXT));
        TextView help=Ui.text(this,"搜索需自备 API Key；下载可能需要登录并受服务商额度限制。\n这里只连接官方 API，不内置他人的密钥。密钥和登录令牌在电视上加密保存，密码不保存。",14,Ui.MUTED);help.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,18));box.addView(help);
        EditText key=Ui.edit(this,"OpenSubtitles API Key",vault.get("os_api"),true);box.addView(key);
        box.addView(Ui.button(this,"保存 API Key",()->{try{vault.put("os_api",key.getText().toString().trim());Ui.toast(this,"已保存到本机");}catch(Exception e){Ui.message(this,"保存失败","系统加密存储不可用，密钥未保存。");}}));
        EditText user=Ui.edit(this,"OpenSubtitles.com 用户名","",false),pass=Ui.edit(this,"密码（不保存）","",true);box.addView(user);box.addView(pass);
        Button login=Ui.button(this,"登录字幕服务",()->{});login.setOnClickListener(v->{
            String u=user.getText().toString().trim(),p=pass.getText().toString();if(u.isEmpty()||p.isEmpty()){Ui.toast(this,"请填写用户名和密码");return;}login.setEnabled(false);
            worker.execute(()->{try{new Subtitles(this).login(u,p);runOnUiThread(()->{if(!isFinishing()){pass.setText("");Ui.toast(this,"字幕服务登录成功");login.setEnabled(true);}});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()){Ui.message(this,"登录失败",e.getMessage());login.setEnabled(true);}});}});
        });box.addView(login);
        box.addView(Ui.button(this,"搜索语言",()->new AlertDialog.Builder(this).setTitle("字幕语言").setItems(new String[]{"中文优先 + 英文","简体中文","繁体中文","英文"},(d,n)->{
            String[] langs={"zh-cn,zh-tw,en","zh-cn","zh-tw","en"};getSharedPreferences("settings",0).edit().putString("sub_languages",langs[n]).apply();Ui.toast(this,"已更新字幕搜索语言");}).show()));

        box.addView(Ui.button(this,"清除所有网盘登录和字幕凭据",()->new AlertDialog.Builder(this).setTitle("清除本机登录和密钥？").setMessage("清除本电视上云幕 TV 保存的网盘登录、字幕服务凭据、豆包与 TMDB 密钥，以及本机影片索引，不会删除网盘文件。")
            .setNegativeButton("取消",null).setPositiveButton("清除",(d,n)->{for(Provider p:Provider.values())AccountSessions.invalidate(p);MovieLibrary.clearAll(this);getSharedPreferences("movie_metadata",0).edit().clear().apply();getSharedPreferences("movie_favorites",0).edit().clear().apply();vault.clear();CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();Ui.toast(this,"本机登录状态和密钥已清除");});key.setText("");doubaoKey.setText("");doubaoStatus.setText("尚未配置豆包密钥");}).show()));
        box.addView(Ui.button(this,"关于与隐私",()->Ui.message(this,"云幕 TV "+AppVersion.name(this)+" · 实验版","为 Android TV 遥控器设计。\n\n四家网盘均提供实验性个人文件列表和独立退出账号。天翼、百度、阿里使用官方扫码接口，夸克使用电视官网。登录页支持从当前官网提取二维码并在电视上显示，二维码会话只在本机和官方网盘页面之间使用。\n\n只读网盘，不上传、删除、转存或分享文件。没有广告、统计 SDK 或开发者中转服务。官网可能按自身政策收集访问数据。字幕查询仅在你点击搜索后发给 OpenSubtitles。配置 TMDB 密钥后，海报墙会发送清理后的片名和年份来匹配海报、简介；不会发送网盘文件路径或凭据。TMDB 数据署名见“电影海报 · TMDB 设置”。\n\n网页登录 Cookie/网页存储留在应用私有目录，字幕 API Key/令牌和豆包 API Key 使用 Android Keystore 加密。豆包仅在主动匹配或测试时发送清理后的片名和字幕候选标签；只连接火山方舟官方接口，不发送网盘凭据。开启字幕翻译后会发送所选字幕文字；开启图片字幕翻译时会发送所选字幕裁片，不发送完整电影画面。卸载或清除登录可移除本机凭据。\n\n应用更新仅在你检查或下载时访问本项目 GitHub 仓库，不携带网盘凭据。下载后校验完整性、包名、版本和原签名，再由系统确认安装。\n\n源码随安装包提供，采用 AGPL-3.0-or-later。Media3 / OkHttp / ZXing 为 Apache-2.0；网盘协议参考 OpenList。")));
        box.addView(Ui.button(this,"登录诊断",()->Ui.message(this,"本机登录诊断",LoginDiagnostics.read(this))));
        box.addView(Ui.button(this,"返回",this::finish));
        if(getIntent().getBooleanExtra("focus_doubao",false))scroll.post(()->scroll.smoothScrollTo(0,doubaoTitle.getTop()));
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
