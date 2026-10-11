package cn.cloudcurtain.tv;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.*;

/** On-device only. Never store exception messages, URLs, cookies or tokens. */
final class LoginDiagnostics {
    static void install(Context context){
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            record(context,null,"未处理异常",error);
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
    static synchronized void record(Context context,Provider provider,String stage,Throwable error){
        if(context==null)return;
        try{
            StringBuilder line=new StringBuilder(new SimpleDateFormat("MM-dd HH:mm:ss",Locale.US).format(new Date()));
            line.append(" · ").append(provider==null?"应用":provider.title).append(" · ").append(stage).append('\n');
            for(int depth=0;error!=null&&depth<3;depth++,error=error.getCause()){
                line.append(error.getClass().getSimpleName()).append('\n');int count=0;
                if(error instanceof Net.ResponseFormatException){Net.ResponseFormatException e=(Net.ResponseFormatException)error;line.append("HTTP ").append(e.httpStatus).append(" / ").append(e.responseKind).append('\n');}
                else if(error instanceof Net.ServiceException){Net.ServiceException e=(Net.ServiceException)error;line.append("HTTP ").append(e.httpStatus).append(" / ").append(e.serviceCode).append('\n');}
                else if(PlaybackErrors.httpStatus(error)!=0)line.append("HTTP ").append(PlaybackErrors.httpStatus(error)).append('\n');
                for(StackTraceElement at:error.getStackTrace())if(at.getClassName().startsWith("cn.cloudcurtain.tv.")&&count++<4)line.append(at.getClassName().substring(19)).append('.').append(at.getMethodName()).append(':').append(at.getLineNumber()).append('\n');
            }
            android.content.SharedPreferences prefs=context.getSharedPreferences("login_diagnostics",0);
            String old=prefs.getString("events","");if(old.length()>2400)old=old.substring(0,2400);
            prefs.edit().putString("events",line+"\n"+old).commit();
        }catch(Exception ignored){/* Error reporting cannot replace the original error. */}
    }
    static String read(Context c){return "云幕 TV "+AppVersion.name(c)+" · Android "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")\n仅保存在本电视，不含账号、文件名、二维码或密钥。\n\n"+c.getSharedPreferences("login_diagnostics",0).getString("events","暂无错误记录。");}
}
