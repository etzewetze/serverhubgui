package de.serverhub.velocity;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import de.serverhub.common.Wire;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import java.nio.file.Path;
import static org.mockito.Mockito.*;
class ProxySecurityTest {
    @Test void clientCannotImpersonatePaperOrForwardPrivatePackets(){
        var proxy=mock(ProxyServer.class);var plugin=new ServerHubVelocity(proxy,mock(Logger.class),Path.of("test"));
        var event=mock(PluginMessageEvent.class);
        when(event.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));
        when(event.getSource()).thenReturn(mock(Player.class));
        plugin.pluginMessage(event);
        verify(event).setResult(PluginMessageEvent.ForwardResult.handled());
        verify(event,never()).getData();verifyNoInteractions(proxy);
    }
}
