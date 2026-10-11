package cn.cloudcurtain.tv;

import android.content.Context;
import android.view.*;

/** Equal poster sizes, safe focus outlines and deterministic D-pad row navigation. */
final class PosterGrid extends ViewGroup {
    private int columns=1,cellWidth,rowHeight,gap;int leftFocus=View.NO_ID,topFocus=View.NO_ID;
    PosterGrid(Context c){super(c);setClipChildren(false);setClipToPadding(false);setTag("poster_grid");setPadding(Ui.dp(c,2),Ui.dp(c,2),Ui.dp(c,2),Ui.dp(c,4));}
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-1,-2);}
    @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight();gap=Ui.dp(getContext(),14);float font=Math.max(1,getResources().getConfiguration().fontScale);columns=Math.max(1,Math.min(7,(width+gap)/(Ui.dp(getContext(),125*font)+gap)));cellWidth=Math.max(1,(width-gap*(columns-1))/columns);rowHeight=0;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));rowHeight=Math.max(rowHeight,child.getMeasuredHeight());}
        int rows=(getChildCount()+columns-1)/columns;setMeasuredDimension(MeasureSpec.getSize(w),resolveSize(getPaddingTop()+getPaddingBottom()+rows*rowHeight+Math.max(0,rows-1)*gap,h));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int x=getPaddingLeft()+(i%columns)*(cellWidth+gap),y=getPaddingTop()+(i/columns)*(rowHeight+gap);v.layout(x,y,x+cellWidth,y+rowHeight);
        v.setNextFocusLeftId(i%columns==0?leftFocus:getChildAt(i-1).getId());v.setNextFocusRightId(i%columns==columns-1||i==getChildCount()-1?v.getId():getChildAt(i+1).getId());v.setNextFocusUpId(i<columns?topFocus:getChildAt(i-columns).getId());v.setNextFocusDownId(i+columns<getChildCount()?getChildAt(i+columns).getId():v.getId());}}
    int columns(){return columns;}
}