package cn.cloudcurtain.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.json.JSONObject;

/** Only accepts QR pixels from the selected provider's HTTPS login documents. No token bridge. */
final class LoginQr {
    static Set<String> origins(Provider p){
        switch(p){
            case TIANYI:return new HashSet<>(Arrays.asList("https://cloud.189.cn","https://open.e.189.cn","https://static.e.189.cn","https://login.e.189.cn"));
            case QUARK:return new HashSet<>(Arrays.asList("https://pan.quark.cn","https://uop.quark.cn"));
            case BAIDU:return new HashSet<>(Arrays.asList("https://pan.baidu.com","https://passport.baidu.com"));
            default:return new HashSet<>(Arrays.asList("https://www.alipan.com","https://www.aliyundrive.com","https://passport.aliyundrive.com","https://passport.alipan.com","https://auth.aliyundrive.com","https://auth.alipan.com","https://login.aliyun.com","https://gw.alipayobjects.com","https://img.alicdn.com"));
        }
    }
    static boolean originAllowed(Provider p,String url){
        for(String origin:origins(p))if(UrlRules.sameOrigin(origin,url))return true;
        return false;
    }
    static String script(Context c,String nonce)throws IOException{
        try(InputStream in=c.getAssets().open("login-qr.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return out.toString(StandardCharsets.UTF_8.name()).replace("__SESSION_NONCE__",JSONObject.quote(nonce));
        }
    }
    static boolean imageUrlAllowed(Provider p,String url){
        return url!=null&&url.startsWith("https://")&&originAllowed(p,url);
    }
    static Bitmap decode(byte[] bytes)throws IOException{
        if(bytes.length>1024*1024)throw new IOException("二维码图片过大");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<80||o.outHeight<80||o.outWidth>1200||o.outHeight>1200||Math.abs(o.outWidth-o.outHeight)>Math.max(o.outWidth,o.outHeight)*.15)throw new IOException("二维码图片尺寸不正确");
        Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IOException("无法读取二维码图片");return b;
    }
    static Bitmap image(Provider p,String value)throws Exception{
        if(value.startsWith("data:image/png;base64,")&&value.length()<1_400_000)return decode(Base64.decode(value.substring(value.indexOf(',')+1),Base64.DEFAULT));
        if(!imageUrlAllowed(p,value))throw new IOException("二维码图片来源不匹配");
        // Do not follow a QR image redirect onto a different service.
        OkHttpClient client=Net.client(null).newBuilder().followRedirects(false).followSslRedirects(false).build();
        try(Response response=client.newCall(new Request.Builder().url(value).header("User-Agent",Net.UA).build()).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new IOException("二维码图片请求失败");
            try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new IOException("二维码图片过大");out.write(buffer,0,n);}return decode(out.toByteArray());
            }
        }
    }
}
