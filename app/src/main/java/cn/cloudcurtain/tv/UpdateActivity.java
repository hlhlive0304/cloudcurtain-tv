package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.*;

public final class UpdateActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UpdateClient client=new UpdateClient();
    private volatile boolean cancelled;
    private boolean busy;
    private UpdateManifest release;
    private File ready;
    private TextView status,notes;
    private ProgressBar progress;
    private Button check,download,install,cancel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout page=Ui.page(this,"应用更新","当前版本："+AppVersion.name(this));
        ScrollView scroll=new ScrollView(this);LinearLayout box=Ui.column(this);scroll.addView(box);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=Ui.text(this,"点击检查更新。下载安装由你确认，账号和设置保留。",18,Ui.TEXT);status.setTag("update_status");box.addView(status);
        notes=Ui.text(this,"",15,Ui.MUTED);notes.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,18));notes.setTag("update_notes");box.addView(notes);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(android.view.View.GONE);box.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,16)));
        AdaptiveLayout.Flow actions=new AdaptiveLayout.Flow(this);box.addView(actions);
        check=Ui.button(this,"检查更新",this::check);check.setTag("update_check");actions.addView(check);
        download=Ui.button(this,"下载新版",this::download);download.setTag("update_download");actions.addView(download);
        install=Ui.button(this,"安装新版",this::install);install.setTag("update_install");actions.addView(install);
        cancel=Ui.button(this,"取消下载",this::cancel);cancel.setTag("update_cancel");actions.addView(cancel);
        Button manual=Ui.button(this,"手机扫码下载",this::manual);manual.setTag("update_manual");actions.addView(manual);
        box.addView(Ui.button(this,"返回",this::onBackPressed));updateButtons();check.requestFocus();
    }
    private void ui(Runnable action){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())action.run();});}
    private void updateButtons(){check.setEnabled(!busy);download.setVisibility(release!=null&&ready==null?android.view.View.VISIBLE:android.view.View.GONE);download.setEnabled(!busy);install.setVisibility(ready!=null?android.view.View.VISIBLE:android.view.View.GONE);install.setEnabled(!busy);cancel.setText("取消操作");cancel.setEnabled(true);cancel.setVisibility(busy?android.view.View.VISIBLE:android.view.View.GONE);}
    private void check(){
        if(busy)return;busy=true;cancelled=false;release=null;ready=null;notes.setText("");progress.setVisibility(android.view.View.GONE);status.setText("正在检查更新…");updateButtons();
        worker.execute(()->{try{
            UpdateManifest found=client.check(()->cancelled);long current=AppVersion.code(AppVersion.installed(this));
            ui(()->{busy=false;if(cancelled){status.setText("已取消检查。");}else if(!found.newerThan(current)){status.setText("当前已是最新版本。");}
                else if(Build.VERSION.SDK_INT<found.minSdk){status.setText("发现新版 "+found.versionName+"，但当前 Android 系统不满足安装要求。");notes.setText(found.notes);}
                else{release=found;status.setText("发现新版："+found.versionName+" · "+String.format(java.util.Locale.ROOT,"%.1f",found.apkSize/1048576.0)+" MB");notes.setText(found.notes);}
                updateButtons();if(release!=null)download.requestFocus();});
        }catch(Exception e){failed(e,"检查");}});
    }
    private void download(){
        if(busy||release==null)return;UpdateManifest chosen=release;busy=true;cancelled=false;progress.setProgress(0);progress.setVisibility(android.view.View.VISIBLE);status.setText("正在下载新版…");updateButtons();
        worker.execute(()->{try{
            File file=client.download(new File(getCacheDir(),"updates"),chosen,()->cancelled,percent->ui(()->{progress.setProgress(percent);status.setText(percent==100?"下载完成，正在校验安装包…":"正在下载："+percent+"% ");}),candidate->ApkVerifier.verify(this,candidate,chosen));
            ui(()->{busy=false;if(cancelled){status.setText("已取消下载。");}else{ready=file;status.setText("安装包校验通过。选择安装新版后，按系统提示确认。");}updateButtons();if(ready!=null)install.requestFocus();});
        }catch(Exception e){failed(e,"下载");}});
    }
    private void failed(Exception error,String stage){ui(()->{busy=false;status.setText(cancelled?"已取消"+stage+"。":stage+"未完成："+(error instanceof java.io.IOException?error.getMessage():"安装包未通过校验，请重新下载。"));updateButtons();check.requestFocus();});}
    private void cancel(){if(!busy)return;cancelled=true;client.cancel();status.setText("正在取消…");cancel.setEnabled(false);}
    private void manual(){
        String url=UpdateManifest.REPOSITORY+"/releases";
        try{
            android.graphics.Bitmap bitmap=AliQrPixels.qr(url);LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),0);
            TextView caption=Ui.text(this,"手机扫码进入本项目发布页，下载最新版 APK，再传到电视覆盖安装，可保留账号与设置。",16,Ui.TEXT);box.addView(caption);
            ImageView qr=new ImageView(this);qr.setImageBitmap(bitmap);qr.setBackgroundColor(android.graphics.Color.WHITE);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(Ui.dp(this,240),Ui.dp(this,240));size.gravity=android.view.Gravity.CENTER_HORIZONTAL;size.setMargins(0,Ui.dp(this,16),0,Ui.dp(this,12));box.addView(qr,size);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("下载云幕 TV 更新").setView(box).setNegativeButton("关闭",null)
                .setPositiveButton("电视打开发布页",(d,n)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Ui.toast(this,"电视没有浏览器，请用手机扫码。");}}).create();
            dialog.setOnDismissListener(d->{qr.setImageDrawable(null);bitmap.recycle();});dialog.show();
        }catch(Exception e){Ui.message(this,"下载发布页",url);}
    }
    private void install(){
        if(busy||ready==null||release==null)return;
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("允许安装云幕 TV 更新").setMessage("系统需要你允许此应用安装更新。开启后返回这里，再选择“安装新版”。")
                .setNegativeButton("取消",null).setPositiveButton("打开系统设置",(d,n)->{try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){Ui.message(this,"无法打开设置","请在电视系统设置中允许云幕 TV 安装应用，再返回这里安装。");}}).show();return;
        }
        UpdateManifest chosen=release;File file=ready;busy=true;cancelled=false;status.setText("正在复核安装包…");updateButtons();
        worker.execute(()->{try{ApkVerifier.verify(this,file,chosen);ui(()->{busy=false;updateButtons();if(cancelled)return;
            try{startActivity(installationIntent(this,file));status.setText("已打开系统安装界面，请确认更新；取消后可以重试。");}catch(ActivityNotFoundException|SecurityException e){status.setText("电视未提供可用的安装入口，请使用手动安装包。");}
        });}catch(Exception e){ui(()->{ready=null;progress.setVisibility(android.view.View.GONE);});failed(e,"安装");}});
    }
    static Intent installationIntent(Context context,File file){
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".updates",file);
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("云幕 TV 更新",uri));return intent;
    }
    @Override public void onBackPressed(){if(busy)new AlertDialog.Builder(this).setTitle("取消当前更新操作？").setNegativeButton("继续",null).setPositiveButton("取消并返回",(d,n)->{cancelled=true;client.cancel();finish();}).show();else super.onBackPressed();}
    @Override protected void onDestroy(){cancelled=true;client.cancel();worker.shutdownNow();super.onDestroy();}
}
