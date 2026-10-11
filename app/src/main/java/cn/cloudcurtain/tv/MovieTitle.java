package cn.cloudcurtain.tv;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** A conservative filename query; never submits paths or file IDs to a metadata service. */
final class MovieTitle {
    final String title,year;final boolean television;
    MovieTitle(String title,String year,boolean tv){this.title=title;this.year=year;this.television=tv;}
    private static final Pattern YEAR=Pattern.compile("(?<![0-9])((?:19|20)[0-9]{2})(?![0-9])");
    private static final Pattern EPISODE=Pattern.compile("(?i)(?:\\bS[0-9]{1,2}\\s*E[0-9]{1,3}\\b|第[一二三四五六七八九十0-9]+[季集])");
    private static final Pattern RELEASE=Pattern.compile("(?i)(?:\\b(?:2160p|1080p|720p|480p|4k|8k|web[ -]?dl|webrip|bluray|blu[ -]?ray|bdrip|remux|hdr10\\+?|hdr|dv|h[ .-]?26[45]|x26[45]|hevc|avc|aac|dts|truehd|atmos|ddp|itunes|hamiVideo|netflix)\\b)");
    static MovieTitle parse(String file,List<String> ancestors){
        String raw=Normalizer.normalize(file==null?"":file,Normalizer.Form.NFKC);
        raw=raw.replaceFirst("(?i)\\.(mkv|mp4|avi|mov|m4v|ts|m2ts|wmv|webm|flv)$","");
        boolean tv=EPISODE.matcher(raw).find();String year=year(raw),title=clean(raw);
        if(title.isEmpty())for(int i=ancestors.size()-1;i>=0;i--){String parent=Normalizer.normalize(ancestors.get(i),Normalizer.Form.NFKC);if(generic(parent))continue;String candidate=clean(parent);if(!candidate.isEmpty()){title=candidate;if(year.isEmpty())year=year(parent);tv|=EPISODE.matcher(parent).find();break;}}
        if(title.isEmpty())title="未识别影片";
        return new MovieTitle(title,year,tv);
    }
    private static String year(String s){Matcher m=YEAR.matcher(s);String found="";while(m.find())found=m.group(1);return found;}
    private static boolean generic(String s){return s.matches("(?i)(?:season\\s*[0-9]+|S[0-9]{1,2}|第.+季|电影|电视剧|剧集|视频|来自分享|分享|个人空间|资源库|备份盘|4k|1080p|2160p)");}
    private static String clean(String s){
        s=s.replaceAll("[|｜]","").replace('_',' ').replace('.',' ');
        s=s.replaceFirst("^[A-Za-z]\\s*[)）]\\s*","");
        Matcher episode=EPISODE.matcher(s);if(episode.find())s=s.substring(0,episode.start());
        Matcher release=RELEASE.matcher(s);if(release.find())s=s.substring(0,release.start());
        // A trailing year is useful for querying, but not part of the displayed title.
        Matcher dated=YEAR.matcher(s);int start=-1,end=-1;while(dated.find()){start=dated.start();end=dated.end();}if(start>=0&&(start>0||!s.substring(end).matches(".*[\\p{L}].*")))s=s.substring(0,start)+" "+s.substring(end);
        s=s.replaceAll("[\\[\\]（）()]", " ").replaceAll("^[\\s-]+|[\\s-]+$","").replaceAll("\\s+"," ").trim();
        if(!s.matches("(?:19|20)[0-9]{2}")&&s.matches("(?i)(?:[0-9\\s-]+|S[0-9]+E[0-9]+|3Audio|CHS|CHT|中字|国语|双语)"))return "";
        return s;
    }
    static boolean personal(String file){return file!=null&&Pattern.compile("(?i)(?:^|[ ._-])(?:IMG|VID|MOV|DSC|MVI|PXL)[ ._-]?[0-9]{3,}").matcher(file).find();}
    static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
}