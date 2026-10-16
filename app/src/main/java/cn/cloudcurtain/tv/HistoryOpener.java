package cn.cloudcurtain.tv;
import android.app.Activity;import android.widget.TextView;import java.util.concurrent.*;

/** Re-resolve cloud sources away from the UI before restoring the recorded position. */
final class HistoryOpener implements AutoCloseable {
    private final Activity activity;private final ExecutorService worker=Executors.newSingleThreadExecutor();private boolean busy,closed;
    HistoryOpener(Activity activity){this.activity=activity;}
    void open(PlaybackHistory.Entry entry,boolean fromStart,TextView status){
        if(busy||closed)return;busy=true;status.setText("正在打开："+entry.title());Provider provider=entry.source.provider.isEmpty()?null:Provider.read(entry.source.provider);int generation=provider==null?0:AccountSessions.generation(provider);
        worker.execute(()->{try{MediaSpec fresh=PlaybackHistory.resolve(entry);activity.runOnUiThread(()->{busy=false;if(closed||activity.isFinishing())return;if(provider!=null&&generation!=AccountSessions.generation(provider)){status.setText("网盘账号已更换，请重新选择影片。");return;}status.setText("");PlayerActivity.openAt(activity,fresh,fromStart?0:entry.resumePosition());});}
            catch(Exception error){activity.runOnUiThread(()->{busy=false;if(!closed&&!activity.isFinishing()){status.setText("暂未打开，可选择记录重试。");Ui.message(activity,"无法继续播放",LoginErrors.friendly(error));}});}});
    }
    @Override public void close(){closed=true;worker.shutdownNow();}
}
