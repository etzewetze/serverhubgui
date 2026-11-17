package de.serverhub.paper;

import de.serverhub.common.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import java.io.*;

final class GuiText {
    private final FontBook fonts;
    GuiText()throws IOException {
        try(var input=getClass().getResourceAsStream("/font-widths.properties")){
            if(input==null)throw new IOException("Schriftmetriken fehlen");fonts=FontBook.properties(input);
        }
    }
    private GuiText(FontBook fonts){this.fonts=fonts;}
    GuiText forSettings(Settings settings){return settings.fonts().isEmpty()?this:new GuiText(settings.fonts());}
    static Component base(){return Component.empty().color(NamedTextColor.WHITE).shadowColor(ShadowColor.none()).decoration(TextDecoration.ITALIC,false).decoration(TextDecoration.BOLD,false);}
    static Component move(int pixels){
        if(Math.abs(pixels)>2047)throw new IllegalArgumentException("Text offset too large");
        var s=new StringBuilder();int rest=Math.abs(pixels),sign=pixels<0?0xF100:0xF200;
        for(int bit=10;bit>=0;bit--)if((rest&(1<<bit))!=0)s.append((char)(sign+bit));
        return base().append(Component.text(s.toString()).font(Key.key("serverhub:space")));
    }
    int width(String text,int size){return fonts.width(text,"sans",size);}
    String fit(String input,int size,int max){return fit(input,"sans",size,max);}
    private String fit(String input,String family,int size,int max){
        String text=input.replaceAll("[&§](?:#[0-9A-Fa-f]{6}|[0-9a-fk-orA-FK-OR])","").replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]","?");
        if(fonts.width(text,family,size)<=max)return text;
        while(!text.isEmpty()&&fonts.width(text+"..",family,size)>max)text=text.substring(0,text.length()-1);
        return text+"..";
    }
    private final class Canvas {
        Component value=base();int cursor;
        void at(int x){value=value.append(move(x-cursor));cursor=x;}
        void background(int type,int width,TextColor tint){
            int cap=type==0?12:4,code=type==0?0xE400:type==1?0xE410:0xE420;
            String glyphs=""+(char)code+'\uF0FF'+((""+(char)(code+1)+'\uF0FF').repeat(Math.max(0,width-cap*2)))+(char)(code+2)+'\uF0FF';
            value=value.append(Component.text(glyphs).font(Key.key("serverhub:gui")).color(tint));cursor+=width;
        }
        void text(String text,String family,int size,int x,TextColor color,MenuOptions settings){
            family=fonts.family(family,size);int w=fonts.width(text,family,size);
            if(settings.flag("text.shadow")){
                at(x+1);value=value.append(Component.text(text).font(Key.key("serverhub:label_"+family+"_"+size+"_shadow")).color(Settings.color(settings.text("text.shadow-color"))));cursor+=w;
            }
            at(x);value=value.append(Component.text(text).font(Key.key("serverhub:label_"+family+"_"+size)).color(color));cursor+=w;
        }
        Component finish(int width){at(width);return value;}
    }
    Component heading(String title,TextColor color,MenuOptions options){
        String family=options.text("menu.font-family");int size=options.number("menu.title-font-size"),padding=options.number("menu.title-padding");
        String text=fit(title,family,size,options.number("menu.title-max-width")-padding*2);int w=fonts.width(text,family,size),roll=Math.max(48,w+padding*2);
        var c=new Canvas();c.background(0,roll,Settings.color(options.text("menu.title-background-color")));c.text(text,family,size,(roll-w)/2,color,options);return c.finish(roll);
    }
    Component fixedHeading(MenuOptions options){return base().append(move(options.number("menu.title-warning-offset"))).append(heading(options.text("menu.title"),Settings.color(options.text("menu.title-color")),options));}
    Component button(String title,int size,TextColor color,int width,String family,TextColor background,MenuOptions options){return button(title,size,color,width,family,background,options,null);}
    private Component button(String title,int size,TextColor color,int width,String family,TextColor background,MenuOptions options,MenuSnapshot.Status status){
        if(family.isBlank())family=options.text("menu.font-family");
        String text=fit(title,family,size,width-(status==null?16:40));int w=fonts.width(text,family,size);var c=new Canvas();c.at(-4);if(options.text("menu.button-style").equals("wood"))c.background(1,width,background);
        if(status!=null){int rgb=switch(status){case ONLINE->0x62D86B;case OFFLINE->0xF05A56;case SLEEPING,STARTING->0xFFD15C;case UNKNOWN->0xA7ADB4;};
            c.at(6);c.value=c.value.append(Component.text("\uE300").font(Key.key("serverhub:status")).color(TextColor.color(rgb)));c.cursor+=7;
        }
        c.text(text,family,size,-4+(width-w)/2,color,options);return c.finish(width-8);
    }
    Component server(Settings.Entry entry,MenuOptions options){return button(entry.name(),entry.size(),entry.color(),options.number("menu.button-width"),entry.family(),Settings.color(options.text("menu.button-background-color")),options,options.flag("status.enabled")?entry.status():null);}
    Component information(String text,int width,MenuOptions options){
        String family=options.text("menu.font-family"),fitted=fit(text,family,8,width-16);var c=new Canvas();c.at(-4);boolean wood=options.text("menu.button-style").equals("wood");if(wood)c.background(2,width,NamedTextColor.WHITE);
        c.text(fitted,family,8,-4+(width-fonts.width(fitted,family,8))/2,TextColor.color(wood?0x4D3620:0xB7B7B7),options);return c.finish(width-8);
    }
    Component hint(int x,int y){int horizontal=Math.clamp(x,94,512),vertical=Math.clamp(y,-32,32);return base().append(move(horizontal)).append(Component.text(String.valueOf((char)(0xE120+vertical))).font(Key.key("serverhub:hint"))).append(move(-horizontal-31));}
}
