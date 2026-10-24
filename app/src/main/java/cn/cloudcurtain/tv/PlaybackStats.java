package cn.cloudcurtain.tv;
import android.os.SystemClock;
import androidx.media3.datasource.*;
import java.util.*;
/** Counts bytes and response timing only. No URL, cookie, account or file name is retained. */
@androidx.media3.common.util.UnstableApi
final class PlaybackStats implements TransferListener {
 private final long[] times=new long[6],sizes=new long[6];volatile long bytes,lastByteTime;volatile int requests;
 public void onTransferInitializing(DataSource s,DataSpec spec,boolean network){}
 public synchronized void onTransferStart(DataSource s,DataSpec spec,boolean network){if(network)requests++;}
 public synchronized void onBytesTransferred(DataSource s,DataSpec spec,boolean network,int n){if(!network)return;bytes+=n;lastByteTime=SystemClock.elapsedRealtime();long sec=lastByteTime/1000;int i=(int)(sec%6);if(times[i]!=sec){times[i]=sec;sizes[i]=0;}sizes[i]+=n;}
 public void onTransferEnd(DataSource s,DataSpec spec,boolean network){}
 synchronized long recentBytesPerSecond(){long now=SystemClock.elapsedRealtime()/1000,sum=0;for(int i=0;i<6;i++)if(times[i]>now-5&&times[i]<=now)sum+=sizes[i];return sum/5;}
 synchronized void reset(){bytes=0;lastByteTime=0;requests=0;Arrays.fill(times,0);Arrays.fill(sizes,0);}
}
