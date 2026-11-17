package de.serverhub.velocity;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.security.*;
import java.time.Duration;
import java.util.HexFormat;

final class PackProbe {
    record Result(boolean success,String detail){}
    private PackProbe(){}
    static Result check(String url,String expectedSha){
        try(var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()){
            var response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent","ServerHub/2.5.0 packcheck").build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()){
                if(response.statusCode()!=200)return new Result(false,"HTTP "+response.statusCode()+" statt 200; Downloadadresse und Freigabe prüfen");
                MessageDigest digest=MessageDigest.getInstance("SHA-1");
                byte[] buffer=new byte[8192];int total=0,read;
                while((read=body.read(buffer))!=-1){total+=read;if(total>32*1024*1024)return new Result(false,"Antwort größer als 32 MiB");digest.update(buffer,0,read);}
                String actual=HexFormat.of().formatHex(digest.digest());
                if(!actual.equals(expectedSha))return new Result(false,"SHA-1 stimmt nicht überein: "+actual+"; erwartet "+expectedSha+". Falsche ZIP oder HTML-Seite unter dieser URL.");
                return new Result(true,"HTTP 200; "+total+" Bytes; SHA-1 korrekt; Weiterleitungen geprüft");
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();return new Result(false,"Prüfung unterbrochen");}
        catch(Exception e){return new Result(false,e.getClass().getSimpleName()+": "+e.getMessage());}
    }
}
