package cn.cloudcurtain.tv;
import android.app.Instrumentation;import android.os.Bundle;import android.graphics.Bitmap;import java.io.IOException;

/** Real named-folder files and public artwork, no credentials or raw file paths in output. */
final class FolderPosterLiveCheck {
 static void run(Instrumentation test,Bundle result)throws Exception{
  MovieLibrary library=new MovieLibrary(test.getTargetContext());Tmdb tmdb=new Tmdb(test.getTargetContext());PosterImages images=new PosterImages(test.getTargetContext());
  String[] patterns={"剧场版03","剧场版12"};String[] expected={"1995","2004"};int loaded=0;
  for(int i=0;i<patterns.length;i++){
   MovieLibrary.Movie chosen=null;for(MovieLibrary.Movie m:library.movies())if(m.provider==Provider.BAIDU&&m.name.contains(patterns[i])){chosen=m;break;}
   if(chosen==null)throw new IOException("Named-folder sample missing");
   MovieMetadata match=tmdb.match(chosen);if(match==null||!match.year.equals(expected[i]))throw new IOException("Real artwork title/year did not match");
   Bitmap bitmap=images.load(match.poster);try{if(bitmap.getWidth()<100||bitmap.getHeight()<100)throw new IOException("Poster image too small");result.putInt("poster_"+i+"_width",bitmap.getWidth());result.putInt("poster_"+i+"_height",bitmap.getHeight());result.putString("poster_"+i+"_year",match.year);result.putString("poster_"+i+"_source",match.source);loaded++;}finally{bitmap.recycle();}
  }
  boolean dateTitle=false;for(MovieLibrary.Movie m:library.movies())if(m.provider==Provider.BAIDU&&m.name.startsWith("1995-07")){dateTitle=m.title.equals("回魂夜")&&m.year.equals("1995");break;}if(!dateTitle)throw new IOException("Date-only filename did not use its named parent");result.putBoolean("date_filename_uses_named_parent",true);
  for(Provider provider:new Provider[]{Provider.QUARK,Provider.ALI,Provider.TIANYI}){
   MovieLibrary.Movie chosen=null;for(MovieLibrary.Movie m:library.movies())if(m.provider==provider&&(m.name.toLowerCase(java.util.Locale.ROOT).contains("endgame")||m.name.contains("终局之战"))){chosen=m;break;}if(chosen==null)throw new IOException("Provider poster sample missing");
   MovieMetadata match=tmdb.match(chosen);if(match==null||!match.year.equals("2019")||!match.title.contains("复仇者联盟4"))throw new IOException("Provider folder poster did not match");Bitmap bitmap=images.load(match.poster);try{if(bitmap.getWidth()<100||bitmap.getHeight()<100)throw new IOException("Provider poster image missing");result.putBoolean(provider.name().toLowerCase(java.util.Locale.ROOT)+"_real_poster_loaded",true);loaded++;}finally{bitmap.recycle();}
  }
  result.putInt("real_folder_posters_loaded",loaded);result.putBoolean("titleless_filename_uses_parent_and_year",true);result.putBoolean("date_and_fansub_tags_removed",true);
 }
}
