package dev.geco.gmusic.link;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GPlayState;
import dev.geco.gmusic.object.GPlaySettings;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

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
        return Arrays.asList(
            "option_volume",
            "option_join",
            "option_playmode",
            "option_toggle",
            "playing",
            "playing_id",
            "playing_title",
            "playing_author",
            "playing_original_author",
            "playing_paused"
        );
    }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String placeholder) {
        if(offlinePlayer == null) return null;

        switch(placeholder.toLowerCase()) {
            case "option_volume" -> {
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(offlinePlayer.getUniqueId());
                return String.valueOf(playSettings.getVolume());
            }
            case "option_join" -> {
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(offlinePlayer.getUniqueId());
                return String.valueOf(playSettings.isPlayOnJoin());
            }
            case "option_playmode" -> {
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(offlinePlayer.getUniqueId());
                return switch(playSettings.getPlayMode()) {
                    case DEFAULT -> "once";
                    case SHUFFLE -> "shuffle";
                    case LOOP -> "loop";
                    case CONTINUE -> "continue";
                    case CATEGORY -> "category";
                };
            }
            case "option_toggle" -> {
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(offlinePlayer.getUniqueId());
                return String.valueOf(playSettings.isToggleMode());
            }
            case "playing" -> {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return String.valueOf(playState != null);
            }
            case "playing_id" -> {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return playState != null ? playState.getSong().getId() : "";
            }
            case "playing_title" -> {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return playState != null ? playState.getSong().getTitle() : "";
            }
            case "playing_author" -> {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                if(playState == null) return "";
                String author = playState.getSong().getAuthor();
                return author.isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author") : author;
            }
            case "playing_original_author" -> {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                if(playState == null) return "";
                String originalAuthor = playState.getSong().getOriginalAuthor();
                return originalAuthor.isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor") : originalAuthor;
            }
            case "playing_paused" -> {
                GPlayState playState = gMusicMain.getPlayService().getPlayState(offlinePlayer.getUniqueId());
                return String.valueOf(playState != null && playState.isPaused());
            }
        }
        return null;
    }

}
