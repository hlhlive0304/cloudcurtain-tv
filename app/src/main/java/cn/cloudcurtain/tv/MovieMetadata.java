package cn.cloudcurtain.tv;

import org.json.*;
import java.util.*;

final class MovieMetadata {
    long id;String source="TMDB",sourceUrl="",imagePage="",title="",original="",year="",overview="",poster="";double rating;boolean television;
    static MovieMetadata read(JSONObject j,boolean tv){MovieMetadata m=new MovieMetadata();tv=j.has("media_type")?j.optString("media_type").equals("tv"):tv;m.television=tv;m.id=j.optLong("id");m.title=j.optString(tv?"name":"title");m.original=j.optString(tv?"original_name":"original_title");String date=j.optString(tv?"first_air_date":"release_date");m.year=date.length()>=4?date.substring(0,4):"";m.overview=j.optString("overview");String path=j.optString("poster_path");m.poster=path.matches("/[A-Za-z0-9_-]+\\.(jpg|png)")?path:"";m.rating=j.optDouble("vote_average",0);if((j.optString("source").equals("WIKIPEDIA")||j.optString("source").equals("DOUBAN"))&&PublicPosters.imageUrl(j.optString("poster_path"))&&PublicPosters.articleUrl(j.optString("source_url"))){m.source=j.optString("source");m.poster=j.optString("poster_path");m.sourceUrl=j.optString("source_url");String page=j.optString("image_page");m.imagePage=PublicPosters.articleUrl(page)?page:m.sourceUrl;}return m;}
    JSONObject json()throws JSONException{return new JSONObject().put("media_type",television?"tv":"movie").put("id",id).put(television?"name":"title",title).put(television?"original_name":"original_title",original).put(television?"first_air_date":"release_date",year).put("overview",overview).put("poster_path",poster).put("vote_average",rating).put("source",source).put("source_url",sourceUrl).put("image_page",imagePage);}
    static MovieMetadata automatic(List<MovieMetadata> candidates,MovieTitle query){
        String name=MovieTitle.normalized(query.title);if(name.isEmpty()||name.equals(MovieTitle.normalized("未识别影片")))return null;
        List<MovieMetadata> exact=new ArrayList<>();for(MovieMetadata m:candidates)if(m.television==query.television&&(MovieTitle.normalized(m.title).equals(name)||MovieTitle.normalized(m.original).equals(name))&&(query.year.isEmpty()||query.year.equals(m.year)))exact.add(m);
        // Ambiguous titles require the user's selection; don't silently choose a remake.
        return exact.size()==1?exact.get(0):null;
    }
    String caption(){return (year.isEmpty()?"年份未知":year)+(source.equals("WIKIPEDIA")?"  ·  维基百科":"")+(source.equals("DOUBAN")?"  ·  豆瓣电影":"")+(rating>0?"  ·  TMDB "+String.format(Locale.ROOT,"%.1f",rating):"");}
}