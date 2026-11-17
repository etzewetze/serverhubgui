package de.serverhub.common;

import java.io.*;
import java.util.*;

/** Geometry and glyph assignments from the exact pack offered to this player. */
public record UiCatalog(int rollLeft,int rollRight,boolean icons,Map<String,Skin> skins) {
    public record Skin(int glyph,int columns){public Skin{if(glyph<0xE000||glyph+columns>0xF800||columns<1||columns>192)throw new IllegalArgumentException("Ungültige Button-Grafik");}}
    public UiCatalog(int left,int right,Map<String,Skin> skins){this(left,right,true,skins);}
    public UiCatalog {if(rollLeft<1||rollLeft>64||rollRight<1||rollRight>64||skins.size()>32)throw new IllegalArgumentException("Ungültiger Grafikkatalog");var copy=new TreeMap<String,Skin>();skins.forEach((id,skin)->{if(!id.matches("[a-z][a-z0-9_-]{0,31}(?:\\.round)?"))throw new IllegalArgumentException("Ungültiger Grafikname");copy.put(id,skin);});skins=Collections.unmodifiableMap(copy);}
    public static UiCatalog empty(){return new UiCatalog(12,12,false,Map.of());}
    public Skin skin(String id,boolean rounded){return skins.get(rounded&&skins.containsKey(id+".round")?id+".round":id);}
    public void write(DataOutput out)throws IOException {out.writeByte(rollLeft);out.writeByte(rollRight);out.writeBoolean(icons);out.writeByte(skins.size());for(var e:skins.entrySet()){out.writeUTF(e.getKey());out.writeShort(e.getValue().glyph);out.writeShort(e.getValue().columns);}}
    public static UiCatalog read(DataInput in)throws IOException {int left=in.readUnsignedByte(),right=in.readUnsignedByte();boolean icons=in.readBoolean();int count=in.readUnsignedByte();if(count>32)throw new IOException("Zu viele Grafiken");var map=new TreeMap<String,Skin>();try{for(int i=0;i<count;i++){String id=in.readUTF();if(map.put(id,new Skin(in.readUnsignedShort(),in.readUnsignedShort()))!=null)throw new IOException("Doppelte Grafik");}return new UiCatalog(left,right,icons,map);}catch(IllegalArgumentException e){throw new IOException(e);}}
    public Properties properties(){var p=new Properties();p.setProperty("roll.left",Integer.toString(rollLeft));p.setProperty("roll.right",Integer.toString(rollRight));p.setProperty("icons",Boolean.toString(icons));skins.forEach((id,s)->p.setProperty("skin."+id,s.glyph+","+s.columns));return p;}
    public static UiCatalog properties(InputStream input)throws IOException {var p=new Properties();p.load(input);try{var skins=new TreeMap<String,Skin>();for(String key:p.stringPropertyNames())if(key.startsWith("skin.")){String[] n=p.getProperty(key).split(",");skins.put(key.substring(5),new Skin(Integer.parseInt(n[0]),Integer.parseInt(n[1])));}return new UiCatalog(Integer.parseInt(p.getProperty("roll.left","12")),Integer.parseInt(p.getProperty("roll.right","12")),Boolean.parseBoolean(p.getProperty("icons","false")),skins);}catch(RuntimeException e){throw new IOException("Ungültiger Grafikkatalog",e);}}
}
