package dev.geco.gmusic.link;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GPlayMode;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GPlayState;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/** PlaceholderAPI 連携: %gmusic_<名前>% */
public class PlaceholderAPILink extends PlaceholderExpansion {

    private final GMusicMain gMusicMain;

    public PlaceholderAPILink(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
    }

    @Override
    public boolean canRegister() { return gMusicMain.isEnabled(); }

    @Override
    public @NotNull String getName() { return gMusicMain.getDescription().getName(); }

    @Override
    public @NotNull String getIdentifier() { return GMusicMain.NAME.toLowerCase(); }

    @Override
    public @NotNull String getAuthor() { return gMusicMain.getDescription().getAuthors().toString(); }

    @Override
    public @NotNull String getVersion() { return gMusicMain.getDescription().getVersion(); }

    @Override
    public @NotNull List<String> getPlaceholders() {
        return Arrays.asList("option_volume", "option_join", "option_playmode", "option_toggle", "playing", "playing_id", "playing_title", "playing_author", "playing_original_author", "playing_paused");
    }

    @Override
    public boolean persist() { return true; }

    /**
     * オンラインの人はキャッシュ（無ければ読み込み）、オフラインの人はキャッシュにある時だけ返す。
     * 1.3.0 ではオフラインの人の設定も DB から読んでキャッシュに入れたままにしており、
     * 呼ばれるたびにメモリが増え、PlaceholderAPI のスレッドで DB を読んでいた。
     */
    private GPlaySettings getSettings(OfflinePlayer offlinePlayer) {
        GPlaySettings cached = gMusicMain.getPlaySettingsService().getCachedPlaySettings(offlinePlayer.getUniqueId());
        if(cached != null) return cached;
        if(offlinePlayer.isOnline() && org.bukkit.Bukkit.isPrimaryThread()) return gMusicMain.getPlaySettingsService().getPlaySettings(offlinePlayer.getUniqueId());
        return null;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String placeholder) {
        if(offlinePlayer == null) return null;
        switch(placeholder.toLowerCase()) {
            case "option_volume": {
                GPlaySettings playSettings = getSettings(offlinePlayer);
                if(playSettings == null) return "";
                return String.valueOf(playSettings.getVolume());
            }
            case "option_join": {
                GPlaySettings playSettings = getSettings(offlinePlayer);
                if(playSettings == null) return "";
                return String.valueOf(playSettings.isPlayOnJoin());
            }
            case "option_playmode": {
                GPlaySettings playSettings = getSettings(offlinePlayer);
                if(playSettings == null) return "";
                GPlayMode mode = playSettings.getPlayMode();
                return switch(mode) {
                    case DEFAULT -> "once";
                    case SHUFFLE -> "shuffle";
                    case LOOP -> "loop";
                    case CONTINUE -> "continue";
                    case CATEGORY -> "category";
                };
            }
            case "option_toggle": {
                GPlaySettings playSettings = getSettings(offlinePlayer);
                if(playSettings == null) return "";
                return String.valueOf(playSettings.isToggleMode());
            }
            case "playing": {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return String.valueOf(playState != null);
            }
            case "playing_id": {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return playState != null ? playState.getSong().getId() : "";
            }
            case "playing_title": {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return playState != null ? playState.getSong().getTitle() : "";
            }
            case "playing_author": {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                if(playState == null) return "";
                String author = playState.getSong().getAuthor();
                return author.isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author") : author;
            }
            case "playing_original_author": {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                if(playState == null) return "";
                String originalAuthor = playState.getSong().getOriginalAuthor();
                return originalAuthor.isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor") : originalAuthor;
            }
            case "playing_paused": {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return String.valueOf(playState != null && playState.isPaused());
            }
            default:
                return null;
        }
    }

}
