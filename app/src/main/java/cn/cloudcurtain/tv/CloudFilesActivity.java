package cn.cloudcurtain.tv;
import android.app.*;import android.os.Bundle;import android.view.*;import android.widget.*;import android.content.*;import java.util.*;import java.util.concurrent.*;
/** Read-only cloud browsing with local page controls and a remote-friendly focus order. */
public class CloudFilesActivity extends Activity {
 private Provider provider;private CloudApi api;private LinearLayout rows;private FrameLayout area;private TextView status;private int page=1,epoch=0;private boolean more,loading;
 private final ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();private List<CloudApi.Entry> current=new ArrayList<>();
 private final Map<String,List<CloudApi.Entry>> levels=new HashMap<>();private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private FilePresentation.Options options=new FilePresentation.Options();private Button filterButton,sortButton,viewButton;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);provider=Provider.read(getIntent().getStringExtra("provider"));api=new CloudApi(provider);ids.add(provider.root());names.add(api.spaceTitle());readOptions();
  LinearLayout root=Ui.page(this,provider.title+" · 文件","方向键选择，确定键打开 · 筛选和排序用于已加载的本页");
  AdaptiveLayout.Flow bar=new AdaptiveLayout.Flow(this);root.addView(bar);
  bar.addView(Ui.button(this,"上一级",this::up));bar.addView(Ui.button(this,"刷新",this::load));
  filterButton=Ui.button(this,"筛选",this::chooseFilters);bar.addView(filterButton);
  sortButton=Ui.button(this,"排序",this::chooseSort);bar.addView(sortButton);
  viewButton=Ui.button(this,"视图",this::chooseView);bar.addView(viewButton);
  bar.addView(Ui.button(this,"搜索本页",this::search));bar.addView(Ui.button(this,"更多",this::moreActions));
  status=Ui.text(this,"正在读取…",14,Ui.MUTED);status.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,10));root.addView(status);
  area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));load();
 }
 private String preferenceKey(){return provider.name()+":";}
 private void readOptions(){SharedPreferences p=getSharedPreferences("file_browser",0);String k=preferenceKey();options.types.addAll(p.getStringSet(k+"types",Collections.emptySet()));options.sources.addAll(p.getStringSet(k+"sources",Collections.emptySet()));options.sort=Math.max(0,Math.min(5,p.getInt(k+"sort",0)));options.view=Math.max(0,Math.min(3,p.getInt(k+"view",1)));options.descending=p.getBoolean(k+"descending",false);}
 private void saveOptions(){String k=preferenceKey();getSharedPreferences("file_browser",0).edit().putStringSet(k+"types",new HashSet<>(options.types)).putStringSet(k+"sources",new HashSet<>(options.sources)).putInt(k+"sort",options.sort).putInt(k+"view",options.view).putBoolean(k+"descending",options.descending).apply();}
 private void load(){
  if(worker.isShutdown())return;int ticket=++epoch;String folder=ids.get(ids.size()-1);int requestedPage=page;loading=true;area.removeAllViews();status.setText("第 "+page+" 页 · 读取中…");
  worker.execute(()->{try{CloudApi.Page data=api.list(folder,requestedPage);runOnUiThread(()->{
   if(ticket!=epoch||isFinishing())return;loading=false;current=data.entries;more=data.more;levels.put(folder,new ArrayList<>(current));
   Set<String> retained=new HashSet<>(ids.subList(Math.max(0,ids.size()-3),ids.size()));levels.keySet().retainAll(retained);render();
  });}catch(Exception e){LoginDiagnostics.record(this,provider,"读取文件",e);runOnUiThread(()->{if(ticket!=epoch||isFinishing())return;loading=false;status.setText("未能读取文件："+LoginErrors.friendly(e));area.addView(Ui.button(this,"重试读取",this::load));});}});
 }
 private void render(){
  if(loading)return;area.removeAllViews();rows=Ui.column(this);List<CloudApi.Entry> visible=FilePresentation.select(current,options);
  filterButton.setText(options.types.isEmpty()&&options.sources.isEmpty()?"筛选":"筛选 · 已启用");sortButton.setText("排序 · "+FilePresentation.SORT_LABELS[options.sort]);viewButton.setText("视图 · "+FilePresentation.VIEW_LABELS[options.view]);
  status.setText(android.text.TextUtils.join(" / ",names)+" · 第 "+page+" 页 · "+visible.size()+" / "+current.size()+" 项"+(options.query.isEmpty()?"":" · 搜索："+options.query));
  if(options.view==0){renderColumns(visible);return;}
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(rows);area.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  if(options.view==1){for(CloudApi.Entry entry:visible)rows.addView(new FileRow(this,entry,()->open(entry)));}
  else{FileCard.Grid grid=new FileCard.Grid(this,options.view==3);rows.addView(grid,new LinearLayout.LayoutParams(-1,-2));for(CloudApi.Entry entry:visible)grid.addView(new FileCard(this,entry,options.view==3,()->open(entry)));}
  if(visible.isEmpty())rows.addView(Ui.text(this,"本页没有符合条件的文件。可清除筛选或翻到下一页。",16,Ui.MUTED));addPaging(rows);
  View first=firstFile(rows);if(first!=null)first.requestFocus();
 }
 private View firstFile(View view){if(view instanceof FileRow||view instanceof FileCard)return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=firstFile(group.getChildAt(i));if(found!=null)return found;}}return null;}
 private void renderColumns(List<CloudApi.Entry> visible){
  LinearLayout panes=Ui.row(this);panes.setGravity(Gravity.TOP);area.addView(panes,new FrameLayout.LayoutParams(-1,-1));
  int max=getResources().getDisplayMetrics().widthPixels>=Ui.dp(this,800)?3:2,first=Math.max(0,ids.size()-max);LinearLayout last=null;
  for(int depth=first;depth<ids.size();depth++){
   final int level=depth;LinearLayout pane=Ui.column(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(0,0,Ui.dp(this,10),0);panes.addView(pane,lp);
   TextView title=Ui.text(this,names.get(depth),16,Ui.ACCENT);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);title.setPadding(0,0,0,Ui.dp(this,10));pane.addView(title);
   ScrollView scroll=new ScrollView(this);LinearLayout entries=Ui.column(this);scroll.addView(entries);pane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   if(depth==ids.size()-1){for(CloudApi.Entry e:visible)entries.addView(new FileRow(this,e,()->open(e)));if(visible.isEmpty())entries.addView(Ui.text(this,"没有符合条件的文件",14,Ui.MUTED));addPaging(entries);last=entries;rows=entries;}
   else{List<CloudApi.Entry> cached=levels.get(ids.get(depth));if(cached!=null)for(CloudApi.Entry e:cached)if(e.directory)entries.addView(new FileRow(this,e,()->navigate(level,e)));if(entries.getChildCount()==0)entries.addView(Ui.button(this,"回到这一层",()->{truncate(level+1);page=1;load();}));}
  }
  if(last!=null){View firstFile=firstFile(last);if(firstFile!=null)firstFile.requestFocus();}
 }
 private void addPaging(LinearLayout target){AdaptiveLayout.Flow nav=new AdaptiveLayout.Flow(this);target.addView(nav);if(page>1)nav.addView(Ui.button(this,"上一页",()->{page--;load();}));if(more)nav.addView(Ui.button(this,"下一页",()->{page++;load();}));}
 private void open(CloudApi.Entry entry){if(entry.directory){navigate(ids.size()-1,entry);return;}if(UrlRules.subtitle(entry.name))Ui.message(this,"同目录字幕","播放本目录的电影时，可在字幕菜单选择这个文件。");else if(UrlRules.video(entry.name))play(entry);else Ui.message(this,FilePresentation.typeLabel(entry),entry.name+"\n"+FilePresentation.sourceLabel(entry)+"\n此文件可在网盘官网中打开。");}
 private void truncate(int count){while(ids.size()>count){ids.remove(ids.size()-1);names.remove(names.size()-1);}}
 private void navigate(int level,CloudApi.Entry folder){truncate(level+1);ids.add(folder.id);names.add(folder.name);page=1;load();}
 private void chooseView(){new AlertDialog.Builder(this).setTitle("文件视图").setSingleChoiceItems(FilePresentation.VIEW_LABELS,options.view,(dialog,n)->{options.view=n;saveOptions();dialog.dismiss();render();}).setNegativeButton("取消",null).show();}
 private void chooseFilters(){
  FilePresentation.Options selected=options.copy();LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,12));
  box.addView(Ui.text(this,"本页筛选。文件夹保留，方便继续进入目录。\n文件来源仅采用网盘明确提供的信息。",14,Ui.MUTED));
  ArrayList<CheckBox> checks=new ArrayList<>();filterChecks(box,"文件类型",FilePresentation.TYPES,FilePresentation.TYPE_LABELS,selected.types,checks);filterChecks(box,"文件来源",FilePresentation.SOURCES,FilePresentation.SOURCE_LABELS,selected.sources,checks);
  box.addView(Ui.text(this,"未勾选时显示全部。数值来源代码或缺失信息归为“来源未提供”。",13,Ui.MUTED));ScrollView scroll=new ScrollView(this);scroll.addView(box);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("筛选文件").setView(scroll).setNeutralButton("重置",null).setNegativeButton("取消",null).setPositiveButton("确定",(d,n)->{options.types=selected.types;options.sources=selected.sources;saveOptions();render();}).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{for(CheckBox check:checks)check.setChecked(false);}));dialog.show();
 }
 private void filterChecks(LinearLayout box,String title,String[] keys,String[] labels,Set<String> selected,List<CheckBox> checks){
  TextView heading=Ui.text(this,title,17,Ui.TEXT);heading.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,8));box.addView(heading);AdaptiveLayout.Flow flow=new AdaptiveLayout.Flow(this);box.addView(flow);
  for(int i=0;i<keys.length;i++){String key=keys[i];CheckBox check=new CheckBox(this);check.setText(labels[i]);check.setTextSize(15);check.setTextColor(Ui.TEXT);check.setMinHeight(Ui.dp(this,48));check.setChecked(selected.contains(key));check.setPadding(Ui.dp(this,6),0,Ui.dp(this,18),0);check.setOnCheckedChangeListener((b,on)->{if(on)selected.add(key);else selected.remove(key);});flow.addView(check);checks.add(check);}
 }
 private void chooseSort(){
  final int[] sort={options.sort};final boolean[] descending={options.descending};LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);RadioGroup fields=new RadioGroup(this);
  for(int i=0;i<FilePresentation.SORT_LABELS.length;i++){RadioButton b=new RadioButton(this);b.setText(FilePresentation.SORT_LABELS[i]);b.setTextColor(Ui.TEXT);b.setId(View.generateViewId());b.setTag(i);fields.addView(b);b.setChecked(i==sort[0]);}fields.setOnCheckedChangeListener((g,id)->{RadioButton b=g.findViewById(id);sort[0]=(int)b.getTag();});box.addView(fields);
  RadioGroup direction=new RadioGroup(this);direction.setOrientation(LinearLayout.HORIZONTAL);for(int i=0;i<2;i++){RadioButton b=new RadioButton(this);b.setId(View.generateViewId());b.setTag(i==1);b.setText(i==0?"正序":"倒序");b.setTextColor(Ui.TEXT);direction.addView(b);b.setChecked(descending[0]==(i==1));}direction.setOnCheckedChangeListener((g,id)->descending[0]=(boolean)g.findViewById(id).getTag());box.addView(direction);
  ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle("本页排序 · 文件夹优先").setView(scroll).setNegativeButton("取消",null).setPositiveButton("确定",(d,n)->{options.sort=sort[0];options.descending=descending[0];saveOptions();render();}).show();
 }
 private void search(){LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),0);EditText text=Ui.edit(this,"按文件名搜索本页",options.query,false);box.addView(text);new AlertDialog.Builder(this).setTitle("搜索本页").setView(box).setNeutralButton("清除",(d,n)->{options.query="";render();}).setNegativeButton("取消",null).setPositiveButton("搜索",(d,n)->{options.query=text.getText().toString().trim();render();}).show();}
 private void moreActions(){String[] labels=provider==Provider.ALI?new String[]{"海报选片","切换空间","打开官网","退出登录 / 更换账号"}:new String[]{"海报选片","打开官网","退出登录 / 更换账号"};new AlertDialog.Builder(this).setTitle("网盘").setItems(labels,(d,n)->{String label=labels[n];if(label.equals("海报选片"))startActivity(new Intent(this,MovieWallActivity.class).putExtra("provider",provider.name()));else if(label.equals("切换空间"))chooseAliSpace();else if(label.equals("打开官网"))official();else AccountSessions.ask(this,provider,()->{epoch++;worker.shutdownNow();area.removeAllViews();status.setText("正在退出登录…");});}).setNegativeButton("取消",null).show();}
 private void chooseAliSpace(){new AlertDialog.Builder(this).setTitle("阿里云盘空间").setItems(new String[]{"资源库","备份盘"},(d,n)->{String space=n==0?"resource":"backup";getSharedPreferences("cloud_preferences",0).edit().putString("ali_space",space).apply();api.selectAliSpace(space);ids.clear();ids.add(provider.root());names.clear();names.add(api.spaceTitle());levels.clear();page=1;load();}).setNegativeButton("取消",null).show();}
 private void official(){startActivity(new Intent(this,CloudBrowserActivity.class).putExtra("provider",provider.name()));finish();}
 private void play(CloudApi.Entry entry){if(worker.isShutdown())return;status.setText("正在获取播放地址…");List<CloudApi.Entry> siblings=new ArrayList<>(current);List<String> parents=new ArrayList<>(names);int ticket=epoch;
  worker.execute(()->{try{MediaSpec media=api.resolveSelected(entry);MovieTitle match=MovieTitle.parse(entry.name,parents);media.matchTitle=match.title+" "+match.year;
   for(CloudApi.Entry e:siblings)if(!e.directory&&UrlRules.subtitle(e.name))media.subtitles.add(new MediaSpec.Sub("cloud:"+e.id,e.name,UrlRules.subtitleMime(e.name)));
   runOnUiThread(()->{if(ticket==epoch&&!isFinishing()){status.setText(entry.name);getSharedPreferences("progress",0).edit().putBoolean("compatible:"+Integer.toHexString(media.key.hashCode()),media.compatible).apply();PlayerActivity.open(this,media);}});
  }catch(Exception e){LoginDiagnostics.record(this,provider,"获取播放地址",e);runOnUiThread(()->{if(ticket==epoch&&!isFinishing()){status.setText("获取播放地址失败，可重新选择视频重试。");Ui.message(this,"无法开始播放",LoginErrors.friendly(e));}});}});
 }
 private void up(){if(ids.size()>1){truncate(ids.size()-1);page=1;load();}else finish();}
 @Override public void onBackPressed(){up();}
 @Override protected void onDestroy(){epoch++;worker.shutdownNow();super.onDestroy();}
}
