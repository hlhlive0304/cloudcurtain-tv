package cn.cloudcurtain.tv;

import android.net.Uri;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Read-only adapters. Endpoint shapes cross-checked against OpenList (see NOTICE). */
public final class CloudApi {
    static final String QUARK_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch";
    public static class Entry {
        public String id,name,path;public boolean directory;public long size,created,modified;public String source="unknown";
        Entry(String id,String name,boolean directory,long size){this.id=id;this.name=name;this.directory=directory;this.size=size;}
    }
    public static class Page {public List<Entry> entries=new ArrayList<>();public boolean more;public String nextMarker="";}
    private final Provider provider;
    interface Transport { JSONObject request(String url,JSONObject body,Map<String,String> headers)throws Exception; }
    private final Transport transport;
    interface TokenSource { JSONObject read()throws Exception; }
    private final TokenSource tokens;
    private AliDeviceSession aliDevice;private AliTokenSession aliTokens;private TianyiSession tianyiSession;private volatile String aliSpace="default";private final Map<String,String> aliDrives=new java.util.concurrent.ConcurrentHashMap<>();
    public CloudApi(Provider provider){this(provider,Net::json);if(provider==Provider.TIANYI)tianyiSession=new TianyiSession(transport);if(provider==Provider.ALI){aliDevice=new AliDeviceSession(transport);aliTokens=new AliTokenSession(transport);aliSpace=CloudApp.context().getSharedPreferences("cloud_preferences",0).getString("ali_space","resource");}}
    CloudApi(Provider provider,Transport transport){this(provider,transport,()->{String value=new Vault(CloudApp.context()).get("ali_token");if(value.isEmpty())throw new IOException("请先扫码登录阿里云盘。");return new JSONObject(value);});}
    CloudApi(Provider provider,Transport transport,TokenSource tokens){this.provider=provider;this.transport=transport;this.tokens=tokens;}
    private JSONObject call(String url,JSONObject body)throws Exception{
        Map<String,String> headers=Net.headers("Referer",provider.origin,"Origin",provider.origin.substring(0,provider.origin.length()-1));
        if(provider==Provider.TIANYI)headers.put("Accept","application/json;charset=UTF-8");
        if(provider==Provider.QUARK&&(url.contains("/file/download?")||url.contains("/file/v2/play")))headers.put("User-Agent",QUARK_UA);
        JSONObject j=tianyiSession==null?transport.request(url,body,headers):tianyiSession.request(url,body,headers);
        if(provider==Provider.QUARK){if(j.optInt("status",200)>=400||j.optInt("code",0)!=0)throw new Net.ServiceException(j.optInt("status",400),j.optString("code"));}
        else if(provider==Provider.BAIDU&&j.optInt("errno",-1)!=0)throw new IOException("百度会话失效或访问受限（"+j.optInt("errno",-1)+"），请重新登录。");
        else if(provider==Provider.TIANYI&&(!j.optString("res_code","0").equals("0")||!j.optString("errorCode","").isEmpty()))throw new Net.ServiceException(200,j.optString("errorCode",j.optString("res_code")));
        return j;
    }
    public Page list(String folder,int page,String continuation)throws Exception{if(provider==Provider.ALI&&page>1&&!continuation.isEmpty()){String drive=aliDrive(aliToken());aliMarkers.put(drive+":"+folder+":"+page,continuation);}return list(folder,page);}
    public Page list(String folder,int page)throws Exception{
        Page result=new Page();
        if(provider==Provider.QUARK){
            String url=Uri.parse("https://drive.quark.cn/1/clouddrive/file/sort").buildUpon().appendQueryParameter("pr","ucpro").appendQueryParameter("fr","pc")
                .appendQueryParameter("pdir_fid",folder).appendQueryParameter("_page",""+page).appendQueryParameter("_size","100").appendQueryParameter("_fetch_total","1").appendQueryParameter("_sort","file_type:asc,file_name:asc").build().toString();
            JSONObject j=call(url,null);JSONObject d=j.optJSONObject("data");if(d==null||d.optJSONArray("list")==null)throw new IOException("未获取到夸克文件列表，请先在官网登录。");
            JSONArray a=d.getJSONArray("list");for(int i=0;i<a.length();i++){JSONObject f=a.getJSONObject(i);result.entries.add(CloudFileMetadata.apply(new Entry(f.getString("fid"),f.getString("file_name"),!f.optBoolean("file",true),f.optLong("size")),f));}
            JSONObject meta=j.optJSONObject("metadata");result.more=meta!=null?page*100<meta.optLong("_total",meta.optLong("total",0)):a.length()==100;
        }else if(provider==Provider.TIANYI){
            String url=Uri.parse("https://cloud.189.cn/api/open/file/listFiles.action").buildUpon().appendQueryParameter("pageSize","60").appendQueryParameter("pageNum",""+page)
                .appendQueryParameter("mediaType","0").appendQueryParameter("folderId",folder).appendQueryParameter("iconOption","5").appendQueryParameter("orderBy","filename").appendQueryParameter("descending","false").appendQueryParameter("noCache",""+System.currentTimeMillis()).build().toString();
            JSONObject j=call(url,null);JSONObject d=j.optJSONObject("fileListAO");if(d==null)throw new IOException("未获取到天翼文件列表，请先在官网登录。");
            for(String type:new String[]{"folderList","fileList"}){JSONArray a=TianyiResponse.items(d,type);for(int i=0;i<a.length();i++){JSONObject f=a.getJSONObject(i);result.entries.add(CloudFileMetadata.apply(new Entry(f.getString("id"),f.getString("name"),type.equals("folderList"),f.optLong("size")),f));}}
            result.more=result.entries.size()==60;
        }else if(provider==Provider.BAIDU){
            String url=Uri.parse("https://pan.baidu.com/api/list").buildUpon().appendQueryParameter("dir",folder).appendQueryParameter("page",""+page).appendQueryParameter("num","100").appendQueryParameter("order","name").appendQueryParameter("desc","0").appendQueryParameter("web","1").build().toString();
            JSONObject j=call(url,null);JSONArray a=j.optJSONArray("list");if(a==null)throw new IOException("百度尚未授权文件访问，请重新扫码。");
            for(int i=0;i<a.length();i++){JSONObject f=a.getJSONObject(i);Entry e=new Entry(f.getString("path"),f.getString("server_filename"),f.optInt("isdir")==1,f.optLong("size"));e.path=f.getString("path");result.entries.add(CloudFileMetadata.apply(e,f));}result.more=a.length()==100;
        }else if(provider==Provider.ALI){
            JSONObject token=aliToken();String drive=aliDrive(token);
            // Ali uses opaque markers; retain each folder's marker only within this adapter instance.
            String key=drive+":"+folder+":"+page,marker=aliMarkers.get(key);if(page>1&&marker==null)throw new IOException("目录分页已失效，请返回第一层重试。");
            JSONObject j=aliCall("/adrive/v3/file/list",new JSONObject().put("drive_id",drive).put("parent_file_id",folder).put("limit",100).put("marker",marker==null?"":marker).put("order_by","name").put("order_direction","ASC"),token);
            JSONArray a=j.optJSONArray("items");if(a==null)throw new IOException("未能读取阿里文件列表，请重新登录。");for(int i=0;i<a.length();i++){JSONObject f=a.getJSONObject(i);result.entries.add(CloudFileMetadata.apply(new Entry(f.getString("file_id"),f.getString("name"),f.optString("type").equals("folder"),f.optLong("size")),f));}
            String next=j.optString("next_marker");result.nextMarker=next;result.more=!next.isEmpty();if(result.more)aliMarkers.put(drive+":"+folder+":"+(page+1),next);
        }else throw new IOException("该网盘请使用官网选片。");
        Collections.sort(result.entries,(a,b)->a.directory!=b.directory?(a.directory?-1:1):a.name.compareToIgnoreCase(b.name));return result;
    }
    public MediaSpec resolve(Entry e)throws Exception{return resolve(e,true);}
    public MediaSpec resolveOriginal(Entry e)throws Exception{return resolve(e,false);}
    private MediaSpec resolve(Entry e,boolean fallback)throws Exception{
        String url;
        if(provider==Provider.QUARK){
            try{
                JSONObject j=call("https://drive.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc",new JSONObject().put("fids",new JSONArray().put(e.id)));
                JSONArray a=j.optJSONArray("data");url=a==null||a.length()==0?"":a.getJSONObject(0).optString("download_url");
                if(url.isEmpty())throw new IOException("夸克没有返回原文件地址。");
            }catch(IOException original){
                if(!fallback||!UrlRules.video(e.name))throw original;
                // Some accounts allow online viewing but not the download endpoint.
                // Request only streams the service actually grants to this signed-in account.
                try{return quarkStream(e,true);}catch(Exception playback){playback.addSuppressed(original);throw playback;}
            }
        }else if(provider==Provider.BAIDU){
            JSONObject j=call("https://pan.baidu.com/api/filemetas?dlink=1&web=5&origin=dlna&target="+Uri.encode(new JSONArray().put(e.id).toString()),null);JSONArray a=j.optJSONArray("info");if(a==null||a.length()==0)throw new IOException("百度未返回播放地址，可通过官网登录页播放。");url=a.getJSONObject(0).optString("dlink");
        }else if(provider==Provider.ALI){
            JSONObject token=aliToken();String drive=aliDrive(token);
            try{
                JSONObject j=aliCall("/v2/file/get_download_url",new JSONObject().put("drive_id",drive).put("file_id",e.id).put("expire_sec",14400),token);url=j.optString("url");
                if(!UrlRules.isWeb(url))url=j.optString("cdn_url");
                if(!UrlRules.isWeb(url))throw new IOException("阿里未提供原文件地址。");
            }catch(IOException original){
                if(!fallback||!UrlRules.video(e.name))throw original;
                try{return aliStream(e,token,drive);}catch(Exception playback){playback.addSuppressed(original);throw playback;}
            }
        }else{
            JSONObject j=call("https://cloud.189.cn/api/portal/getFileInfo.action?fileId="+Uri.encode(e.id),null);url=j.optString("downloadUrl",j.optString("fileDownloadUrl"));if(url.startsWith("//"))url="https:"+url;
        }
        MediaSpec resolved=media(e,url);return tianyiSession==null?resolved:TianyiPlayback.resolve(resolved);
    }
    public MediaSpec resolveCompatible(Entry e)throws Exception{
        if(provider==Provider.ALI&&UrlRules.video(e.name)){JSONObject token=aliToken();return aliStream(e,token,aliDrive(token));}if(provider!=Provider.QUARK||!UrlRules.video(e.name))throw new IOException("此文件暂不支持在线兼容播放。");
        return quarkStream(e,true);
    }
    public static final class FilePlaybackInfo {
        public int width,height;public final List<MediaSpec.Sub> subtitles=new ArrayList<>();
    }
    static JSONObject normalizeAliPreview(JSONObject info)throws Exception{
        if(info==null)return null;JSONObject normalized=new JSONObject(info.toString());
        if(info.optJSONArray("quick_video_list")!=null)normalized.put("live_transcoding_task_list",info.getJSONArray("quick_video_list"));
        if(info.optJSONArray("quick_video_subtitle_list")!=null)normalized.put("live_transcoding_subtitle_task_list",info.getJSONArray("quick_video_subtitle_list"));return normalized;
    }
    private JSONObject aliPreview(Entry entry,JSONObject token,String drive)throws Exception{
        try{
            JSONObject quick=aliCall("/v2/file/get_video_preview_play_info",new JSONObject().put("drive_id",drive).put("file_id",entry.id).put("category","quick_video").put("template_id","").put("mode","high_res").put("get_subtitle_info",true).put("url_expire_sec",14400),token).optJSONObject("video_preview_play_info");
            JSONArray streams=quick==null?null:quick.optJSONArray("quick_video_list");if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject row=streams.optJSONObject(i);if(row!=null&&"finished".equals(row.optString("status"))&&UrlRules.isWeb(row.optString("url")))return normalizeAliPreview(quick);}
        }catch(Net.ServiceException ignored){/* Older service deployments still expose live_transcoding. */}
        return normalizeAliPreview(aliCall("/v2/file/get_video_preview_play_info",new JSONObject().put("drive_id",drive).put("file_id",entry.id).put("category","live_transcoding").put("get_subtitle_info",true).put("url_expire_sec",14400),token).optJSONObject("video_preview_play_info"));
    }
    static FilePlaybackInfo parseAliPlaybackInfo(JSONObject info)throws Exception{
        FilePlaybackInfo result=new FilePlaybackInfo();if(info==null)return result;
        JSONObject meta=info.optJSONObject("meta");if(meta!=null){result.width=Math.max(0,meta.optInt("width"));result.height=Math.max(0,meta.optInt("height"));}
        JSONArray rows=info.optJSONArray("live_transcoding_subtitle_task_list");
        if(rows!=null)for(int i=0;i<rows.length()&&result.subtitles.size()<8;i++){
            JSONObject row=rows.optJSONObject(i);if(row==null||!"finished".equals(row.optString("status"))||!UrlRules.isWeb(row.optString("url")))continue;
            String url=row.getString("url"),language=row.optString("language");
            result.subtitles.add(new MediaSpec.Sub(url,"同文件字幕 · "+SubtitlePolicy.languageName(language),UrlRules.subtitleMime(url)).exactFile(language));
        }return result;
    }
    /** Independent of video quality, so original playback retains the provider's extracted subtitles. */
    public FilePlaybackInfo filePlaybackInfo(Entry entry)throws Exception{
        if(provider==Provider.QUARK){JSONObject data=quarkPreview(entry).optJSONObject("meta");FilePlaybackInfo result=new FilePlaybackInfo();if(data!=null){result.width=Math.max(0,data.optInt("width"));result.height=Math.max(0,data.optInt("height"));}return result;}
        if(provider!=Provider.ALI)return new FilePlaybackInfo();
        JSONObject token=aliToken();return parseAliPlaybackInfo(aliPreview(entry,token,aliDrive(token)));
    }
    private static void copyPlaybackInfo(MediaSpec media,FilePlaybackInfo info){
        media.sourceWidth=info.width;media.sourceHeight=info.height;media.subtitles.addAll(info.subtitles);
    }
    private MediaSpec aliStream(Entry e,JSONObject token,String drive)throws Exception{
        JSONObject info=aliPreview(e,token,drive);JSONArray streams=info==null?null:info.optJSONArray("live_transcoding_task_list");String best="";long score=-1;int bestWidth=0,bestHeight=0;
        if(streams!=null)for(int i=0;i<streams.length();i++){
            JSONObject row=streams.optJSONObject(i);if(row==null||!row.optString("status").equals("finished")||!UrlRules.isWeb(row.optString("url")))continue;
            String template=row.optString("template_id");long height=row.optLong("template_height",template.equals("FHD")?1080:template.equals("HD")?720:template.equals("SD")?540:template.equals("LD")?360:0),width=row.optLong("template_width",0);
            if(height>1080||width>1920)continue;
            if(height>score){score=height;best=row.optString("url");bestWidth=(int)width;bestHeight=(int)height;}
        }
        if(best.isEmpty())throw new IOException("阿里暂未提供可用的在线播放地址，请在官网检查转码状态与账号播放权限。");
        MediaSpec media=media(e,best);media.mime="application/x-mpegURL";media.compatible=true;media.quality="compatible";media.qualityLabel=resolutionLabel(bestWidth,bestHeight)+" · 在线转码";
        copyPlaybackInfo(media,parseAliPlaybackInfo(info));
        return media;
    }
    private MediaSpec media(Entry e,String url)throws Exception{
        url=url.replace("&amp;","&");if(!UrlRules.isWeb(url))throw new IOException("网盘没有返回有效播放地址，可改用官网播放。");
        MediaSpec m=new MediaSpec(url,e.name);m.provider=provider.name();m.space=provider==Provider.ALI?aliSpace:"";m.key=provider.name()+":"+e.id;m.headers.put("Referer",provider.origin);m.headers.put("User-Agent",provider==Provider.BAIDU?"pan.baidu.com":provider==Provider.QUARK?QUARK_UA:Net.UA);
        // Quark's own download hosts can require the web-session cookie. Never send it to an arbitrary returned host.
        if(provider==Provider.QUARK&&provider.allows(url)){
            String cookie=android.webkit.CookieManager.getInstance().getCookie("https://drive.quark.cn/");
            if(cookie!=null&&!cookie.isEmpty())m.headers.put("Cookie",cookie);
        }
        return m;
    }
    private MediaSpec quarkStream(Entry entry,boolean compatible)throws Exception{
        JSONObject j=call("https://drive-pc.quark.cn/1/clouddrive/file/v2/play?pr=ucpro&fr=pc",new JSONObject().put("fid",entry.id).put("resolutions",compatible?"low,normal,high,super":"low,normal,high,super,2k,4k").put("supports","fmp4,m3u8"));
        JSONObject data=j.optJSONObject("data");JSONArray list=data==null?null:data.optJSONArray("video_list");String best="";long pixels=-1;int chosenWidth=0,chosenHeight=0;
        if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject item=list.optJSONObject(i),info=item==null?null:item.optJSONObject("video_info");if(info==null)continue;
            String url=info.optString("url");long score=info.optLong("width",0)*info.optLong("height",0);
            if(compatible&&(info.optLong("width",0)>1920||info.optLong("height",0)>1080))continue;
            if(item.optBoolean("accessable",true)&&info.optBoolean("success",true)&&info.optBoolean("finish",true)&&UrlRules.isWeb(url)&&score>pixels){best=url;pixels=score;chosenWidth=info.optInt("width");chosenHeight=info.optInt("height");}
        }
        if(best.isEmpty())throw new IOException("夸克没有提供可用的在线播放地址，请在官网确认该视频已完成转码及账号播放权限。");MediaSpec selected=media(entry,best);selected.compatible=compatible;selected.quality="compatible";selected.qualityLabel=resolutionLabel(chosenWidth,chosenHeight)+" · 在线转码";return selected;
    }

    public static final class Quality {
        public final String id,label;public final int width,height;final MediaSpec spec;
        Quality(String id,String label,int width,int height,MediaSpec spec){this.id=id;this.label=label;this.width=width;this.height=height;this.spec=spec;}
    }
    static String resolutionLabel(int width,int height){if(height>=2160||width>=3800)return "4K";if(height>=1440||width>=2560)return "2K";return height>0?height+"p":"在线画质";}
    private JSONObject quarkPreview(Entry entry)throws Exception{
        JSONObject data=call("https://drive-pc.quark.cn/1/clouddrive/file/v2/play?pr=ucpro&fr=pc",new JSONObject().put("fid",entry.id).put("resolutions","low,normal,high,super,2k,4k").put("supports","fmp4,m3u8")).optJSONObject("data");
        if(data==null)throw new IOException("网盘没有返回视频信息。");return data;
    }
    public List<Quality> qualities(Entry entry)throws Exception{
        List<Quality> out=new ArrayList<>();out.add(new Quality("original","原文件 · 最高画质",0,0,null));
        if(provider==Provider.ALI){
            JSONObject token=aliToken(),info=aliPreview(entry,token,aliDrive(token));
            FilePlaybackInfo source=parseAliPlaybackInfo(info);out.set(0,new Quality("original",SubtitlePolicy.originalLabel(source.width,source.height),source.width,source.height,null));
            JSONArray streams=info==null?null:info.optJSONArray("live_transcoding_task_list");
            if(streams!=null)for(int i=0;i<streams.length();i++){
                JSONObject row=streams.optJSONObject(i);if(row==null||!"finished".equals(row.optString("status"))||!UrlRules.isWeb(row.optString("url")))continue;
                String template=row.optString("template_id");if(!template.matches("[A-Za-z0-9_-]{1,40}"))continue;
                int height=row.optInt("template_height",template.equals("UHD")?2160:template.equals("QHD")?1440:template.equals("FHD")?1080:template.equals("HD")?720:template.equals("SD")?540:template.equals("LD")?360:0),width=row.optInt("template_width",0);
                MediaSpec m=media(entry,row.getString("url"));m.mime="application/x-mpegURL";m.quality="ali:"+template;m.qualityLabel=resolutionLabel(width,height);m.compatible=height<=1080&&width<=1920;
                copyPlaybackInfo(m,parseAliPlaybackInfo(info));
                out.add(new Quality(m.quality,m.qualityLabel+" · 在线转码",width,height,m));
            }
        }else if(provider==Provider.QUARK){
            JSONObject data=quarkPreview(entry);
            JSONObject meta=data.optJSONObject("meta");if(meta!=null){int width=meta.optInt("width"),height=meta.optInt("height");out.set(0,new Quality("original",SubtitlePolicy.originalLabel(width,height),width,height,null));}
            JSONArray list=data==null?null:data.optJSONArray("video_list");Set<String> seen=new HashSet<>();
            if(list!=null)for(int i=0;i<list.length();i++){
                JSONObject item=list.optJSONObject(i),info=item==null?null:item.optJSONObject("video_info");if(info==null||!item.optBoolean("accessable",true)||!info.optBoolean("success",true)||!info.optBoolean("finish",true)||!UrlRules.isWeb(info.optString("url")))continue;
                int width=info.optInt("width"),height=info.optInt("height");String id="quark:"+width+"x"+height;if(!seen.add(id))continue;
                MediaSpec m=media(entry,info.getString("url"));m.quality=id;m.qualityLabel=resolutionLabel(width,height);m.compatible=height<=1080&&width<=1920;
                out.add(new Quality(id,m.qualityLabel+" · 在线转码",width,height,m));
            }
        }
        Collections.sort(out.subList(1,out.size()),(a,b)->Long.compare((long)b.height*100000000L+(long)b.width*b.height,(long)a.height*100000000L+(long)a.width*a.height));return out;
    }
    public MediaSpec resolveQuality(Entry entry,String id)throws Exception{
        if("compatible".equals(id))return resolveCompatible(entry);if("original".equals(id))return resolveOriginal(entry);if(id.isEmpty()||"auto".equals(id)){MediaSpec chosen=resolve(entry);if(!chosen.compatible){chosen.quality="auto";chosen.qualityLabel="自动 · 原文件";}return chosen;}
        for(Quality quality:qualities(entry))if(quality.id.equals(id)&&quality.spec!=null)return quality.spec;
        throw new IOException("该清晰度当前不可用，请重新选择。");
    }
    public MediaSpec resolveSelected(Entry entry)throws Exception{
        String hash=Integer.toHexString((provider.name()+":"+entry.id).hashCode());
        android.content.SharedPreferences prefs=CloudApp.context().getSharedPreferences("progress",0);String chosen=prefs.getString("quality:"+hash,"");
        if(!chosen.isEmpty()&&!"auto".equals(chosen))return resolveQuality(entry,chosen);
        boolean compatible=(provider==Provider.ALI||provider==Provider.QUARK)&&prefs.getBoolean("compatible:"+hash,false);
        return compatible?resolveCompatible(entry):resolveQuality(entry,"auto");
    }

    private final Map<String,String> aliMarkers=new java.util.concurrent.ConcurrentHashMap<>();
    public void selectAliSpace(String space){if(provider!=Provider.ALI||!Arrays.asList("resource","backup").contains(space))return;aliSpace=space;aliDrives.remove(space);aliMarkers.clear();}
    public String spaceTitle(){return provider==Provider.ALI?(aliSpace.equals("resource")?"资源库":aliSpace.equals("backup")?"备份盘":"个人空间"):"个人空间";}
    static String chooseAliDrive(JSONObject token,JSONObject account,String space){
        String field=space.equals("resource")?"resource_drive_id":space.equals("backup")?"backup_drive_id":"default_drive_id";
        String drive=account==null?"":account.optString(field);if(drive.isEmpty())drive=token.optString(field);
        if(drive.isEmpty()){drive=token.optString("default_drive_id");if(drive.isEmpty()&&account!=null)drive=account.optString("default_drive_id");}return drive;
    }
    private String aliDrive(JSONObject token)throws Exception{
        String space=aliSpace,cached=aliDrives.get(space);if(cached!=null)return cached;
        JSONObject account=space.equals("default")&&!token.optString("default_drive_id").isEmpty()?null:aliCall("/v2/user/get",new JSONObject(),token);
        String drive=chooseAliDrive(token,account,space);if(drive.isEmpty())throw new IOException("阿里未返回所选空间，请到官网完成验证。");aliDrives.put(space,drive);return drive;
    }
    private JSONObject aliToken()throws Exception{return aliTokens==null?tokens.read():aliTokens.read();}
    private JSONObject aliCall(String path,JSONObject body,JSONObject token)throws Exception{
        if(aliDevice!=null){try{return aliDevice.request(path,body,token);}catch(Net.ServiceException e){if(aliTokens==null||!(e.serviceCode.equals("AccessTokenInvalid")||e.serviceCode.equals("AccessTokenExpired")))throw e;return aliDevice.request(path,body,aliTokens.refresh(token));}}
        String access=token.optString("access_token");if(access.isEmpty())throw new IOException("阿里登录失效，请退出后重新扫码。");
        JSONObject j=transport.request((path.equals("/v2/user/get")?AliDeviceSession.USER_API:AliDeviceSession.API)+path,body,Net.headers("Referer","https://www.alipan.com/","Authorization","Bearer "+access));
        if(!j.optString("code").isEmpty())throw new IOException("阿里请求失败（"+j.optString("code")+"），请重新扫码或使用官网。");return j;
    }
}
