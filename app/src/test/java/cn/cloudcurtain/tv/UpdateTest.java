package cn.cloudcurtain.tv;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.*;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class UpdateTest {
    private static final String APK=UpdateManifest.REPOSITORY+"/releases/download/v0.1.16-preview/CloudCurtainTV-0.1.16-preview.apk";
    private final byte[] bytes={1,2,3,4,5};
    private File directory;
    @Before public void setup()throws Exception {directory=Files.createTempDirectory("cloudcurtain-updates").toFile();}
    @After public void cleanup(){for(File f:Objects.requireNonNull(directory.listFiles()))assertTrue(f.delete());assertTrue(directory.delete());}
    private JSONObject json()throws Exception{return new JSONObject().put("schemaVersion",1).put("packageName",UpdateManifest.PACKAGE).put("versionCode",17).put("versionName","0.1.16-preview").put("minSdk",23).put("apkUrl",APK).put("apkSize",bytes.length).put("sha256",UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(bytes))).put("notes","测试更新");}
    private UpdateManifest manifest()throws Exception{return UpdateManifest.parse(json().toString());}
    private Response reply(Request request,int code,byte[] body,String location){Response.Builder b=new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test").body(ResponseBody.create(body,null));if(location!=null)b.header("Location",location);return b.build();}
    private interface Checked {void run()throws Exception;}
    private void rejects(Checked action)throws Exception{try{action.run();fail("Invalid update was accepted");}catch(IOException expected){}}
    @Test public void compareNumericVersionAndValidateRelease()throws Exception{UpdateManifest m=manifest();assertTrue(m.newerThan(16));assertFalse(m.newerThan(17));assertFalse(m.newerThan(18));assertEquals("0.1.16-preview",m.versionName);}
    @Test public void wrongPackageAndMalformedFeedAreRejected()throws Exception{rejects(()->UpdateManifest.parse(json().put("packageName","other.app").toString()));rejects(()->UpdateManifest.parse("<html>sign in</html>"));rejects(()->UpdateManifest.parse(json().put("schemaVersion",2).toString()));rejects(()->UpdateManifest.parse(json().put("sha256","bad").toString()));}
    @Test public void untrustedOrDowngradedDownloadAddressesAreRejected()throws Exception{
        for(String u:Arrays.asList("http://github.com/hlhlive0304/cloudcurtain-tv/releases/download/v1/a.apk",APK.replace("hlhlive0304","other"),APK+"?access_token=secret",APK+"#fragment",APK.replace("github.com","github.com.evil.example"),APK.replace("github.com","user:pass@github.com"),APK.replace("github.com","github.com:8443"),APK.replace("v0.1.16-preview","..")))rejects(()->UpdateManifest.parse(json().put("apkUrl",u).toString()));
    }
    @Test public void unreasonableSizesAndInvalidVersionsAreRejected()throws Exception{for(long size:new long[]{0,-1,UpdateManifest.MAX_APK_BYTES+1})rejects(()->UpdateManifest.parse(json().put("apkSize",size).toString()));for(long code:new long[]{0,-1,2147483648L})rejects(()->UpdateManifest.parse(json().put("versionCode",code).toString()));}
    @Test public void feedReadsOnlyTheConfiguredPublicEndpointAndNoAccountHeaders()throws Exception{
        String feed=json().toString();UpdateClient client=new UpdateClient(request->{assertEquals(UpdateManifest.FEED,request.url().toString());assertNull(request.header("Cookie"));assertNull(request.header("Authorization"));assertNull(request.header("Referer"));return reply(request,200,feed.getBytes(java.nio.charset.StandardCharsets.UTF_8),null);});assertEquals(17,client.check(()->false).versionCode);
    }
    @Test public void emptyRepositoryAndHttpFailuresAreTerminal()throws Exception{for(int code:new int[]{404,403,429,500})rejects(()->new UpdateClient(r->reply(r,code,new byte[0],null)).check(()->false));}
    @Test public void oversizedFeedStopsBeforeParsing()throws Exception{rejects(()->new UpdateClient(r->reply(r,200,new byte[65537],null)).check(()->false));}
    @Test public void redirectCannotSendRequestsToAnUntrustedServer()throws Exception{
        final int[] requests={0};UpdateClient c=new UpdateClient(r->{requests[0]++;return reply(r,302,new byte[0],"https://evil.example/app.apk");});rejects(()->c.download(directory,manifest(),()->false,p->{},f->{}));assertEquals(1,requests[0]);assertEquals(0,directory.listFiles().length);
    }
    @Test public void githubAssetRedirectAndDownloadAreCheckedAndPublishedAtomically()throws Exception{
        final int[] requests={0},verified={0};UpdateClient c=new UpdateClient(r->{requests[0]++;return r.url().host().equals("github.com")?reply(r,302,new byte[0],"https://release-assets.githubusercontent.com/github-production-release-asset/1/a.apk?sig=1"):reply(r,200,bytes,null);});
        File file=c.download(directory,manifest(),()->false,p->{},f->{assertTrue(f.getName().endsWith(".part"));verified[0]++;});assertEquals(2,requests[0]);assertEquals(1,verified[0]);assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));assertEquals(1,directory.listFiles().length);
    }
    @Test public void corruptBytesAndTruncatedDownloadsLeaveNoInstallableFile()throws Exception{for(byte[] invalid:new byte[][]{{1,2},{9,8,7,6,5},{1,2,3,4,5,6}}){UpdateClient c=new UpdateClient(r->reply(r,200,invalid,null));rejects(()->c.download(directory,manifest(),()->false,p->{},f->fail("Corrupt file reached verifier")));assertEquals(0,directory.listFiles().length);}}
    @Test public void cancellationCleansPartialDownload()throws Exception{
        AtomicBoolean cancelled=new AtomicBoolean();UpdateClient c=new UpdateClient(r->reply(r,200,bytes,null));rejects(()->c.download(directory,manifest(),cancelled::get,p->cancelled.set(true),f->fail("Cancelled file reached verifier")));assertEquals(0,directory.listFiles().length);
    }
    @Test public void rejectedArchiveNeverBecomesInstallable()throws Exception{
        UpdateClient c=new UpdateClient(r->reply(r,200,bytes,null));rejects(()->c.download(directory,manifest(),()->false,p->{},f->{throw new IOException("wrong signature");}));assertEquals(0,directory.listFiles().length);
    }
    @Test public void cachedArtifactIsReverifiedWithoutNetworkAndTamperingIsRejected()throws Exception{
        UpdateClient c=new UpdateClient(r->reply(r,200,bytes,null));File file=c.download(directory,manifest(),()->false,p->{},f->{});
        final int[] checks={0};UpdateClient offline=new UpdateClient(r->{throw new IOException("Should use cache");});assertEquals(file,offline.download(directory,manifest(),()->false,p->{},f->checks[0]++));assertEquals(1,checks[0]);Files.write(file.toPath(),new byte[]{5,4,3,2,1});rejects(()->offline.download(directory,manifest(),()->false,p->{},f->fail("Tampered cache accepted")));
    }
    @Test public void wrongSignerWrongPackageAndDowngradesCannotInstall()throws Exception{
        UpdateManifest m=manifest();ApkVerifier.validateIdentity(UpdateManifest.PACKAGE,17,m.versionName,UpdateManifest.SIGNER,16,UpdateManifest.SIGNER,m);
        rejects(()->ApkVerifier.validateIdentity("other",17,m.versionName,UpdateManifest.SIGNER,16,UpdateManifest.SIGNER,m));
        rejects(()->ApkVerifier.validateIdentity(UpdateManifest.PACKAGE,17,m.versionName,"bad",16,UpdateManifest.SIGNER,m));
        rejects(()->ApkVerifier.validateIdentity(UpdateManifest.PACKAGE,17,m.versionName,UpdateManifest.SIGNER,16,"bad",m));
        rejects(()->ApkVerifier.validateIdentity(UpdateManifest.PACKAGE,16,m.versionName,UpdateManifest.SIGNER,15,UpdateManifest.SIGNER,m));
        rejects(()->ApkVerifier.validateIdentity(UpdateManifest.PACKAGE,17,m.versionName,UpdateManifest.SIGNER,17,UpdateManifest.SIGNER,m));
        rejects(()->ApkVerifier.validateIdentity(UpdateManifest.PACKAGE,17,"incorrect",UpdateManifest.SIGNER,16,UpdateManifest.SIGNER,m));
    }
}
