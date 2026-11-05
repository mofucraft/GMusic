package dev.geco.gmusic.cmd;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.gui.GMusicGUI;
import dev.geco.gmusic.object.GPlayMode;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GPlayState;
import dev.geco.gmusic.object.GSong;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GMusicCommand implements CommandExecutor {

    private final GMusicMain gMusicMain;

    public GMusicCommand(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if(!(sender instanceof Player player)) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-sender-error");
            return true;
        }

        if(!gMusicMain.getPermissionService().hasPermission(sender, "Music")) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
            return true;
        }

        if(args.length == 0) {
            GMusicGUI musicGUI = new GMusicGUI(player.getUniqueId(), GMusicGUI.MenuType.DEFAULT);
            player.openInventory(musicGUI.getInventory());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-help");
            }
            case "play" -> {
                if(args.length == 1) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-use-error");
                    return true;
                }
                GSong song = gMusicMain.getSongService().getSongById(args[1]);
                if(song == null) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-song-error", "%Song%", args[1]);
                    return true;
                }
                gMusicMain.getPlayService().playSong(player, song);
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-play", "%Song%", song.getId(), "%SongTitle%", song.getTitle());
            }
            case "playing" -> {
                if(!gMusicMain.getPlayService().hasPlayingSong(player.getUniqueId()) || gMusicMain.getPlayService().hasPausedSong(player.getUniqueId())) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-playing-error");
                    return true;
                }
                GSong song = gMusicMain.getPlayService().getPlayingSong(player.getUniqueId());
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-playing", "%Song%", song.getId(), "%SongTitle%", song.getTitle());
            }
            case "random" -> {
                GSong song = gMusicMain.getPlayService().getRandomSong(player.getUniqueId());
                if(song == null) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-no-song-error");
                    return true;
                }
                gMusicMain.getPlayService().playSong(player, song);
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-play", "%Song%", song.getId(), "%SongTitle%", song.getTitle());
            }
            case "stop" -> {
                if(!gMusicMain.getPlayService().hasPlayingSong(player.getUniqueId())) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-playing-error");
                    return true;
                }
                gMusicMain.getPlayService().stopSong(player);
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-stop");
            }
            case "pause" -> {
                if(!gMusicMain.getPlayService().hasPlayingSong(player.getUniqueId()) || gMusicMain.getPlayService().hasPausedSong(player.getUniqueId())) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-playing-error");
                    return true;
                }
                gMusicMain.getPlayService().pauseSong(player);
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-pause");
            }
            case "resume" -> {
                if(!gMusicMain.getPlayService().hasPausedSong(player.getUniqueId())) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-paused-error");
                    return true;
                }
                gMusicMain.getPlayService().resumeSong(player);
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-resume");
            }
            case "skip" -> {
                GSong song = gMusicMain.getPlayService().getNextSong(player);
                if(song == null) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-no-song-error");
                    return true;
                }
                gMusicMain.getPlayService().playSong(player, song);
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-play", "%Song%", song.getId(), "%SongTitle%", song.getTitle());
            }
            case "toggle" -> {
                // /music toggle [on|off]（引数なしは切り替え）。on = 専用ジュークボックスの音を聞く（toggleMode は「聞かない」の印）
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());
                Boolean wanted = args.length >= 2 ? parseOnOff(args[1]) : null;
                if(args.length >= 2 && wanted == null) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-onoff-usage", "%Command%", "toggle");
                    return true;
                }
                boolean hearJukeBoxes = wanted != null ? wanted : playSettings.isToggleMode();
                playSettings.setToggleMode(!hearJukeBoxes);
                gMusicMain.getPlaySettingsService().savePlaySettingsAsync(player.getUniqueId(), playSettings);
                gMusicMain.getMessageService().sendMessage(sender, hearJukeBoxes ? "Messages.command-gmusic-toggle-on" : "Messages.command-gmusic-toggle-off");
            }
            case "loop" -> {
                // /music loop [on|off]（引数なしは切り替え）
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());
                Boolean wanted = args.length >= 2 ? parseOnOff(args[1]) : null;
                if(args.length >= 2 && wanted == null) {
                    // 以前は on/off 以外の語でも黙って切り替えていた
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-onoff-usage", "%Command%", "loop");
                    return true;
                }
                GPlayMode playMode = loopTarget(playSettings.getPlayMode(), wanted);
                playSettings.setPlayMode(playMode);
                gMusicMain.getPlaySettingsService().savePlaySettingsAsync(player.getUniqueId(), playSettings);
                gMusicMain.getMessageService().sendMessage(sender, playMode == GPlayMode.LOOP ? "Messages.command-gmusic-loop-on" : "Messages.command-gmusic-loop-off");
            }
            case "favorite" -> {
                // 再生中の曲をお気に入りに追加/削除
                GPlayState playState = gMusicMain.getPlayService().getPlayState(player.getUniqueId());
                if (playState == null) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-playing-error");
                    return true;
                }
                GSong song = playState.getSong();
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());
                if (playSettings.getFavorites().contains(song)) {
                    playSettings.removeFavoriteSong(song);
                    gMusicMain.getPlaySettingsService().savePlaySettingsAsync(player.getUniqueId(), playSettings);
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-favorite-remove", "%SongTitle%", song.getTitle());
                } else {
                    playSettings.addFavoriteSong(song);
                    gMusicMain.getPlaySettingsService().savePlaySettingsAsync(player.getUniqueId(), playSettings);
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-favorite-add", "%SongTitle%", song.getTitle());
                }
                GMusicGUI musicGUI = GMusicGUI.getMusicGUI(player.getUniqueId());
                if (musicGUI != null) {
                    musicGUI.setPage(1);
                    musicGUI.setDefaultBar();
                }
            }
            case "reload" -> {
                if (!gMusicMain.getPermissionService().hasPermission(sender, "Reload")) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
                    return true;
                }
                gMusicMain.getSongService().loadSongs();
                // 読み直した曲にお気に入りを付け替える（古い曲オブジェクトを持ち続けないように）
                gMusicMain.getPlaySettingsService().refreshFavorites();
                int songCount = gMusicMain.getSongService().getSongs().size();
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-reload-songs", "%Count%", String.valueOf(songCount));
            }
            case "speaker" -> {
                // 以前は isOp() 固定で、LuckPerms などから権限を渡せなかった。既定は op のまま
                if (!gMusicMain.getPermissionService().hasPermission(sender, "Speaker")) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
                    return true;
                }
                GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());

                if (args.length < 2) {
                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-speaker-usage");
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "on" -> {
                        playSettings.setSpeakerMode(true);
                        if (args.length >= 3) {
                            try {
                                long uniformRadius = Long.parseLong(args[2]);
                                playSettings.setUniformRadius(uniformRadius);
                            } catch (NumberFormatException ignored) {}
                        }
                        if (args.length >= 4) {
                            try {
                                long maxRange = Long.parseLong(args[3]);
                                playSettings.setSpeakerRange(maxRange);
                            } catch (NumberFormatException ignored) {}
                        }
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-speaker-on",
                                "%Uniform%", String.valueOf(playSettings.getUniformRadius()),
                                "%Max%", String.valueOf(playSettings.getSpeakerRange()));
                    }
                    case "off" -> {
                        playSettings.setSpeakerMode(false);
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-speaker-off");
                    }
                    case "range" -> {
                        if (args.length >= 3) {
                            try {
                                playSettings.setUniformRadius(Long.parseLong(args[2]));
                            } catch (NumberFormatException ignored) {}
                        }
                        if (args.length >= 4) {
                            try {
                                playSettings.setSpeakerRange(Long.parseLong(args[3]));
                            } catch (NumberFormatException ignored) {}
                        }
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-speaker-range",
                                "%Uniform%", String.valueOf(playSettings.getUniformRadius()),
                                "%Max%", String.valueOf(playSettings.getSpeakerRange()));
                    }
                    default -> gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-speaker-usage");
                }
            }
            default -> gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gmusic-use-error");
        }

        return true;
    }

    /** "on"/"off"（と同じ意味の語）を読む。読めなければ null。 */
    static Boolean parseOnOff(String arg) {
        if(arg == null) return null;
        return switch(arg.toLowerCase(java.util.Locale.ROOT)) {
            case "on", "true", "enable", "1" -> Boolean.TRUE;
            case "off", "false", "disable", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * /music loop の後の再生モード。wanted が null なら切り替え。
     * off はループ中だけ DEFAULT に戻す（シャッフルや連続再生を勝手に消さない）。
     */
    static GPlayMode loopTarget(GPlayMode current, Boolean wanted) {
        boolean looping = current == GPlayMode.LOOP;
        boolean enable = wanted != null ? wanted : !looping;
        if(enable) return GPlayMode.LOOP;
        return looping ? GPlayMode.DEFAULT : (current != null ? current : GPlayMode.DEFAULT);
    }

}
