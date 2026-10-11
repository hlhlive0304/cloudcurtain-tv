package cn.cloudcurtain.tv;

import androidx.media3.common.*;
import java.util.*;

/** Exact-file subtitles are authoritative; ambiguous sibling files still require matching. */
final class SubtitlePolicy {
    private SubtitlePolicy(){}
    static boolean chinese(String language,String label){
        String lang=language==null?"":language.toLowerCase(Locale.ROOT).replace('_','-');
        return lang.equals("chi")||lang.equals("zho")||lang.equals("chs")||lang.equals("cht")||lang.equals("zh")||lang.startsWith("zh-")
            ||(label!=null&&(label.contains("中文")||label.contains("简体")||label.contains("繁体")||label.contains("中英")||label.contains("中日")));
    }
    static TrackSelectionOverride bestChinese(Tracks tracks){
        TrackSelectionOverride best=null;int score=Integer.MIN_VALUE;
        for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<g.length;i++){
            Format f=g.getTrackFormat(i);if(!g.isTrackSupported(i)||!chinese(f.language,f.label))continue;
            int s=((f.selectionFlags&C.SELECTION_FLAG_FORCED)==0?100:0)+((f.selectionFlags&C.SELECTION_FLAG_DEFAULT)!=0?10:0);
            if(s>score){score=s;best=new TrackSelectionOverride(g.getMediaTrackGroup(),i);}
        }return best;
    }
    static TrackSelectionOverride bestAvailable(Tracks tracks){
        TrackSelectionOverride chinese=bestChinese(tracks);if(chinese!=null)return chinese;
        TrackSelectionOverride best=null;int score=Integer.MIN_VALUE;
        for(Tracks.Group g:tracks.getGroups())if(g.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<g.length;i++){
            if(!g.isTrackSupported(i))continue;Format f=g.getTrackFormat(i);int s=("en".equals(f.language)||"eng".equals(f.language)?100:0)+((f.selectionFlags&C.SELECTION_FLAG_FORCED)==0?20:0)+((f.selectionFlags&C.SELECTION_FLAG_DEFAULT)!=0?5:0);
            if(s>score){score=s;best=new TrackSelectionOverride(g.getMediaTrackGroup(),i);}
        }return best;
    }
    static MediaSpec.Sub exactChinese(List<MediaSpec.Sub> subtitles){
        for(MediaSpec.Sub sub:subtitles)if(sub.sameFile&&chinese(sub.language,sub.label))return sub;return null;
    }
    static String languageName(String language){
        if(chinese(language,null))return "中文";
        if("jpn".equals(language)||"ja".equals(language))return "日语";
        if("eng".equals(language)||"en".equals(language))return "英语";
        return language==null||language.isEmpty()?"未标注语言":language;
    }
    static String originalLabel(int width,int height){
        return width>0&&height>0?CloudApi.resolutionLabel(width,height)+" · 原文件":"原文件 · 原始画质";
    }
}