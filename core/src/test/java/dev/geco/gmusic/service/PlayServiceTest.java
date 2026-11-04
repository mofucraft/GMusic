package dev.geco.gmusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayServiceTest {

    @Test
    void actionBarIsSentOncePerSecond() {
        int sent = 0;
        for(long position = -1000; position < 10_000; position++) if(PlayService.isActionBarTick(position)) sent++;
        // 11 秒分で 11 回（以前は 11000 回）
        assertEquals(11, sent);
        assertTrue(PlayService.isActionBarTick(0));
        assertTrue(PlayService.isActionBarTick(-1000));
        assertFalse(PlayService.isActionBarTick(1));
        assertFalse(PlayService.isActionBarTick(-1));
    }

}
