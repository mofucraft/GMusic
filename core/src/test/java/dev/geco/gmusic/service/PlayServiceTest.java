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
        // 前に 100 まで広げた住民でも、住民の上限 30 で頭打ち
        assertEquals(30, PlayService.clampSpeakerRange(100, 100, 30, false));
        assertEquals(20, PlayService.clampSpeakerRange(20, 100, 30, false));
        // 管理者は設定の上限まで
        assertEquals(100, PlayService.clampSpeakerRange(150, 100, 30, true));
        assertEquals(80, PlayService.clampSpeakerRange(80, 100, 30, true));
        // 住民の上限が全体の上限より大きい設定でも全体の上限を超えない・0 以下にしない
        assertEquals(50, PlayService.clampSpeakerRange(70, 50, 60, false));
        assertEquals(1, PlayService.clampSpeakerRange(0, 50, 30, false));
    }

    @Test
    void membersCanWidenUpToMemberMaxNotJustTheDefault() {
        // MOFUCRAFT main: 既定 20・住民の上限 50・全体の上限 50
        assertEquals(50, PlayService.speakerRangeLimit(50, 50, false));
        assertEquals(50, PlayService.clampSpeakerRange(50, 50, 50, false)); // すでに 50 の人はそのまま
        assertEquals(50, PlayService.clampSpeakerRange(100, 50, 50, false));
        assertEquals(20, PlayService.clampSpeakerRange(20, 50, 50, false)); // 新しく使う人の初めの値
        // 住民の上限 50・全体の上限 100: 管理者だけ 100 まで
        assertEquals(50, PlayService.speakerRangeLimit(100, 50, false));
        assertEquals(100, PlayService.speakerRangeLimit(100, 50, true));
        // 上限が 0 以下の設定でも 1 は残す
        assertEquals(1, PlayService.speakerRangeLimit(0, 0, false));
    }

    @Test
    void listenerVolumeFactorScalesOnlyTheListenerSide() {
        float delta = 1e-6f;
        // 倍率 1.0 は今まで通り: 均一半径の中は fixedVolume、外は二乗減衰
        assertEquals(1.4f, PlayService.speakerListenerBaseVolume(3, 5, 50, 1.4f, 1.0), delta);
        assertEquals(0.7f, PlayService.speakerListenerBaseVolume(50, 0, 50, 1.4f, 1.0), delta);
        assertEquals(1.4f / 1.25f, PlayService.speakerListenerBaseVolume(25, 0, 50, 1.4f, 1.0), delta);
        // 倍率 0.6 は全部の距離で 6 割（減り方の形は同じ）
        assertEquals(1.4f * 0.6f, PlayService.speakerListenerBaseVolume(3, 5, 50, 1.4f, 0.6), delta);
        assertEquals(0.7f * 0.6f, PlayService.speakerListenerBaseVolume(50, 0, 50, 1.4f, 0.6), delta);
        // 倍率 0 は聞こえない
        assertEquals(0f, PlayService.speakerListenerBaseVolume(10, 0, 50, 1.4f, 0.0), delta);
        // 範囲が均一半径と同じでも 0 で割らない
        float v = PlayService.speakerListenerBaseVolume(21, 20, 20, 1.0f, 1.0);
        assertFalse(Float.isNaN(v) || Float.isInfinite(v));
    }

}
