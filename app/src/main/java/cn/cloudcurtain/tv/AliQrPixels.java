package cn.cloudcurtain.tv;
import android.graphics.*;
import android.webkit.WebView;
import org.json.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.*;

/** Older WebViews cannot run document-start scripts inside cross-origin frames.
 * Read QR pixels only within the visible official authentication iframe. */
final class AliQrPixels {
    static final String FRAME="(function(){var f=Array.from(document.querySelectorAll('iframe')).find(function(f){try{var u=new URL(f.src);return u.protocol==='https:'&&(u.hostname==='auth.alipan.com'||u.hostname==='auth.aliyundrive.com')}catch(e){return false}});if(!f)return null;document.body.style.visibility='hidden';f.style.visibility='visible';f.style.position='fixed';f.style.left='50%';f.style.top='50%';f.style.width='348px';f.style.height='362px';f.style.border='0';f.style.borderRadius='12px';f.style.transform='translate(-50%,-50%) scale('+Math.min(1,innerHeight/362,innerWidth/348)*.95+')';document.body.style.background='#0b1322';var r=f.getBoundingClientRect();return JSON.stringify({x:r.x,y:r.y,width:r.width,height:r.height,viewport:innerWidth});})()";
    static Rect bounds(String raw,int width,int height)throws Exception{
        Object value=new JSONTokener(raw).nextValue();if(!(value instanceof String))return null;
        JSONObject j=new JSONObject((String)value);double viewport=j.optDouble("viewport",0);if(viewport<=0)return null;
        double scale=width/viewport,x=j.optDouble("x",-1)*scale,y=j.optDouble("y",-1)*scale,w=j.optDouble("width",0)*scale,h=j.optDouble("height",0)*scale;
        if(!Double.isFinite(x+y+w+h)||x<0||y<0||w<80||h<80||x+w>width+2||y+h>height+2)return null;
        return new Rect((int)x,(int)y,Math.min(width,(int)Math.ceil(x+w)),Math.min(height,(int)Math.ceil(y+h)));
    }
    static Bitmap capture(WebView web,Rect area){
        Bitmap page=Bitmap.createBitmap(web.getWidth(),web.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(page);canvas.drawColor(Color.WHITE);web.draw(canvas);
        Bitmap cropped=Bitmap.createBitmap(page,area.left,area.top,area.width(),area.height());if(cropped!=page)page.recycle();return cropped;
    }
    static String decode(Bitmap image)throws Exception{
        int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
        BinaryBitmap data=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels)));
        String value=new QRCodeReader().decode(data,Collections.singletonMap(DecodeHintType.TRY_HARDER,true)).getText();
        return Provider.ALI.allows(value)?value:"";
    }
    static Bitmap qr(String value)throws Exception{
        BitMatrix data=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,480,480,Collections.singletonMap(EncodeHintType.MARGIN,4));int[] pixels=new int[480*480];
        for(int y=0;y<480;y++)for(int x=0;x<480;x++)pixels[y*480+x]=data.get(x,y)?Color.BLACK:Color.WHITE;
        Bitmap image=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);image.setPixels(pixels,0,480,0,0,480,480);return image;
    }
}