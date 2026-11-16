package de.serverhub.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import java.io.*;
import java.util.Properties;

final class GuiText {
    private final Properties widths = new Properties();
    GuiText() throws IOException {
        try (var input = getClass().getResourceAsStream("/font-widths.properties")) {
            if (input == null) throw new IOException("font-widths.properties fehlt");
            widths.load(input);
        }
    }
    static Component base() {
        return Component.empty().color(NamedTextColor.WHITE).shadowColor(ShadowColor.none())
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false);
    }
    static Component move(int pixels) {
        if(Math.abs(pixels)>2047)throw new IllegalArgumentException("Text offset too large");
        var s = new StringBuilder();
        int rest = Math.abs(pixels), sign = pixels < 0 ? 0xF100 : 0xF200;
        for (int bit=10;bit>=0;bit--) if ((rest & (1<<bit)) != 0) s.append((char)(sign+bit));
        return base().append(Component.text(s.toString()).font(Key.key("serverhub:space")));
    }
    int width(String text, int size) {
        return text.chars().map(c -> Integer.parseInt(widths.getProperty(size+"."+c, widths.getProperty(size+".63")))).sum();
    }
    String fit(String input, int size, int max) {
        String text = input.replaceAll("[&§](?:#[0-9A-Fa-f]{6}|[0-9a-fk-orA-FK-OR])", "")
            .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
        if (width(text, size) <= max) return text;
        while (!text.isEmpty() && width(text+"..", size) > max) text = text.substring(0,text.length()-1);
        return text+"..";
    }
    private Component decorated(String input,int size,int max,TextColor color,char sprite,int spriteWidth) {
        return decorated(input,size,max,color,sprite,spriteWidth,Component.empty(),0);
    }
    private Component decorated(String input,int size,int max,TextColor color,char sprite,int spriteWidth,Component prefix,int prefixWidth) {
        String fitted=fit(input,size,max);
        int offset=Math.floorDiv(width(fitted,size)+prefixWidth-spriteWidth,2);
        // Zero net sprite advance: the measured label determines its centering.
        return base().append(move(offset))
            .append(Component.text(String.valueOf(sprite)).font(Key.key("serverhub:gui")))
            .append(move(-offset-spriteWidth-1))
            .append(prefix)
            .append(Component.text(fitted).font(Key.key("serverhub:label"+size)).color(color));
    }
    Component heading(String title,TextColor color) { return decorated(title,12,190,color,'\uE000',220); }
    Component button(String title,int size,TextColor color) { return decorated(title,size,126,color,'\uE001',140); }
    Component information(String value){return decorated(value,8,126,TextColor.color(0x4D3620),'\uE002',140);}
    Component server(String title,int size,TextColor color,de.serverhub.common.MenuSnapshot.Status status,boolean showStatus){
        if(!showStatus)return button(title,size,color);
        int rgb=switch(status){case ONLINE->0x62D86B;case OFFLINE->0xF05A56;case SLEEPING->0xFFD15C;case UNKNOWN->0xA7ADB4;};
        Component dot=base().append(Component.text("\uE300").font(Key.key("serverhub:status")).color(TextColor.color(rgb))).append(move(2));
        return decorated(title,size,117,color,'\uE001',140,dot,9);
    }
    Component hint(int x,int y) {
        int horizontal=Math.clamp(x,94,512),vertical=Math.clamp(y,-32,32);
        return base().append(move(horizontal))
            .append(Component.text(String.valueOf((char)(0xE120+vertical))).font(Key.key("serverhub:hint")))
            .append(move(-horizontal-31));
    }
}
