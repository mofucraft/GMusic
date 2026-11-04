package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;

public class VersionService {

    private final String LATEST_VERSION = "v1_21_6";
    private final HashMap<String, String> VERSION_MAPPING = new HashMap<>() {{
        put("v1_18_1", "v1_18");
        put("v1_19_2", "v1_19_1");
        put("v1_20_1", "v1_20");
        put("v1_20_4", "v1_20_3");
        put("v1_20_6", "v1_20_5");
        put("v1_21_1", "v1_21");
        put("v1_21_3", "v1_21_2");
        // 26.x は Mojang 名のまま配布されるので、Mojang 名でビルドした 1.21.6 の実装を使う
        put("v26_1", "v1_21_6");
        put("v26_1_1", "v1_21_6");
        put("v26_1_2", "v1_21_6");
    }};
    private final GMusicMain gMusicMain;
    private final String serverVersion;
    private final int[] serverVersionParts;
    private String packagePath;
    private boolean available;

    public VersionService(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
        serverVersion = extractServerVersion();
        int[] parts;
        try {
            parts = Arrays.stream(serverVersion.split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch(NumberFormatException e) {
            parts = new int[]{1, 21, 6};
        }
        serverVersionParts = parts;
        String packageVersion = "v" + serverVersion.replace(".", "_");
        packagePath = gMusicMain.getClass().getPackage().getName() + ".mcv." + VERSION_MAPPING.getOrDefault(packageVersion, packageVersion);
        available = hasPackageClass("object.gui.GMusicInputGUI");
        if(available) return;
        packagePath = gMusicMain.getClass().getPackage().getName() + ".mcv." + LATEST_VERSION;
        available = hasPackageClass("object.gui.GMusicInputGUI");
    }

    /** "git-Paper-xxx (MC: 26.2)" から "26.2" を取り出す。取れなければ getBukkitVersion() を使う */
    private String extractServerVersion() {
        try {
            String rawServerVersion = Bukkit.getServer().getVersion();
            int mcIndexStart = rawServerVersion.indexOf("MC:");
            if(mcIndexStart != -1) {
                mcIndexStart += 4;
                int mcIndexEnd = rawServerVersion.indexOf(')', mcIndexStart);
                if(mcIndexEnd != -1) rawServerVersion = rawServerVersion.substring(mcIndexStart, mcIndexEnd);
            }
            return rawServerVersion.split(" ")[0].trim();
        } catch(Throwable e) {
            try {
                String raw = Bukkit.getServer().getBukkitVersion();
                return raw.substring(0, raw.indexOf('-'));
            } catch(Throwable e2) {
                return "1.21.6";
            }
        }
    }

    public String getServerVersion() { return serverVersion; }

    public String getPackagePath() { return packagePath; }

    public boolean isAvailable() { return available; }

    /** すべての桁で比べる。例: isNewerOrVersion(1, 18, 2)。26.x は 1.x より新しいと判定される */
    public boolean isNewerOrVersion(int... version) {
        int max = Math.max(serverVersionParts.length, version.length);
        for(int i = 0; i < max; i++) {
            int sv = i < serverVersionParts.length ? serverVersionParts[i] : 0;
            int tv = i < version.length ? version[i] : 0;
            if(sv > tv) return true;
            if(sv < tv) return false;
        }
        return true;
    }

    public Object getPackageObjectInstance(String className, Object... parameters) {
        try {
            Class<?> mcvPackageClass = Class.forName(packagePath + "." + className);
            if(parameters.length == 0) return mcvPackageClass.getConstructor().newInstance();
            Class<?>[] parameterTypes = Arrays.stream(parameters).map(Object::getClass).toArray(Class<?>[]::new);
            return mcvPackageClass.getConstructor(parameterTypes).newInstance(parameters);
        } catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not get package object with class name '" + className + "'!", e); }
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
        try {
            Class.forName(packagePath + "." + className);
            return true;
        } catch(Throwable ignored) { }
        return false;
    }

}