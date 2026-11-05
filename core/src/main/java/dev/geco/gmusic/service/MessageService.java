package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConfigurationOptions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract public class MessageService {

    protected final String PREFIX_PLACEHOLDER = "[P]";
    protected final String PREFIX_REPLACE = "&7[&6" + GMusicMain.NAME + "&7]";
    protected final char AMPERSAND_CHAR = '&';
    protected final char COLOR_CHAR = org.bukkit.ChatColor.COLOR_CHAR;
    protected final Pattern HEX_PATTERN = Pattern.compile("#([a-fA-F0-9]{6})");
    protected final GMusicMain gMusicMain;
    protected final HashMap<String, FileConfiguration> messages = new HashMap<>();
    protected String defaultLanguage;

    /** 言語ファイルの版を書くキー。無いファイルは版 1 とみなす。 */
    static final String LANG_VERSION_KEY = "Plugin.lang-version";
    /**
     * 版ごとに「中身を新しくした文」。既にある言語ファイルは足りないキーしか足されないため、
     * 文を直してもサーバーの古いファイルが優先されて新しい文が出なかった（例: ヘルプの loop [on|off]）。
     * ファイルの版がこれより古いときだけ、ここに挙げた文を jar の文に置き換える。
     */
    static final Map<Integer, List<String>> UPDATED_KEYS = Map.of(
            2, List.of("Messages.command-gmusic-help")
    );

    public MessageService(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
        loadMessages();
    }

    public FileConfiguration getMessages() { return getMessages(defaultLanguage); }

    public FileConfiguration getMessages(String languageCode) { return messages.getOrDefault(languageCode, new YamlConfiguration()); }

    public void loadMessages() {
        messages.clear();
        boolean betterSave = gMusicMain.getVersionManager().isNewerOrVersion(1, 18, 2);
        try(JarFile jarFile = new JarFile(Paths.get(gMusicMain.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toString())) {
            Enumeration<JarEntry> jarFiles = jarFile.entries();
            while(jarFiles.hasMoreElements()) {
                JarEntry jarEntry = jarFiles.nextElement();
                if(!jarEntry.getName().startsWith("lang") || jarEntry.isDirectory()) continue;
                File langFile = new File(gMusicMain.getDataFolder(), jarEntry.getName());
                if(!betterSave) {
                    if(!langFile.exists()) gMusicMain.saveResource(jarEntry.getName(), false);
                    continue;
                }
                FileConfiguration lang = YamlConfiguration.loadConfiguration(langFile);
                InputStream langSteam = gMusicMain.getResource(jarEntry.getName());
                if(langSteam != null) {
                    FileConfiguration langSteamConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(langSteam, StandardCharsets.UTF_8));
                    int fileVersion = langFile.exists() ? lang.getInt(LANG_VERSION_KEY, 1) : langSteamConfig.getInt(LANG_VERSION_KEY, 1);
                    boolean outdated = fileVersion < langSteamConfig.getInt(LANG_VERSION_KEY, 1);
                    if(!outdated && lang.getKeys(true).equals(langSteamConfig.getKeys(true))) continue;
                    if(outdated) backupLangFile(langFile, fileVersion);
                    lang.setDefaults(langSteamConfig);
                    YamlConfigurationOptions options = (YamlConfigurationOptions) lang.options();
                    options.parseComments(true).copyDefaults(true).width(500);
                    lang.loadFromString(lang.saveToString());
                    if(outdated) {
                        List<String> refreshed = applyLangUpdates(lang, langSteamConfig, fileVersion);
                        if(!refreshed.isEmpty()) gMusicMain.getLogger().info("Updated " + refreshed.size() + " text(s) in " + jarEntry.getName() + " to version " + langSteamConfig.getInt(LANG_VERSION_KEY, 1) + " (old file: lang-backup/)");
                    }
                    for(String comments : lang.getKeys(true)) lang.setComments(comments, langSteamConfig.getComments(comments));
                    lang.save(langFile);
                } else if(!langFile.exists()) gMusicMain.saveResource(jarEntry.getName(), false);
            }
        } catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not load messages!", e); }
        File langFolder = new File(gMusicMain.getDataFolder(), "lang");
        for(File langFile : Objects.requireNonNull(langFolder.listFiles())) messages.put(langFile.getName().replaceFirst("lang/", "").replaceFirst(".yml", ""), YamlConfiguration.loadConfiguration(langFile));
        defaultLanguage = messages.containsKey(gMusicMain.getConfigService().L_LANG) ? gMusicMain.getConfigService().L_LANG : "en_us";
    }

    /**
     * 版が古い言語ファイルの文を新しくする。fileVersion より新しい版で直した文だけを jar の文で置き換え、
     * それ以外（サーバーで書き換えた文）はそのまま残す。置き換えたキーを返す。
     */
    static List<String> applyLangUpdates(FileConfiguration lang, FileConfiguration jarLang, int fileVersion) {
        int jarVersion = jarLang.getInt(LANG_VERSION_KEY, 1);
        List<String> refreshed = new ArrayList<>();
        for(int version = fileVersion + 1; version <= jarVersion; version++) {
            for(String key : UPDATED_KEYS.getOrDefault(version, List.of())) {
                if(!jarLang.contains(key, true)) continue;
                Object jarValue = jarLang.get(key);
                if(!Objects.equals(lang.get(key), jarValue)) refreshed.add(key);
                lang.set(key, jarValue);
            }
        }
        lang.set(LANG_VERSION_KEY, jarVersion);
        return refreshed;
    }

    /** 置き換える前のファイルを lang-backup/<名前>-v<版>.yml に写す（既にあれば上書きしない）。 */
    private void backupLangFile(File langFile, int fileVersion) {
        if(!langFile.exists()) return;
        try {
            File backupFolder = new File(gMusicMain.getDataFolder(), "lang-backup");
            if(!backupFolder.exists() && !backupFolder.mkdirs()) return;
            File backup = new File(backupFolder, langFile.getName().replaceFirst("\\.yml$", "") + "-v" + fileVersion + ".yml");
            if(!backup.exists()) Files.copy(langFile.toPath(), backup.toPath());
        } catch(Throwable e) { gMusicMain.getLogger().log(Level.WARNING, "Could not back up " + langFile.getName(), e); }
    }

    abstract public String toFormattedMessage(String text, Object... rawReplaceList);

    abstract public void sendMessage(@NotNull CommandSender target, String message, Object... replaceList);

    abstract public void sendActionBarMessage(@NotNull Player target, String message, Object... replaceList);

    public String getMessage(String message, Object... replaceList) { return getMessage(message, null, replaceList); }

    public String getMessage(String message, Entity entity, Object... replaceList) { return getTranslatedMessage(message, getLanguageForTarget(entity), replaceList); }

    public String getTranslatedMessage(String message, String languageCode, Object... replaceList) { return toFormattedMessage(getRawTranslatedMessage(message, languageCode, replaceList)); }

    public String getRawTranslatedMessage(String message, String languageCode, Object... replaceList) {
        String raw = "";
        if(message != null && !message.isEmpty()) {
            raw = getMessages(languageCode).getString(message);
            // その言語のファイルに無い文は、キーの名前でなく既定の言語の文を出す（例: de_de に無い loop の文）
            if(raw == null) raw = getMessages(defaultLanguage).getString(message, message);
        }
        return replaceWithLanguageCode(raw, languageCode, replaceList);
    }

    public String getLanguageForTarget(CommandSender target) {
        if(!gMusicMain.getConfigService().L_CLIENT_LANG || !(target instanceof Player)) return defaultLanguage;
        String language = ((Player) target).getLocale();
        return messages.containsKey(language) ? language : defaultLanguage;
    }

    protected String replaceText(String text, Object @NotNull ... replaceList) {
        if(replaceList.length == 0 || replaceList.length % 2 != 0) return text;
        for(int count = 0; count < replaceList.length; count += 2) {
            if(replaceList[count] != null && replaceList[count + 1] != null) {
                String key = replaceList[count].toString();
                String value = replaceList[count + 1].toString();
                text = text.replace(key, value);
            }
        }
        return text;
    }

    protected String replaceHexColorsDirectly(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        int lastIndex = 0;
        while(matcher.find()) {
            result.append(text, lastIndex, matcher.start()).append(COLOR_CHAR).append('x');
            char[] chars = matcher.group().substring(1).toCharArray();
            for(char c : chars) result.append(COLOR_CHAR).append(c);
            lastIndex = matcher.end();
        }
        result.append(text.substring(lastIndex));
        return result.toString();
    }

    private String replaceWithLanguageCode(String message, String languageCode, Object ... replaceList) {
        message = replaceText(message, replaceList);
        return message.replace(PREFIX_PLACEHOLDER, getMessages(languageCode).getString("Plugin.plugin-prefix", PREFIX_REPLACE));
    }

}