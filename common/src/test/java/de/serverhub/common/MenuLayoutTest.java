package de.serverhub.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuLayoutTest {
    @Test void seventhServerCreatesSecondPageAndReloadCannotLeavePageOutOfRange(){
        assertEquals(1,MenuLayout.pages(0));assertEquals(1,MenuLayout.pages(6));assertEquals(2,MenuLayout.pages(7));
        assertEquals(0,MenuLayout.clampPage(8,5));assertEquals(1,MenuLayout.clampPage(1,7));
    }
    @Test void allSixButtonsHaveClickAreasAndDividersCannotTriggerAServer(){
        int[] counts=new int[6];
        for(int slot=0;slot<45;slot++){int entry=MenuLayout.entryAt(slot);if(entry>=0)counts[entry]++;}
        assertArrayEquals(new int[]{8,8,8,8,4,4},counts);
        for(int slot=4;slot<45;slot+=9)assertEquals(-1,MenuLayout.entryAt(slot));
        for(int slot=45;slot<54;slot++)assertEquals(-1,MenuLayout.entryAt(slot));
        assertEquals(-1,MenuLayout.entryAt(-999));
    }
}
