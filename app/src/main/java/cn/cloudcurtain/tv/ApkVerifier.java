package cn.cloudcurtain.tv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

final class ApkVerifier {
    @SuppressWarnings("deprecation")
    static void verify(Context context,File file,UpdateManifest release)throws Exception {
        if(Build.VERSION.SDK_INT<release.minSdk)throw new IOException("新版需要更高的 Android 系统版本。");
        UpdateClient.verifyFile(file,release);
        PackageManager pm=context.getPackageManager();
        PackageInfo current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if(candidate==null)throw new IOException("安装包不能被系统读取，未启动安装。");
        validateIdentity(candidate.packageName,AppVersion.code(candidate),candidate.versionName,signer(candidate.signatures),
            AppVersion.code(current),signer(current.signatures),release);
        if(Build.VERSION.SDK_INT>=24&&candidate.applicationInfo!=null&&candidate.applicationInfo.minSdkVersion!=release.minSdk)
            throw new IOException("安装包系统要求与更新信息不一致。");
    }
    static void validateIdentity(String packageName,long code,String name,String signer,long installed,String currentSigner,UpdateManifest release)throws IOException {
        if(!UpdateManifest.PACKAGE.equals(packageName))throw new IOException("安装包不是云幕 TV，未启动安装。");
        if(code!=release.versionCode||!release.versionName.equals(name)||code<=installed)throw new IOException("安装包版本不匹配或不是更新版本。");
        if(!UpdateManifest.SIGNER.equals(signer)||!UpdateManifest.SIGNER.equals(currentSigner))throw new IOException("安装包签名与当前应用不一致，未启动安装。");
    }
    private static String signer(Signature[] signatures)throws Exception {
        if(signatures==null||signatures.length!=1)return "";
        return UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }
}
