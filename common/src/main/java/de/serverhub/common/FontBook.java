package de.serverhub.common;

import java.io.*;
import java.util.*;

/** Pixel advances from the exact font atlases included in a player's offered pack. */
public final class FontBook {
    public static final int[] SIZES={8,10,12,14,16};
    public static final List<String> FAMILIES=List.of("sans","serif","mono","custom");
    private final Map<String,int[]> profiles;
    public FontBook(Map<String,int[]> input){
        if(input.size()>20)throw new IllegalArgumentException("Zu viele Schriftprofile");
        var copy=new TreeMap<String,int[]>();input.forEach((key,value)->{
            if(!key.matches("(?:sans|serif|mono|custom)\\.(?:8|10|12|14|16)")||value.length!=192)throw new IllegalArgumentException("Ungültige Schriftmetriken");
            if(Arrays.stream(value).anyMatch(n->n<1||n>64))throw new IllegalArgumentException("Ungültige Glyphenbreite");
            copy.put(key,value.clone());
        });profiles=Collections.unmodifiableMap(copy);
    }
    public static FontBook empty(){return new FontBook(Map.of());}
    public boolean isEmpty(){return profiles.isEmpty();}
    public String family(String requested,int size){return profiles.containsKey(requested+"."+size)?requested:"sans";}
    public int advance(String family,int size,int code){
        var data=profiles.get(this.family(family,size)+"."+size);
        if(data==null)return Math.max(3,size/2);
        int index=code>=32&&code<128?code-32:code>=160&&code<256?code-64:31;
        return data[index];
    }
    public int width(String text,String family,int size){return text.chars().map(c->advance(family,size,c)).sum();}
    public void write(DataOutput out)throws IOException {
        out.writeByte(profiles.size());for(var p:profiles.entrySet()){out.writeUTF(p.getKey());for(int n:p.getValue())out.writeByte(n);}
    }
    public static FontBook read(DataInput in)throws IOException {
        int count=in.readUnsignedByte();if(count>20)throw new IOException("Zu viele Schriftprofile");
        var result=new LinkedHashMap<String,int[]>();for(int i=0;i<count;i++){
            String key=in.readUTF();var values=new int[192];for(int c=0;c<192;c++)values[c]=in.readUnsignedByte();
            if(result.put(key,values)!=null)throw new IOException("Doppeltes Schriftprofil");
        }
        try{return new FontBook(result);}catch(IllegalArgumentException e){throw new IOException(e);}
    }
    public static FontBook properties(InputStream input)throws IOException {
        var p=new Properties();p.load(input);var profiles=new TreeMap<String,int[]>();
        for(String key:p.stringPropertyNames()){
            String normalized=key.matches("\\d+\\.\\d+")?"sans."+key:key;
            String[] parts=normalized.split("\\.");if(parts.length!=3)continue;
            String profile=parts[0]+"."+parts[1];int code=Integer.parseInt(parts[2]);
            if(!(code>=32&&code<128||code>=160&&code<256))continue;
            int[] values=profiles.computeIfAbsent(profile,k->{int[] a=new int[192];Arrays.fill(a,6);return a;});
            values[code<128?code-32:code-64]=Integer.parseInt(p.getProperty(key));
        }
        return new FontBook(profiles);
    }
    public Properties properties(){
        var p=new Properties();profiles.forEach((key,values)->{for(int i=0;i<192;i++)p.setProperty(key+"."+(i<96?i+32:i+64),Integer.toString(values[i]));});return p;
    }
    @Override public boolean equals(Object other){if(!(other instanceof FontBook f)||!profiles.keySet().equals(f.profiles.keySet()))return false;return profiles.entrySet().stream().allMatch(e->Arrays.equals(e.getValue(),f.profiles.get(e.getKey())));}
    @Override public int hashCode(){int h=0;for(var e:profiles.entrySet())h+=e.getKey().hashCode()^Arrays.hashCode(e.getValue());return h;}
}
