package cn.cloudcurtain.tv;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class UpdateParallelTest {
    private File directory;private byte[] bytes;private UpdateManifest manifest;
    @Before public void setup()throws Exception {
        directory=Files.createTempDirectory("parallel-update").toFile();bytes=new byte[1048593];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)(n*31+n/1009);
        manifest=UpdateManifest.parse(new JSONObject().put("schemaVersion",1).put("packageName",UpdateManifest.PACKAGE).put("versionCode",21).put("versionName","0.1.20-preview").put("minSdk",23)
            .put("apkUrl",UpdateManifest.REPOSITORY+"/releases/download/v0.1.20-preview/CloudCurtainTV-0.1.20-preview.apk").put("apkSize",bytes.length)
            .put("sha256",UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(bytes))).put("notes","test").toString());
    }
    @After public void clean(){for(File file:Objects.requireNonNull(directory.listFiles()))assertTrue(file.delete());assertTrue(directory.delete());}
    private UpdateClient client(UpdateClient.Transport assets)throws Exception {
        String metadata=new JSONObject().put("draft",false).put("tag_name","v0.1.20-preview").put("assets",new JSONArray().put(new JSONObject().put("id",99).put("url",UpdateManifest.API+"/releases/assets/99")
            .put("browser_download_url",manifest.apkUrl).put("size",bytes.length).put("state","uploaded"))).toString();
        return new UpdateClient(r->r.url().toString().equals(manifest.metadataUrl())?reply(r,200,metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8),null):assets.execute(r));
    }
    private Response reply(Request r,int code,byte[] data,String contentRange){Response.Builder b=new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(ResponseBody.create(data,null)).header("ETag","\"fixed-asset\"");if(contentRange!=null)b.header("Content-Range",contentRange);return b.build();}
    private Response segment(Request r){String[] parts=r.header("Range").substring(6).split("-");int start=Integer.parseInt(parts[0]),end=Integer.parseInt(parts[1]);
        return reply(r,206,Arrays.copyOfRange(bytes,start,end+1),"bytes "+start+"-"+end+"/"+bytes.length);}
    @Test public void independentRangesRunConcurrentlyAndOnlyVerifiedWholeFileBecomesInstallable()throws Exception {
        CountDownLatch started=new CountDownLatch(4);AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger(),validated=new AtomicInteger();List<Integer> progress=new ArrayList<>();
        UpdateClient client=client(r->{assertNull(r.header("Authorization"));assertNull(r.header("Cookie"));assertEquals("identity",r.header("Accept-Encoding"));
            if(!r.header("Range").equals("bytes=0-0")){int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);started.countDown();try{assertTrue(started.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new InterruptedIOException();}finally{active.decrementAndGet();}assertEquals("\"fixed-asset\"",r.header("If-Range"));}
            return segment(r);});
        File file=client.download(directory,manifest,()->false,progress::add,partial->{assertTrue(partial.getName().endsWith(".part"));assertArrayEquals(bytes,Files.readAllBytes(partial.toPath()));validated.incrementAndGet();});
        assertEquals(4,maximum.get());assertEquals(1,validated.get());assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));assertEquals(1,directory.listFiles().length);
        for(int n=1;n<progress.size();n++)assertTrue(progress.get(n)>=progress.get(n-1));assertEquals(Integer.valueOf(100),progress.get(progress.size()-1));
    }
    @Test public void unsupportedRangeSafelyFallsBackToOneFullVerifiedDownload()throws Exception {
        AtomicInteger ranges=new AtomicInteger(),full=new AtomicInteger();UpdateClient client=client(r->{if(r.header("Range")!=null)ranges.incrementAndGet();else full.incrementAndGet();return reply(r,200,bytes,null);});
        File file=client.download(directory,manifest,()->false,p->{},f->{});assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));assertEquals(1,ranges.get());assertEquals(1,full.get());
    }
    @Test public void ignoredWorkerRangeStopsAllPartsBeforeSequentialFallback()throws Exception {
        AtomicInteger full=new AtomicInteger();UpdateClient client=client(r->{if(r.header("Range")==null){full.incrementAndGet();return reply(r,200,bytes,null);}if(r.header("Range").equals("bytes=0-0"))return segment(r);return reply(r,200,bytes,null);});
        assertArrayEquals(bytes,Files.readAllBytes(client.download(directory,manifest,()->false,p->{},f->{}).toPath()));assertEquals(1,full.get());
    }
    @Test public void wrongRangeWrongAssetAndCorruptPartNeverReachTheArchiveValidator()throws Exception {
        for(int failure=0;failure<4;failure++){
            final int kind=failure;UpdateClient client=client(r->{Response valid=segment(r);if(r.header("Range").equals("bytes=0-0"))return valid;
                if(kind==0)return valid.newBuilder().header("Content-Range","bytes 1-2/3").build();
                if(kind==1)return valid.newBuilder().header("ETag","\"changed-asset\"").build();
                if(kind==2){byte[] data=valid.body().bytes();data[0]^=1;return valid.newBuilder().body(ResponseBody.create(data,null)).build();}
                return valid.newBuilder().header("Content-Encoding","gzip").build();});
            assertThrows(IOException.class,()->client.download(directory,manifest,()->false,p->{},f->fail("Invalid file reached verifier")));assertEquals(0,directory.listFiles().length);
        }
    }
    @Test public void truncatedPartCanRecoverWithAFullDownload()throws Exception {
        AtomicInteger full=new AtomicInteger();UpdateClient client=client(r->{if(r.header("Range")==null){full.incrementAndGet();return reply(r,200,bytes,null);}Response response=segment(r);
            if(r.header("Range").equals("bytes=0-0"))return response;byte[] data=response.body().bytes();ResponseBody truncated=new ResponseBody(){public MediaType contentType(){return null;}public long contentLength(){return data.length;}public okio.BufferedSource source(){return new okio.Buffer().write(data,0,data.length-1);}};
            return response.newBuilder().body(truncated).build();});
        assertTrue(client.download(directory,manifest,()->false,p->{},f->{}).isFile());assertEquals(1,full.get());
    }
    @Test public void cancellingParallelDownloadCleansEveryPartialAndDoesNotFallback()throws Exception {
        AtomicBoolean cancelled=new AtomicBoolean();AtomicInteger full=new AtomicInteger();UpdateClient client=client(r->{if(r.header("Range")==null)full.incrementAndGet();return segment(r);});
        assertThrows(InterruptedIOException.class,()->client.download(directory,manifest,cancelled::get,p->{if(p>0)cancelled.set(true);},f->fail("Cancelled file reached verifier")));
        assertEquals(0,directory.listFiles().length);assertEquals(0,full.get());
    }
}
