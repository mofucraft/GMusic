package dev.geco.gmusic.service;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }

    @Test
    void outdatedFileGetsOnlyChangedTexts() throws Exception {
        YamlConfiguration jar = yaml("Plugin:\n  lang-version: 2\nMessages:\n  command-gmusic-help: \"new help loop [on|off]\"\n  command-gmusic-stop: \"jar stop\"\n");
        // 本番の古いファイル（版の印なし＝版 1）。stop はサーバーで書き換えた文
        YamlConfiguration file = yaml("Messages:\n  command-gmusic-help: \"old help loop\"\n  command-gmusic-stop: \"server stop\"\n");

        List<String> refreshed = MessageService.applyLangUpdates(file, jar, file.getInt(MessageService.LANG_VERSION_KEY, 1));

        assertEquals(List.of("Messages.command-gmusic-help"), refreshed);
        assertEquals("new help loop [on|off]", file.getString("Messages.command-gmusic-help"));
        assertEquals("server stop", file.getString("Messages.command-gmusic-stop"));
        assertEquals(2, file.getInt(MessageService.LANG_VERSION_KEY));
    }

    @Test
    void upToDateFileIsLeftAlone() throws Exception {
        YamlConfiguration jar = yaml("Plugin:\n  lang-version: 2\nMessages:\n  command-gmusic-help: \"new\"\n");
        YamlConfiguration file = yaml("Plugin:\n  lang-version: 2\nMessages:\n  command-gmusic-help: \"my own help\"\n");

        List<String> refreshed = MessageService.applyLangUpdates(file, jar, file.getInt(MessageService.LANG_VERSION_KEY, 1));

        assertTrue(refreshed.isEmpty());
        assertEquals("my own help", file.getString("Messages.command-gmusic-help"));
    }

    @Test
    void shippedLangFilesCarryTheCurrentVersionAndKeys() {
        File langFolder = findLangFolder();
        int newest = MessageService.UPDATED_KEYS.keySet().stream().max(Integer::compare).orElse(1);
        for(String name : List.of("ja_jp.yml", "en_us.yml", "de_de.yml")) {
            YamlConfiguration lang = YamlConfiguration.loadConfiguration(new File(langFolder, name));
            assertEquals(newest, lang.getInt(MessageService.LANG_VERSION_KEY, 1), name);
        }
        for(String name : List.of("ja_jp.yml", "en_us.yml")) {
            YamlConfiguration lang = YamlConfiguration.loadConfiguration(new File(langFolder, name));
            for(Map.Entry<Integer, List<String>> entry : MessageService.UPDATED_KEYS.entrySet()) {
                for(String key : entry.getValue()) assertTrue(lang.contains(key), name + " " + key);
            }
            assertTrue(lang.getString("Messages.command-gmusic-help").contains("loop [on|off]"), name);
            assertTrue(lang.getString("Messages.command-gmusic-help").contains("toggle [on|off]"), name);
            for(String key : List.of("command-gmusic-onoff-usage", "command-gmusic-loop-on", "command-gmusic-loop-off", "command-gmusic-toggle-on", "command-gmusic-toggle-off")) {
                assertTrue(lang.contains("Messages." + key), name + " " + key);
            }
        }
    }

    private static File findLangFolder() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while(dir != null) {
            File lang = new File(dir, "resources/lang");
            if(new File(lang, "ja_jp.yml").exists()) return lang;
            dir = dir.getParentFile();
        }
        throw new AssertionError("resources/lang not found");
    }

}
