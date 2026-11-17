package de.serverhub.common;

import java.util.*;

/** Validated settings shared by the proxy and all backend sessions. */
public record MenuOptions(Map<String,String> values) {
    public static final Map<String,Object> DEFAULTS;
    static {
        var m=new LinkedHashMap<String,Object>();
        m.put("menu.title","Serverauswahl");m.put("menu.title-color","#3D2817");
        m.put("menu.font-size",12);m.put("menu.text-color","#FFF4D6");
        m.put("menu.font-family","sans");m.put("menu.title-font-size",12);m.put("menu.title-padding",12);m.put("menu.title-max-width",320);
        m.put("menu.button-width",140);m.put("menu.button-style","minecraft");m.put("menu.title-warning-offset",24);m.put("menu.button-background-color","#FFFFFF");m.put("menu.title-background-color","#FFFFFF");m.put("text.shadow",true);m.put("text.shadow-color","#24180E");
        m.put("font.url","");m.put("font.refresh",false);m.put("integration.servernavigator",true);m.put("runtime.waiting",false);
        m.put("menu.layout","list");m.put("menu.navigation","scroll");m.put("menu.page-size",6);
        m.put("menu.show-page-info",true);m.put("menu.show-server-count",false);m.put("menu.single-page-info",false);
        m.put("menu.previous","Zurück");m.put("menu.next","Weiter");m.put("menu.close","Schließen");
        for(String button:List.of("previous","next","close")){
            m.put("buttons."+button+".font-size",10);m.put("buttons."+button+".color","#FFF4D6");
            m.put("buttons."+button+".font-family","");m.put("buttons."+button+".width",140);m.put("buttons."+button+".background-color","#FFFFFF");
        }
        m.put("status.enabled",true);m.put("status.ping-interval-seconds",1);m.put("status.timeout-ms",1000);m.put("status.refresh-open-menu",true);
        m.put("status.backend-reports",true);m.put("status.report-bind","0.0.0.0");m.put("status.report-port",8125);m.put("status.report-host","auto");m.put("status.report-scheme","http");m.put("status.heartbeat-interval-seconds",1);m.put("status.heartbeat-timeout-seconds",5);
        m.put("shift-f-hint.enabled",true);m.put("shift-f-hint.interval-seconds",1);
        m.put("shift-f-hint.x-offset",106);m.put("shift-f-hint.y-offset",0);
        m.put("hotbar-icon.enabled",false);m.put("hotbar-icon.slot",8);
        m.put("proxy.handshake-timeout-seconds",30);
        m.put("messages.no-permission","&cDafür hast du keine Berechtigung.");
        m.put("messages.loading","&eDas erforderliche Menüpaket wird noch geladen.");
        m.put("messages.discovering","&eDie zentrale Konfiguration wird gerade vom Proxy geladen. Bitte gleich erneut öffnen.");
        m.put("messages.proxy-missing","&cDie Verbindung zum ServerHub-Proxy fehlt. Bitte informiere das Serverteam.");
        m.put("messages.full-inventory","&eFür das Menü-Icon fehlt ein freier Inventarplatz. Shift + F funktioniert weiterhin.");
        m.put("messages.unavailable","&cDieser Server ist gerade nicht erreichbar.");
        m.put("messages.already-connected","&eDu bist bereits auf diesem Server.");
        m.put("messages.navigator-unavailable","&cServerNavigator benötigt die Integrationsschnittstelle ab Version 1.2.0 oder ist noch nicht bereit.");
        m.put("messages.denied","&cDer Proxy hat den Wechsel nicht erlaubt.");
        DEFAULTS=Collections.unmodifiableMap(m);
    }
    public MenuOptions {
        var next=new LinkedHashMap<String,String>();
        DEFAULTS.forEach((key,value)->next.put(key,String.valueOf(value)));
        values.forEach((key,value)->{
            if(!DEFAULTS.containsKey(key)||value==null||value.length()>(key.equals("font.url")?2048:256))throw new IllegalArgumentException("Ungültige Einstellung: "+key);
            Object standard=DEFAULTS.get(key);
            if(standard instanceof Boolean&&!Set.of("true","false").contains(value))throw new IllegalArgumentException("Boolean erwartet: "+key);
            if(standard instanceof Integer)Integer.parseInt(value);
            next.put(key,value);
        });
        if(!Set.of("list","grid").contains(next.get("menu.layout")))throw new IllegalArgumentException("menu.layout: list oder grid");
        if(!Set.of("scroll","pages").contains(next.get("menu.navigation")))throw new IllegalArgumentException("menu.navigation: scroll oder pages");
        if(!Set.of("minecraft","wood").contains(next.get("menu.button-style")))throw new IllegalArgumentException("menu.button-style: minecraft oder wood");
        range(next,"menu.title-warning-offset",0,40);
        font(Integer.parseInt(next.get("menu.font-size")));
        font(Integer.parseInt(next.get("menu.title-font-size")));color(next.get("text.shadow-color"));
        if(!FontBook.FAMILIES.contains(next.get("menu.font-family")))throw new IllegalArgumentException("menu.font-family: sans, serif, mono oder custom");
        range(next,"menu.title-padding",4,40);if(Integer.parseInt(next.get("menu.title-max-width"))<Integer.parseInt(next.get("menu.title-padding"))*2+16)throw new IllegalArgumentException("Titelbreite ist zu klein für den Rand");range(next,"menu.title-max-width",64,600);range(next,"menu.button-width",80,300);
        for(String button:List.of("previous","next","close")){
            String prefix="buttons."+button+".";font(Integer.parseInt(next.get(prefix+"font-size")));color(next.get(prefix+"color"));color(next.get(prefix+"background-color"));range(next,prefix+"width",80,300);
            if(!next.get(prefix+"font-family").isBlank()&&!FontBook.FAMILIES.contains(next.get(prefix+"font-family")))throw new IllegalArgumentException(prefix+"font-family ungültig");
        }
        color(next.get("menu.title-color"));color(next.get("menu.text-color"));color(next.get("menu.button-background-color"));color(next.get("menu.title-background-color"));
        range(next,"menu.page-size",1,50);range(next,"status.ping-interval-seconds",1,300);
        range(next,"status.report-port",1,65535);range(next,"status.heartbeat-interval-seconds",1,60);range(next,"status.heartbeat-timeout-seconds",3,300);
        if(Integer.parseInt(next.get("status.heartbeat-timeout-seconds"))<Integer.parseInt(next.get("status.heartbeat-interval-seconds"))*3)throw new IllegalArgumentException("status.heartbeat-timeout-seconds muss mindestens drei Heartbeat-Intervalle umfassen");
        if(!Set.of("http","https").contains(next.get("status.report-scheme")))throw new IllegalArgumentException("status.report-scheme: http oder https");
        new BackendReportLink("validation",next.get("status.report-host"),Integer.parseInt(next.get("status.report-port")),next.get("status.report-scheme").equals("https"),"0".repeat(64),1000);
        range(next,"status.timeout-ms",250,10000);range(next,"hotbar-icon.slot",0,8);
        range(next,"shift-f-hint.interval-seconds",1,2);range(next,"shift-f-hint.x-offset",94,512);
        range(next,"shift-f-hint.y-offset",-32,32);range(next,"proxy.handshake-timeout-seconds",10,120);
        values=Collections.unmodifiableMap(next);
    }
    private static void range(Map<String,String> m,String key,int min,int max){
        int n=Integer.parseInt(m.get(key));if(n<min||n>max)throw new IllegalArgumentException(key+": "+min+"–"+max);
    }
    public static MenuOptions defaults(){return new MenuOptions(Map.of());}
    public String text(String key){return values.get(key);}
    public int number(String key){return Integer.parseInt(text(key));}
    public boolean flag(String key){return Boolean.parseBoolean(text(key));}
    public Object typed(String key){Object d=DEFAULTS.get(key);return d instanceof Boolean?flag(key):d instanceof Integer?number(key):text(key);}
    public int columns(){return text("menu.layout").equals("list")?1:2;}
    public boolean scrolling(){return text("menu.navigation").equals("scroll");}
    public boolean pageInfo(int count){return !scrolling()&&flag("menu.show-page-info")&&(MenuLayout.pages(count,number("menu.page-size"))>1||flag("menu.single-page-info"));}
    public static void color(String value){if(value==null||!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Ungültige Hexfarbe: "+value);}
    public static void font(int size){if(Arrays.stream(FontBook.SIZES).noneMatch(n->n==size))throw new IllegalArgumentException("font-size: 8, 10, 12, 14 oder 16");}
}
