package cn.cloudcurtain.tv;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import java.util.*;
import java.util.concurrent.*;

public class MovieWallActivity extends Activity {
    private AlertDialog detailsDialog;private final List<Button> sideButtons=new ArrayList<>();private MovieLibrary library;private Tmdb tmdb;private PosterImages posters;private PosterGrid grid;private TextView status,count;private Button search,scan,previous,next;private int pageIndex=0;private ScrollView scroll;
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final ThreadPoolExecutor metadata=boundedPool(),images=boundedPool();
    private Future<?> reload;private volatile boolean active;private boolean firstLoad=true;
    private static final int PAGE_SIZE=24;
    private static ThreadPoolExecutor boundedPool(){return new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16));}
    private List<MovieLibrary.Movie> all=new ArrayList<>();private String query="",category="movie",provider="";private volatile int render=0;private boolean busy=false;private volatile boolean closed=false;private String scanStatus="";private String selected="";private Button activeCategory;private final List<Button> categoryButtons=new ArrayList<>(),filterButtons=new ArrayList<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);Ui.full(this);library=new MovieLibrary(this);tmdb=new Tmdb(this);posters=new PosterImages(this);
        if(state!=null){query=state.getString("query","");category=state.getString("category","movie");provider=state.getString("provider","");selected=state.getString("selected","");pageIndex=state.getInt("pageIndex",0);}else provider=getIntent().getStringExtra("provider")==null?"":getIntent().getStringExtra("provider");
        LinearLayout root=new AdaptiveLayout.Page(this);root.setBackgroundColor(0xFF171B22);setContentView(root);
        AdaptiveLayout.Flow header=new AdaptiveLayout.Flow(this);root.addView(header,new LinearLayout.LayoutParams(-1,-2));TextView brand=Ui.text(this,"云幕  /  我的影院",25,Ui.TEXT);brand.setTypeface(null,Typeface.BOLD);brand.setPadding(0,0,Ui.dp(this,30),Ui.dp(this,14));header.addView(brand);
        search=Ui.button(this,"搜索片名",()->search());search.setId(View.generateViewId());header.addView(search);scan=Ui.button(this,"更新影片库",()->scanChoices());header.addView(scan);header.addView(Ui.button(this,"海报设置",()->startActivity(new Intent(this,PosterSettingsActivity.class))));header.addView(Ui.button(this,"返回",this::finish));
        LinearLayout body=Ui.row(this);body.setGravity(Gravity.TOP);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));ScrollView sideScroll=new ScrollView(this);LinearLayout side=Ui.column(this);sideScroll.addView(side);LinearLayout.LayoutParams sideLp=new LinearLayout.LayoutParams(Ui.dp(this,120*Math.min(1.4f,getResources().getConfiguration().fontScale)),-1);sideLp.setMargins(0,Ui.dp(this,8),Ui.dp(this,18),0);body.addView(sideScroll,sideLp);
        String[] names={"全部影片","电影","漫剧","电视剧","继续观看","我的收藏"},values={"all","movie","animation","tv","continue","favorite"};for(int i=0;i<names.length;i++){final String value=values[i];Button b=Ui.button(this,names[i],()->{category=value;show(true);});b.setId(View.generateViewId());b.setTag("category:"+value);b.setTextSize(14);b.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));addSide(side,b);categoryButtons.add(b);}
        TextView label=Ui.text(this,"网盘账号",12,Ui.MUTED);label.setPadding(Ui.dp(this,8),Ui.dp(this,20),0,Ui.dp(this,10));side.addView(label);for(Provider p:MovieLibrary.ORDER){Button b=Ui.button(this,p.shortTitle+" · 文件",()->startActivity(new Intent(this,CloudBrowserActivity.class).putExtra("provider",p.name()).putExtra("scan_login",true)));b.setTextSize(13);b.setPadding(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10));addSide(side,b);}
        LinearLayout main=Ui.column(this);body.addView(main,new LinearLayout.LayoutParams(0,-1,1));AdaptiveLayout.Flow filters=new AdaptiveLayout.Flow(this);main.addView(filters);Button every=Ui.button(this,"全部网盘",()->{provider="";show(true);});every.setTag("filter:all");every.setId(View.generateViewId());filters.addView(every);filterButtons.add(every);for(Provider p:MovieLibrary.ORDER){Button b=Ui.button(this,p.shortTitle,()->{provider=p.name();show(true);});b.setTag("filter:"+p.name());b.setId(View.generateViewId());filters.addView(b);filterButtons.add(b);}
        count=Ui.text(this,"",13,Ui.TEXT);count.setPadding(Ui.dp(this,3),0,0,Ui.dp(this,8));LinearLayout pager=Ui.row(this);main.addView(pager);pager.addView(count,new LinearLayout.LayoutParams(0,-2,1));previous=Ui.button(this,"上一页",()->{pageIndex=Math.max(0,pageIndex-1);show(false);scroll.scrollTo(0,0);grid.post(()->{if(grid.getChildAt(0)!=null)grid.getChildAt(0).requestFocus();});});next=Ui.button(this,"下一页",()->{pageIndex++;show(false);scroll.scrollTo(0,0);grid.post(()->{if(grid.getChildAt(0)!=null)grid.getChildAt(0).requestFocus();});});previous.setTextSize(12);next.setTextSize(12);pager.addView(previous);pager.addView(next);status=Ui.text(this,"",12,Ui.MUTED);status.setMaxLines(2);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(Ui.dp(this,3),0,0,Ui.dp(this,12));main.addView(status);
        scroll=new ScrollView(this);scroll.setClipToPadding(false);grid=new PosterGrid(this);grid.topFocus=every.getId();scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.getViewTreeObserver().addOnScrollChangedListener(this::loadVisible);
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->loadVisible());scan.requestFocus();
    }
    private void addSide(LinearLayout side,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,8));side.addView(b,lp);sideButtons.add(b);b.setMinHeight(Ui.dp(this,42));b.setMinimumHeight(Ui.dp(this,42));}
    private void search(){LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),Ui.dp(this,14),Ui.dp(this,22),0);EditText input=Ui.edit(this,"片名 / 原文件名",query,false);box.addView(input);new AlertDialog.Builder(this).setTitle("搜索我的网盘影片").setView(box).setNegativeButton("取消",null).setNeutralButton("清空",(d,n)->{query="";show(true);}).setPositiveButton("搜索",(d,n)->{query=input.getText().toString().trim();show(true);}).show();}
    private void scanChoices(){if(busy){Ui.toast(this,"正在扫描，可继续浏览已找到的影片");return;}new AlertDialog.Builder(this).setTitle("更新影片库").setItems(new String[]{"继续扫描 / 添加影片","重新扫描当前筛选网盘"},(d,n)->scan(n==1)).setNegativeButton("取消",null).show();}
    private void scan(boolean restart){if(busy||closed)return;busy=true;scan.setText("扫描中…");final String filter=provider;worker.execute(()->{
        List<String> notes=new ArrayList<>();for(Provider p:MovieLibrary.ORDER){if(!filter.isEmpty()&&!p.name().equals(filter))continue;if(Thread.currentThread().isInterrupted())return;
            try{MovieLibrary.Result r=library.scan(p,restart,(which,pages,movies)->runOnUiThread(()->{if(closed)return;scanStatus=which.title+" · 已读 "+pages+" 页 · "+movies+" 部";status.setText(scanStatus);}));notes.add(p.shortTitle+" "+r.movies+" 部"+(r.complete&&r.categories.isEmpty()?"（根目录未找到指定文件夹）":r.complete?"":"（还有目录，请继续扫描）"));runOnUiThread(()->{if(!closed)show(false);});}
            catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){notes.add(p.shortTitle+"："+LoginErrors.friendly(e));LoginDiagnostics.record(this,p,"影片库扫描",e);}
        }runOnUiThread(()->{if(closed)return;busy=false;scan.setText("更新影片库");scanStatus=android.text.TextUtils.join("  ·  ",notes);show(false);});
    });}
    private void cancelArtwork(){
        metadata.getQueue().clear();images.getQueue().clear();tmdb.cancelPending();posters.cancelPending();
        if(grid!=null)for(int i=0;i<grid.getChildCount();i++)if(grid.getChildAt(i) instanceof PosterCard)((PosterCard)grid.getChildAt(i)).releaseBitmap();
    }
    private void show(boolean focusFirst){
        if(closed||!active)return;if(focusFirst)pageIndex=0;int ticket=++render;
        if(reload!=null)reload.cancel(true);cancelArtwork();
        final String wantedCategory=category,wantedProvider=provider,wantedQuery=query,old=selected;final int wantedPage=pageIndex;
        status.setText("正在加载影片库…");
        reload=worker.submit(()->{
            List<MovieLibrary.Movie> loaded=library.movies(),visible=new ArrayList<>();
            Map<String,MovieMetadata> cache=new HashMap<>();String normalized=MovieTitle.normalized(wantedQuery);
            Set<String> favorites=getSharedPreferences("movie_favorites",0).getStringSet("keys",Collections.emptySet());
            for(MovieLibrary.Movie movie:loaded){
                if(Thread.currentThread().isInterrupted()||closed||ticket!=render)return;
                if(!wantedProvider.isEmpty()&&!movie.provider.name().equals(wantedProvider))continue;
                if(wantedCategory.equals("movie")&&!movie.category().equals("电影")||wantedCategory.equals("animation")&&!movie.category().equals("漫剧")||wantedCategory.equals("tv")&&!movie.category().equals("电视剧")||wantedCategory.equals("continue")&&library.position(movie)<5000||wantedCategory.equals("favorite")&&!favorites.contains(movie.key()))continue;
                MovieMetadata meta=tmdb.cached(movie);if(meta!=null)cache.put(movie.key(),meta);
                String match=movie.title+" "+movie.name+" "+(meta==null?"":meta.title+" "+meta.original);
                if(!normalized.isEmpty()&&!MovieTitle.normalized(match).contains(normalized))continue;visible.add(movie);
            }
            Collections.sort(visible,(a,b)->wantedCategory.equals("continue")?Long.compare(library.position(b),library.position(a)):a.title.compareToIgnoreCase(b.title));
            boolean configured=tmdb.configured();int total=visible.size(),page=Math.min(wantedPage,Math.max(0,(total-1)/PAGE_SIZE)),start=page*PAGE_SIZE;
            List<MovieLibrary.Movie> slice=new ArrayList<>(visible.subList(start,Math.min(total,start+PAGE_SIZE)));
            runOnUiThread(()->{
                if(closed||!active||ticket!=render)return;all=loaded;pageIndex=page;grid.removeAllViews();
                activeCategory=categoryButtons.get(0);for(Button button:categoryButtons){boolean isActive=button.getTag().equals("category:"+category);button.setTextColor(isActive?Ui.ACCENT:Ui.TEXT);if(isActive)activeCategory=button;}grid.leftFocus=activeCategory.getId();
                for(Button button:filterButtons)button.setTextColor(button.getTag().equals("filter:"+(provider.isEmpty()?"all":provider))?Ui.ACCENT:Ui.TEXT);
                previous.setVisibility(total>PAGE_SIZE?View.VISIBLE:View.GONE);next.setVisibility(total>PAGE_SIZE?View.VISIBLE:View.GONE);previous.setEnabled(pageIndex>0);next.setEnabled(start+PAGE_SIZE<total);
                count.setText((provider.isEmpty()?"全部网盘":Provider.read(provider).title)+"  /  "+total+" 部"+(query.isEmpty()?"":"  /  搜索："+query)+(total>PAGE_SIZE?"  ·  第 "+(pageIndex+1)+" / "+((total+PAGE_SIZE-1)/PAGE_SIZE)+" 页":""));search.setText(query.isEmpty()?"搜索片名":"搜索："+query);
                status.setText(busy?scanStatus:!scanStatus.isEmpty()?scanStatus:all.isEmpty()?"只读取各网盘根目录的电影、漫剧、电视剧三个文件夹。未登录可从左侧扫码。":!configured?"按片名匹配网络封面；可在详情用豆包识别或更换。":"封面 / 简介来自 TMDB · 方向键选择，确定键查看并播放");
                View focus=null;
                for(MovieLibrary.Movie movie:slice){PosterCard card=new PosterCard(this,movie,library.position(movie),()->{selected=movie.key();details(movie);});card.cached=cache.get(movie.key());card.metadata(card.cached);grid.addView(card);if(movie.key().equals(old))focus=card;}
                if(grid.getChildCount()==0){TextView empty=Ui.text(this,"暂无影片\n\n请确认网盘根目录有电影、漫剧或电视剧文件夹，再更新影片库。",17,Ui.MUTED);empty.setPadding(Ui.dp(this,10),Ui.dp(this,40),0,0);grid.addView(empty);}
                if(focusFirst)scroll.scrollTo(0,0);
                if((focusFirst||focus!=null)&&!slice.isEmpty()){View target=focus==null?grid.getChildAt(0):focus;target.post(()->{if(ticket==render)target.requestFocus();});}
                grid.post(this::loadVisible);
                if(firstLoad){firstLoad=false;if(loaded.isEmpty()||library.needsMigration())scan(false);}
            });
        });
    }
    private void loadVisible(){
        if(closed||!active||grid==null||scroll.getHeight()==0)return;
        int top=scroll.getScrollY()-scroll.getHeight()/3,bottom=scroll.getScrollY()+scroll.getHeight()*4/3;
        int ticket=render;
        for(int i=0;i<grid.getChildCount();i++){
            if(!(grid.getChildAt(i) instanceof PosterCard))continue;PosterCard card=(PosterCard)grid.getChildAt(i);
            card.nearViewport=card.getBottom()>top&&card.getTop()<bottom;
            if(!card.nearViewport){card.releaseBitmap();continue;}
            if(card.cached!=null){loadImage(card,card.cached,ticket);continue;}
            if(card.metadataRequested||metadata.isShutdown()||metadata.getQueue().remainingCapacity()==0)continue;
            card.metadataRequested=true;
            metadata.execute(()->{
                boolean skipped=closed||!active||ticket!=render||!card.nearViewport;MovieMetadata found=null;try{if(!skipped)found=tmdb.match(card.movie);}catch(Exception ignored){}
                final MovieMetadata answer=found;runOnUiThread(()->{if(closed||!active||ticket!=render)return;if(skipped)card.metadataRequested=false;if(answer!=null){card.cached=answer;card.metadata(answer);loadImage(card,answer,ticket);}loadVisible();});
            });
        }
    }
    private void loadImage(PosterCard card,MovieMetadata meta,int ticket){
        if(meta.poster.isEmpty()||card.imageRequested||!card.nearViewport||images.isShutdown()||images.getQueue().remainingCapacity()==0)return;card.imageRequested=true;
        final int width=Math.max(64,card.getWidth()),height=Math.max(96,Math.round(width*1.5f));
        images.execute(()->{
            android.graphics.Bitmap bitmap=null;try{if(!closed&&active&&ticket==render&&card.nearViewport)bitmap=posters.load(meta.poster,width,height);}catch(Exception ignored){}
            final android.graphics.Bitmap answer=bitmap;
            runOnUiThread(()->{if(closed||!active||ticket!=render)return;if(card.nearViewport&&answer!=null)card.bitmap(answer);loadVisible();});
        });
    }
    private void details(MovieLibrary.Movie m){MovieMetadata meta=tmdb.cached(m);LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),Ui.dp(this,8),Ui.dp(this,22),Ui.dp(this,12));ScrollView sc=new ScrollView(this);sc.addView(box);
        String title=meta==null?m.title:meta.title;TextView caption=Ui.text(this,(meta==null?m.year:meta.caption())+"  ·  "+m.provider.title,14,Ui.MUTED);box.addView(caption);TextView overview=Ui.text(this,meta==null?"尚未匹配封面和简介。可用豆包识别或手动搜索片名。":meta.overview.isEmpty()?"暂无简介":meta.overview,15,Ui.TEXT);overview.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,16));overview.setMaxLines(7);overview.setEllipsize(TextUtils.TruncateAt.END);box.addView(overview);TextView file=Ui.text(this,"文件："+m.name+"\n目录："+android.text.TextUtils.join(" / ",m.parents),12,Ui.MUTED);file.setMaxLines(4);file.setEllipsize(TextUtils.TruncateAt.END);box.addView(file);
        long pos=library.position(m);String playLabel=pos>5000?"继续播放 · "+format(pos):"播放";AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(sc).setNegativeButton("返回",null).create();AdaptiveLayout.Flow actions=new AdaptiveLayout.Flow(this);box.addView(actions);actions.setPadding(0,Ui.dp(this,18),0,0);Button playButton=Ui.button(this,playLabel,()->{dialog.dismiss();play(m,false);});playButton.setTag("wall_play");actions.addView(playButton);if(pos>5000)actions.addView(Ui.button(this,"从头播放",()->{dialog.dismiss();play(m,true);}));
        if(meta!=null&&!meta.source.equals("TMDB"))actions.addView(Ui.button(this,"封面图片来源",()->{if(PublicPosters.articleUrl(meta.imagePage))openPosterSource(meta.imagePage);}));actions.addView(Ui.button(this,"匹配 / 更换封面",()->{dialog.dismiss();match(m);}));Button doubaoButton=Ui.button(this,"豆包匹配封面",()->{dialog.dismiss();doubaoPoster(m);});doubaoButton.setTag("wall_doubao");actions.addView(doubaoButton);boolean favorite=getSharedPreferences("movie_favorites",0).getStringSet("keys",Collections.emptySet()).contains(m.key());actions.addView(Ui.button(this,favorite?"取消收藏":"加入收藏",()->{Set<String> keys=new HashSet<>(getSharedPreferences("movie_favorites",0).getStringSet("keys",Collections.emptySet()));if(!keys.remove(m.key()))keys.add(m.key());getSharedPreferences("movie_favorites",0).edit().putStringSet("keys",keys).apply();dialog.dismiss();show(false);}));detailsDialog=dialog;dialog.show();dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-Ui.dp(this,40),Ui.dp(this,720)),-2);
    }
    private void doubaoPoster(MovieLibrary.Movie movie){
        if(movie.personal){Ui.message(this,"个人视频","个人视频不自动发送给豆包识别。可使用“匹配 / 更换封面”自行选择。");return;}
        if(new Vault(this).get("doubao_api_key").isEmpty()){startActivity(new Intent(this,SettingsActivity.class).putExtra("focus_doubao",true));return;}
        int generation=AccountSessions.generation(movie.provider);status.setText("正在用豆包识别片名…");
        worker.execute(()->{try{
            Doubao.Match identified=new Doubao(this).match(movie.title+" "+movie.year,Collections.emptyList());
            if(identified.confidence<.8)throw new java.io.IOException("豆包未能可靠识别片名，请手动修改片名匹配封面。");
            List<MovieMetadata> candidates=posterSearch(identified.query,identified.year,movie.television);
            runOnUiThread(()->{if(closed||generation!=AccountSessions.generation(movie.provider))return;if(candidates.isEmpty()){Ui.message(this,"未找到真实海报","豆包识别片名："+identified.query+"。可手动修改片名或年份重试。");return;}String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).title+" · "+candidates.get(i).caption();detailsDialog=new AlertDialog.Builder(this).setTitle("豆包识别 · 选择电影海报").setItems(labels,(d,n)->{try{tmdb.save(movie,candidates.get(n));show(false);}catch(Exception e){Ui.message(this,"保存失败","海报匹配未保存，请重试。");}}).setNegativeButton("返回影片库",null).show();status.setText("豆包已识别片名，请选择正确影片");});
        }catch(Exception error){runOnUiThread(()->{if(!closed){status.setText("封面匹配未完成");LoginDiagnostics.record(this,null,"豆包封面匹配",error);Ui.message(this,"豆包封面匹配",error instanceof java.io.IOException?error.getMessage():"封面查询未完成，请稍后重试。");}});}});
    }
    private void openPosterSource(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(ActivityNotFoundException e){Ui.message(this,"封面图片来源",url);}}
    private List<MovieMetadata> posterSearch(String title,String year,boolean tv)throws Exception{return tmdb.configured()?tmdb.search(title,year,tv):new PublicPosters().search(title,year,tv);}
    private void match(MovieLibrary.Movie m){
        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),Ui.dp(this,14),Ui.dp(this,22),0);EditText input=Ui.edit(this,"准确片名",m.title,false),year=Ui.edit(this,"年份（可留空）",m.year,false);box.addView(input);box.addView(year);CheckBox tv=new CheckBox(this);tv.setText("按剧集搜索");tv.setTextColor(Ui.TEXT);tv.setChecked(m.television);box.addView(tv);new AlertDialog.Builder(this).setTitle("纠正海报匹配").setView(box).setNegativeButton("取消",null).setNeutralButton("清除匹配",(d,n)->{tmdb.remove(m);show(false);}).setPositiveButton("搜索",(d,n)->{
            String title=input.getText().toString().trim(),y=year.getText().toString().trim();boolean television=tv.isChecked();int generation=AccountSessions.generation(m.provider);status.setText("正在搜索网络封面…");worker.execute(()->{try{List<MovieMetadata> candidates=posterSearch(title,y,television);runOnUiThread(()->{if(closed||generation!=AccountSessions.generation(m.provider))return;if(candidates.isEmpty()){Ui.message(this,"未找到匹配","可以修改片名或清空年份再搜索。");return;}String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).title+" · "+candidates.get(i).caption();new AlertDialog.Builder(this).setTitle("选择正确影片").setItems(labels,(choose,index)->{try{tmdb.save(m,candidates.get(index));show(false);}catch(Exception e){Ui.message(this,"保存失败","海报匹配未保存，请重试。");}}).setNegativeButton("取消",null).show();});}catch(Exception e){runOnUiThread(()->{if(!closed)Ui.message(this,"海报搜索失败",e.getMessage());});}});
        }).show();}
    private void play(MovieLibrary.Movie m,boolean fromStart){int generation=AccountSessions.generation(m.provider);status.setText("正在获取播放地址…");worker.execute(()->{try{MediaSpec media=library.resolve(m,fromStart);runOnUiThread(()->{if(!closed&&generation==AccountSessions.generation(m.provider)){status.setText("正在播放："+m.title);PlayerActivity.open(this,media);}});}catch(Exception e){LoginDiagnostics.record(this,m.provider,"海报选片播放",e);runOnUiThread(()->{if(!closed)Ui.message(this,"无法开始播放",LoginErrors.friendly(e));});}});}
    static String format(long ms){long seconds=ms/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
    @Override protected void onStart(){super.onStart();active=true;}
    @Override protected void onResume(){super.onResume();if(library!=null&&grid!=null)show(false);}
    @Override protected void onStop(){active=false;render++;if(reload!=null)reload.cancel(true);cancelArtwork();super.onStop();}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("query",query);state.putString("category",category);state.putString("provider",provider);state.putString("selected",selected);state.putInt("pageIndex",pageIndex);}
    @Override protected void onDestroy(){closed=true;render++;worker.shutdownNow();metadata.shutdownNow();images.shutdownNow();posters.close();super.onDestroy();}
}