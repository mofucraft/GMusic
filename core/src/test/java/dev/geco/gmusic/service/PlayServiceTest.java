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

    @Test
    void speakerRangeIsCappedForNonAdmins() {
        // 前に 100 まで広げた住民でも、既定の 30 で頭打ち
        assertEquals(30, PlayService.clampSpeakerRange(100, 100, 30, false));
        assertEquals(20, PlayService.clampSpeakerRange(20, 100, 30, false));
        // 管理者は設定の上限まで
        assertEquals(100, PlayService.clampSpeakerRange(150, 100, 30, true));
        assertEquals(80, PlayService.clampSpeakerRange(80, 100, 30, true));
        // 既定が上限より大きい設定でも上限を超えない・0 以下にしない
        assertEquals(50, PlayService.clampSpeakerRange(70, 50, 60, false));
        assertEquals(1, PlayService.clampSpeakerRange(0, 50, 30, false));
    }

}
