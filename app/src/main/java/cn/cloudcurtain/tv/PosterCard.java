package cn.cloudcurtain.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import android.graphics.Bitmap;

final class PosterCard extends LinearLayout {
    volatile boolean nearViewport;boolean metadataRequested,imageRequested;MovieMetadata cached;final MovieLibrary.Movie movie;private final FrameLayout poster;private final ImageView image;private final TextView title,info,placeholder,year;
    PosterCard(Context c,MovieLibrary.Movie movie,long position,Runnable click){super(c);this.movie=movie;setOrientation(VERTICAL);setId(View.generateViewId());setTag("poster:"+movie.key());setFocusable(true);setClickable(true);setPadding(Ui.dp(c,3),Ui.dp(c,3),Ui.dp(c,3),Ui.dp(c,4));setBackground(Ui.bg(0xFF202630,0,c));
        poster=new FrameLayout(c);addView(poster,new LayoutParams(-1,0));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{movie.provider.color&0xFF5F5F5F|0xFF20252D,0xFF10151D});gradient.setCornerRadius(Ui.dp(c,8));poster.setBackground(gradient);poster.setClipToOutline(true);
        image=new ImageView(c);image.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.addView(image,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=Ui.column(c);center.setGravity(Gravity.CENTER);center.setPadding(Ui.dp(c,14),0,Ui.dp(c,14),0);poster.addView(center,new FrameLayout.LayoutParams(-1,-1));
        TextView film=Ui.text(c,"▶",32,movie.provider.color);center.addView(film);placeholder=Ui.text(c,movie.title,19,Ui.TEXT);placeholder.setGravity(Gravity.CENTER);placeholder.setMaxLines(4);placeholder.setEllipsize(TextUtils.TruncateAt.END);placeholder.setPadding(0,Ui.dp(c,12),0,0);center.addView(placeholder);center.setTag("placeholder");
        TextView source=Ui.text(c,movie.provider.shortTitle,10,Ui.TEXT);source.setPadding(Ui.dp(c,6),Ui.dp(c,4),Ui.dp(c,6),Ui.dp(c,4));source.setBackground(Ui.bg(0xCF121820,0,c));FrameLayout.LayoutParams sourceLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);sourceLp.setMargins(0,Ui.dp(c,5),Ui.dp(c,5),0);poster.addView(source,sourceLp);
        year=Ui.text(c,movie.year,11,Ui.TEXT);year.setPadding(Ui.dp(c,6),Ui.dp(c,4),Ui.dp(c,6),Ui.dp(c,4));year.setBackground(Ui.bg(0xCF121820,0,c));FrameLayout.LayoutParams yearLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);yearLp.setMargins(0,0,Ui.dp(c,5),Ui.dp(c,5));poster.addView(year,yearLp);
        if(position>5000){TextView progress=Ui.text(c,"继续观看",10,Ui.ACCENT);progress.setPadding(Ui.dp(c,6),Ui.dp(c,4),Ui.dp(c,6),Ui.dp(c,4));progress.setBackground(Ui.bg(0xE0121820,0,c));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);pp.setMargins(Ui.dp(c,5),0,0,Ui.dp(c,5));poster.addView(progress,pp);}
        title=Ui.text(c,movie.title,14,Ui.TEXT);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(Ui.dp(c,3),Ui.dp(c,8),Ui.dp(c,3),Ui.dp(c,3));addView(title,new LayoutParams(-1,-2));
        info=Ui.text(c,movie.personal?"个人视频":movie.television?"剧集 · 待匹配封面":"电影 · 待匹配封面",10,Ui.MUTED);info.setSingleLine(true);info.setEllipsize(TextUtils.TruncateAt.END);info.setPadding(Ui.dp(c,3),0,Ui.dp(c,3),0);addView(info,new LayoutParams(-1,-2));setContentDescription(movie.title+"，"+movie.provider.title);
        setOnClickListener(v->click.run());setOnFocusChangeListener((v,focus)->setBackground(Ui.bg(focus?0xFF234B50:0xFF202630,focus?Ui.ACCENT:0,c)));
    }
    @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight();poster.getLayoutParams().height=Math.round(Math.max(0,width)*1.5f);super.onMeasure(w,h);}
    void metadata(MovieMetadata m){if(m==null)return;title.setText(m.title);info.setText(m.caption());placeholder.setText(m.title);year.setText(m.year);setContentDescription(m.title+"，"+movie.provider.title);}
    void bitmap(Bitmap bitmap){if(bitmap==null)return;image.setImageBitmap(bitmap);poster.findViewWithTag("placeholder").setVisibility(GONE);}
    void releaseBitmap(){nearViewport=false;if(image.getDrawable()!=null){image.setImageDrawable(null);poster.findViewWithTag("placeholder").setVisibility(VISIBLE);}imageRequested=false;}
}