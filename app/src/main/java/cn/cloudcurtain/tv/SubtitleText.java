package cn.cloudcurtain.tv;

import java.nio.*;
import java.nio.charset.*;

public final class SubtitleText {
    private SubtitleText(){}
    static String cleanAliVtt(String text){return text.replaceAll("\\{=[0-9]+(?:=[0-9]+)*\\}","");}
    public static String decode(byte[] bytes)throws CharacterCodingException{
        if(bytes.length>=3&&(bytes[0]&255)==239&&(bytes[1]&255)==187&&(bytes[2]&255)==191)return new String(bytes,3,bytes.length-3,StandardCharsets.UTF_8);
        if(bytes.length>=2&&(bytes[0]&255)==255&&(bytes[1]&255)==254)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if(bytes.length>=2&&(bytes[0]&255)==254&&(bytes[1]&255)==255)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){return Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    }
}
