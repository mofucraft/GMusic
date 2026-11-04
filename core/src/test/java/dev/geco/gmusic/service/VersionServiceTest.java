package dev.geco.gmusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionServiceTest {

    @Test
    void readsMinecraftVersionFromPaperVersionString() {
        assertEquals("26.2", VersionService.extractMinecraftVersion("git-Paper-112 (MC: 26.2)"));
        assertEquals("1.21.8", VersionService.extractMinecraftVersion("git-Paper-60 (MC: 1.21.8)"));
        assertEquals("1.21.8", VersionService.extractMinecraftVersion("1.21.8"));
    }

    @Test
    void parsesVersionAndFallsBackWhenUnreadable() {
        assertArrayEquals(new int[]{ 26, 2 }, VersionService.parseVersion("26.2"));
        assertArrayEquals(new int[]{ 1, 21, 8 }, VersionService.parseVersion("1.21.8"));
        assertArrayEquals(new int[]{ 1, 21, 6 }, VersionService.parseVersion("26.2-pre1"));
    }

    @Test
    void comparesEveryVersionPart() {
        int[] v1_21_8 = { 1, 21, 8 };
        assertTrue(VersionService.isNewerOrVersion(v1_21_8, 1, 18, 2));
        assertTrue(VersionService.isNewerOrVersion(v1_21_8, 1, 21, 8));
        assertFalse(VersionService.isNewerOrVersion(v1_21_8, 1, 21, 9));
        assertFalse(VersionService.isNewerOrVersion(new int[]{ 1, 18 }, 1, 18, 2));
        assertTrue(VersionService.isNewerOrVersion(new int[]{ 1, 18 }, 1, 18));
        // 26.x は 1.x より新しい（文字の比べ方だと逆になる所）
        assertTrue(VersionService.isNewerOrVersion(new int[]{ 26, 2 }, 1, 21, 8));
        assertTrue(VersionService.isNewerOrVersion(new int[]{ 26, 2 }, 1, 18, 2));
    }

}
