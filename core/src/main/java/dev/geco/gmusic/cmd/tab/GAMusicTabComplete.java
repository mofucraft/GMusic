package dev.geco.gmusic.cmd.tab;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.cmd.GAMusicCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GAMusicTabComplete implements TabCompleter {

    private final GMusicMain gMusicMain;

    public GAMusicTabComplete(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> complete = new ArrayList<>(), completeStarted = new ArrayList<>();
        if(!(sender instanceof Player)) return complete;

        if(args.length == 1) {
            if(gMusicMain.getPermissionService().hasPermission(sender, "AMusic.Download")) {
                complete.add("download");
            }
        } else if(args[0].equalsIgnoreCase("download") && gMusicMain.getPermissionService().hasPermission(sender, "AMusic.Download")) {
            switch(args.length) {
                case 2 -> {
                    complete.add(GAMusicCommand.NBS_TYPE);
                    complete.add(GAMusicCommand.MIDI_TYPE);
                }
                case 3 -> complete.add("<FileName>");
                case 4 -> complete.add("<URL>");
            }
        }

        if(!args[args.length - 1].isEmpty()) {
            for(String entry : complete) if(entry.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) completeStarted.add(entry);
            complete.clear();
        }

        return complete.isEmpty() ? completeStarted : complete;
    }

}