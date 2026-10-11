package cn.cloudcurtain.tv;
import android.content.Context;import android.view.*;import android.widget.*;import android.text.TextUtils;
/** Generic cards need no thumbnail network requests and remain usable on a TV remote. */
final class FileCard extends LinearLayout {
 FileCard(Context c,CloudApi.Entry entry,boolean large,Runnable click){
  super(c);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(Ui.dp(c,12),Ui.dp(c,16),Ui.dp(c,12),Ui.dp(c,16));setFocusable(true);setClickable(true);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
  TextView icon=Ui.text(c,FilePresentation.icon(entry),large?52:34,Ui.ACCENT);icon.setGravity(Gravity.CENTER);addView(icon,new LayoutParams(-1,0,1));
  TextView name=Ui.text(c,entry.name,16,Ui.TEXT);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);addView(name,new LayoutParams(-1,-2));
  TextView info=Ui.text(c,FilePresentation.typeLabel(entry)+(entry.directory?"":" · "+UrlRules.size(entry.size)),12,Ui.MUTED);info.setPadding(0,Ui.dp(c,8),0,0);info.setGravity(Gravity.CENTER);addView(info);
  setMinimumHeight(Ui.dp(c,large?210:154));setBackground(Ui.bg(Ui.PANEL,0,c));setContentDescription(entry.name+"，"+FilePresentation.detail(entry));
  setOnFocusChangeListener((v,f)->setBackground(Ui.bg(f?0xFF234B50:Ui.PANEL,f?Ui.ACCENT:0,c)));setOnClickListener(v->click.run());
 }
 static final class Grid extends ViewGroup {
  private final boolean large;private int columns,width,height;Grid(Context c,boolean large){super(c);this.large=large;}
  protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-1,-2);}
  protected void onMeasure(int ws,int hs){int total=MeasureSpec.getSize(ws),gap=Ui.dp(getContext(),12),min=Ui.dp(getContext(),(large?220:150)*Math.max(1f,getResources().getConfiguration().fontScale));columns=Math.max(1,Math.min(large?4:6,(total+gap)/(min+gap)));width=Math.max(0,(total-(columns-1)*gap)/columns);height=Ui.dp(getContext(),large?230:174);
   for(int i=0;i<getChildCount();i++){View v=getChildAt(i);v.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));height=Math.max(height,v.getMeasuredHeight());}
   for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
   int rows=(getChildCount()+columns-1)/columns;setMeasuredDimension(total,resolveSize(rows*height+Math.max(0,rows-1)*gap,hs));
  }
  protected void onLayout(boolean changed,int l,int t,int r,int b){int gap=Ui.dp(getContext(),12);for(int i=0;i<getChildCount();i++){int x=i%columns*(width+gap),y=i/columns*(height+gap);getChildAt(i).layout(x,y,x+width,y+height);}}
 }
}