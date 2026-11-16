package de.serverhub.paper;

import de.serverhub.common.MenuLayout;
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

/** A native client dialog: no container, fake items, slots or player inventory. */
final class DialogMenu {
    private DialogMenu() {}
    static void show(Player player, GuiText gui, Settings settings, List<Settings.Entry> entries, int page,
                     Consumer<Runnable> dispatch, Consumer<Settings.Entry> choose, IntConsumer navigate) {
        var buttons = new ArrayList<ActionButton>();
        int start = page * MenuLayout.PAGE_SIZE;
        for (var entry : entries.subList(Math.min(start,entries.size()),Math.min(start+MenuLayout.PAGE_SIZE,entries.size()))) {
            Component tooltip = Component.text(entry.name(),NamedTextColor.WHITE);
            for (String line : entry.description()) tooltip = tooltip.appendNewline().append(Component.text(line,NamedTextColor.GRAY));
            tooltip = tooltip.appendNewline().append(Component.text("Ziel: "+entry.target(),NamedTextColor.DARK_GRAY));
            buttons.add(button(player,gui.button(entry.name(),entry.size(),entry.color()),tooltip,
                dispatch,()->choose.accept(entry)));
        }
        if (MenuLayout.pages(entries.size()) > 1) {
            // Keep navigation on its own two-column row, including odd-sized last pages.
            if (buttons.size()%2 != 0) buttons.add(ActionButton.create(Component.empty(),null,140,null));
            buttons.add(button(player,gui.button(settings.yaml().getString("menu.previous","Zurück"),10,NamedTextColor.WHITE),
                Component.text("Vorherige Seite"),dispatch,()->navigate.accept(page-1)));
            buttons.add(button(player,gui.button(settings.yaml().getString("menu.next","Weiter"),10,NamedTextColor.WHITE),
                Component.text("Nächste Seite"),dispatch,()->navigate.accept(page+1)));
        }
        var body = new ArrayList<DialogBody>();
        body.add(DialogBody.plainMessage(Component.text("\n"),300)); // Room below the parchment title.
        body.add(DialogBody.plainMessage(Component.text(entries.isEmpty()?"Keine freigegebenen Server gefunden."
            :"Seite "+(page+1)+" / "+MenuLayout.pages(entries.size())+" · "+entries.size()+" Server",NamedTextColor.GRAY),300));
        ActionButton close = button(player,gui.button(settings.yaml().getString("menu.close","Schließen"),10,NamedTextColor.WHITE),
            Component.text("Zurück zum Spiel"),dispatch,player::closeDialog);
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(gui.heading(settings.yaml().getString("menu.title","Serverauswahl"),
                    Settings.color(settings.yaml().getString("menu.title-color","#3D2817"))))
                .externalTitle(Component.text("Serverauswahl")).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build())
            .type(buttons.isEmpty()?DialogType.notice(close):DialogType.multiAction(buttons,close,2)));
        player.showDialog(dialog);
    }
    private static ActionButton button(Player owner, Component label, Component tooltip, Consumer<Runnable> dispatch, Runnable run) {
        return ActionButton.create(label,tooltip,140,DialogAction.customClick((view,audience)-> {
            if (audience instanceof Player actor && actor.getUniqueId().equals(owner.getUniqueId())) dispatch.accept(run);
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
    }
}
