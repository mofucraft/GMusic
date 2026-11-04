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
        serverVersionParts = parseVersion(serverVersion);
        String packageVersion = "v" + serverVersion.replace(".", "_");
        packagePath = gMusicMain.getClass().getPackage().getName() + ".mcv." + VERSION_MAPPING.getOrDefault(packageVersion, packageVersion);
        available = hasPackageClass("object.gui.GMusicInputGUI");
        if(available) return;
        packagePath = gMusicMain.getClass().getPackage().getName() + ".mcv." + LATEST_VERSION;
        available = hasPackageClass("object.gui.GMusicInputGUI");
    }

    // 26.x 以降は getBukkitVersion() の形が変わるので、getVersion() の "(MC: x.y.z)" を先に読む
    private String extractServerVersion() {
        try {
            return extractMinecraftVersion(Bukkit.getServer().getVersion());
        } catch(Throwable e) {
            try {
                String rawServerVersion = Bukkit.getServer().getBukkitVersion();
                return rawServerVersion.substring(0, rawServerVersion.indexOf('-'));
            } catch(Throwable ignored) { return "1.21.6"; }
        }
    }

    public String getServerVersion() { return serverVersion; }

    public String getPackagePath() { return packagePath; }

    public boolean isAvailable() { return available; }

    // 例: isNewerOrVersion(1, 18, 2) は 1.18.2 以上なら true。26.1 も 1.21.x より新しいと判定できる
    public boolean isNewerOrVersion(int... version) { return isNewerOrVersion(serverVersionParts, version); }

    static boolean isNewerOrVersion(int[] serverVersionParts, int... version) {
        int length = Math.max(serverVersionParts.length, version.length);
        for(int i = 0; i < length; i++) {
            int server = i < serverVersionParts.length ? serverVersionParts[i] : 0;
            int target = i < version.length ? version[i] : 0;
            if(server > target) return true;
            if(server < target) return false;
        }
        return true;
    }

    // "1.21.8" → {1, 21, 8}。読めない時は 1.21.6 として扱う
    static int[] parseVersion(String version) {
        try {
            return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch(NumberFormatException e) { return new int[]{ 1, 21, 6 }; }
    }

    // 例: "git-Paper-112 (MC: 26.2)" → "26.2"
    static String extractMinecraftVersion(String version) {
        int start = version.indexOf("MC:");
        if(start != -1) {
            start += 4;
            int end = version.indexOf(')', start);
            if(end != -1) version = version.substring(start, end);
        }
        return version.split(" ")[0].trim();
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