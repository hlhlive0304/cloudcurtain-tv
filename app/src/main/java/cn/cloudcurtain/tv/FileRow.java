package cn.cloudcurtain.tv;
import android.content.Context;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
/** Shared remote-focusable file row for all four providers. */
final class FileRow extends LinearLayout {
    FileRow(Context c,CloudApi.Entry e,Runnable open){super(c);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setFocusable(true);setClickable(true);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setPadding(Ui.dp(c,20),Ui.dp(c,14),Ui.dp(c,20),Ui.dp(c,14));setMinimumHeight(Ui.dp(c,76));
        TextView icon=Ui.text(c,FilePresentation.icon(e),24,Ui.ACCENT);addView(icon,new LayoutParams(Ui.dp(c,44),-2));LinearLayout detail=Ui.column(c);addView(detail,new LayoutParams(0,-2,1));
        TextView name=Ui.text(c,e.name,19,Ui.TEXT);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.MIDDLE);detail.addView(name,new LayoutParams(-1,-2));
        TextView info=Ui.text(c,FilePresentation.detail(e),14,Ui.MUTED);info.setPadding(0,Ui.dp(c,7),0,0);detail.addView(info);setContentDescription(e.name+"，"+info.getText());setBackground(Ui.bg(Ui.PANEL,0,c));
        setOnFocusChangeListener((v,f)->setBackground(Ui.bg(f?0xFF234B50:Ui.PANEL,f?Ui.ACCENT:0,c)));setOnClickListener(v->open.run());LayoutParams lp=new LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(c,12));setLayoutParams(lp);
    }
}
