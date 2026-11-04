package dev.geco.gmusic.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GSongTest {

    @TempDir
    Path dir;

    private File write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, text);
        return file.toFile();
    }

    @Test
    void notesAtTheSameTimeDoNotChangeTheOriginalNote() throws IOException {
        GSong song = new GSong(write("same_time.gnbs", """
                Song:
                  Id: same_time
                  Content:
                    Instruments:
                      a: 0
                    Main:
                    - 1!a
                    - 0!a
                    - 1!a
                """));

        assertEquals(3, song.getNoteAmount());
        assertEquals(2, song.getContent().get(1L).size());
        assertEquals(1, song.getContent().get(2L).size());
        // 前は同じ時刻の2つ目の音が1つ目の GNote の中身に足されていた
        assertEquals(1, song.getMain().get(0).getNoteParts().size());
    }

    @Test
    void repeatedPartsDoNotGrow() throws IOException {
        GSong song = new GSong(write("repeat.gnbs", """
                Song:
                  Id: repeat
                  Content:
                    Instruments:
                      a: 0
                    Parts:
                      A:
                      - 0!a
                    Main:
                    - 1!?A;2
                    - 0!a
                """));

        GNote part = song.getParts().get("A").get(0);
        assertEquals(1, part.getNoteParts().size());
        assertEquals(4, song.getNoteAmount());
    }

    @Test
    void explainsWhyASongHasNoNotes() throws IOException {
        GSong broken = new GSong(write("broken.gnbs", "Other: 1\n"));
        assertEquals(0, broken.getNoteAmount());
        assertTrue(broken.getLoadProblem().contains("no 'Song:' section"));
        assertEquals("broken", broken.getId());

        GSong empty = new GSong(write("empty.gnbs", """
                Song:
                  Id: empty
                  Content:
                    Main: []
                """));
        assertTrue(empty.getLoadProblem().contains("'Song.Content.Main' is empty"));
    }

}
