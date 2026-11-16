package de.serverhub.paper;

import de.serverhub.common.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuiTextTest {
    record Run(String text,String font,int x,TextColor color){}
    FontBook fonts()throws Exception {try(var input=getClass().getResourceAsStream("/font-widths.properties")){return FontBook.properties(input);}}
    List<Run> runs(Component component)throws Exception {var result=new ArrayList<Run>();walk(component,"minecraft:default",new int[]{0},result,fonts());return result;}
    void walk(Component component,String inherited,int[] cursor,List<Run> result,FontBook book){
        String font=component.font()==null?inherited:component.font().asString();
        if(component instanceof TextComponent text&&!text.content().isEmpty()){
            result.add(new Run(text.content(),font,cursor[0],component.color()));
            for(char code:text.content().toCharArray()){
                if(font.equals("serverhub:space"))cursor[0]+=(code>=0xF200?1:-1)*(1<<(code&0xFF));
                else if(font.equals("serverhub:gui"))cursor[0]+=code==0xF0FF?-1:code==0xE400||code==0xE402?13:code==0xE401||code==0xE411||code==0xE421?2:5;
                else if(font.equals("serverhub:status"))cursor[0]+=7;
                else if(font.startsWith("serverhub:label_")){String[] p=font.substring(16).split("_");cursor[0]+=book.advance(p[0],Integer.parseInt(p[1]),code);}
            }
        }
        for(Component child:component.children())walk(child,font,cursor,result,book);
    }
    int finalWidth(List<Run> runs){Run last=runs.getLast();int width=last.x();for(char code:last.text().toCharArray())width+=(code>=0xF200?1:-1)*(1<<(code&0xFF));return width;}
    @Test void theRollFitsEachHeadingAndCentersItsVisibleText()throws Exception {
        var gui=new GuiText();var options=MenuOptions.defaults();int shortWidth=0;
        for(String title:List.of("Menü","Unsere lange Serverauswahl")){
            var runs=runs(gui.heading(title,NamedTextColor.BLACK,options));int total=finalWidth(runs);
            Run label=runs.stream().filter(r->r.font().equals("serverhub:label_sans_12")).findFirst().orElseThrow();int text=fonts().width(title,"sans",12);
            assertTrue(Math.abs(label.x()*2+text-total)<=1);assertEquals(Math.max(48,text+24),total);
            if(shortWidth==0)shortWidth=total;else assertTrue(total>shortWidth);
        }
    }
    @Test void everyStatusDotUsesOneLeftEdgeRegardlessOfNameOrFontSize()throws Exception {
        var gui=new GuiText();var options=MenuOptions.defaults();
        for(var entry:List.of(new Settings.Entry("a","Lobby","a","",NamedTextColor.WHITE,8,List.of(),MenuSnapshot.Status.ONLINE,"serif"),new Settings.Entry("b","Überlebenswelt","b","",NamedTextColor.WHITE,16,List.of(),MenuSnapshot.Status.STARTING,"mono"))){
            var runs=runs(gui.server(entry,options));Run dot=runs.stream().filter(r->r.font().equals("serverhub:status")).findFirst().orElseThrow();assertEquals(6,dot.x());assertEquals(132,finalWidth(runs));
            assertTrue(runs.stream().anyMatch(r->r.font().endsWith("_shadow")));
        }
    }
    @Test void aControlCanChooseItsOwnWidthColorsAndAlternativeFont()throws Exception {
        var options=new MenuOptions(Map.of("text.shadow","false"));var runs=runs(new GuiText().button("Schließen",14,TextColor.color(0x123456),190,"serif",TextColor.color(0x987654),options));
        assertEquals(182,finalWidth(runs));assertTrue(runs.stream().anyMatch(r->r.font().equals("serverhub:label_serif_14")&&r.color().value()==0x123456));
        assertTrue(runs.stream().anyMatch(r->r.font().equals("serverhub:gui")&&r.color().value()==0x987654));assertFalse(runs.stream().anyMatch(r->r.font().endsWith("_shadow")));
    }
}
