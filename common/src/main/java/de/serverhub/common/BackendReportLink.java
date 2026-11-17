package de.serverhub.common;

import java.io.*;
import java.net.*;
import java.util.*;

/** A bounded status-ping virtual host bootstraps the report path without any connected player. */
public record BackendReportLink(String target,String host,int port,boolean https,String token,int intervalMillis) {
    private static final String PREFIX="sh3-",SUFFIX=".invalid";
    public BackendReportLink {
        if(!Wire.validTarget(target)||host==null||host.length()>64||port<1||port>65535||intervalMillis<1000||intervalMillis>60000||token==null||!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Ungültiger Statuskanal");
        endpoint(host.equals("auto")?"127.0.0.1":host,port,https,target);
    }
    public URI endpoint(String peerHost){return endpoint(host.equals("auto")?peerHost:host,port,https,target);}
    private static URI endpoint(String host,int port,boolean https,String target){
        if(host==null||host.isBlank()||!host.matches("[A-Za-z0-9.:%\\[\\]-]+"))throw new IllegalArgumentException("Ungültiger Statushost");
        try {var uri=new URI(https?"https":"http",null,host,port,"/serverhub-status","target="+target,null);if(uri.getHost()==null)throw new IllegalArgumentException("Ungültiger Statushost");return uri;}
        catch(URISyntaxException e){throw new IllegalArgumentException("Ungültiger Statushost",e);}
    }
    public String virtualHost(){
        try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);out.writeUTF(target);out.writeUTF(host);out.writeShort(port);out.writeBoolean(https);out.writeShort(intervalMillis);out.write(HexFormat.of().parseHex(token));
            String value=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())+SUFFIX;if(value.length()>253)throw new IllegalArgumentException("Statuskennung zu lang");return value;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static Optional<BackendReportLink> fromVirtualHost(String host){
        if(host==null||host.length()>253||!host.startsWith(PREFIX)||!host.endsWith(SUFFIX))return Optional.empty();
        try{byte[] data=Base64.getUrlDecoder().decode(host.substring(PREFIX.length(),host.length()-SUFFIX.length()));if(data.length>180)return Optional.empty();var in=new DataInputStream(new ByteArrayInputStream(data));
            if(in.readUnsignedByte()!=1)return Optional.empty();String target=in.readUTF(),address=in.readUTF();int port=in.readUnsignedShort();boolean https=in.readBoolean();int interval=in.readUnsignedShort();byte[] token=in.readNBytes(32);if(token.length!=32||in.available()!=0)return Optional.empty();
            return Optional.of(new BackendReportLink(target,address,port,https,HexFormat.of().formatHex(token),interval));
        }catch(IOException|IllegalArgumentException e){return Optional.empty();}
    }
}
