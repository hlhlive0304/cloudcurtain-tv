package cn.cloudcurtain.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

public final class Ui {
    public static final int BG=0xFF0B1322, PANEL=0xFF142338, TEXT=0xFFF1F5FC, MUTED=0xFF9EADC5, ACCENT=0xFF68DFC1;
    public static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    public static void full(Activity a) { a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); }
    public static GradientDrawable bg(int color,int stroke,Context c) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,14));
        if(stroke!=0)d.setStroke(dp(c,2),stroke);return d;
    }
    public static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l; }
    public static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    public static TextView text(Context c,String text,int sp,int color) {
        TextView t=new TextView(c);t.setText(text);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);return t;
    }
    public static Button button(Context c,String label,Runnable click) {
        Button b=new Button(c);b.setText(label);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(TEXT);
        b.setPadding(dp(c,18),dp(c,8),dp(c,18),dp(c,8));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
        b.setBackground(bg(PANEL,0,c));b.setOnClickListener(v->click.run());
        b.setOnFocusChangeListener((v,focus)->{b.setBackground(bg(focus?0xFF234B50:PANEL,focus?ACCENT:0,c));b.setTextColor(focus?Color.WHITE:TEXT);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,0,dp(c,12),dp(c,12));b.setLayoutParams(lp);return b;
    }
    public static LinearLayout page(Activity a,String title,String caption) {
        full(a);LinearLayout root=new AdaptiveLayout.Page(a);
        TextView name=text(a,title,29,TEXT);name.setTypeface(null,Typeface.BOLD);root.addView(name);
        TextView sub=text(a,caption,14,MUTED);sub.setPadding(0,dp(a,8),0,dp(a,20));root.addView(sub);a.setContentView(root);return root;
    }
    public static EditText edit(Context c,String hint,String value,boolean secret) {
        EditText e=new EditText(c);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(16);e.setHint(hint);e.setSingleLine(true);e.setText(value);
        e.setInputType(secret ? 129 : 1);e.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        e.setMinHeight(dp(c,52));e.setBackground(bg(PANEL,0,c));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(c,12));e.setLayoutParams(lp);return e;
    }
    public interface Input { void submit(String value); }
    public static void ask(Activity a,String title,String hint,String initial,Input action) {
        LinearLayout box=column(a);box.setPadding(dp(a,22),dp(a,14),dp(a,22),0);EditText e=edit(a,hint,initial,false);box.addView(e);
        AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(box).setNegativeButton("取消",null).setPositiveButton("确定",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String s=e.getText().toString().trim();if(s.isEmpty()){e.setError("请输入内容");return;}d.dismiss();action.submit(s);}));d.show();
    }
    public static void message(Activity a,String title,String text) { if(!a.isFinishing())new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show(); }
    public static void toast(Context c,String text) { Toast.makeText(c,text,Toast.LENGTH_LONG).show(); }
}
