package de.serverhub.velocity;

import java.net.*;

final class PackUrls {
    static final String RELEASE_URL="https://github.com/etzewetze/serverhubgui/releases/download/v2.3.0/serverhub-pack.zip";
    private PackUrls(){}
    static String external(String raw){
        URI uri=URI.create(raw==null?"":raw.trim());
        if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
            ||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.toASCIIString().length()>2048)
            throw new IllegalArgumentException("Die externe Paketadresse muss eine vollständige HTTP(S)-ZIP-URL ohne Zugangsdaten oder Fragment sein");
        return uri.toASCIIString();
    }
    static String selfHosted(String scheme,String host,int port,String sha){
        if(host==null||host.isBlank())throw new IllegalArgumentException("Öffentlicher Host fehlt; self-host.public-host setzen");
        if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
        try{
            int uriPort=scheme.equals("http")&&port==80||scheme.equals("https")&&port==443?-1:port;
            URI uri=new URI(scheme,null,host,uriPort,"/serverhub.zip","sha1="+sha,null);
            if(uri.getHost()==null||uri.getUserInfo()!=null)throw new URISyntaxException(host,"Ungültiger Host");
            return uri.toASCIIString();
        }catch(URISyntaxException e){throw new IllegalArgumentException("Ungültiger öffentlicher Host: "+host,e);}
    }
}
