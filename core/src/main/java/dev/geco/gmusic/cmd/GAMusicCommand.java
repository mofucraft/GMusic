package dev.geco.gmusic.cmd;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GSong;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class GAMusicCommand implements CommandExecutor {

    private final GMusicMain gMusicMain;

    public GAMusicCommand(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if(!gMusicMain.getPermissionService().hasPermission(sender, "AMusic")) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
            return true;
        }

        switch(args[0].toLowerCase()) {
            case "download":
                if(!(sender instanceof Player player)) {
                    if (!gMusicMain.getPermissionService().hasPermission(sender, "AMusic.Download", "AMusic.*", "*")) {
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
                        return true;
                    }
                }
                if (args.length > 3) {
                    String t = args[1].toLowerCase();
                    if (t.equalsIgnoreCase(GMusicMain.NBS_EXT) || t.equalsIgnoreCase(GMusicMain.GNBS_EXT) || t.equalsIgnoreCase(GMusicMain.MIDI_EXT)) {
                        File file = new File("plugins/" + gMusicMain.getName(), t.equalsIgnoreCase(GMusicMain.NBS_EXT) ? GMusicMain.CONVERT_PATH + "/" + args[2] + GMusicMain.NBS_FILETYP : t.equalsIgnoreCase(GMusicMain.GNBS_EXT) ? GMusicMain.SONGS_PATH + "/" + args[2] + GMusicMain.GNBS_FILETYP : GMusicMain.MIDI_PATH + "/" + args[2] + GMusicMain.MID_FILETYP);
                        try {
                            if (!file.exists()) {
                                boolean result = gMusicMain.getFileUtil().downloadFile(args[3], file);
                                if(result){
                                    File songsDir = new File(gMusicMain.getDataFolder(), "songs");
                                    gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download");
                                    if(!songsDir.exists() && !songsDir.mkdir()) {
                                        gMusicMain.getLogger().severe("Could not create 'songs' directory!");
                                        return true;
                                    }
                                    if(t.equalsIgnoreCase(GMusicMain.NBS_EXT)) {
                                        File songFile = new File(songsDir.getAbsolutePath() + "/" + file.getName().replaceFirst("[.][^.]+$", "") + ".gnbs");
                                        if(songFile.exists()) {
                                            gMusicMain.getLogger().severe("Could not found song file!");
                                        }
                                        boolean convertResult = gMusicMain.getNBSConverter().convertNBSFile(file);
                                        if(convertResult){
                                            GSong song = new GSong(songFile);
                                            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-convert", "%SongName%", song.getTitle());
                                            songFile.renameTo(new File(songsDir.getAbsolutePath() + "/" + song.getTitle().replaceFirst("[.][^.]+$", "") + ".gnbs"));
                                            gMusicMain.getSongService().loadSongs();
                                        }
                                        else{
                                            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-convert-error");
                                            if (file.exists()) file.delete();
                                            if (songFile.exists()) songFile.delete();
                                        }
                                        return true;
                                    }
                                    else if(t.equalsIgnoreCase(GMusicMain.MIDI_EXT)) {
                                        File songFile = new File(songsDir.getAbsolutePath() + "/" + file.getName().replaceFirst("[.][^.]+$", "") + ".gnbs");
                                        if(songFile.exists()) {
                                            gMusicMain.getLogger().severe("Could not found song file!");
                                        }
                                        boolean convertResult = gMusicMain.getMidiConverter().convertMidiFile(file);
                                        if(convertResult){
                                            GSong song = new GSong(songFile);
                                            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-convert", "%SongName%", song.getTitle());
                                            songFile.renameTo(new File(songsDir.getAbsolutePath() + "/" + song.getTitle().replaceFirst("[.][^.]+$", "") + ".gnbs"));
                                            gMusicMain.getSongService().loadSongs();
                                        }
                                        else{
                                            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-convert-error");
                                            if (file.exists()) file.delete();
                                            if (songFile.exists()) songFile.delete();
                                        }
                                        return true;
                                    }
                                }
                                else{
                                    gMusicMain.getLogger().severe("Failed Song Download!");
                                }
                            } else
                                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-name-error", "%Name%", args[2]);
                        } catch (Exception e) {
                            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-error", "%Error%", e.getMessage());
                            if (file.exists()) file.delete();
                        }
                    } else
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-folder-error", "%Folder%", t);
                } else gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-download-use-error");
                break;
            case "jukebox":
                if(!(sender instanceof Player player)) {
                    if (!gMusicMain.getPermissionService().hasPermission(sender, "AMusic.JukeBox", "AMusic.*", "*")) {
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
                        return true;
                    }
                }
                if (args.length > 1) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player != null) {
                        long am = 1;
                        if (args.length > 2) {
                            try {
                                am = Long.parseLong(args[2]);
                                if (am <= 0) throw new NumberFormatException();
                            } catch (NumberFormatException e) {
                                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-jukebox-amount-error", "%Amount%", args[2]);
                                return true;
                            }
                        }
                        player.getInventory().addItem(gMusicMain.getJukeBoxService().createJukeBoxItem());
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-jukebox", "%Player%", args[1], "%Amount%", "" + am);
                    } else
                        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-jukebox-online-error", "%Player%", args[1]);
                } else gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-jukebox-use-error");
                break;
            default:
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-gamusic-use-error");
                break;
        }
        return true;
    }

}