package dev.geco.gmusic.cmd;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GSong;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GAMusicCommand implements CommandExecutor {

    public static final String NBS_TYPE = "nbs";
    public static final String MIDI_TYPE = "midi";
    private static final String NBS_FILETYP = ".nbs";
    private static final String MIDI_FILETYP = ".mid";
    private static final String GNBS_FILETYP = ".gnbs";

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

        if(args.length > 0 && args[0].equalsIgnoreCase("download")) {
            if(!gMusicMain.getPermissionService().hasPermission(sender, "AMusic.Download")) {
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-permission-error");
                return true;
            }
            downloadSong(sender, args);
            return true;
        }

        if(!(sender instanceof Player player)) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-sender-error");
            return true;
        }

        player.getInventory().addItem(gMusicMain.getJukeBoxService().createJukeBoxItem());

        return true;
    }

    private void downloadSong(CommandSender sender, String[] args) {
        if(args.length < 4) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-use-error");
            return;
        }

        String type = args[1].toLowerCase();
        if(!type.equals(NBS_TYPE) && !type.equals(MIDI_TYPE)) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-folder-error", "%Folder%", args[1]);
            return;
        }

        File typeDir = new File(gMusicMain.getDataFolder(), type);
        if(!typeDir.exists() && !typeDir.mkdirs()) {
            gMusicMain.getLogger().severe("Could not create '" + type + "' directory!");
            return;
        }

        File songsDir = new File(gMusicMain.getDataFolder(), "songs");
        if(!songsDir.exists() && !songsDir.mkdirs()) {
            gMusicMain.getLogger().severe("Could not create 'songs' directory!");
            return;
        }

        File downloadFile = new File(typeDir, args[2] + (type.equals(NBS_TYPE) ? NBS_FILETYP : MIDI_FILETYP));
        File songFile = new File(songsDir, args[2] + GNBS_FILETYP);
        if(downloadFile.exists() || songFile.exists()) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-name-error", "%Name%", args[2]);
            return;
        }

        try {
            if(!gMusicMain.getFileUtil().downloadFile(args[3], downloadFile)) {
                gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-error", "%Error%", args[3]);
                downloadFile.delete();
                return;
            }
        } catch(Throwable e) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-error", "%Error%", e.getMessage());
            downloadFile.delete();
            return;
        }

        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download");

        boolean converted = type.equals(NBS_TYPE) ? gMusicMain.getNBSConverter().convertNBSFile(downloadFile) : gMusicMain.getMidiConverter().convertMidiFile(downloadFile);
        if(!converted || !songFile.exists()) {
            gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-convert-error");
            downloadFile.delete();
            songFile.delete();
            return;
        }

        gMusicMain.getSongService().loadSongs();

        gMusicMain.getMessageService().sendMessage(sender, "Messages.command-agmusic-download-convert", "%SongName%", new GSong(songFile).getTitle());
    }

}
