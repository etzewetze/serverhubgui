package de.serverhub.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuLayoutTest {
    @Test void seventhServerCreatesSecondPageAndReloadCannotLeavePageOutOfRange(){
        assertEquals(1,MenuLayout.pages(0));assertEquals(1,MenuLayout.pages(6));assertEquals(2,MenuLayout.pages(7));
        assertEquals(0,MenuLayout.clampPage(8,5));assertEquals(1,MenuLayout.clampPage(1,7));
    }
    @Test void emptyListsAndNegativeNavigationAlwaysHaveAValidPage(){
        assertEquals(0,MenuLayout.clampPage(-1,0));assertEquals(0,MenuLayout.clampPage(-20,12));
        assertEquals(42,MenuLayout.clampPage(999,256));assertEquals(43,MenuLayout.pages(256));
    }
}
