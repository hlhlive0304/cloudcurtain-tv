package cn.cloudcurtain.tv;

import org.json.JSONObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.*;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

/** Alipan's public web device-session protocol, with our own random device key.
 * No third-party private key or client secret. Vault identity survives restarts.
 */
final class AliDeviceSession {
    static final String API="https://api.aliyundrive.com";
    static final String USER_API="https://user.aliyundrive.com";
    // Public web client identifier from www.alipan.com's Global.app_id.
    static final String WEB_APP_ID="5dde4e1bdf9e4966b387ba58f4b3fdc3";
    private static final Object LOCK=new Object();
    interface Store { String read();void write(String value)throws Exception; }
    private final CloudApi.Transport transport;private final Store store;
    private final int generation;
    AliDeviceSession(CloudApi.Transport transport){this(transport,new Store(){
        public String read(){return new Vault(CloudApp.context()).get("ali_device");}
        public void write(String value)throws Exception{new Vault(CloudApp.context()).put("ali_device",value);}
    });}
    AliDeviceSession(CloudApi.Transport transport,Store store){this.transport=transport;this.store=store;generation=AccountSessions.generation(Provider.ALI);}
    private void active()throws IOException{if(generation!=AccountSessions.generation(Provider.ALI))throw new IOException("账号已退出，请重新登录。");}
    JSONObject request(String path,JSONObject body,JSONObject token)throws Exception{
        synchronized(LOCK){
            active();String access=token.optString("access_token"),user=token.optString("user_id");
            if(access.isEmpty())throw new IOException("阿里登录失效，请重新扫码。");
            if(user.isEmpty())user=transport.request(USER_API+"/v2/user/get",new JSONObject(),Net.headers("Authorization","Bearer "+access,"Referer",Provider.ALI.origin)).optString("user_id");
            if(user.isEmpty())throw new IOException("阿里尚未返回账号信息，请重新扫码。");
            String saved=store.read();JSONObject identity=saved.isEmpty()?new JSONObject():new JSONObject(saved);
            if(!user.equals(identity.optString("user"))||identity.optString("key").isEmpty()){
                identity=new JSONObject().put("user",user).put("device",UUID.randomUUID().toString().replace("-",""));
                BigInteger key;SecureRandom random=new SecureRandom();do{key=new BigInteger(256,random);}while(key.signum()==0||key.compareTo(CURVE.getN())>=0);
                identity.put("key",Hex.toHexString(fixed(key)));
                synchronized(AccountSessions.class){active();store.write(identity.toString());}
            }
            String device=identity.getString("device");BigInteger key=new BigInteger(identity.getString("key"),16);
            String signature=sign(key,WEB_APP_ID+":"+device+":"+user+":0");
            Map<String,String> headers=Net.headers("Authorization","Bearer "+access,"Referer",Provider.ALI.origin,"Origin","https://www.alipan.com",
                "X-Device-Id",device,"X-Signature",signature,"X-Canary","client=web,app=adrive,version=v6.8.12","X-Request-Id",UUID.randomUUID().toString());
            try{return checked(path,body,headers);}catch(Net.ServiceException e){
                // Register/renew only this app's device; never evict other devices.
                if(!"DeviceSessionSignatureInvalid".equals(e.serviceCode))throw e;
                JSONObject registration=new JSONObject().put("deviceName","云幕 TV").put("modelName","Android TV").put("nonce",0)
                    .put("pubKey",Hex.toHexString(CURVE.getG().multiply(key).normalize().getEncoded(false)));
                JSONObject created=checked("/users/v1/users/device/create_session",registration,headers);
                if(!created.optBoolean("result",false))throw new IOException("阿里未能建立本电视的设备会话，请在手机检查已登录设备数量后重试。");
                return checked(path,body,headers);
            }
        }
    }
    private JSONObject checked(String path,JSONObject body,Map<String,String> headers)throws Exception{
        active();JSONObject result=transport.request((path.equals("/v2/user/get")?USER_API:API)+path,body,headers);active();
        String code=result.optString("code");if(!code.isEmpty())throw new Net.ServiceException(200,code);return result;
    }
    static final X9ECParameters CURVE=CustomNamedCurves.getByName("secp256k1");
    static final ECDomainParameters DOMAIN=new ECDomainParameters(CURVE.getCurve(),CURVE.getG(),CURVE.getN(),CURVE.getH());
    private static byte[] fixed(BigInteger number){byte[] raw=number.toByteArray(),out=new byte[32];int length=Math.min(32,raw.length);System.arraycopy(raw,raw.length-length,out,32-length,length);return out;}
    static String sign(BigInteger key,String text)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        ECDSASigner signer=new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));signer.init(true,new ECPrivateKeyParameters(key,DOMAIN));
        BigInteger[] rs=signer.generateSignature(hash);BigInteger n=CURVE.getN(),r=rs[0],s=rs[1];if(s.compareTo(n.shiftRight(1))>0)s=n.subtract(s);
        ECPoint publicKey=CURVE.getG().multiply(key).normalize();int recovery=-1;
        for(int candidate=0;candidate<4;candidate++){
            BigInteger x=r.add(n.multiply(BigInteger.valueOf(candidate/2)));if(x.bitLength()>256)continue;
            byte[] compressed=new byte[33];compressed[0]=(byte)(2+(candidate&1));System.arraycopy(fixed(x),0,compressed,1,32);
            try{ECPoint point=CURVE.getCurve().decodePoint(compressed);if(!point.multiply(n).isInfinity())continue;
                ECPoint recovered=point.multiply(s).subtract(CURVE.getG().multiply(new BigInteger(1,hash).mod(n))).multiply(r.modInverse(n)).normalize();
                if(recovered.equals(publicKey)){recovery=candidate;break;}
            }catch(IllegalArgumentException ignored){/* x is outside the curve. */}
        }
        if(recovery<0)throw new GeneralSecurityException("Device signature recovery failed");
        byte[] result=new byte[65];System.arraycopy(fixed(r),0,result,0,32);System.arraycopy(fixed(s),0,result,32,32);result[64]=(byte)recovery;return Hex.toHexString(result);
    }
}
