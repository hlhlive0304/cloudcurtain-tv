package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    private LiveTranslation translation;private SubtitleView translatedView;private final CaptionSamples captions=new CaptionSamples();private final PlaybackStats playbackStats=new PlaybackStats();private String manualOriginalSubtitle;private OriginalSubtitles originalSubtitles;private SubtitleView originalSubtitleView;private boolean originalSubtitleLoading;private PlayerView view;private ExoPlayer player;private MediaSpec media;private TextView info;
    private final List<MediaSpec.Sub> added=new ArrayList<>();private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private long position=0;private boolean resumePlay=true;private float speed=1;private int selectedText=-1;private boolean textDisabled=false;private String pendingSubtitleLabel;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Button previousEpisode,nextEpisode;private TextView episodePosition;private EpisodeQueue episodes;private boolean episodeLoading,episodeSwitching;private int episodeFocusDirection;
    private Provider historyOwner;private int historyEpoch;private long lastHistoryPosition=-1;private boolean lastHistoryCompleted;
    private final Runnable historyTick=new Runnable(){@Override public void run(){if(!started)return;recordHistory();handler.postDelayed(this,10000);}};
    private void recordHistory(){
        if(player==null||!firstFrame||historyOwner!=null&&historyEpoch!=AccountSessions.generation(historyOwner))return;
        long at=player.getCurrentPosition();boolean ended=player.getPlaybackState()==Player.STATE_ENDED;if(!ended&&at<1000||at==lastHistoryPosition&&ended==lastHistoryCompleted)return;
        try{PlaybackHistory.Entry entry=PlaybackHistory.snapshot(media,at,player.getDuration(),ended,System.currentTimeMillis());lastHistoryPosition=at;lastHistoryCompleted=ended;
            PlaybackHistory.IO.execute(()->{if(historyOwner!=null&&historyEpoch!=AccountSessions.generation(historyOwner))return;try{new PlaybackHistory(getApplicationContext()).save(entry);}catch(Exception error){LoginDiagnostics.record(this,historyOwner,"保存播放记录",error);}});
        }catch(Exception error){LoginDiagnostics.record(this,historyOwner,"保存播放记录",error);}
    }
    private PlaybackRecovery recovery;private long waitingSince;private boolean firstFrame,watchdogFailed;private AlertDialog errorDialog;private AlertDialog playbackPanel;private AlertDialog subtitleSuggestion;
    private final Runnable watchdog=new Runnable(){@Override public void run(){
        if(player==null)return;long now=SystemClock.elapsedRealtime();
        boolean waiting=player.getPlayWhenReady()&&(player.getPlaybackState()==Player.STATE_BUFFERING||(!firstFrame&&player.getPlaybackState()==Player.STATE_READY));
        if(!waiting||refreshing)waitingSince=0;else if(waitingSince==0)waitingSince=now;
        else if(!watchdogFailed&&(firstFrame||now-playbackStats.lastByteTime>=15000||now-waitingSince>=120000)&&now-waitingSince>=(supportsRecovery()&&!compatiblePlayback&&firstFrame&&"auto".equals(media.quality)?15000:PlaybackRecovery.STALL_MS)){
            watchdogFailed=true;LoginDiagnostics.record(PlayerActivity.this,Provider.read(media.provider),"播放等待超时",new java.util.concurrent.TimeoutException());
            if(recovery.stalled(now-waitingSince,"auto".equals(media.quality)&&!compatiblePlayback,firstFrame)&&!"original".equals(media.quality)){if(!compatiblePlayback&&supportsRecovery()){Ui.toast(PlayerActivity.this,"原文件缓冲慢，自动模式切换网盘兼容线路；可手动选回原画");refreshAddress(true);}else recoverAutomatically();return;}
            player.pause();showFailure("视频长时间没有出画面，请重新获取播放地址或返回文件。",!compatiblePlayback&&supportsRecovery());
        }
        handler.postDelayed(this,1000);
    }};
    private ClipSkipPolicy skipPolicy;
    private final Runnable skipCheck=new Runnable(){@Override public void run(){
        if(player==null||!started)return;
        ClipSkipPolicy.Action action=skipPolicy.check(player.getCurrentPosition(),player.getDuration(),player.isPlaying(),player.getPlaybackState()==Player.STATE_READY);
        if(action==ClipSkipPolicy.Action.INTRO){player.seekTo(skipPolicy.intro()*1000L);Ui.toast(PlayerActivity.this,"已按你的设置跳过片头");}
        else if(action==ClipSkipPolicy.Action.OUTRO){player.seekTo(player.getDuration());player.pause();Ui.toast(PlayerActivity.this,"已按你的设置跳过片尾");}
        handler.postDelayed(this,500);
    }};
    private void skipMenu(){
        LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,24),Ui.dp(this,16),Ui.dp(this,24),0);
        root.addView(Ui.text(this,"仅用于当前视频。默认关闭；设为 0 表示不跳过。手动退回片头后不会再次强制跳过。",15,Ui.MUTED));
        root.addView(Ui.text(this,"片头时长（秒，0–1800）",16,Ui.TEXT));
        EditText intro=Ui.edit(this,"例如 90",String.valueOf(skipPolicy.intro()),false);intro.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);intro.setTag("skip-intro");root.addView(intro);
        root.addView(Ui.text(this,"片尾时长（秒，0–1800）",16,Ui.TEXT));
        EditText outro=Ui.edit(this,"例如 120",String.valueOf(skipPolicy.outro()),false);outro.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);outro.setTag("skip-outro");root.addView(outro);
        ScrollView content=new ScrollView(this);content.setClipToPadding(false);content.addView(root);playbackPanel=new AlertDialog.Builder(this).setTitle("跳过片头 / 片尾").setView(content).setPositiveButton("保存并应用",null).setNeutralButton("全部关闭",(d,n)->saveSkip(0,0)).setNegativeButton("返回播放",null).create();
        playbackPanel.setOnShowListener(d->playbackPanel.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int a=Integer.parseInt(intro.getText().toString()),b=Integer.parseInt(outro.getText().toString());if(a<0||b<0||a>1800||b>1800)throw new NumberFormatException();saveSkip(a,b);playbackPanel.dismiss();}catch(NumberFormatException e){Ui.toast(this,"请填写 0–1800 秒");}}));playbackPanel.show();
    }
    private void saveSkip(int intro,int outro){skipPolicy.configure(intro,outro);String key=Integer.toHexString(media.key.hashCode());getSharedPreferences("clip_skip",0).edit().putInt(key+":intro",intro).putInt(key+":outro",outro).apply();Ui.toast(this,intro==0&&outro==0?"片头片尾跳过已关闭":"已保存当前视频的跳过设置");}
    private boolean supportsRecovery(){return "ALI".equals(media.provider)||"QUARK".equals(media.provider)||"BAIDU".equals(media.provider);}
    private void recoverAutomatically(){Ui.toast(this,"正在更新当前清晰度的播放地址…");refreshAddress(compatiblePlayback);}
    private void showFailure(String message,boolean offerCompatible){
        if(isFinishing()||errorDialog!=null&&errorDialog.isShowing())return;
        errorDialog=new AlertDialog.Builder(this).setTitle("播放未成功").setMessage(message+(offerCompatible?"\n可切换网盘提供的在线转码视频（按账号实际提供的画质），保留当前进度。":""))
            .setPositiveButton(offerCompatible?"切换兼容播放":"重新获取地址",(d,n)->refreshAddress(offerCompatible||compatiblePlayback)).setNegativeButton("返回文件",(d,n)->finish()).setNeutralButton("关闭",null).show();
    }
    public static void open(Context c,MediaSpec media){c.startActivity(new Intent(c,PlayerActivity.class).putExtra("media",media.json()));}
    static void openAt(Context c,MediaSpec media,long position){c.startActivity(new Intent(c,PlayerActivity.class).putExtra("media",media.json()).putExtra("resume_position",Math.max(0,position)));}
    @Override public void onCreate(Bundle state){super.onCreate(state);Ui.full(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try{media=MediaSpec.parse(getIntent().getStringExtra("media"));compatiblePlayback=media.compatible;recovery=new PlaybackRecovery(media.provider);}catch(Exception e){finish();return;}
        try{historyOwner=Provider.valueOf(media.provider);historyEpoch=AccountSessions.generation(historyOwner);}catch(IllegalArgumentException ignored){}
        String skipKey=Integer.toHexString(media.key.hashCode());skipPolicy=new ClipSkipPolicy(getSharedPreferences("clip_skip",0).getInt(skipKey+":intro",0),getSharedPreferences("clip_skip",0).getInt(skipKey+":outro",0));
        position=state!=null?state.getLong("position"):getIntent().hasExtra("resume_position")?Math.max(0,getIntent().getLongExtra("resume_position",0)):getIntent().getBooleanExtra("from_start",false)?0:getSharedPreferences("progress",0).getLong(Integer.toHexString(media.key.hashCode()),0);
        speed=getIntent().getFloatExtra("speed",1);episodeFocusDirection=getIntent().getIntExtra("episode_direction",0);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF000000);setContentView(root);
        view=(PlayerView)getLayoutInflater().inflate(R.layout.tv_player,root,false);view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);view.setControllerShowTimeoutMs(5000);view.setShowSubtitleButton(false);view.setShowNextButton(false);view.setShowPreviousButton(false);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));originalSubtitleView=new SubtitleView(this);root.addView(originalSubtitleView,new FrameLayout.LayoutParams(-1,-1));originalSubtitleView.setClickable(false);originalSubtitleView.setFocusable(false);translatedView=new SubtitleView(this);root.addView(translatedView,new FrameLayout.LayoutParams(-1,-1));translatedView.setClickable(false);translatedView.setFocusable(false);
        info=Ui.text(this,media.title+"\n菜单键：底部播放菜单    左右键：快退 / 快进",15,Ui.TEXT);info.setBackgroundColor(0xAA0B1322);info.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,12));info.setMaxLines(2);
        root.addView(info,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout controls=view.findViewById(androidx.media3.ui.R.id.exo_basic_controls);
        for(int i=0;i<PlaybackPanel.TABS.length;i++){final int tab=i;Button b=Ui.button(this,PlaybackPanel.TABS[i],()->openPlaybackTab(tab));b.setId(View.generateViewId());b.setTag("player-tab:"+i);b.setTextSize(14);b.setPadding(Ui.dp(this,5),Ui.dp(this,8),Ui.dp(this,5),Ui.dp(this,8));b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxLines(1);b.setStateListAnimator(null);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,Ui.dp(this,6),0);controls.addView(b,lp);
            b.setNextFocusUpId(androidx.media3.ui.R.id.exo_progress);
        }
        LinearLayout episodeControls=view.findViewById(R.id.episode_controls);episodePosition=view.findViewById(R.id.episode_position);
        previousEpisode=episodeButton("上一集",-1,episodeControls);nextEpisode=episodeButton("下一集",1,episodeControls);
        updateEpisodeButtons();loadEpisodes();
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->info.setVisibility(visibility));
        view.requestFocus();
    }
    private Button episodeButton(String label,int direction,LinearLayout controls){
        Button button=Ui.button(this,label,()->switchEpisode(direction));button.setId(View.generateViewId());button.setTag(direction<0?"episode_previous":"episode_next");button.setTextSize(14);button.setStateListAnimator(null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,48));lp.setMargins(Ui.dp(this,6),0,0,Ui.dp(this,6));controls.addView(button,lp);return button;
    }
    private void updateEpisodeButtons(){
        if(previousEpisode==null)return;View focused=getCurrentFocus();boolean idle=!episodeLoading&&!episodeSwitching&&!refreshing;
        previousEpisode.setEnabled(idle&&episodes!=null&&episodes.neighbor(-1)!=null);nextEpisode.setEnabled(idle&&episodes!=null&&episodes.neighbor(1)!=null);
        previousEpisode.setAlpha(previousEpisode.isEnabled()?1f:.4f);nextEpisode.setAlpha(nextEpisode.isEnabled()?1f:.4f);
        View first=view.findViewWithTag("player-tab:0"),progress=view.findViewById(androidx.media3.ui.R.id.exo_progress);
        View entry=previousEpisode.isEnabled()?previousEpisode:nextEpisode.isEnabled()?nextEpisode:first;
        previousEpisode.setNextFocusUpId(progress.getId());nextEpisode.setNextFocusUpId(progress.getId());previousEpisode.setNextFocusDownId(first.getId());nextEpisode.setNextFocusDownId(first.getId());
        previousEpisode.setNextFocusRightId(nextEpisode.isEnabled()?nextEpisode.getId():previousEpisode.getId());nextEpisode.setNextFocusLeftId(previousEpisode.isEnabled()?previousEpisode.getId():nextEpisode.getId());
        progress.setNextFocusDownId(entry.getId());
        for(int i=0;i<PlaybackPanel.TABS.length;i++)view.findViewWithTag("player-tab:"+i).setNextFocusUpId((i<3&&previousEpisode.isEnabled()?previousEpisode:nextEpisode.isEnabled()?nextEpisode:entry).getId());
        if((focused==previousEpisode&&!previousEpisode.isEnabled())||(focused==nextEpisode&&!nextEpisode.isEnabled()))entry.requestFocus();
        if(episodeSwitching)episodePosition.setText("正在切换剧集…");else if(episodeLoading)episodePosition.setText("正在读取剧集…");else if(episodes!=null)episodePosition.setText("第 "+(episodes.index+1)+" / "+episodes.videos.size()+" 个视频");else episodePosition.setText(EpisodeQueue.available(media)?"剧集读取失败 · 更多中重试":"当前来源无剧集目录");
    }
    private void loadEpisodes(){
        if(episodeLoading||episodeSwitching||!EpisodeQueue.available(media)||worker.isShutdown())return;
        episodeLoading=true;updateEpisodeButtons();final MediaSpec scope=media;Provider provider=Provider.valueOf(scope.provider);int generation=AccountSessions.generation(provider);
        worker.execute(()->{try{CloudApi api=new CloudApi(provider);if(provider==Provider.ALI)api.selectAliSpace(scope.space);EpisodeQueue loaded=EpisodeQueue.load(scope,api::list);
            runOnUiThread(()->{if(isFinishing()||generation!=AccountSessions.generation(provider))return;episodeLoading=false;episodes=loaded;updateEpisodeButtons();
                if(episodeFocusDirection!=0){Button target=episodeFocusDirection>0?nextEpisode:previousEpisode;if(!target.isEnabled())target=episodeFocusDirection>0?previousEpisode:nextEpisode;if(target.isEnabled()){view.showController();target.requestFocus();}episodeFocusDirection=0;}
            });
        }catch(Exception e){LoginDiagnostics.record(this,provider,"读取剧集目录",e);runOnUiThread(()->{if(!isFinishing()){episodeLoading=false;episodes=null;updateEpisodeButtons();}});}});
    }
    private void switchEpisode(int direction){
        if(episodes==null||episodeLoading||episodeSwitching||refreshing||!started||worker.isShutdown())return;
        CloudApi.Entry target=episodes.neighbor(direction);if(target==null)return;
        episodeSwitching=true;updateEpisodeButtons();view.setControllerShowTimeoutMs(0);view.showController();final MediaSpec scope=media;final EpisodeQueue queue=episodes;Provider provider=Provider.valueOf(scope.provider);int generation=AccountSessions.generation(provider);float playbackSpeed=player==null?speed:player.getPlaybackParameters().speed;
        worker.execute(()->{try{
            CloudApi api=new CloudApi(provider);if(provider==Provider.ALI)api.selectAliSpace(scope.space);MediaSpec next=api.resolveSelected(target).inFolder(scope.folder,scope.parents);
            MovieTitle title=MovieTitle.parse(target.name,scope.parents);next.matchTitle=title.title+" "+title.year;next.subtitles.addAll(queue.subtitles);
            if(generation!=AccountSessions.generation(provider))throw new java.io.IOException("账号已退出，请重新登录。");
            runOnUiThread(()->{if(isFinishing())return;episodeSwitching=false;view.setControllerShowTimeoutMs(5000);updateEpisodeButtons();if(!started||generation!=AccountSessions.generation(provider))return;
                startActivity(new Intent(this,PlayerActivity.class).putExtra("media",next.json()).putExtra("from_start",true).putExtra("speed",playbackSpeed).putExtra("episode_direction",direction));finish();
            });
        }catch(Exception e){LoginDiagnostics.record(this,provider,"切换剧集",e);runOnUiThread(()->{if(!isFinishing()){episodeSwitching=false;view.setControllerShowTimeoutMs(5000);updateEpisodeButtons();if(started)Ui.message(this,"切换剧集失败",LoginErrors.friendly(e)+"\n当前视频和播放进度已保留，可重试。");}});}});
    }
    private void init(){if(player!=null||media==null)return;soundMode=getSharedPreferences("settings",0).getString("sound_mode","auto");firstFrame=false;watchdogFailed=false;waitingSince=0;
        OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(PlaybackNetwork.client(media)).setUserAgent(media.headers.containsKey("User-Agent")?media.headers.get("User-Agent"):Net.UA).setTransferListener(playbackStats);
        DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this).setRenderersFactory(new androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true)).setLoadControl(PlaybackBuffer.create(media)).setMediaSourceFactory(new DefaultMediaSourceFactory(data,captions.wrap(new androidx.media3.extractor.DefaultExtractorsFactory(),"main"))).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);view.setPlayer(player);player.setPlaybackSpeed(speed);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("zh").setForceHighestSupportedBitrate(true).setViewportSize(Integer.MAX_VALUE,Integer.MAX_VALUE,false).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,textDisabled).build());
        player.addListener(new Player.Listener(){
            @Override public void onTracksChanged(Tracks tracks){
                if(PlaybackVideo.unsupported(tracks)){if(refreshing)return;if(supportsRecovery()&&!compatiblePlayback&&!"original".equals(media.quality)&&recovery.error(null,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,false)){refreshAddress(true);return;}player.pause();watchdogFailed=true;handler.removeCallbacks(watchdog);showFailure("当前设备的解码器不支持这条视频轨道。4K 分辨率与 Dolby Vision / HEVC 的编码支持是两件事，可选择网盘提供的兼容画质。",supportsRecovery()&&!compatiblePlayback);return;}
                applyAudio(tracks);applySubtitles(tracks);
            }
            @Override public void onAudioSessionIdChanged(int id){audioSession=id;updateSound();}
            @Override public void onPlayerError(PlaybackException e){
                if(isFinishing())return;
                Provider p=media.provider.isEmpty()?null:Provider.read(media.provider);LoginDiagnostics.record(PlayerActivity.this,p,"播放视频",e);
                if(recovery.error(e,e.errorCode,compatiblePlayback)){if(!compatiblePlayback&&PlaybackErrors.httpStatus(e)==0&&!"original".equals(media.quality)){Ui.toast(PlayerActivity.this,"原文件暂不可解码，自动模式切换兼容播放");refreshAddress(true);return;}if(PlaybackErrors.httpStatus(e)>0){recoverAutomatically();return;}}
                watchdogFailed=true;showFailure(PlaybackErrors.describe(e,e.getErrorCodeName()),supportsRecovery()&&!compatiblePlayback);
            }
            @Override public void onRenderedFirstFrame(){firstFrame=true;}
            @Override public void onPlaybackStateChanged(int s){if(s==Player.STATE_ENDED){getSharedPreferences("progress",0).edit().remove(Integer.toHexString(media.key.hashCode())).apply();recordHistory();}}
        });if(!textDisabled&&selectedText>=0&&selectedText<added.size())pendingSubtitleLabel=added.get(selectedText).label;setItem(position,resumePlay);ensureFileSubtitles();ensureOriginalSubtitles();handler.removeCallbacks(skipCheck);handler.postDelayed(skipCheck,500);if(UrlRules.isWeb(media.url)){handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,1000);}
    }
    private boolean refreshing,compatiblePlayback,started;private boolean manualSubtitle,fileInfoRead,subtitleAutoLoading;private int subtitleChoiceRevision;
    private void applySubtitles(Tracks tracks){
        if(player==null)return;
        if(!compatiblePlayback&&("original".equals(media.quality)||"auto".equals(media.quality))){
            for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<g.length;i++){Format f=g.getTrackFormat(i);if(f.width>0&&f.height>0){media.sourceWidth=f.width;media.sourceHeight=f.height;break;}}
        }
        if(pendingSubtitleLabel!=null){
            for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<g.length;i++)if(pendingSubtitleLabel.equals(g.getTrackFormat(i).label)&&g.isTrackSupported(i)){
                pendingSubtitleLabel=null;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(),i)).build());return;
            }return;
        }
        if(textDisabled||manualSubtitle||selectedText>=0)return;
        TrackSelectionOverride best=SubtitlePolicy.bestChinese(tracks);
        if(best!=null){
            TrackSelectionOverride old=player.getTrackSelectionParameters().overrides.get(best.mediaTrackGroup);
            if(!best.equals(old))player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(best).build());
        }else autoFileSubtitle();
    }
    private void ensureFileSubtitles(){
        if(fileInfoRead||!("ALI".equals(media.provider)||"QUARK".equals(media.provider)))return;fileInfoRead=true;final MediaSpec scope=media;
        worker.execute(()->{try{
            CloudApi.FilePlaybackInfo data=playbackApi().filePlaybackInfo(playbackEntry());
            runOnUiThread(()->{if(isFinishing()||scope!=media)return;media.sourceWidth=data.width;media.sourceHeight=data.height;
                for(Iterator<MediaSpec.Sub> it=media.subtitles.iterator();it.hasNext();)if(it.next().sameFile)it.remove();media.subtitles.addAll(data.subtitles);
                getIntent().putExtra("media",media.json());autoFileSubtitle();
            });
        }catch(Exception e){LoginDiagnostics.record(this,Provider.read(scope.provider),"读取视频与字幕信息",e);}});
    }
    private void autoFileSubtitle(){
        if(player==null||!started||textDisabled||manualSubtitle||subtitleAutoLoading||selectedText>=0||SubtitlePolicy.bestChinese(player.getCurrentTracks())!=null)return;
        MediaSpec.Sub sub=SubtitlePolicy.exactChinese(media.subtitles);if(sub==null)return;
        subtitleAutoLoading=true;final MediaSpec scope=media;final int revision=subtitleChoiceRevision;
        worker.execute(()->{try{MediaSpec.Sub cached=new Subtitles(this).cache(sub,scope);runOnUiThread(()->{
            subtitleAutoLoading=false;if(!isFinishing()&&started&&scope==media&&revision==subtitleChoiceRevision&&!textDisabled&&!manualSubtitle&&player!=null&&SubtitlePolicy.bestChinese(player.getCurrentTracks())==null)attach(cached,false);
        });}catch(Exception e){LoginDiagnostics.record(this,Provider.read(scope.provider),"自动读取同文件字幕",e);runOnUiThread(()->subtitleAutoLoading=false);}});
    }
    private void ensureOriginalSubtitles(){
        if(!compatiblePlayback||!"QUARK".equals(media.provider)||player==null||textDisabled||(manualSubtitle&&manualOriginalSubtitle==null)||selectedText>=0||originalSubtitles!=null||originalSubtitleLoading)return;
        originalSubtitleLoading=true;final MediaSpec scope=media;final ExoPlayer main=player;
        worker.execute(()->{try{CloudApi api=new CloudApi(Provider.QUARK);MediaSpec original=api.resolve(new CloudApi.Entry(scope.key.substring(6),scope.title,false,0));
            runOnUiThread(()->{originalSubtitleLoading=false;if(!isFinishing()&&started&&scope==media&&player==main&&!textDisabled&&(!manualSubtitle||manualOriginalSubtitle!=null)&&!original.compatible){originalSubtitles=new OriginalSubtitles(this,original,main,originalSubtitleView,manualOriginalSubtitle,captions);}});
        }catch(Exception e){LoginDiagnostics.record(this,Provider.QUARK,"获取原文件字幕来源",e);runOnUiThread(()->originalSubtitleLoading=false);}});
    }
    private void closeOriginalSubtitles(){if(originalSubtitles!=null){originalSubtitles.close();originalSubtitles=null;}}
    private void refreshAddress(boolean compatible){refreshAddress(compatible,null);}
    private void refreshAddress(boolean compatible,String quality){
        if(refreshing||player==null)return;
        if(media.provider.isEmpty()||!media.key.startsWith(media.provider+":")){player.prepare();return;}
        if(episodeSwitching)return;refreshing=true;updateEpisodeButtons();handler.removeCallbacks(watchdog);long at=player.getCurrentPosition();boolean wasPlaying=player.getPlayWhenReady();position=at;resumePlay=wasPlaying;closeTranslation();closeOriginalSubtitles();player.stop();String id=media.key.substring(media.provider.length()+1);Ui.toast(this,"正在重新获取播放地址…");
        worker.execute(()->{try{CloudApi api=playbackApi();CloudApi.Entry entry=new CloudApi.Entry(id,media.title,false,media.sourceBytes);MediaSpec fresh=compatible?api.resolveCompatible(entry):api.resolveQuality(entry,quality==null?media.quality:quality);if(quality!=null&&quality.equals("auto")&&!fresh.compatible){fresh.quality="auto";fresh.qualityLabel="原文件 · 自动最高";}for(MediaSpec.Sub sub:media.subtitles)if(sub.url.startsWith("cloud:"))fresh.subtitles.add(sub);
            runOnUiThread(()->{refreshing=false;if(isFinishing())return;if(errorDialog!=null)errorDialog.dismiss();if(player!=null){sound.close();view.setPlayer(null);player.release();player=null;}fresh.inFolder(media.folder,media.parents);fresh.matchTitle=media.matchTitle;fresh.sourceWidth=media.sourceWidth;fresh.sourceHeight=media.sourceHeight;for(MediaSpec.Sub sub:media.subtitles)if(sub.sameFile&&SubtitlePolicy.exactChinese(fresh.subtitles)==null)fresh.subtitles.add(sub);compatiblePlayback=fresh.compatible;media=fresh;fileInfoRead=false;getIntent().putExtra("media",media.json());updateEpisodeButtons();getSharedPreferences("progress",0).edit().putBoolean("compatible:"+Integer.toHexString(media.key.hashCode()),media.compatible).putString("quality:"+Integer.toHexString(media.key.hashCode()),media.quality).apply();position=at;resumePlay=wasPlaying;if(started)init();});
        }catch(Exception e){LoginDiagnostics.record(this,Provider.read(media.provider),"重新获取播放地址",e);runOnUiThread(()->{refreshing=false;if(!isFinishing()){updateEpisodeButtons();if(started&&player!=null){setItem(at,wasPlaying);ensureOriginalSubtitles();handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,1000);}Ui.message(this,"获取地址失败",LoginErrors.friendly(e)+"\n已保留原播放来源和进度。");}});}});
    }
    private void setItem(long pos,boolean play){
        if(player==null)return;MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(media.url)).setMediaId(media.key).setMediaMetadata(new MediaMetadata.Builder().setTitle(media.title).build());
        String mime=media.mime.isEmpty()?UrlRules.mediaMime(media.url):media.mime;if(mime!=null)b.setMimeType(mime);
        List<MediaItem.SubtitleConfiguration> configs=new ArrayList<>();for(int i=0;i<added.size();i++){
            MediaSpec.Sub s=added.get(i);configs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url)).setMimeType(s.mime).setLabel(s.label).setLanguage(s.language.isEmpty()?null:s.language).setId("external-"+i).setSelectionFlags(i==selectedText?C.SELECTION_FLAG_DEFAULT:0).build());
        }b.setSubtitleConfigurations(configs);player.setMediaItem(b.build(),pos);player.prepare();player.setPlayWhenReady(play);
    }

    private String manualAudio;private final SoundEffects sound=new SoundEffects();private String soundMode="auto";private int audioSession;
    private void updateSound(){if(player==null)return;Format f=player.getAudioFormat();sound.attach(audioSession,soundMode,f==null?0:f.channelCount);}
    private void applyAudio(Tracks tracks){if(player==null)return;TrackSelectionOverride choice=AudioPolicy.best(tracks,manualAudio);if(choice==null)return;
        TrackSelectionOverride old=player.getTrackSelectionParameters().overrides.get(choice.mediaTrackGroup);if(!choice.equals(old))player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(choice).build());updateSound();
    }
    private CloudApi playbackApi(){CloudApi api=new CloudApi(Provider.read(media.provider));if("ALI".equals(media.provider))api.selectAliSpace(media.space);return api;}
    private CloudApi.Entry playbackEntry(){return new CloudApi.Entry(media.key.substring(media.provider.length()+1),media.title,false,media.sourceBytes);}
    private void qualityMenu(){
        if(player==null)return;List<PlaybackPanel.Choice> nativeChoices=new ArrayList<>();
        nativeChoices.add(new PlaybackPanel.Choice("自动 · 最高可播放",media.quality.equals("auto"),()->{
            if(supportsRecovery()){getSharedPreferences("progress",0).edit().putString("quality:"+Integer.toHexString(media.key.hashCode()),"auto").remove("compatible:"+Integer.toHexString(media.key.hashCode())).apply();changeQuality("auto");}
            else if(player!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setForceHighestSupportedBitrate(true).build());
        }));
        if(!supportsRecovery()){
            for(Tracks.Group g:player.getCurrentTracks().getGroups())if(g.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<g.length;i++)if(g.isTrackSupported(i)){
                Format f=g.getTrackFormat(i);TrackSelectionOverride choice=new TrackSelectionOverride(g.getMediaTrackGroup(),i);
                nativeChoices.add(new PlaybackPanel.Choice(CloudApi.resolutionLabel(f.width,f.height)+(f.bitrate>0?" · "+f.bitrate/1000000.0+" Mbps":""),g.isTrackSelected(i),()->{if(player!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(choice).build());}));
            }
            playbackPanel=PlaybackPanel.show(this,"清晰度",qualityCaption()+"\n档位来自当前视频；原文件只有一档时无法另选低清。",nativeChoices);return;
        }
        Ui.toast(this,"正在读取可用清晰度…");worker.execute(()->{try{List<CloudApi.Quality> variants=playbackApi().qualities(playbackEntry());runOnUiThread(()->{
            if(isFinishing()||player==null)return;for(CloudApi.Quality q:variants){String label=q.label;if(q.id.equals("original")){if(q.width>0&&q.height>0){media.sourceWidth=q.width;media.sourceHeight=q.height;}label=SubtitlePolicy.originalLabel(media.sourceWidth,media.sourceHeight);}nativeChoices.add(new PlaybackPanel.Choice(label,media.quality.equals(q.id),()->changeQuality(q.id)));}
            playbackPanel=PlaybackPanel.show(this,"清晰度",qualityCaption()+"\n原文件档位标示文件本身的分辨率；播放需要网盘提供地址和设备支持解码。\n转码仅列出网盘实际开放的档位，当前分辨率以正在播放的视频为准。",nativeChoices);
        });}catch(Exception e){runOnUiThread(()->{if(!isFinishing())Ui.message(this,"清晰度读取失败",LoginErrors.friendly(e));});}});
    }
    private String qualityCaption(){Format f=player==null?null:player.getVideoFormat();return "当前："+(!compatiblePlayback&&("original".equals(media.quality)||"auto".equals(media.quality))?SubtitlePolicy.originalLabel(media.sourceWidth,media.sourceHeight):media.qualityLabel)+(f!=null&&f.width>0?" · "+f.width+" × "+f.height:"");}
    private void changeQuality(String id){if(!refreshing&&id.equals(media.quality))return;recovery=new PlaybackRecovery(media.provider);refreshAddress(false,id);}
    private void soundMenu(){
        List<PlaybackPanel.Choice> choices=new ArrayList<>();for(int i=0;i<SoundEffects.IDS.length;i++){final String id=SoundEffects.IDS[i];choices.add(new PlaybackPanel.Choice(SoundEffects.LABELS[i],soundMode.equals(id),()->{
            soundMode=id;getSharedPreferences("settings",0).edit().putString("sound_mode",id).apply();updateSound();Ui.toast(this,sound.status());
        }));}
        Format f=player==null?null:player.getAudioFormat();playbackPanel=PlaybackPanel.show(this,"音效",sound.status()+"\n当前音轨："+(f==null?"尚未读取":AudioPolicy.label(f))+"\n自动优先最高可解码音轨和原声；虚拟环绕不等同于杜比全景声。",choices);
    }
    void openPlaybackTab(int tab){
        if(playbackPanel!=null)playbackPanel.dismiss();view.showController();
        switch(tab){case 0:subtitleMenu();break;case 1:qualityMenu();break;case 2:soundMenu();break;case 3:tracks(C.TRACK_TYPE_AUDIO);break;case 4:speedMenu();break;default:menu();}
    }
    private void speedMenu(){List<PlaybackPanel.Choice> choices=new ArrayList<>();for(float value:new float[]{.75f,1,1.25f,1.5f,2})choices.add(new PlaybackPanel.Choice(value+"×",speed==value,()->{speed=value;if(player!=null)player.setPlaybackSpeed(value);}));
        playbackPanel=PlaybackPanel.show(this,"倍速","切换后保留播放进度。",choices);
    }
    private void subtitleMenu(){
        List<PlaybackPanel.Choice> choices=new ArrayList<>();
        choices.add(new PlaybackPanel.Choice("在线字幕\n豆包优先",true,this::searchSubs));
        choices.add(new PlaybackPanel.Choice("豆包翻译现有字幕\n简体中文",translation!=null,this::translationMenu));
        choices.add(new PlaybackPanel.Choice("字幕轨道 / 关闭",false,()->tracks(C.TRACK_TYPE_TEXT)));
        choices.add(new PlaybackPanel.Choice("网盘字幕",false,this::cloudSubs));
        choices.add(new PlaybackPanel.Choice("OpenSubtitles\n手动搜索",false,this::manualSearchSubs));
        choices.add(new PlaybackPanel.Choice("字幕直链",false,this::subtitleLink));
        choices.add(new PlaybackPanel.Choice("本地字幕",false,this::localSubtitle));
        choices.add(new PlaybackPanel.Choice("字幕字号",false,this::subtitleSize));
        choices.add(new PlaybackPanel.Choice("跳过片头 / 片尾",false,this::skipMenu));
        playbackPanel=PlaybackPanel.show(this,"字幕","同文件中文字幕自动启用；在线匹配先用豆包识别片名、筛选实际字幕，匹配失败仍可使用同文件字幕。",choices);
    }
    private void closeTranslation(){
        if(translation!=null){translation.close();translation=null;}
        if(translatedView!=null)translatedView.setCues(Collections.emptyList());
        if(view!=null&&view.getSubtitleView()!=null)view.getSubtitleView().setVisibility(View.VISIBLE);
        if(originalSubtitleView!=null)originalSubtitleView.setVisibility(View.VISIBLE);
    }
    private void translationMenu(){
        if(translation!=null){new AlertDialog.Builder(this).setTitle("豆包字幕翻译").setMessage("正在按原字幕时间轴显示译文。").setPositiveButton("关闭翻译",(d,n)->closeTranslation()).setNegativeButton("继续翻译",null).show();return;}
        if(new Vault(this).get("doubao_api_key").isEmpty()){startActivity(new Intent(this,SettingsActivity.class).putExtra("focus_doubao",true));return;}
        List<ExoPlayer> sources=new ArrayList<>();if(originalSubtitles!=null)sources.add(originalSubtitles.player());if(player!=null)sources.add(player);
        ExoPlayer chosen=null;TrackSelectionOverride track=null;
        for(ExoPlayer source:sources)for(Tracks.Group g:source.getCurrentTracks().getGroups())if(g.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<g.length;i++)if(g.isTrackSupported(i)){
            Format f=g.getTrackFormat(i);if(SubtitlePolicy.chinese(f.language,f.label))continue;
            if(track==null||(f.language!=null&&f.language.startsWith("en"))){track=new TrackSelectionOverride(g.getMediaTrackGroup(),i);chosen=source;if(f.language!=null&&f.language.startsWith("en"))break;}
        }
        if(track==null){Ui.message(this,"未找到可翻译的文字字幕","影片需要可读取的内嵌文字字幕或 SRT / VTT 等字幕文件。已经烧录到画面上的英文无法作为文字轨道读取。");return;}
        final ExoPlayer source=chosen;final TrackSelectionOverride selected=track;Format f=track.mediaTrackGroup.getFormat(track.trackIndices.get(0));
        new AlertDialog.Builder(this).setTitle("豆包翻译现有字幕").setMessage("翻译："+SubtitlePolicy.languageName(f.language)+" → 简体中文\n使用你的豆包 API 额度。文字字幕发送文字；PGS 等图片字幕只发送所选字幕的小图片，不发送电影画面或网盘凭据。保留原时间轴；译文未完成时先显示原字幕。")
            .setPositiveButton("开始翻译",(d,n)->startTranslation(source,selected)).setNegativeButton("取消",null).show();
    }
    private void startTranslation(ExoPlayer source,TrackSelectionOverride selected){
        Format format=selected.mediaTrackGroup.getFormat(selected.trackIndices.get(0));String id=Objects.toString(format.id,"");
        if(source==player&&id.startsWith("external-")){
            try{int index=Integer.parseInt(id.substring(9));MediaSpec.Sub sub=added.get(index);int ticket=++subtitleChoiceRevision;Ui.toast(this,"正在读取字幕时间轴…");worker.execute(()->{try{captions.readExternal(this,sub,"main:"+id);runOnUiThread(()->{if(ticket==subtitleChoiceRevision&&started&&!isFinishing())startTranslationReady(source,selected);});}catch(Exception error){runOnUiThread(()->{if(started&&!isFinishing())Ui.message(this,"字幕翻译读取失败",LoginErrors.friendly(error));});}});}catch(RuntimeException error){Ui.toast(this,"字幕文件已变化，请重新选择");}return;
        }startTranslationReady(source,selected);
    }
    private void startTranslationReady(ExoPlayer source,TrackSelectionOverride selected){
        if(player==null||!started)return;if(source!=player&&(originalSubtitles==null||source!=originalSubtitles.player())){Ui.toast(this,"字幕来源已变化，请重新选择");return;}
        closeTranslation();Format format=selected.mediaTrackGroup.getFormat(selected.trackIndices.get(0));
        String scope=source==player?"main":"original";boolean bitmap=MimeTypes.APPLICATION_PGS.equals(format.codecs)||MimeTypes.APPLICATION_VOBSUB.equals(format.codecs)||MimeTypes.APPLICATION_DVBSUBS.equals(format.codecs);if(bitmap)captions.enableImages(scope+":"+Objects.toString(format.id,""),player.getCurrentPosition());
        manualSubtitle=true;subtitleChoiceRevision++;selectedText=-1;textDisabled=false;pendingSubtitleLabel=null;
        if(source==player){manualOriginalSubtitle=null;source.setTrackSelectionParameters(source.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(selected).build());}
        else {manualOriginalSubtitle=AudioPolicy.identity(format);originalSubtitles.select(selected);}
        if(view.getSubtitleView()!=null)view.getSubtitleView().setVisibility(bitmap&&source==player?View.VISIBLE:View.INVISIBLE);originalSubtitleView.setVisibility(bitmap&&source!=player?View.VISIBLE:View.INVISIBLE);
        translation=new LiveTranslation(player,captions,scope+":"+Objects.toString(format.id,""),translatedView,new Doubao(this),message->{if(!isFinishing()&&started)Ui.message(this,"字幕翻译未完成",message+"\n保留原字幕显示，可稍后关闭并重试。");});
        Ui.toast(this,"已开启豆包字幕翻译，正在读取原字幕");
    }
    private void subtitleLink(){Ui.ask(this,"字幕直链","https://…/subtitle.srt","",url->{if(!UrlRules.isWeb(url)){Ui.toast(this,"请输入 HTTP 或 HTTPS 字幕直链");return;}loadSub(new MediaSpec.Sub(url,"外部字幕",UrlRules.subtitleMime(url)));});}
    private void localSubtitle(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),200);}catch(ActivityNotFoundException e){Ui.message(this,"没有文件选择器","这台电视没有系统文件选择器，请使用字幕链接或同目录字幕。");}}
    private void subtitleSize(){new AlertDialog.Builder(this).setTitle("字幕字号").setItems(new String[]{"小","中","大","特大"},(x,i)->{if(view.getSubtitleView()!=null){view.getSubtitleView().setFractionalTextSize(new float[]{.035f,.05f,.065f,.08f}[i]);originalSubtitleView.setFractionalTextSize(new float[]{.035f,.05f,.065f,.08f}[i]);translatedView.setFractionalTextSize(new float[]{.035f,.05f,.065f,.08f}[i]);}}).show();}
    private void searchSubs(){smartSubs();}
    private boolean subtitleMatching;
    private void smartSubs(){
        if(subtitleMatching){Ui.toast(this,"豆包正在匹配，请稍候");return;}
        if(new Vault(this).get("doubao_api_key").isEmpty()){MediaSpec.Sub exact=SubtitlePolicy.exactChinese(media.subtitles);if(exact!=null){recommendExact(exact);return;}subtitleSuggestion=new AlertDialog.Builder(this).setTitle("在线字幕 · 豆包优先").setMessage("这台电视尚未导入豆包配置。请使用随安装包提供的配置码导入一次，之后无需重复输入。").setPositiveButton("导入 / 配置豆包",(d,n)->startActivity(new Intent(this,SettingsActivity.class).putExtra("focus_doubao",true))).setNegativeButton("直接选择网盘字幕",(d,n)->cloudSubs()).show();return;}
        subtitleMatching=true;Ui.toast(this,"正在用豆包匹配片名和字幕…");final MediaSpec subtitleScope=media;final List<MediaSpec.Sub> candidates=new ArrayList<>(media.subtitles);final List<TrackSelectionOverride> embedded=new ArrayList<>();final List<ExoPlayer> embeddedPlayers=new ArrayList<>();final List<String> embeddedLabels=new ArrayList<>();
        List<ExoPlayer> textSources=new ArrayList<>();if(player!=null)textSources.add(player);if(originalSubtitles!=null)textSources.add(originalSubtitles.player());for(ExoPlayer source:textSources)for(Tracks.Group group:source.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){Format f=group.getTrackFormat(i);embedded.add(new TrackSelectionOverride(group.getMediaTrackGroup(),i));embeddedPlayers.add(source);embeddedLabels.add((source==player?"内嵌字幕 · ":"原文件内嵌字幕 · ")+(f.label==null?"":f.label)+" · "+SubtitlePolicy.languageName(f.language));}
        worker.execute(()->{try{
            if("ALI".equals(subtitleScope.provider)&&SubtitlePolicy.exactChinese(candidates)==null){try{CloudApi api=new CloudApi(Provider.ALI);api.selectAliSpace(subtitleScope.space);CloudApi.FilePlaybackInfo file=api.filePlaybackInfo(new CloudApi.Entry(subtitleScope.key.substring(4),subtitleScope.title,false,0));candidates.addAll(file.subtitles);runOnUiThread(()->{if(subtitleScope==media){for(MediaSpec.Sub sub:file.subtitles){boolean duplicate=false;for(MediaSpec.Sub old:media.subtitles)if(old.url.equals(sub.url))duplicate=true;if(!duplicate)media.subtitles.add(sub);}}});}catch(Exception e){LoginDiagnostics.record(this,Provider.ALI,"在线匹配同文件字幕",e);}}
            List<String> labels=new ArrayList<>();for(MediaSpec.Sub s:candidates)labels.add(s.label);labels.addAll(embeddedLabels);
            Doubao.Match match=new Doubao(this).match(media.matchTitle.isEmpty()?media.title:media.matchTitle,labels);
            if(match.index>=candidates.size()&&match.index>=0){final TrackSelectionOverride selected=embedded.get(match.index-candidates.size());final ExoPlayer source=embeddedPlayers.get(match.index-candidates.size());final String label=embeddedLabels.get(match.index-candidates.size());runOnUiThread(()->{if(!isFinishing())subtitleSuggestion=new AlertDialog.Builder(this).setTitle("豆包推荐内嵌字幕").setMessage(match.query+"\n"+label).setPositiveButton("使用这条字幕",(d,n)->{if(player!=null){if(source!=player&&(originalSubtitles==null||source!=originalSubtitles.player())){Ui.toast(this,"播放来源已变化，请重新选择字幕");return;}manualSubtitle=true;subtitleChoiceRevision++;selectedText=-1;textDisabled=false;pendingSubtitleLabel=null;if(source==player){manualOriginalSubtitle=null;if(originalSubtitles!=null)originalSubtitles.select(null);player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(selected).build());}else{manualOriginalSubtitle=AudioPolicy.identity(selected.mediaTrackGroup.getFormat(selected.trackIndices.get(0)));originalSubtitles.select(selected);player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}Ui.toast(this,"已选择内嵌字幕");}}).setNegativeButton("字幕轨道",(d,n)->tracks(C.TRACK_TYPE_TEXT)).show();});return;}
            if(match.index>=0){MediaSpec.Sub selected=candidates.get(match.index);runOnUiThread(()->{if(!isFinishing())subtitleSuggestion=new AlertDialog.Builder(this).setTitle("豆包推荐字幕").setMessage(match.query+(match.year.isEmpty()?"":" · "+match.year)+"\n"+selected.label+"\n加载后可在字幕菜单切换；字幕时间轴以原文件为准。").setPositiveButton("使用这条字幕",(d,n)->loadSub(selected)).setNegativeButton("全部网盘字幕",(d,n)->cloudSubs()).show();});return;}
            MediaSpec.Sub exact=SubtitlePolicy.exactChinese(candidates);if(exact!=null){runOnUiThread(()->recommendExact(exact));return;}
            if(new Vault(this).get("os_api").isEmpty()){runOnUiThread(()->{if(!isFinishing())new AlertDialog.Builder(this).setTitle("已识别片名 · "+match.query).setMessage("当前没有可确认的中文字幕。可用豆包翻译视频内可读取的英文字幕，保留原时间轴；画面中烧录的文字需要另外的字幕文件。").setPositiveButton("豆包翻译成中文",(d,n)->translationMenu()).setNeutralButton("外部字幕搜索",(d,n)->manualSearchSubs()).setNegativeButton("网盘字幕",(d,n)->cloudSubs()).show();});return;}
            List<Subtitles.Result> found=new Subtitles(this).search(match.query);if(found.isEmpty()){runOnUiThread(()->Ui.message(this,"没有找到字幕","片名："+match.query+"。可使用网盘、本地或字幕直链。"));return;}
            labels.clear();for(Subtitles.Result s:found)labels.add(s.label);Doubao.Match ranked=new Doubao(this).match(match.query+" "+match.year,labels);
            runOnUiThread(()->{if(isFinishing())return;String[] items=new String[found.size()];for(int i=0;i<items.length;i++)items[i]=(ranked.index==i?"推荐 · ":"")+found.get(i).label;
                new AlertDialog.Builder(this).setTitle("在线字幕 · "+match.query).setItems(items,(d,n)->{Ui.toast(this,"正在下载字幕…");worker.execute(()->{try{MediaSpec.Sub sub=new Subtitles(this).download(found.get(n));runOnUiThread(()->attach(sub));}catch(Exception e){runOnUiThread(()->{if(!isFinishing())Ui.message(this,"下载失败",e.getMessage());});}});}).setNegativeButton("返回播放",null).show();
            });
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing())Ui.message(this,"豆包匹配失败",e.getMessage());});}finally{runOnUiThread(()->subtitleMatching=false);}});
    }

    private void playbackDiagnostics(){
        if(player==null)return;Format format=player.getVideoFormat();long ahead=Math.max(0,player.getBufferedPosition()-player.getCurrentPosition());
        Ui.message(this,"播放诊断","来源："+media.qualityLabel+"\n实际分辨率："+(format==null?"尚未读取":format.width+" × "+format.height)+"\n编码："+(format==null?"尚未读取":Objects.toString(format.sampleMimeType,"未知"))+"\n最近视频下载："+String.format(Locale.ROOT,"%.2f",playbackStats.recentBytesPerSecond()*8/1000000.0)+" Mbps\n原文件平均码率："+(PlaybackBuffer.averageBitsPerSecond(media,player.getDuration())==0?"未知 / 在线转码":String.format(Locale.ROOT,"%.1f Mbps",PlaybackBuffer.averageBitsPerSecond(media,player.getDuration())/1000000.0))+"\n已缓冲："+ahead/1000+" 秒\n已下载："+playbackStats.bytes/1048576+" MB\n缓冲内存上限："+PlaybackBuffer.targetBytes(Runtime.getRuntime().maxMemory(),media)/1048576+" MB\n以上为当前视频线路数据，不是家庭宽带测速。");
    }
    private void menu(){
        List<PlaybackPanel.Choice> choices=new ArrayList<>();
        choices.add(new PlaybackPanel.Choice("画面比例",false,()->new AlertDialog.Builder(this).setTitle("画面比例").setItems(new String[]{"适应屏幕","填满屏幕（拉伸）","裁切填满"},(x,i)->view.setResizeMode(new int[]{AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_FILL,AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[i])).show()));
        choices.add(new PlaybackPanel.Choice("字幕字号",false,this::subtitleSize));
        choices.add(new PlaybackPanel.Choice("跳过片头 / 片尾",false,this::skipMenu));
        choices.add(new PlaybackPanel.Choice("重新获取地址",false,()->refreshAddress(compatiblePlayback)));
        if(EpisodeQueue.available(media))choices.add(new PlaybackPanel.Choice("重新读取剧集",false,this::loadEpisodes));
        if(supportsRecovery())choices.add(new PlaybackPanel.Choice(compatiblePlayback?"切回原文件":"切换兼容播放",false,()->refreshAddress(!compatiblePlayback,compatiblePlayback?"original":null)));
        choices.add(new PlaybackPanel.Choice("播放诊断",false,this::playbackDiagnostics));
        choices.add(new PlaybackPanel.Choice("字幕与设置",false,()->startActivity(new Intent(this,SettingsActivity.class).putExtra("focus_doubao",true))));
        playbackPanel=PlaybackPanel.show(this,"更多","遥控器菜单键打开底部入口；返回键关闭选项，播放继续。",choices);
    }
    private void tracks(int type){if(type==C.TRACK_TYPE_TEXT)closeTranslation();
        if(player==null)return;ArrayList<String> labels=new ArrayList<>();ArrayList<TrackSelectionOverride> choices=new ArrayList<>();ArrayList<ExoPlayer> targets=new ArrayList<>();targets.add(player);labels.add(type==C.TRACK_TYPE_TEXT?"关闭字幕":"自动 · 最高可解码音轨");choices.add(null);
        List<ExoPlayer> sources=new ArrayList<>();sources.add(player);if(type==C.TRACK_TYPE_TEXT&&originalSubtitles!=null)sources.add(originalSubtitles.player());
        for(ExoPlayer source:sources)for(Tracks.Group group:source.getCurrentTracks().getGroups()){if(group.getType()!=type)continue;
            for(int i=0;i<group.length;i++){Format f=group.getTrackFormat(i);if(!group.isTrackSupported(i)){if(type==C.TRACK_TYPE_TEXT){labels.add((f.label!=null?f.label:SubtitlePolicy.languageName(f.language))+" · 原字幕格式暂不支持");choices.add(null);targets.add(source);}continue;}
                labels.add((group.isTrackSelected(i)?"✓ ":"")+(source==player?"":"原文件内嵌 · ")+(f.label!=null?f.label:(f.language!=null?f.language:"轨道 "+(labels.size())))+(f.channelCount>0?" · "+f.channelCount+" 声道":""));choices.add(new TrackSelectionOverride(group.getMediaTrackGroup(),i));targets.add(source);
            }
        }
        List<PlaybackPanel.Choice> items=new ArrayList<>();
        for(int i=0;i<labels.size();i++){final int n=i;items.add(new PlaybackPanel.Choice(labels.get(i),i==0&&(type==C.TRACK_TYPE_TEXT?textDisabled:manualAudio==null),()->{
            if(player==null)return;if(n>0&&choices.get(n)==null){Ui.message(this,"原字幕格式暂不支持","可使用网盘为同一文件提取的字幕，或添加 SRT / ASS / VTT 字幕。");return;}TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT&&n==0);
            if(type==C.TRACK_TYPE_TEXT&&originalSubtitles!=null){if(n>0&&targets.get(n)==originalSubtitles.player()){manualOriginalSubtitle=AudioPolicy.identity(choices.get(n).mediaTrackGroup.getFormat(choices.get(n).trackIndices.get(0)));originalSubtitles.select(choices.get(n));b.setTrackTypeDisabled(type,true);}else{manualOriginalSubtitle=null;originalSubtitles.select(null);}}if(n>0&&targets.get(n)==player)b.setOverrideForType(choices.get(n));if(type==C.TRACK_TYPE_AUDIO){manualAudio=n==0?null:AudioPolicy.identity(choices.get(n).mediaTrackGroup.getFormat(choices.get(n).trackIndices.get(0)));}if(type==C.TRACK_TYPE_TEXT){subtitleChoiceRevision++;manualSubtitle=true;pendingSubtitleLabel=null;selectedText=-1;textDisabled=n==0;}player.setTrackSelectionParameters(b.build());if(type==C.TRACK_TYPE_AUDIO&&n==0)applyAudio(player.getCurrentTracks());
        }));}
        playbackPanel=PlaybackPanel.show(this,type==C.TRACK_TYPE_TEXT?"字幕轨道":"音轨","来自当前视频；关闭字幕后不会被自动重新开启。",items);
    }
    private void recommendExact(MediaSpec.Sub sub){if(isFinishing())return;subtitleSuggestion=new AlertDialog.Builder(this).setTitle("已匹配同文件中文字幕").setMessage(sub.label+"\n这条字幕由网盘从当前视频中提取，不需要依赖片名搜索。").setPositiveButton("使用这条字幕",(d,n)->loadSub(sub)).setNegativeButton("全部网盘字幕",(d,n)->cloudSubs()).show();}
    private void cloudSubs(){
        if(media.subtitles.isEmpty()){Ui.message(this,"当前没有网盘字幕","同目录字幕和网盘提供的在线字幕会出现在这里。\n也可使用在线搜索、字幕链接或本地字幕。");return;}
        String[] labels=new String[media.subtitles.size()];for(int i=0;i<labels.length;i++)labels[i]=media.subtitles.get(i).label;
        subtitleSuggestion=new AlertDialog.Builder(this).setTitle("选择网盘字幕").setItems(labels,(d,n)->loadSub(media.subtitles.get(n))).show();
    }
    private void loadSub(MediaSpec.Sub sub){Ui.toast(this,"正在读取字幕…");worker.execute(()->{try{MediaSpec.Sub cached=new Subtitles(this).cache(sub,media);runOnUiThread(()->attach(cached));}catch(Exception e){runOnUiThread(()->{
        if(isFinishing())return;
        if(e instanceof BaiduSubtitleDownload.SessionUnavailable)subtitleSuggestion=new AlertDialog.Builder(this).setTitle("字幕下载需要百度网页验证").setMessage(e.getMessage())
            .setNegativeButton("知道了",null).setPositiveButton("打开百度官网",(d,n)->startActivity(new android.content.Intent(this,CloudBrowserActivity.class).putExtra("provider",Provider.BAIDU.name()))).show();
        else Ui.message(this,"字幕加载失败",e.getMessage());
    });}});}
    private void attach(MediaSpec.Sub sub){attach(sub,true);}
    private void attach(MediaSpec.Sub sub,boolean manual){if(isFinishing())return;closeTranslation();closeOriginalSubtitles();manualOriginalSubtitle=null;manualSubtitle=manual;if(manual)subtitleChoiceRevision++;
        long pos=player==null?position:player.getCurrentPosition();boolean playing=player==null?resumePlay:player.getPlayWhenReady();
        if(added.size()>=8)added.remove(0);added.add(sub);selectedText=added.size()-1;textDisabled=false;pendingSubtitleLabel=sub.label;
        if(player!=null){player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setPreferredTextLanguage(null).build());setItem(pos,playing);}
        Ui.toast(this,"已添加字幕，可在“字幕轨道”中切换");
    }
    private void manualSearchSubs(){
        if(new Vault(this).get("os_api").isEmpty()){new AlertDialog.Builder(this).setTitle("在线字幕需要配置").setMessage("请在设置填写你自己的 OpenSubtitles API Key。也可直接使用字幕链接、同目录或本地字幕。")
            .setPositiveButton("打开设置",(d,n)->startActivity(new Intent(this,SettingsActivity.class))).setNegativeButton("取消",null).show();return;}
        Ui.ask(this,"在线字幕 · 豆包优先","电影名 / 英文片名",media.title,query->{Ui.toast(this,"正在查询字幕…");worker.execute(()->{try{
            List<Subtitles.Result> results=new Subtitles(this).search(query);runOnUiThread(()->{
                if(isFinishing())return;if(results.isEmpty()){Ui.message(this,"没有找到字幕","尝试英文片名或简短关键词，也可以调整搜索语言。");return;}
                String[] labels=new String[results.size()];for(int i=0;i<labels.length;i++)labels[i]=results.get(i).label;
                new AlertDialog.Builder(this).setTitle("选择字幕 · "+query).setItems(labels,(d,n)->{
                    Ui.toast(this,"正在下载字幕…");worker.execute(()->{try{MediaSpec.Sub sub=new Subtitles(this).download(results.get(n));runOnUiThread(()->attach(sub));}catch(Exception e){runOnUiThread(()->{if(!isFinishing())Ui.message(this,"下载失败",e.getMessage());});}});
                }).setNegativeButton("取消",null).show();
            });
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing())Ui.message(this,"字幕搜索失败",e.getMessage());});}});});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==200&&result==RESULT_OK&&data!=null&&data.getData()!=null){
        Uri uri=data.getData();String name="本地字幕.srt";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}
        loadSub(new MediaSpec.Sub(uri.toString(),name,UrlRules.subtitleMime(name)));
    }}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN){view.showController();view.findViewWithTag("player-tab:0").requestFocus();}return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_NEXT||key==KeyEvent.KEYCODE_MEDIA_PREVIOUS){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)switchEpisode(key==KeyEvent.KEYCODE_MEDIA_NEXT?1:-1);return true;}
        if(player!=null&&!view.isControllerFullyVisible()&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_LEFT)player.seekBack();else player.seekForward();}return true;
        }return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(view!=null&&view.isControllerFullyVisible())view.hideController();else super.onBackPressed();}
    @Override protected void onStart(){super.onStart();started=true;init();handler.removeCallbacks(historyTick);handler.postDelayed(historyTick,10000);}
    @Override protected void onPause(){recordHistory();super.onPause();}
    @Override protected void onStop(){recordHistory();started=false;closeTranslation();closeOriginalSubtitles();handler.removeCallbacks(historyTick);handler.removeCallbacks(watchdog);if(errorDialog!=null)errorDialog.dismiss();if(playbackPanel!=null)playbackPanel.dismiss();if(subtitleSuggestion!=null)subtitleSuggestion.dismiss();if(player!=null){position=player.getCurrentPosition();resumePlay=player.getPlayWhenReady();speed=player.getPlaybackParameters().speed;
        if(player.getPlaybackState()!=Player.STATE_ENDED)getSharedPreferences("progress",0).edit().putLong(Integer.toHexString(media.key.hashCode()),position).apply();
        sound.close();view.setPlayer(null);player.release();player=null;
    }super.onStop();}
    @Override public void onSaveInstanceState(Bundle state){state.putLong("position",player==null?position:player.getCurrentPosition());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
