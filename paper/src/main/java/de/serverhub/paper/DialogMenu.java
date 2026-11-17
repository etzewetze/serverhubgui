package de.serverhub.paper;

import de.serverhub.common.*;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

/** Native scrollable or paginated dialog; the proxy supplies the complete configuration. */
final class DialogMenu {
    private DialogMenu() {}
    static void show(Player player,GuiText gui,Settings settings,List<Settings.Entry> entries,int page,
                     Consumer<Runnable> dispatch,Consumer<Settings.Entry> choose,IntConsumer navigate){
        MenuOptions options=settings.menuOptions();int columns=options.columns();
        int shared=entries.stream().mapToInt(entry->gui.preferredWidth(entry,options)).max().orElse(options.number("menu.button-min-width"));
        int pageSize=options.number("menu.page-size"),pages=options.scrolling()?1:MenuLayout.pages(entries.size(),pageSize);
        int start=options.scrolling()?0:page*pageSize,end=options.scrolling()?entries.size():Math.min(start+pageSize,entries.size());
        var buttons=new ArrayList<ActionButton>();
        for(var entry:entries.subList(Math.min(start,entries.size()),end)){
            Component tooltip=null;
            if(options.flag("hover.enabled")&&entry.style().hoverEnabled()){
                String template=entry.style().hover().isBlank()?options.text("hover.text"):entry.style().hover();String value=MenuTemplate.render(template,entry.name(),entry.target(),entry.status(),entry.style().players(),entry.style().maximum());
                value=PlaceholderBridge.apply(player,value,options.flag("prefix.placeholderapi"));tooltip=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(value).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false);
            }
            int width=options.flag("menu.align-button-widths")?shared:gui.preferredWidth(entry,options);
            buttons.add(button(player,gui.server(entry,options,width),tooltip,width,dispatch,()->choose.accept(entry)));
        }
        if(pages>1){
            align(buttons,columns,shared);
            buttons.add(button(player,navigation(gui,options,"previous"),Component.text("Vorherige Seite"),options.number("buttons.previous.width"),dispatch,()->navigate.accept(page-1)));
            buttons.add(button(player,navigation(gui,options,"next"),Component.text("Nächste Seite"),options.number("buttons.next.width"),dispatch,()->navigate.accept(page+1)));
        }
        boolean info=options.pageInfo(entries.size());boolean count=options.flag("menu.show-server-count");
        if(info||count){
            align(buttons,columns,shared);
            String text=info?"Seite "+(page+1)+" / "+pages:"";
            if(columns==1){if(count)text+=(text.isBlank()?"":" · ")+entries.size()+" Server";buttons.add(information(gui,text,shared,options));}
            else {buttons.add(information(gui,text,shared,options));buttons.add(information(gui,count?entries.size()+" Server":"",shared,options));}
        }
        var body=new ArrayList<DialogBody>();
        if(entries.isEmpty())body.add(DialogBody.plainMessage(Component.text("Keine freigegebenen Server gefunden.",NamedTextColor.GRAY),300));
        ActionButton close=button(player,navigation(gui,options,"close"),Component.text("Zurück zum Spiel"),options.number("buttons.close.width"),dispatch,player::closeDialog);
        player.showDialog(Dialog.create(builder->builder.empty()
            .base(DialogBase.builder(gui.fixedHeading(options))
                .externalTitle(Component.text(options.text("menu.title"))).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build())
            .type(buttons.isEmpty()?DialogType.notice(close):DialogType.multiAction(buttons,close,columns))));
    }
    private static void align(List<ActionButton> buttons,int columns,int width){if(buttons.size()%columns!=0)buttons.add(ActionButton.create(Component.empty(),null,width,null));}
    private static ActionButton information(GuiText gui,String text,int width,MenuOptions options){return ActionButton.create(gui.information(text,width,options),null,width,null);}
    static void news(Player player,Settings settings,Settings.Entry entry,Component content,Consumer<Runnable> dispatch,Runnable connect,Runnable back){
        var options=settings.menuOptions();int width=Math.clamp(options.number("menu.button-min-width"),140,300);
        var enter=button(player,Component.text(options.text("news.connect")),null,width,dispatch,connect);var returnButton=button(player,Component.text(options.text("news.back")),null,width,dispatch,back);
        player.showDialog(Dialog.create(builder->builder.empty()
            .base(DialogBase.builder(Component.text("Neuigkeiten: "+entry.name())).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE).body(List.of(DialogBody.plainMessage(content,Math.max(240,width)))).build())
            .type(DialogType.multiAction(List.of(enter),returnButton,1))));
    }
    private static Component navigation(GuiText gui,MenuOptions options,String button){String p="buttons."+button+".";return gui.button(options.text("menu."+button),options.number(p+"font-size"),Settings.color(options.text(p+"color")),options.number(p+"width"),options.text(p+"font-family"),Settings.color(options.text(p+"background-color")),options);}
    static String statusText(MenuSnapshot.Status status){return switch(status){case ONLINE->"Online";case OFFLINE->"Nicht erreichbar";case SLEEPING->"Schläft";case STARTING->"Startet / wartet auf Aufnahme";case UNKNOWN->"Wird geprüft";};}
    private static ActionButton button(Player owner,Component label,Component tooltip,int width,Consumer<Runnable> dispatch,Runnable run){
        return ActionButton.create(label,tooltip,width,DialogAction.customClick((view,audience)->{
            if(audience instanceof Player actor&&actor.getUniqueId().equals(owner.getUniqueId()))dispatch.accept(run);
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
    }
}
