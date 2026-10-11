package cn.cloudcurtain.tv;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Layout uses the measured window width (in pixels), including the user's font scale. */
public final class AdaptiveLayout {
    private AdaptiveLayout() {}

    public static final class Page extends LinearLayout {
        public Page(Context c) { super(c); setOrientation(VERTICAL); setBackgroundColor(Ui.BG); }
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int w=MeasureSpec.getSize(widthSpec);
            int side=Math.max(Ui.dp(getContext(),20),Math.min(Ui.dp(getContext(),48),Math.round(w*.04f)));
            int vertical=Math.max(Ui.dp(getContext(),16),Math.min(Ui.dp(getContext(),28),Math.round(w*.025f)));
            if(getPaddingLeft()!=side||getPaddingTop()!=vertical)setPadding(side,vertical,side,vertical);
            super.onMeasure(widthSpec,heightSpec);
        }
    }

    /** Wraps controls at their natural text size; every row reserves its tallest control. */
    public static final class Flow extends ViewGroup {
        public Flow(Context c) { super(c); }
        @Override protected LayoutParams generateDefaultLayoutParams(){return new MarginLayoutParams(-2,-2);}
        @Override protected LayoutParams generateLayoutParams(LayoutParams p){return p instanceof MarginLayoutParams?new MarginLayoutParams((MarginLayoutParams)p):new MarginLayoutParams(p);}
        @Override protected boolean checkLayoutParams(LayoutParams p){return p instanceof MarginLayoutParams;}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int limit=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE/4:MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            int x=0,y=0,row=0,max=0,state=0;
            for(int i=0;i<getChildCount();i++) {
                View v=getChildAt(i);if(v.getVisibility()==GONE)continue;
                MarginLayoutParams p=(MarginLayoutParams)v.getLayoutParams();
                v.measure(getChildMeasureSpec(MeasureSpec.makeMeasureSpec(Math.max(0,limit-p.leftMargin-p.rightMargin),MeasureSpec.AT_MOST),0,p.width),
                        getChildMeasureSpec(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),0,p.height));
                int w=v.getMeasuredWidth()+p.leftMargin+p.rightMargin,h=v.getMeasuredHeight()+p.topMargin+p.bottomMargin;
                if(x>0&&x+w>limit){y+=row;x=0;row=0;}
                x+=w;row=Math.max(row,h);max=Math.max(max,x);state=combineMeasuredStates(state,v.getMeasuredState());
            }
            setMeasuredDimension(resolveSizeAndState(max+getPaddingLeft()+getPaddingRight(),widthSpec,state),
                    resolveSizeAndState(y+row+getPaddingTop()+getPaddingBottom(),heightSpec,state<<MEASURED_HEIGHT_STATE_SHIFT));
        }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
            int limit=right-left-getPaddingRight(),x=getPaddingLeft(),y=getPaddingTop(),row=0;
            for(int i=0;i<getChildCount();i++) {
                View v=getChildAt(i);if(v.getVisibility()==GONE)continue;MarginLayoutParams p=(MarginLayoutParams)v.getLayoutParams();
                int w=v.getMeasuredWidth()+p.leftMargin+p.rightMargin,h=v.getMeasuredHeight()+p.topMargin+p.bottomMargin;
                if(x>getPaddingLeft()&&x+w>limit){y+=row;x=getPaddingLeft();row=0;}
                v.layout(x+p.leftMargin,y+p.topMargin,x+p.leftMargin+v.getMeasuredWidth(),y+p.topMargin+v.getMeasuredHeight());
                x+=w;row=Math.max(row,h);
            }
        }
    }

    /** Four columns on a normal TV window, two or one when width or large text requires it. */
    public static final class Cards extends ViewGroup {
        private int columns=1,cellWidth,rowHeight;
        public Cards(Context c){super(c);}
        @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-1,-2);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),gap=Ui.dp(getContext(),16);
            float fontScale=Math.max(1f,getResources().getConfiguration().fontScale);
            int min=Ui.dp(getContext(),180*fontScale);
            columns=width>=4*min+3*gap?4:width>=2*min+gap?2:1;
            cellWidth=Math.max(0,(width-(columns-1)*gap)/columns);
            rowHeight=Math.max(Ui.dp(getContext(),168),Math.min(Ui.dp(getContext(),216),Math.round(cellWidth*.8f)));
            for(int i=0;i<getChildCount();i++) {
                View v=getChildAt(i);v.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                rowHeight=Math.max(rowHeight,v.getMeasuredHeight());
            }
            for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(rowHeight,MeasureSpec.EXACTLY));
            int rows=(getChildCount()+columns-1)/columns;
            setMeasuredDimension(width,resolveSize(rows*rowHeight+Math.max(0,rows-1)*gap,heightSpec));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int gap=Ui.dp(getContext(),16);
            for(int i=0;i<getChildCount();i++){
                int x=(i%columns)*(cellWidth+gap),y=(i/columns)*(rowHeight+gap);
                getChildAt(i).layout(x,y,x+cellWidth,y+rowHeight);
            }
        }
    }
}
