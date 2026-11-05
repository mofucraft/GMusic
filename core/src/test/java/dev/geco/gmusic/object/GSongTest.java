package dev.geco.gmusic.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GSongTest {

    @TempDir
    Path dir;

    @Test
    void missingAuthorsBecomeEmpty() throws Exception {
        File file = dir.resolve("no_author.gnbs").toFile();
        Files.writeString(file.toPath(), "Song:\n  Id: no_author\n  Content:\n    Instruments:\n      a: 0\n    Main:\n    - 1!a\n");
        GSong song = new GSong(file);
        // 以前は null で、/music play の時に NullPointerException で落ちた
        assertEquals("", song.getAuthor());
        assertEquals("", song.getOriginalAuthor());
    }

}
