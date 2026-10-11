package cn.cloudcurtain.tv;
import android.app.Application;
import android.content.Context;
public final class CloudApp extends Application {
    private static Context context;
    @Override public void onCreate(){super.onCreate();context=getApplicationContext();LoginDiagnostics.install(context);}
    static Context context(){return context;}
}
