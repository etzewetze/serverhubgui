package de.serverhub.common;

import java.io.*;
import java.util.UUID;

/** Small, authenticated backend report; timestamps come from the receiving proxy. */
public record BackendStatus(UUID instance,long sequence,boolean paused) {
    public BackendStatus {if(instance==null||sequence<0)throw new IllegalArgumentException("Ungültiger Backend-Status");}
    public byte[] encode(){try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x53485301);out.writeLong(instance.getMostSignificantBits());out.writeLong(instance.getLeastSignificantBits());out.writeLong(sequence);out.writeBoolean(paused);return bytes.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}}
    public static BackendStatus decode(byte[] bytes)throws IOException {
        if(bytes.length!=29)throw new IOException("Ungültige Statuslänge");var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=0x53485301)throw new IOException("Unbekanntes Statusformat");
        try{return new BackendStatus(new UUID(in.readLong(),in.readLong()),in.readLong(),in.readBoolean());}catch(IllegalArgumentException e){throw new IOException(e);}
    }
}
