package cn.cloudcurtain.tv;
import android.content.Context;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Preset is sealed with a separate user-held import code. No decryption key in APK. */
public final class PersonalConfig {
 static final int ITERATIONS=320000;
 public static boolean available(Context c){try(InputStream in=c.getAssets().open("personal-config.json")){return in.read()!=-1;}catch(IOException e){return false;}}
 static JSONObject decrypt(String envelope,String code)throws Exception{
  if(envelope.length()>12000||!code.matches("[0-9]{20}"))throw new IOException("请输入 20 位配置码");
  JSONObject obj=new JSONObject(envelope);if(obj.optInt("v")!=1||obj.optInt("iterations")!=ITERATIONS)throw new IOException("配置格式不支持");
  byte[] salt=Base64.decode(obj.getString("salt"),Base64.DEFAULT),iv=Base64.decode(obj.getString("iv"),Base64.DEFAULT),data=Base64.decode(obj.getString("data"),Base64.DEFAULT);
  if(salt.length!=16||iv.length!=12||data.length>8192)throw new IOException("配置格式不正确");
  PBEKeySpec spec=new PBEKeySpec(code.toCharArray(),salt,ITERATIONS,256);byte[] key=null,plain=null;
  try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD("CloudCurtainTV personal configuration v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));plain=cipher.doFinal(data);JSONObject payload=new JSONObject(new String(plain,java.nio.charset.StandardCharsets.UTF_8));String credential=payload.getString("doubao_api_key");if(credential.length()<20||credential.length()>256||credential.contains("\n"))throw new IOException("密钥格式不正确");return payload;}
  finally{spec.clearPassword();if(key!=null)Arrays.fill(key,(byte)0);if(plain!=null)Arrays.fill(plain,(byte)0);}
 }
 public static void importPreset(Context c,String code)throws Exception{
  try(InputStream in=c.getAssets().open("personal-config.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>12000)throw new IOException("配置太大");}
   JSONObject p=decrypt(out.toString("UTF-8"),code);Vault v=new Vault(c);v.putDurable("doubao_api_key",p.getString("doubao_api_key"));String model=p.optString("model");if(!model.isEmpty())v.putDurable("doubao_model",model);
  }
 }
}