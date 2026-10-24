package cn.cloudcurtain.tv;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Three bounded, ordered range reads of the authorized original. No transcode or local proxy. */
@androidx.media3.common.util.UnstableApi
final class QuarkRangeSource implements DataSource {
    static final int PART_BYTES=2*1024*1024;
    static boolean applies(MediaSpec media){return "QUARK".equals(media.provider)&&!media.compatible&&(media.quality.equals("original")||media.quality.equals("auto"))&&Net.mediaHost(media,media.url);}
    static final class Factory implements DataSource.Factory {
        final OkHttpClient client;final String agent;final TransferListener listener;final int partBytes;
        Factory(OkHttpClient client,String agent,TransferListener listener){this(client,agent,listener,PART_BYTES);}
        Factory(OkHttpClient client,String agent,TransferListener listener,int partBytes){
            this.client=client.newBuilder().addNetworkInterceptor(chain->{Response r=chain.proceed(chain.request().newBuilder().header("Accept-Encoding","identity").build());
                if(r.isSuccessful()&&r.code()!=206){r.close();throw new IOException("原片服务器没有返回分段数据，请重新获取地址。");}return r;}).build();
            this.agent=agent;this.listener=listener;this.partBytes=partBytes;
        }
        public DataSource createDataSource(){QuarkRangeSource source=new QuarkRangeSource(client,agent,partBytes);if(listener!=null)source.addTransferListener(listener);return source;}
    }
    private static final Pattern RANGE=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)");
    private final OkHttpClient client;private final String agent;private final int partBytes;
    private final List<TransferListener> listeners=new CopyOnWriteArrayList<>();
    private final Set<Pending> active=Collections.newSetFromMap(new ConcurrentHashMap<Pending,Boolean>());private final ArrayDeque<Pending> queue=new ArrayDeque<>();
    private ExecutorService workers;private volatile long generation;private DataSpec request;
    private long remaining,total,nextPosition;private Part current;private int offset;private Uri uri;private Map<String,List<String>> headers=Collections.emptyMap();
    private QuarkRangeSource(OkHttpClient client,String agent,int partBytes){this.client=client;this.agent=agent;this.partBytes=partBytes;}
    public void addTransferListener(TransferListener listener){listeners.add(listener);}
    public long open(DataSpec spec)throws IOException{
        close();if(spec.httpMethod!=DataSpec.HTTP_METHOD_GET)throw new IOException("原片分段只支持读取。");
        request=spec;long epoch=generation;remaining=spec.length;nextPosition=spec.position;
        workers=Executors.newFixedThreadPool(3,run->{Thread thread=new Thread(run,"quark-original-range");thread.setDaemon(true);return thread;});
        try{
            Pending first=new Pending(nextPosition,length(),epoch);active.add(first);Part part=first.fetch();
            if(epoch!=generation)throw new IOException("读取已取消。");
            total=part.total;remaining=spec.length==C.LENGTH_UNSET?Math.max(0,total-spec.position):Math.min(spec.length,Math.max(0,total-spec.position));
            current=part;offset=0;uri=part.uri;headers=part.headers;nextPosition=spec.position+part.bytes.length;prefetch(epoch);return remaining;
        }catch(IOException|RuntimeException failure){close();throw failure;}
    }
    private int length(){return (int)Math.min(partBytes,remaining==C.LENGTH_UNSET?partBytes:remaining);}
    private void prefetch(long epoch){
        long end=request.position+(request.length==C.LENGTH_UNSET?total-request.position:Math.min(request.length,Math.max(0,total-request.position)));
        while(queue.size()<3&&nextPosition<end){int size=(int)Math.min(partBytes,end-nextPosition);Pending pending=new Pending(nextPosition,size,epoch);nextPosition+=size;active.add(pending);pending.future=workers.submit(pending::fetch);queue.add(pending);}
    }
    public int read(byte[] target,int start,int length)throws IOException{
        if(length==0)return 0;if(request==null||remaining==0)return C.RESULT_END_OF_INPUT;
        if(current==null||offset==current.bytes.length){
            Pending pending=queue.poll();if(pending==null)throw new EOFException("原片分段数据提前结束。");
            try{Part next=pending.future.get();if(next.total!=total)throw new IOException("原片长度已变化，请重新获取地址。");current=next;offset=0;uri=next.uri;headers=next.headers;prefetch(generation);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("原片读取已取消。");}
            catch(CancellationException e){throw new InterruptedIOException("原片读取已取消。");}
            catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof IOException)throw (IOException)cause;throw new IOException("原片分段读取失败。",cause);}
        }
        int count=(int)Math.min(length,Math.min(current.bytes.length-offset,remaining));
        System.arraycopy(current.bytes,offset,target,start,count);offset+=count;remaining-=count;return count;
    }
    public Uri getUri(){return uri;}
    public Map<String,List<String>> getResponseHeaders(){return headers;}
    public void close(){
        generation++;for(Pending pending:active)pending.cancel();active.clear();for(Pending pending:queue)pending.cancel();queue.clear();
        if(workers!=null){workers.shutdownNow();workers=null;}request=null;current=null;remaining=0;uri=null;headers=Collections.emptyMap();
    }
    private static String header(Map<String,List<String>> values,String name){for(Map.Entry<String,List<String>> e:values.entrySet())if(name.equalsIgnoreCase(e.getKey())&&!e.getValue().isEmpty())return e.getValue().get(0);return "";}
    private static final class Part {
        final byte[] bytes;final long total;final Uri uri;final Map<String,List<String>> headers;
        Part(byte[] bytes,long total,Uri uri,Map<String,List<String>> headers){this.bytes=bytes;this.total=total;this.uri=uri;this.headers=headers;}
    }
    private final class Pending {
        final long position,epoch;final int length;final DataSpec spec;volatile Call call;volatile Future<Part> future;volatile boolean cancelled;
        Pending(long position,int length,long epoch){this.position=position;this.length=length;this.epoch=epoch;this.spec=request;}
        void cancel(){cancelled=true;Call current=call;if(current!=null)current.cancel();Future<Part> waiting=future;if(waiting!=null)waiting.cancel(true);}
        Part fetch()throws IOException{
            OkHttpDataSource source=new OkHttpDataSource.Factory(req->{Call started=client.newCall(req);call=started;if(cancelled||epoch!=generation)started.cancel();return started;}).setUserAgent(agent).createDataSource();
            for(TransferListener listener:listeners)source.addTransferListener(listener);
            try{
                if(cancelled||epoch!=generation)throw new InterruptedIOException("原片读取已取消。");
                long opened=source.open(spec.buildUpon().setPosition(position).setLength(length).build());Map<String,List<String>> response=source.getResponseHeaders();
                if(opened==0&&source.getResponseCode()==416){String end=header(response,"Content-Range");long size=Long.parseLong(end.substring(end.lastIndexOf('/')+1));return new Part(new byte[0],size,source.getUri(),response);}
                Matcher match=RANGE.matcher(header(response,"Content-Range"));if(source.getResponseCode()!=206||!match.matches())throw new IOException("原片分段信息无效。");
                long begin=Long.parseLong(match.group(1)),end=Long.parseLong(match.group(2)),size=Long.parseLong(match.group(3));
                if(begin!=position||end<begin||end-begin>=length||size<=end)throw new IOException("原片分段位置不匹配。");
                byte[] bytes=new byte[(int)(end-begin+1)];int at=0;while(at<bytes.length){int count=source.read(bytes,at,bytes.length-at);if(count==C.RESULT_END_OF_INPUT)throw new EOFException("原片分段下载不完整。");at+=count;}
                return new Part(bytes,size,source.getUri(),response);
            }catch(NumberFormatException error){throw new IOException("原片分段长度无效。",error);}
            finally{try{source.close();}finally{call=null;active.remove(this);}}
        }
    }
}
