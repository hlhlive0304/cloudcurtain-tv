package cn.cloudcurtain.tv;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

/** Same-folder video navigation. Only the selected video's URL is ever resolved. */
final class EpisodeQueue {
    interface Listing {CloudApi.Page list(String folder,int page,String marker)throws Exception;}
    final List<CloudApi.Entry> videos;
    final List<MediaSpec.Sub> subtitles;
    final int index;
    private EpisodeQueue(List<CloudApi.Entry> videos,List<MediaSpec.Sub> subtitles,int index){this.videos=videos;this.subtitles=subtitles;this.index=index;}
    static boolean available(MediaSpec media){
        if(media.folder.isEmpty()||!media.key.startsWith(media.provider+":"))return false;
        try{Provider.valueOf(media.provider);return true;}catch(IllegalArgumentException e){return false;}
    }
    static EpisodeQueue load(MediaSpec media,Listing listing)throws Exception {
        if(!available(media))throw new IOException("当前播放来源没有剧集目录。");
        Map<String,CloudApi.Entry> files=new LinkedHashMap<>();String marker="";boolean complete=false;
        for(int page=1;page<=100;page++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            CloudApi.Page data=listing.list(media.folder,page,marker);int before=files.size();
            for(CloudApi.Entry e:data.entries)files.put(e.id,e);
            if(!data.more){complete=true;break;}
            if(files.size()==before||files.size()>10000)throw new IOException("剧集目录分页未完成，请重新读取。");
            marker=data.nextMarker;
        }
        if(!complete)throw new IOException("剧集目录过大，请进入具体剧集文件夹播放。");
        List<CloudApi.Entry> videos=new ArrayList<>();List<MediaSpec.Sub> subtitles=new ArrayList<>();
        for(CloudApi.Entry e:files.values())if(!e.directory){
            if(UrlRules.video(e.name))videos.add(e);
            else if(UrlRules.subtitle(e.name))subtitles.add(new MediaSpec.Sub("cloud:"+e.id,e.name,UrlRules.subtitleMime(e.name)));
        }
        Collections.sort(videos,(a,b)->{int c=natural(a.name,b.name);return c==0?a.id.compareTo(b.id):c;});
        String id=media.key.substring(media.provider.length()+1);int index=-1;
        for(int i=0;i<videos.size();i++)if(videos.get(i).id.equals(id)){index=i;break;}
        if(index<0)throw new IOException("当前视频已不在这个文件夹，请返回文件列表刷新。");
        return new EpisodeQueue(Collections.unmodifiableList(videos),Collections.unmodifiableList(subtitles),index);
    }
    CloudApi.Entry neighbor(int direction){int target=index+direction;return Math.abs(direction)==1&&target>=0&&target<videos.size()?videos.get(target):null;}
    // Compare digit runs by value, without integer overflow or lexicographic E10-before-E2.
    static int natural(String left,String right){
        String a=Normalizer.normalize(left,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT),b=Normalizer.normalize(right,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int i=0,j=0;
        while(i<a.length()&&j<b.length()){
            char x=a.charAt(i),y=b.charAt(j);
            if(x>='0'&&x<='9'&&y>='0'&&y<='9'){
                int ae=i,be=j;while(ae<a.length()&&a.charAt(ae)>='0'&&a.charAt(ae)<='9')ae++;while(be<b.length()&&b.charAt(be)>='0'&&b.charAt(be)<='9')be++;
                int az=i,bz=j;while(az<ae-1&&a.charAt(az)=='0')az++;while(bz<be-1&&b.charAt(bz)=='0')bz++;
                int comparison=Integer.compare(ae-az,be-bz);if(comparison!=0)return comparison;
                for(int n=0;n<ae-az;n++)if(a.charAt(az+n)!=b.charAt(bz+n))return Character.compare(a.charAt(az+n),b.charAt(bz+n));
                i=ae;j=be;
            }else{if(x!=y)return Character.compare(x,y);i++;j++;}
        }
        return Integer.compare(a.length()-i,b.length()-j);
    }
}
