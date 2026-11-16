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
        var s = new StringBuilder();
        int rest = Math.abs(pixels), sign = pixels < 0 ? 0xF100 : 0xF200;
        for (int bit=10;bit>=0;bit--) if ((rest & (1<<bit)) != 0) s.append((char)(sign+bit));
        return base().append(Component.text(s.toString()).font(Key.key("serverhub:space")));
    }
    Component background(int count) {
        return base().append(move(-8))
            .append(Component.text(String.valueOf((char)(0xE000 + count))).font(Key.key("serverhub:gui")))
            .append(move(-177));
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
    Component label(String text, int size, String row, int center, int max, TextColor color) {
        String fitted = fit(text, size, max);
        int x = center - width(fitted,size)/2;
        return base().append(move(x)).append(Component.text(fitted).color(color).font(Key.key("serverhub:label"+size+"_"+row)))
            .append(move(-x-width(fitted,size)));
    }
}
