package de.serverhub.common;

import java.io.*;

public record ServerStyle(int width,String background,String tint,boolean rounded,int players,int maximum,String prefix,boolean hoverEnabled,String hover,boolean news) {
    public static ServerStyle defaults(){return new ServerStyle(0,"","#FFFFFF",false,-1,-1,"",true,"",false);}
    public ServerStyle {if(width!=0&&(width<80||width>500)||background==null||!background.isBlank()&&!FontBook.validFamily(background)||players< -1||maximum< -1||players>1000000||maximum>1000000||prefix==null||prefix.length()>256||hover==null||hover.length()>2048)throw new IllegalArgumentException("Ungültiger Serverstil");MenuOptions.color(tint);}
    public void write(DataOutput out)throws IOException {out.writeShort(width);out.writeUTF(background);out.writeUTF(tint);out.writeBoolean(rounded);out.writeInt(players);out.writeInt(maximum);out.writeUTF(prefix);out.writeBoolean(hoverEnabled);out.writeUTF(hover);out.writeBoolean(news);}
    public static ServerStyle read(DataInput input)throws IOException {try{return new ServerStyle(input.readUnsignedShort(),input.readUTF(),input.readUTF(),input.readBoolean(),input.readInt(),input.readInt(),input.readUTF(),input.readBoolean(),input.readUTF(),input.readBoolean());}catch(IllegalArgumentException e){throw new IOException(e);}}
    public ServerStyle live(int online,int max,String value,boolean hasNews){return new ServerStyle(width,background,tint,rounded,online,max,value,hoverEnabled,hover,hasNews);}
    public ServerStyle prefix(String value){return new ServerStyle(width,background,tint,rounded,players,maximum,value,hoverEnabled,hover,news);}
}
