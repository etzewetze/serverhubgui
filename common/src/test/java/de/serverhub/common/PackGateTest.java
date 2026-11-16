package de.serverhub.common;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PackGateTest {
    @Test void paperSuccessUsesTheSameExactIdGate(){
        UUID id=UUID.randomUUID();var gate=new PackGate(id);
        assertFalse(gate.accept(UUID.randomUUID(),"SUCCESSFULLY_LOADED"));
        assertTrue(gate.accept(id,"SUCCESSFULLY_LOADED"));assertTrue(gate.ready());
    }
    @Test void onlyOurSuccessfulPackUnlocksMenu(){
        UUID id=UUID.randomUUID();var gate=new PackGate(id);
        assertFalse(gate.ready());
        assertFalse(gate.accept(UUID.randomUUID(),"SUCCESSFUL"));assertFalse(gate.ready());
        gate.accept(id,"ACCEPTED");assertFalse(gate.ready());
        gate.accept(id,"DOWNLOADED");assertFalse(gate.ready());
        assertTrue(gate.accept(id,"SUCCESSFUL"));assertTrue(gate.ready());
    }
    @Test void everyTerminalFailureKeepsTheGateClosed(){
        for(String status:new String[]{"DECLINED","FAILED_DOWNLOAD","INVALID_URL","FAILED_RELOAD","DISCARDED"}){
            UUID id=UUID.randomUUID();var gate=new PackGate(id);
            assertTrue(gate.accept(id,status));assertEquals(PackGate.State.FAILED,gate.state());
            assertFalse(gate.accept(id,"SUCCESSFUL"));assertFalse(gate.ready());
        }
    }
    @Test void discardingAnAlreadyLoadedPackRevokesAccess(){
        UUID id=UUID.randomUUID();var gate=new PackGate(id);
        gate.accept(id,"SUCCESSFUL");gate.accept(id,"DISCARDED");assertFalse(gate.ready());
    }
}
