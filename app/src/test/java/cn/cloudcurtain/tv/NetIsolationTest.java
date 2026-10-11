package cn.cloudcurtain.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NetIsolationTest {
    @Test public void nonJsonResponseDiagnosticsKeepOnlyStatusAndFormat()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();server.enqueue(new MockResponse().setHeader("Content-Type","text/html").setBody("<!DOCTYPE html><html>token=private-ticket</html>"));
            java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newSingleThreadExecutor();
            try{
                java.util.concurrent.Future<?> result=io.submit(()->Net.json(server.url("/files").toString(),null,java.util.Collections.emptyMap()));
                java.util.concurrent.ExecutionException failure=assertThrows(java.util.concurrent.ExecutionException.class,()->result.get(5,java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof Net.ResponseFormatException);Net.ResponseFormatException e=(Net.ResponseFormatException)failure.getCause();assertEquals(200,e.httpStatus);assertEquals("HTML",e.responseKind);
                android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();LoginDiagnostics.record(c,Provider.TIANYI,"读取文件",e);String report=LoginDiagnostics.read(c);
                assertTrue(report.contains("HTTP 200 / HTML"));assertFalse(report.contains("private-ticket"));assertFalse(report.contains("<!DOCTYPE"));assertFalse(report.contains("/files"));
            }finally{io.shutdownNow();}
        }
    }
    @Test public void redirectsNeverLeakApiOrBearerCredentials()throws Exception{
        try(MockWebServer first=new MockWebServer();MockWebServer second=new MockWebServer()){
            first.start();second.start();first.enqueue(new MockResponse().setResponseCode(302).addHeader("Location",second.url("/movie")));second.enqueue(new MockResponse().setBody("test"));
            String u=first.url("/start").toString();MediaSpec m=new MediaSpec(u,"test");m.headers.put("Authorization","Bearer private-test");m.headers.put("Api-Key","private-key");m.headers.put("Cookie","scoped=private-cookie");m.headers.put("X-Signature","device-signature");m.headers.put("X-Device-Id","device-id");m.headers.put("sessionKey","private-session");
            try(Response r=Net.client(m).newCall(new Request.Builder().url(u).build()).execute()){assertEquals(200,r.code());}
            RecordedRequest a=first.takeRequest(),b=second.takeRequest();assertEquals("Bearer private-test",a.getHeader("Authorization"));assertEquals("private-key",a.getHeader("Api-Key"));assertEquals("scoped=private-cookie",a.getHeader("Cookie"));assertNull(b.getHeader("Authorization"));assertNull(b.getHeader("Api-Key"));assertNull(b.getHeader("Cookie"));
            assertEquals("device-signature",a.getHeader("X-Signature"));assertEquals("device-id",a.getHeader("X-Device-Id"));assertNull(b.getHeader("X-Signature"));assertNull(b.getHeader("X-Device-Id"));assertEquals("private-session",a.getHeader("sessionKey"));assertNull(b.getHeader("sessionKey"));
        }
    }
}
