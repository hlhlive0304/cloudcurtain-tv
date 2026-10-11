package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import okhttp3.*;
import org.json.JSONObject;

/** Fixtures are injected only by the separate instrumentation APK, never shipped in production. */
final class UpdateLiveCheck {
    private static Field field(String name)throws Exception{Field f=UpdateActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Response reply(Request request,int code,ResponseBody body,String location){Response.Builder r=new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body);if(location!=null)r.header("Location",location);return r.build();}
    private static ResponseBody body(File file)throws IOException{
        okio.BufferedSource source=okio.Okio.buffer(okio.Okio.source(file));
        return new ResponseBody(){@Override public MediaType contentType(){return MediaType.get("application/vnd.android.package-archive");}@Override public long contentLength(){return file.length();}@Override public okio.BufferedSource source(){return source;}};
    }
    private static void waitIdle(Instrumentation test,UpdateActivity activity)throws Exception{
        Field busy=field("busy");long end=SystemClock.elapsedRealtime()+65000;boolean[] state={true};
        while(SystemClock.elapsedRealtime()<end){test.runOnMainSync(()->{try{state[0]=busy.getBoolean(activity);}catch(Exception e){throw new RuntimeException(e);}});if(!state[0])return;Thread.sleep(200);}throw new IOException("Updater did not finish");
    }
    static void run(Instrumentation test,Bundle result,boolean realFeed)throws Exception{
        Context context=test.getTargetContext();
        UpdateActivity activity=(UpdateActivity)test.startActivitySync(new Intent(context,UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if(realFeed){
            test.runOnMainSync(()->activity.findViewById(android.R.id.content).findViewWithTag("update_check").performClick());waitIdle(test,activity);
            test.runOnMainSync(()->{TextView status=activity.findViewById(android.R.id.content).findViewWithTag("update_status");result.putBoolean("unpublished_feed_explained",status.getText().toString().contains("尚未发布更新信息"));result.putBoolean("still_in_updater",!activity.isFinishing());activity.finish();});
            if(!result.getBoolean("unpublished_feed_explained"))throw new IOException("Public empty feed was not diagnosed");return;
        }
        File candidate=new File(context.getCacheDir(),"update-fixtures/candidate.apk");if(!candidate.isFile())throw new IOException("Candidate fixture missing");
        android.content.pm.PackageInfo identity=context.getPackageManager().getPackageArchiveInfo(candidate.getAbsolutePath(),0);if(identity==null)throw new IOException("Candidate identity missing");
        String apkUrl=UpdateManifest.REPOSITORY+"/releases/download/v"+identity.versionName+"/CloudCurtainTV-"+identity.versionName+".apk";
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(candidate)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        JSONObject json=new JSONObject().put("schemaVersion",1).put("packageName",UpdateManifest.PACKAGE).put("versionCode",AppVersion.code(identity)).put("versionName",identity.versionName).put("minSdk",23)
            .put("apkUrl",apkUrl).put("apkSize",candidate.length()).put("sha256",UpdateClient.hex(digest.digest())).put("notes","更新流程本机测试：下载、校验、系统确认安装。");
        UpdateManifest manifest=UpdateManifest.parse(json.toString());
        ApkVerifier.verify(context,candidate,manifest);result.putBoolean("real_apk_identity_verified",true);
        File wrong=new File(test.getContext().getApplicationInfo().sourceDir);
        JSONObject invalid=new JSONObject(json.toString()).put("apkSize",wrong.length());MessageDigest other=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(wrong)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)other.update(b,0,n);}invalid.put("sha256",UpdateClient.hex(other.digest()));
        boolean rejected=false;try{ApkVerifier.verify(context,wrong,UpdateManifest.parse(invalid.toString()));}catch(IOException expected){rejected=true;}if(!rejected)throw new IOException("Wrong package accepted");result.putBoolean("wrong_real_apk_rejected",true);
        UpdateClient fake=new UpdateClient(request->{
            if(request.url().toString().equals(UpdateManifest.API_FEED))return reply(request,200,ResponseBody.create(json.toString().getBytes(StandardCharsets.UTF_8),null),null);
            if(request.url().toString().equals(manifest.metadataUrl())){
                try{JSONObject info=new JSONObject().put("draft",false).put("tag_name","v"+identity.versionName).put("assets",new org.json.JSONArray().put(new JSONObject()
                    .put("id",99).put("url",UpdateManifest.API+"/releases/assets/99").put("browser_download_url",apkUrl).put("size",candidate.length()).put("state","uploaded")));
                    return reply(request,200,ResponseBody.create(info.toString().getBytes(StandardCharsets.UTF_8),null),null);
                }catch(Exception e){throw new IOException(e);}
            }
            if(UpdateManifest.apiAsset(request.url().toString()))return reply(request,302,ResponseBody.create(new byte[0],null),"https://release-assets.githubusercontent.com/fixture/candidate.apk?sig=test");
            return reply(request,200,body(candidate),null);
        });
        field("client").set(activity,fake);
        test.runOnMainSync(()->activity.findViewById(android.R.id.content).findViewWithTag("update_check").performClick());waitIdle(test,activity);
        if(field("release").get(activity)==null)throw new IOException("New release not offered");result.putBoolean("new_version_offered",true);
        test.runOnMainSync(()->activity.findViewById(android.R.id.content).findViewWithTag("update_download").performClick());waitIdle(test,activity);
        File ready=(File)field("ready").get(activity);if(ready==null)throw new IOException("Downloaded APK was not offered");ApkVerifier.verify(context,ready,manifest);result.putBoolean("download_and_reverify_passed",true);
        Intent intent=UpdateActivity.installationIntent(context,ready);
        if(!"content".equals(intent.getData().getScheme())||!intent.getData().getAuthority().equals(UpdateManifest.PACKAGE+".updates")||(intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)throw new IOException("Invalid install sharing");
        try(InputStream in=context.getContentResolver().openInputStream(intent.getData())){if(in==null||in.read()==-1)throw new IOException("Installer cannot read shared APK");}result.putBoolean("private_apk_shared_by_content_uri",true);
        boolean outside=false;try{UpdateActivity.installationIntent(context,new File(context.getFilesDir(),"credentials"));}catch(IllegalArgumentException expected){outside=true;}if(!outside)throw new IOException("Provider allowed a file outside updates cache");result.putBoolean("other_private_paths_rejected",true);
        if(Build.VERSION.SDK_INT>=26&&!context.getPackageManager().canRequestPackageInstalls())throw new IOException("Set emulator install app-op for installer UI test");
        test.runOnMainSync(()->activity.findViewById(android.R.id.content).findViewWithTag("update_install").performClick());waitIdle(test,activity);
        result.putBoolean("system_installer_requested",true);
    }
}
