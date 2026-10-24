package cn.cloudcurtain.tv;

import androidx.media3.common.C;
import androidx.media3.datasource.*;
import okhttp3.*;import okhttp3.mockwebserver.*;
import org.junit.Test;import java.io.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=28)
public class QuarkRangeSourceTest {
    @Test public void onlyAuthenticatedQuarkOriginalOriginsUseThePrefetcher(){MediaSpec media=new MediaSpec("https://dl-pc-zb.drive.quark.cn/file","");media.provider="QUARK";assertTrue(QuarkRangeSource.applies(media));media.quality="quark:3840x1608";assertFalse(QuarkRangeSource.applies(media));media.quality="original";media.compatible=true;assertFalse(QuarkRangeSource.applies(media));media.compatible=false;for(String url:new String[]{"http://dl-pc-zb.drive.quark.cn/file","https://quark.cn.example.test/file","https://other.example/file"}){media.url=url;assertFalse(QuarkRangeSource.applies(media));}media.url="https://dl-pc-zb.drive.quark.cn/file";media.provider="TIANYI";assertFalse(QuarkRangeSource.applies(media));}
    private void serve(MockWebServer server,String text){server.setDispatcher(new okhttp3.mockwebserver.Dispatcher(){public MockResponse dispatch(RecordedRequest r){String[] parts=r.getHeader("Range").substring(6).split("-");int start=Integer.parseInt(parts[0]),end=Math.min(text.length()-1,Integer.parseInt(parts[1]));if(start>=text.length())return new MockResponse().setResponseCode(416).addHeader("Content-Range","bytes */"+text.length());return new MockResponse().setResponseCode(206).addHeader("Content-Range","bytes "+start+"-"+end+"/"+text.length()).setBody(text.substring(start,end+1));}});}
    private DataSource source(MockWebServer server){return new QuarkRangeSource.Factory(new OkHttpClient(),"Test",null,8).createDataSource();}
    private String read(DataSource source)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[3];int n;while((n=source.read(buffer,0,buffer.length))!=C.RESULT_END_OF_INPUT)bytes.write(buffer,0,n);return bytes.toString("UTF-8");}
    @Test public void orderedPartsPreserveEveryByteIncludingSeekAndShortFinalPart()throws Exception{
        try(MockWebServer server=new MockWebServer()){String original="0123456789abcdefghijklmnopqrstuvwxyz";serve(server,original);server.start();DataSource source=source(server);
            try{assertEquals(original.length()-3,source.open(new DataSpec.Builder().setUri(server.url("/file").toString()).setPosition(3).build()));assertEquals(original.substring(3),read(source));assertEquals(0,source.read(new byte[1],0,0));assertEquals(5,server.getRequestCount());}
            finally{source.close();}
            assertEquals(12,source.open(new DataSpec.Builder().setUri(server.url("/file").toString()).setPosition(20).setLength(12).build()));assertEquals(original.substring(20,32),read(source));source.close();
            for(int i=0;i<7;i++){RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);assertNotNull(request);assertTrue(request.getHeader("Range").matches("bytes=[0-9]+-[0-9]+"));assertEquals("identity",request.getHeader("Accept-Encoding"));}
        }
    }
    @Test public void ignoredRangesAndWrongOffsetsAreRejectedRatherThanDuplicated()throws Exception{
        try(MockWebServer server=new MockWebServer()){server.start();server.enqueue(new MockResponse().setBody("full-file"));DataSource source=source(server);assertThrows(IOException.class,()->source.open(new DataSpec.Builder().setUri(server.url("/ignored").toString()).build()));source.close();
            server.enqueue(new MockResponse().setResponseCode(206).addHeader("Content-Range","bytes 1-7/50").setBody("1234567"));assertThrows(IOException.class,()->source.open(new DataSpec.Builder().setUri(server.url("/wrong").toString()).build()));source.close();
        }
    }
    @Test public void closeCancelsAnActiveReadAndAllowsAReopen()throws Exception{
        try(MockWebServer server=new MockWebServer()){server.start();server.enqueue(new MockResponse().setResponseCode(206).addHeader("Content-Range","bytes 0-7/8").setSocketPolicy(SocketPolicy.NO_RESPONSE));DataSource source=source(server);ExecutorService worker=Executors.newSingleThreadExecutor();
            try{Future<?> pending=worker.submit(()->{try{source.open(new DataSpec.Builder().setUri(server.url("/slow").toString()).build());throw new AssertionError("Cancelled read completed");}catch(IOException expected){}});assertNotNull(server.takeRequest(3,TimeUnit.SECONDS));source.close();pending.get(3,TimeUnit.SECONDS);
                serve(server,"abcd");assertEquals(4,source.open(new DataSpec.Builder().setUri(server.url("/fresh").toString()).build()));assertEquals("abcd",read(source));
            }finally{source.close();worker.shutdownNow();}
        }
    }
}
