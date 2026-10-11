package cn.cloudcurtain.tv;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.*;
import java.io.*;
import java.util.Locale;

/** Tianyi's legacy file API can negotiate XML. Parse data, never HTML login pages. */
final class TianyiResponse {
    static JSONObject parse(String text)throws Exception {
        String lower=text.toLowerCase(Locale.ROOT);
        if(lower.contains("<!doctype")||lower.contains("<!entity"))throw new IOException("不支持的文件列表格式。");
        XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setInput(new StringReader(text));
        int event=p.nextTag();String root=p.getName();
        if(event!=XmlPullParser.START_TAG||!(root.equals("listFiles")||root.equals("fileListAO")||root.equals("getFileInfo")||root.equals("error")))throw new IOException("不是天翼文件数据。");
        Object value=node(p,0);if(!(value instanceof JSONObject))throw new IOException("文件列表数据不完整。");
        if(p.next()!=XmlPullParser.END_DOCUMENT)throw new IOException("文件数据包含额外内容。");
        return root.equals("fileListAO")?new JSONObject().put(root,value):(JSONObject)value;
    }
    private static Object node(XmlPullParser p,int depth)throws Exception {
        if(depth>24)throw new IOException("文件数据层级过多。");
        JSONObject object=new JSONObject();StringBuilder text=new StringBuilder();int children=0;
        while(true){int event=p.next();
            if(event==XmlPullParser.END_TAG)break;
            if(event==XmlPullParser.END_DOCUMENT)throw new IOException("文件数据被截断。");
            if(event==XmlPullParser.TEXT)text.append(p.getText());
            if(event==XmlPullParser.START_TAG){if(++children>10000)throw new IOException("文件数据过多。");String key=p.getName();Object value=node(p,depth+1);Object old=object.opt(key);
                if(old==null)object.put(key,value);else if(old instanceof JSONArray)((JSONArray)old).put(value);else object.put(key,new JSONArray().put(old).put(value));
            }
        }
        return children==0?text.toString().trim():object;
    }
    static JSONArray items(JSONObject parent,String key)throws JSONException {
        Object value=parent.opt(key);if(value instanceof JSONArray)return (JSONArray)value;
        if(value instanceof JSONObject){JSONObject obj=(JSONObject)value;
            if(obj.has("id"))return new JSONArray().put(obj);
            Object nested=obj.opt(key.equals("folderList")?"folder":"file");
            if(nested instanceof JSONArray)return (JSONArray)nested;if(nested instanceof JSONObject)return new JSONArray().put(nested);
        }
        return new JSONArray();
    }
}
