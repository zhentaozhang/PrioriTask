package com.prioritask.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PriorityTest {

    @Test
    void defaultPriorityIsMedium() {
        assertEquals(Priority.MEDIUM, Priority.defaultPriority());
    }

    @Test
    void highHasHighestLevel() {
        assertTrue(Priority.HIGH.level() > Priority.MEDIUM.level());
        assertTrue(Priority.MEDIUM.level() > Priority.LOW.level());
    }

    @Test
    void highComesBeforeLow() {
        assertTrue(Priority.HIGH.compareToPriority(Priority.LOW) < 0);
    }

    @Test
    void lowComesAfterMedium() {
        assertTrue(Priority.LOW.compareToPriority(Priority.MEDIUM) > 0);
    }

    @Test
    void samePriorityIsEqual() {
        assertEquals(0, Priority.MEDIUM.compareToPriority(Priority.MEDIUM));
    }
}
