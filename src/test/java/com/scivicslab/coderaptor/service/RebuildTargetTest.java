package com.scivicslab.coderaptor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The portal sends the dropdown's value; anything else is refused before a job starts. */
class RebuildTargetTest {

    @Test
    void dropdownValuesParse() {
        assertEquals(RebuildService.Target.SYMBOL,   RebuildService.Target.parse("symbol"));
        assertEquals(RebuildService.Target.JAVADOC,  RebuildService.Target.parse("javadoc"));
        assertEquals(RebuildService.Target.PROJECTS, RebuildService.Target.parse("projects"));
        assertEquals(RebuildService.Target.ALL,      RebuildService.Target.parse("all"));
    }

    @Test
    void otherValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> RebuildService.Target.parse(null));
        assertThrows(IllegalArgumentException.class, () -> RebuildService.Target.parse("everything"));
    }
}
