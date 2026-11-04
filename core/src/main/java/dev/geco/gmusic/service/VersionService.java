package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class VersionService {

    /**
     * NMS 実装がある最新の版。26.x（Mojang 名のまま配布される版）も含め、
     * 専用の実装がない版はここへ落とす。
     */
    public static final String LATEST_VERSION = "v1_21_6";

    private static final Map<String, String> VERSION_MAPPING = new HashMap<>();
    static {
        VERSION_MAPPING.put("v1_18_1", "v1_18");
        VERSION_MAPPING.put("v1_19_2", "v1_19_1");
        VERSION_MAPPING.put("v1_20_1", "v1_20");
        VERSION_MAPPING.put("v1_20_4", "v1_20_3");
        VERSION_MAPPING.put("v1_20_6", "v1_20_5");
        VERSION_MAPPING.put("v1_21_1", "v1_21");
        VERSION_MAPPING.put("v1_21_3", "v1_21_2");
        VERSION_MAPPING.put("v1_21_7", "v1_21_6");
        VERSION_MAPPING.put("v1_21_8", "v1_21_6");
        // 26.x は Mojang 名のまま配布されるので、Mojang 名でビルドした 1.21.6 の実装を使う（1.3.0 と同じ）
        VERSION_MAPPING.put("v26_1", "v1_21_6");
        VERSION_MAPPING.put("v26_1_1", "v1_21_6");
        VERSION_MAPPING.put("v26_1_2", "v1_21_6");
    }

    private final GMusicMain gMusicMain;
    private final String serverVersion;
    private final int[] serverVersionParts;
    private String packagePath;
    private boolean available;

    public VersionService(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
        serverVersion = extractServerVersion(Bukkit.getServer().getVersion(), Bukkit.getServer().getBukkitVersion());
        serverVersionParts = parseVersionParts(serverVersion);
        if(!isNewerOrVersion(1, 18)) return;
        packagePath = gMusicMain.getClass().getPackage().getName() + ".mcv." + toPackageVersion(serverVersion);
        available = hasPackageClass("object.gui.GMusicInputGUI");
        if(available) return;
        packagePath = gMusicMain.getClass().getPackage().getName() + ".mcv." + LATEST_VERSION;
        available = hasPackageClass("object.gui.GMusicInputGUI");
    }

    /**
     * "git-Paper-123 (MC: 1.21.4)" や "26.2-112-... (MC: 26.2)" から Minecraft の版だけを取り出す。
     * MC: が無い場合は getBukkitVersion()（"1.21.4-R0.1-SNAPSHOT" など）の '-' より前を使う。
     */
    public static String extractServerVersion(String rawVersion, String bukkitVersion) {
        if(rawVersion != null) {
            int start = rawVersion.indexOf("MC:");
            if(start != -1) {
                start += 3;
                int end = rawVersion.indexOf(')', start);
                String mc = (end != -1 ? rawVersion.substring(start, end) : rawVersion.substring(start)).trim();
                mc = leadingNumericVersion(mc);
                if(!mc.isEmpty()) return mc;
            }
        }
        if(bukkitVersion != null) {
            int dash = bukkitVersion.indexOf('-');
            String v = leadingNumericVersion(dash != -1 ? bukkitVersion.substring(0, dash) : bukkitVersion);
            if(!v.isEmpty()) return v;
        }
        return "0";
    }

    /** "26.1.2.build.12" → "26.1.2" のように、先頭の数字とドットの並びだけを残す。 */
    static String leadingNumericVersion(String value) {
        String[] parts = value.trim().split("[.\\s]");
        StringBuilder sb = new StringBuilder();
        for(String part : parts) {
            if(part.isEmpty() || !part.chars().allMatch(Character::isDigit)) break;
            if(sb.length() > 0) sb.append('.');
            sb.append(part);
        }
        return sb.toString();
    }

    public static int[] parseVersionParts(String version) {
        return Arrays.stream(version.split("\\.")).filter(s -> !s.isEmpty()).mapToInt(s -> {
            try { return Integer.parseInt(s); } catch(NumberFormatException e) { return 0; }
        }).toArray();
    }

    public static String toPackageVersion(String version) {
        String packageVersion = "v" + version.replace(".", "_");
        return VERSION_MAPPING.getOrDefault(packageVersion, packageVersion);
    }

    /** 版の比較。例: isNewerOrVersion(1, 18, 2) は 1.18.2 以上なら true。26.x は 1.x より新しい。 */
    public static boolean isNewerOrVersion(int[] serverVersionParts, int... version) {
        int max = Math.max(serverVersionParts.length, version.length);
        for(int i = 0; i < max; i++) {
            int sv = i < serverVersionParts.length ? serverVersionParts[i] : 0;
            int tv = i < version.length ? version[i] : 0;
            if(sv > tv) return true;
            if(sv < tv) return false;
        }
        return true;
    }

    public String getServerVersion() { return serverVersion; }

    public String getPackagePath() { return packagePath; }

    public boolean isAvailable() { return available; }

    public boolean isNewerOrVersion(int... version) { return isNewerOrVersion(serverVersionParts, version); }

    public Object getPackageObjectInstance(String className, Object... parameters) {
        try {
            Class<?> mcvPackageClass = Class.forName(packagePath + "." + className);
            if(parameters.length == 0) return mcvPackageClass.getConstructor().newInstance();
            Class<?>[] parameterTypes = Arrays.stream(parameters).map(Object::getClass).toArray(Class<?>[]::new);
            return mcvPackageClass.getConstructor(parameterTypes).newInstance(parameters);
        } catch(Throwable e) { gMusicMain.getLogger().log(java.util.logging.Level.SEVERE, "Could not get package object with class name '" + className + "'!", e); }
        return null;
    }

    public Object executeMethod(Object object, String methodName) {
        try {
            Method method = object.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(object);
        } catch(Throwable ignored) { }
        return null;
    }

    public boolean hasPackageClass(String className) {
        if(packagePath == null) return false;
        try {
            Class.forName(packagePath + "." + className);
            return true;
        } catch(Throwable ignored) { }
        return false;
    }

}
