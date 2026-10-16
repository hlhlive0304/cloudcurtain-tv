package cn.cloudcurtain.tv;
import android.content.Context;import android.view.*;import android.widget.*;import android.text.TextUtils;

final class HistoryCard extends LinearLayout {
    HistoryCard(Context context,PlaybackHistory.Entry entry,boolean compact,Runnable click){
        super(context);setOrientation(VERTICAL);setFocusable(true);setClickable(true);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setId(View.generateViewId());setTag("history:"+entry.id());
        setPadding(Ui.dp(context,16),Ui.dp(context,12),Ui.dp(context,16),Ui.dp(context,12));setMinimumHeight(Ui.dp(context,92));setBackground(Ui.bg(Ui.PANEL,0,context));
        TextView title=Ui.text(context,entry.title(),compact?17:19,Ui.TEXT);title.setMaxLines(compact?2:1);title.setEllipsize(TextUtils.TruncateAt.END);addView(title,new LayoutParams(-1,-2));
        TextView detail=Ui.text(context,entry.detail(),13,Ui.MUTED);detail.setPadding(0,Ui.dp(context,6),0,Ui.dp(context,6));addView(detail,new LayoutParams(-1,-2));
        if(!compact){String date=entry.updated==0?"较早记录":new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(entry.updated));TextView stamp=Ui.text(context,date,12,Ui.MUTED);addView(stamp);}
        if(entry.duration>0){ProgressBar progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.min(1000,entry.position*1000/entry.duration));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));LayoutParams lp=new LayoutParams(-1,Ui.dp(context,3));lp.topMargin=Ui.dp(context,4);addView(progress,lp);}
        setContentDescription(entry.title()+"，"+entry.detail()+"，"+(entry.completed?"重新播放":"继续播放"));setOnFocusChangeListener((view,focus)->setBackground(Ui.bg(focus?0xFF234B50:Ui.PANEL,focus?Ui.ACCENT:0,context)));setOnClickListener(view->click.run());
    }
}
