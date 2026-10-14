package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.List;

/** Runs the actual updater on public endpoints; no account data is logged. */
final class UpdateDirectLiveCheck {
    private static Field field(String name)throws Exception{Field f=UpdateActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void idle(Instrumentation test,UpdateActivity a,long limit)throws Exception{
        long end=SystemClock.elapsedRealtime()+limit;boolean[] busy={true};
        do{test.runOnMainSync(()->{try{busy[0]=field("busy").getBoolean(a);}catch(Exception e){throw new RuntimeException(e);}});if(!busy[0])return;Thread.sleep(200);}while(SystemClock.elapsedRealtime()<end);
        throw new IOException("Update UI did not finish within its timeout");
    }
    static void run(Instrumentation test,Bundle result,String task)throws Exception{
        Context c=test.getTargetContext();List<Proxy> routes=ProxySelector.getDefault().select(URI.create(UpdateManifest.API_FEED));
        boolean direct=!routes.isEmpty();for(Proxy route:routes)direct&=route.type()==Proxy.Type.DIRECT;
        result.putBoolean("direct_network",direct);if(!direct)throw new IOException("A direct connection is required for this check");
        UpdateActivity a=(UpdateActivity)test.startActivitySync(new Intent(c,UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean installer=false;
        try{
            if(task.equals("update_offline_ui"))field("client").set(a,new UpdateClient(r->{throw new SocketTimeoutException("timeout");}));
            long began=SystemClock.elapsedRealtime();test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("update_check").performClick());idle(test,a,30000);
            String[] text={""};test.runOnMainSync(()->text[0]=((TextView)a.findViewById(android.R.id.content).findViewWithTag("update_status")).getText().toString());
            if(task.equals("update_offline_ui")){
                result.putBoolean("both_routes_explained",text[0].contains("两条官方更新线路"));result.putBoolean("english_timeout_hidden",!text[0].contains("timeout"));
                if(!result.getBoolean("both_routes_explained")||!result.getBoolean("english_timeout_hidden"))throw new IOException("Offline failure UI missing");
                test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("update_manual").performClick());test.waitForIdleSync();Thread.sleep(500);
                android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
                result.putBoolean("manual_dialog_visible",root!=null&&!root.findAccessibilityNodeInfosByText("下载云幕 TV 更新").isEmpty());
                if(!result.getBoolean("manual_dialog_visible"))throw new IOException("Manual QR entry missing");
                android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"update-manual.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
                test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                return;
            }
            if(!text[0].contains("发现新版")&&!text[0].contains("当前已是最新版本"))throw new IOException("Public update check failed: "+text[0]);
            UpdateClient client=(UpdateClient)field("client").get(a);result.putString("feed_route",client.route());result.putLong("check_ms",SystemClock.elapsedRealtime()-began);result.putBoolean("public_feed_checked",true);
            UpdateManifest release=(UpdateManifest)field("release").get(a);
            if(task.equals("update_direct_check"))return;
            if(release==null)throw new IOException("Install a lower version before the public download test");result.putLong("offered_version",release.versionCode);
            File target=new File(c.getCacheDir(),"updates/cloudcurtain-"+release.versionCode+"-"+release.sha256.substring(0,16)+".apk");
            if(target.exists()&&!target.delete())throw new IOException("Unable to remove old update cache");
            began=SystemClock.elapsedRealtime();test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("update_download").performClick());
            if(task.equals("update_direct_cancel")){
                long end=SystemClock.elapsedRealtime()+45000;boolean[] transferring={false};
                do{Thread.sleep(200);test.runOnMainSync(()->{String caption=((TextView)a.findViewById(android.R.id.content).findViewWithTag("update_status")).getText().toString();transferring[0]=caption.contains("KB/s")&&caption.contains("预计");});}while(!transferring[0]&&SystemClock.elapsedRealtime()<end);
                if(!transferring[0]||!client.route().contains("4 路"))throw new IOException("Parallel transfer and speed UI not visible");
                result.putBoolean("speed_eta_visible",true);
                android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"update-speed.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
                long cancelledAt=SystemClock.elapsedRealtime();test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("update_cancel").performClick());idle(test,a,10000);
                test.runOnMainSync(()->text[0]=((TextView)a.findViewById(android.R.id.content).findViewWithTag("update_status")).getText().toString());result.putLong("cancel_ms",SystemClock.elapsedRealtime()-cancelledAt);
                result.putBoolean("cancel_explained",text[0].contains("已取消下载"));result.putBoolean("partial_removed",!new File(target.getPath()+".part").exists());result.putBoolean("no_installable_file",!target.exists()&&field("ready").get(a)==null);
                if(!result.getBoolean("cancel_explained")||!result.getBoolean("partial_removed")||!result.getBoolean("no_installable_file"))throw new IOException("Cancellation left an update file");return;
            }
            idle(test,a,16*60*1000);
            File ready=(File)field("ready").get(a);if(ready==null){test.runOnMainSync(()->text[0]=((TextView)a.findViewById(android.R.id.content).findViewWithTag("update_status")).getText().toString());throw new IOException("Public download failed: "+text[0]);}
            ApkVerifier.verify(c,ready,release);result.putLong("download_ms",SystemClock.elapsedRealtime()-began);result.putLong("download_bytes",ready.length());result.putString("download_route",client.route());result.putBoolean("fresh_public_download_verified",true);
            if(task.equals("update_direct_install")){
                test.runOnMainSync(()->a.findViewById(android.R.id.content).findViewWithTag("update_install").performClick());idle(test,a,15000);
                long end=SystemClock.elapsedRealtime()+10000;do{Thread.sleep(500);android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();String pkg=root==null?"":String.valueOf(root.getPackageName());installer=pkg.contains("packageinstaller");}while(!installer&&SystemClock.elapsedRealtime()<end);
                result.putBoolean("system_installer_visible",installer);if(!installer)throw new IOException("System installer did not appear");
            }
        }finally{if(!installer)test.runOnMainSync(a::finish);}
    }
}
