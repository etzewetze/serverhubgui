package de.serverhub.common;

public record FontSource(String id,String url,boolean refresh) {
    public FontSource {if(!FontBook.validFamily(id)||url==null||url.length()>2048)throw new IllegalArgumentException("Ungültige Schriftquelle");}
}
