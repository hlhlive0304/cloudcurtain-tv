package cn.cloudcurtain.tv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

final class AppVersion {
    static PackageInfo installed(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(),0);
    }
    static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    static String name(Context context){try{return installed(context).versionName;}catch(Exception ignored){return "未知版本";}}
}
