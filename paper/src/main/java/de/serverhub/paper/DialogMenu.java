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
        int pageSize=options.number("menu.page-size"),pages=options.scrolling()?1:MenuLayout.pages(entries.size(),pageSize);
        int start=options.scrolling()?0:page*pageSize,end=options.scrolling()?entries.size():Math.min(start+pageSize,entries.size());
        var buttons=new ArrayList<ActionButton>();
        for(var entry:entries.subList(Math.min(start,entries.size()),end)){
            Component tooltip=Component.text(entry.name(),NamedTextColor.WHITE);
            for(String line:entry.description())tooltip=tooltip.appendNewline().append(Component.text(line,NamedTextColor.GRAY));
            if(options.flag("status.enabled"))tooltip=tooltip.appendNewline().append(Component.text("Status: "+statusText(entry.status()),NamedTextColor.GRAY));
            tooltip=tooltip.appendNewline().append(Component.text("Ziel: "+entry.target(),NamedTextColor.DARK_GRAY));
            buttons.add(button(player,gui.server(entry.name(),entry.size(),entry.color(),entry.status(),options.flag("status.enabled")),tooltip,dispatch,()->choose.accept(entry)));
        }
        if(pages>1){
            align(buttons,columns);
            buttons.add(button(player,gui.button(options.text("menu.previous"),10,NamedTextColor.WHITE),Component.text("Vorherige Seite"),dispatch,()->navigate.accept(page-1)));
            if(columns==1||columns==2)buttons.add(button(player,gui.button(options.text("menu.next"),10,NamedTextColor.WHITE),Component.text("Nächste Seite"),dispatch,()->navigate.accept(page+1)));
        }
        boolean info=options.pageInfo(entries.size());boolean count=options.flag("menu.show-server-count");
        if(info||count){
            align(buttons,columns);
            String text=info?"Seite "+(page+1)+" / "+pages:"";
            if(columns==1){if(count)text+=(text.isBlank()?"":" · ")+entries.size()+" Server";buttons.add(information(gui,text));}
            else {buttons.add(information(gui,text));buttons.add(information(gui,count?entries.size()+" Server":""));}
        }
        var body=new ArrayList<DialogBody>();body.add(DialogBody.plainMessage(Component.text("\n"),300));
        if(entries.isEmpty())body.add(DialogBody.plainMessage(Component.text("Keine freigegebenen Server gefunden.",NamedTextColor.GRAY),300));
        ActionButton close=button(player,gui.button(options.text("menu.close"),10,NamedTextColor.WHITE),Component.text("Zurück zum Spiel"),dispatch,player::closeDialog);
        player.showDialog(Dialog.create(builder->builder.empty()
            .base(DialogBase.builder(gui.heading(options.text("menu.title"),Settings.color(options.text("menu.title-color"))))
                .externalTitle(Component.text(options.text("menu.title"))).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build())
            .type(buttons.isEmpty()?DialogType.notice(close):DialogType.multiAction(buttons,close,columns))));
    }
    private static void align(List<ActionButton> buttons,int columns){if(buttons.size()%columns!=0)buttons.add(ActionButton.create(Component.empty(),null,140,null));}
    private static ActionButton information(GuiText gui,String text){return ActionButton.create(gui.information(text),null,140,null);}
    static String statusText(MenuSnapshot.Status status){return switch(status){case ONLINE->"Online";case OFFLINE->"Nicht erreichbar";case SLEEPING->"Schläft";case UNKNOWN->"Wird geprüft";};}
    private static ActionButton button(Player owner,Component label,Component tooltip,Consumer<Runnable> dispatch,Runnable run){
        return ActionButton.create(label,tooltip,140,DialogAction.customClick((view,audience)->{
            if(audience instanceof Player actor&&actor.getUniqueId().equals(owner.getUniqueId()))dispatch.accept(run);
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
    }
}
