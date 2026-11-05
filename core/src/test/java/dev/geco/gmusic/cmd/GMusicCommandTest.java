package dev.geco.gmusic.cmd;

import dev.geco.gmusic.object.GPlayMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GMusicCommandTest {

    @Test
    void parsesOnAndOff() {
        assertEquals(Boolean.TRUE, GMusicCommand.parseOnOff("on"));
        assertEquals(Boolean.TRUE, GMusicCommand.parseOnOff("ON"));
        assertEquals(Boolean.FALSE, GMusicCommand.parseOnOff("off"));
        assertEquals(Boolean.FALSE, GMusicCommand.parseOnOff("Off"));
        assertNull(GMusicCommand.parseOnOff("maybe"));
        assertNull(GMusicCommand.parseOnOff(null));
    }

    @Test
    void loopOnAlwaysLoops() {
        for(GPlayMode mode : GPlayMode.values()) assertEquals(GPlayMode.LOOP, GMusicCommand.loopTarget(mode, true));
    }

    @Test
    void loopOffOnlyLeavesLoop() {
        assertEquals(GPlayMode.DEFAULT, GMusicCommand.loopTarget(GPlayMode.LOOP, false));
        // ループでない時の off は、シャッフルや連続再生をそのまま残す
        assertEquals(GPlayMode.SHUFFLE, GMusicCommand.loopTarget(GPlayMode.SHUFFLE, false));
        assertEquals(GPlayMode.CONTINUE, GMusicCommand.loopTarget(GPlayMode.CONTINUE, false));
        assertEquals(GPlayMode.DEFAULT, GMusicCommand.loopTarget(GPlayMode.DEFAULT, false));
        assertEquals(GPlayMode.DEFAULT, GMusicCommand.loopTarget(null, false));
    }

    @Test
    void noArgumentToggles() {
        assertEquals(GPlayMode.DEFAULT, GMusicCommand.loopTarget(GPlayMode.LOOP, null));
        assertEquals(GPlayMode.LOOP, GMusicCommand.loopTarget(GPlayMode.DEFAULT, null));
        assertEquals(GPlayMode.LOOP, GMusicCommand.loopTarget(GPlayMode.SHUFFLE, null));
    }

}
