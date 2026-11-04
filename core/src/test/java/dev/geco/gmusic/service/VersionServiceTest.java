package dev.geco.gmusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionServiceTest {

    @Test
    void extractsMinecraftVersionFromServerVersion() {
        assertEquals("1.21.4", VersionService.extractServerVersion("git-Paper-196 (MC: 1.21.4)", "1.21.4-R0.1-SNAPSHOT"));
        assertEquals("26.2", VersionService.extractServerVersion("26.2-112-main@abc123 (MC: 26.2)", "26.2.build.112-alpha"));
        assertEquals("26.1.2", VersionService.extractServerVersion("x (MC: 26.1.2)", null));
    }

    @Test
    void fallsBackToBukkitVersion() {
        assertEquals("1.21.4", VersionService.extractServerVersion("CraftBukkit", "1.21.4-R0.1-SNAPSHOT"));
        // '-' が無い版文字列でも落ちない（以前は substring(0, -1) で例外）
        assertEquals("26.1.2", VersionService.extractServerVersion(null, "26.1.2.build.12"));
        assertEquals("0", VersionService.extractServerVersion(null, null));
    }

    @Test
    void comparesYearBasedVersionsAsNewer() {
        int[] v26 = VersionService.parseVersionParts("26.2");
        // 以前の比較は 2 番目の数字だけを見ており、26.2 を「1.18 より古い」と判定してプラグインを無効化していた
        assertTrue(VersionService.isNewerOrVersion(v26, 1, 18));
        assertTrue(VersionService.isNewerOrVersion(v26, 1, 18, 2));
        assertTrue(VersionService.isNewerOrVersion(v26, 26, 2));
        assertFalse(VersionService.isNewerOrVersion(v26, 26, 3));
    }

    @Test
    void comparesClassicVersions() {
        assertTrue(VersionService.isNewerOrVersion(VersionService.parseVersionParts("1.18.2"), 1, 18, 2));
        assertTrue(VersionService.isNewerOrVersion(VersionService.parseVersionParts("1.21"), 1, 18, 2));
        assertFalse(VersionService.isNewerOrVersion(VersionService.parseVersionParts("1.18"), 1, 18, 2));
        assertFalse(VersionService.isNewerOrVersion(VersionService.parseVersionParts("1.17.1"), 1, 18));
    }

    @Test
    void mapsPackageVersions() {
        assertEquals("v1_21_6", VersionService.toPackageVersion("1.21.8"));
        assertEquals("v1_21", VersionService.toPackageVersion("1.21.1"));
        assertEquals("v26_2", VersionService.toPackageVersion("26.2"));
        // 1.3.0 と同じく、26.1 系は 1.21.6 の実装を使う（26.2 も実行時に 1.21.6 へ落ちる）
        assertEquals("v1_21_6", VersionService.toPackageVersion("26.1.2"));
        assertEquals("v1_21_6", VersionService.LATEST_VERSION);
    }

}
