package dev.geco.gmusic.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GPlaySettingsTest {

    @TempDir
    Path dir;

    private GSong song(String id) throws IOException {
        File file = dir.resolve(id + ".gnbs").toFile();
        Files.writeString(file.toPath(), "Song:\n  Id: " + id + "\n  Title: " + id + "\n  Material: MUSIC_DISC_CAT\n  Category: RECORDS\n");
        return new GSong(file);
    }

    private GPlaySettings settings(List<GSong> favorites) {
        return new GPlaySettings(UUID.randomUUID(), GPlayListMode.DEFAULT, 70, false, GPlayMode.DEFAULT, true, false, false, 0, null, favorites, false, 20, false, null);
    }

    @Test
    void unresolvedFavoritesAreKeptWhenSaving() throws IOException {
        GPlaySettings settings = settings(new ArrayList<>(List.of(song("a"))));
        settings.setUnresolvedFavoriteIds(List.of("missing", "missing"));
        // 曲ファイルが一時的に無くても、お気に入りの ID は保存対象に残る（以前は消えていた）
        assertEquals(List.of("a", "missing"), settings.getFavoriteIdsForSave());
    }

    @Test
    void addingAFavoriteTwiceDoesNotDuplicate() throws IOException {
        GSong a = song("a");
        GPlaySettings settings = settings(new ArrayList<>());
        settings.addFavoriteSong(a);
        settings.addFavoriteSong(a);
        assertEquals(List.of("a"), settings.getFavoriteIdsForSave());
    }

    @Test
    void reAddingAMissingSongClearsItFromUnresolved() throws IOException {
        GPlaySettings settings = settings(new ArrayList<>());
        settings.setUnresolvedFavoriteIds(List.of("b"));
        settings.addFavoriteSong(song("b"));
        assertTrue(settings.getUnresolvedFavoriteIds().isEmpty());
        assertEquals(List.of("b"), settings.getFavoriteIdsForSave());
        settings.removeFavoriteSong(song("b"));
        assertTrue(settings.getFavoriteIdsForSave().isEmpty());
    }

    @Test
    void favoritesListToleratesChangesWhileIterating() throws IOException {
        // 再生スレッドが回している最中に GUI で追加しても ConcurrentModificationException にならない
        GPlaySettings settings = settings(new ArrayList<>(List.of(song("a"), song("b"))));
        GSong c = song("c");
        assertDoesNotThrow(() -> { for(GSong s : settings.getFavorites()) settings.getFavorites().add(c); });
    }

}
