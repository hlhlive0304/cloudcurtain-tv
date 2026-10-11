package cn.cloudcurtain.tv;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** App-private encrypted storage. No credentials are included in APK or source. */
public final class Vault {
    private final Context context;
    public Vault(Context c){context=c.getApplicationContext();}
    private synchronized javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("cloud-curtain")){
            KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("cloud-curtain",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }return (javax.crypto.SecretKey)ks.getKey("cloud-curtain",null);
    }
    public void put(String name,String value) throws Exception {put(name,value,false);}
    public void putDurable(String name,String value) throws Exception {put(name,value,true);}
    private void put(String name,String value,boolean durable) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String data=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        android.content.SharedPreferences.Editor edit=context.getSharedPreferences("vault",0).edit().putString(name,data);
        if(durable){if(!edit.commit())throw new java.io.IOException("加密配置保存失败");}else edit.apply();
    }
    public String get(String name) {
        try {
            String data=context.getSharedPreferences("vault",0).getString(name,"");if(data.isEmpty())return "";
            String[] parts=data.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],0)));
            return new String(c.doFinal(Base64.decode(parts[1],0)),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    public void remove(String name){context.getSharedPreferences("vault",0).edit().remove(name).apply();}
    public void clear(){context.getSharedPreferences("vault",0).edit().clear().apply();}
}
