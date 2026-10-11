package cn.cloudcurtain.tv;
import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Bottom TV sheet: actual source choices above independent playback tabs. */
public final class PlaybackPanel {
 public static final String[] TABS={"字幕","清晰度","音效","音轨","倍速","更多"};
 public static final class Choice {
  final String label; final boolean selected; final Runnable action;
  public Choice(String label,boolean selected,Runnable action){this.label=label;this.selected=selected;this.action=action;}
 }
 public static AlertDialog show(Activity a,String title,String caption,List<Choice> choices){
  int width=a.getResources().getDisplayMetrics().widthPixels,height=a.getResources().getDisplayMetrics().heightPixels;
  LinearLayout root=Ui.column(a);root.setPadding(Ui.dp(a,28),Ui.dp(a,16),Ui.dp(a,28),Ui.dp(a,12));root.setBackground(Ui.bg(0xEC0B1322,0,a));
  TextView heading=Ui.text(a,title,21,Ui.TEXT);root.addView(heading);TextView text=Ui.text(a,caption,13,Ui.MUTED);text.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,12));root.addView(text);
  ScrollView scroll=new ScrollView(a);LinearLayout items=Ui.column(a);items.setPadding(Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4),0);items.setClipChildren(false);scroll.setClipToPadding(false);scroll.addView(items);
  int columns=Math.max(1,Math.min(4,(width-Ui.dp(a,64))/Ui.dp(a,165)));
  int rows=(choices.size()+columns-1)/columns;
  root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.max(Ui.dp(a,70),Math.min(height/3,Ui.dp(a,rows*84)))));
  AlertDialog dialog=new AlertDialog.Builder(a).setView(root).create();
  for(int i=0;i<choices.size();i+=columns){LinearLayout row=Ui.row(a);row.setClipChildren(false);row.setGravity(Gravity.TOP);row.setBaselineAligned(false);items.addView(row);
   for(int n=i;n<Math.min(i+columns,choices.size());n++){Choice c=choices.get(n);Button b=Ui.button(a,(c.selected?"✓ ":"")+c.label,()->{dialog.dismiss();c.action.run();});
    b.setStateListAnimator(null);b.setTag("playback-choice:"+n);b.setTextSize(14);b.setMinHeight(Ui.dp(a,68));b.setMaxLines(3);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,Ui.dp(a,10),Ui.dp(a,10));row.addView(b,lp);
   }
   for(int n=(choices.size()-i);n<columns;n++)row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
  }
  if(a instanceof PlayerActivity){LinearLayout tabs=Ui.row(a);tabs.setBaselineAligned(false);root.addView(tabs);
   for(int i=0;i<TABS.length;i++){final int tab=i;Button b=Ui.button(a,TABS[i],()->{dialog.dismiss();((PlayerActivity)a).openPlaybackTab(tab);});b.setTag("panel-tab:"+i);final boolean active=title.startsWith(TABS[i]);b.setBackground(Ui.bg(active?0xFF234B50:Ui.PANEL,active?Ui.ACCENT:0,a));b.setOnFocusChangeListener((v,focused)->b.setBackground(Ui.bg(focused||active?0xFF234B50:Ui.PANEL,focused||active?Ui.ACCENT:0,a)));b.setStateListAnimator(null);b.setTextSize(14);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(4,Ui.dp(a,8),4,Ui.dp(a,8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,Ui.dp(a,8),0);tabs.addView(b,lp);}
  }else root.addView(Ui.button(a,"返回播放",dialog::dismiss));
  dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setGravity(Gravity.BOTTOM);w.setLayout(width,-2);}if(!choices.isEmpty())root.findViewWithTag("playback-choice:0").requestFocus();});dialog.show();return dialog;
 }
}