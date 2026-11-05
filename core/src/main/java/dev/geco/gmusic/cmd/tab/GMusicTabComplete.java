package dev.geco.gmusic.cmd.tab;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GSong;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GMusicTabComplete implements TabCompleter {

    private final GMusicMain gMusicMain;

    public GMusicTabComplete(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> complete = new ArrayList<>(), completeStarted = new ArrayList<>();
        if(!(sender instanceof Player)) return complete;

        if(args.length == 1) {
            if(gMusicMain.getPermissionService().hasPermission(sender, "Music")) {
                complete.add("help");
                complete.add("play");
                complete.add("playing");
                complete.add("random");
                complete.add("stop");
                complete.add("pause");
                complete.add("resume");
                complete.add("skip");
                complete.add("toggle");
                complete.add("loop");
                complete.add("favorite");
            }
            if(gMusicMain.getPermissionService().hasPermission(sender, "Reload")) {
                complete.add("reload");
            }
            // コマンド側と同じ権限で出す（以前は isOp() 固定で、権限を渡した人に候補が出なかった）
            if(gMusicMain.getPermissionService().hasPermission(sender, "Speaker")) {
                complete.add("speaker");
            }
            if(!args[args.length - 1].isEmpty()) {
                for(String entry : complete) if(entry.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) completeStarted.add(entry);
                complete.clear();
            }
        } else if(args.length == 2) {
            if(gMusicMain.getPermissionService().hasPermission(sender, "Music")) {
                if(args[0].equalsIgnoreCase("play")) {
                    for(GSong song : gMusicMain.getSongService().getSongs()) complete.add(song.getId());
                }
            }
            if(gMusicMain.getPermissionService().hasPermission(sender, "Speaker") && args[0].equalsIgnoreCase("speaker")) {
                complete.add("on");
                complete.add("off");
                complete.add("range");
            }
            if(gMusicMain.getPermissionService().hasPermission(sender, "Music") && (args[0].equalsIgnoreCase("loop") || args[0].equalsIgnoreCase("toggle"))) {
                complete.add("on");
                complete.add("off");
            }
            if(!args[args.length - 1].isEmpty()) {
                for(String entry : complete) if(entry.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) completeStarted.add(entry);
                complete.clear();
            }
        }
        return complete.isEmpty() ? completeStarted : complete;
    }

}