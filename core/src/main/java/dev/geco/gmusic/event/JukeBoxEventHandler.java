package dev.geco.gmusic.event;

import dev.geco.gmusic.object.GPlayState;
import dev.geco.gmusic.object.gui.GMusicGUI;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

import dev.geco.gmusic.GMusicMain;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class JukeBoxEventHandler implements Listener {

	private final GMusicMain gMusicMain;

	public JukeBoxEventHandler(GMusicMain gMusicMain) {
		this.gMusicMain = gMusicMain;
	}

	@EventHandler (priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerInteractEvent(PlayerInteractEvent event) {
		Player player = event.getPlayer();

		if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Block block = event.getClickedBlock();
		if(block == null || block.getType() != Material.JUKEBOX) return;

		UUID uuid = gMusicMain.getJukeBoxService().getJukeBoxId(block);
		if(uuid == null) return;

		event.setCancelled(true);

		if(!gMusicMain.getPermissionService().hasPermission(player, "AMusic.UseJukeBox", "AMusic.*")) return;

		if(!player.isSneaking()) player.openInventory(GMusicGUI.getMusicGUI(uuid).getInventory());
	}

	@EventHandler (ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void blockPlaceEvent(BlockPlaceEvent event) {
		Block block = event.getBlock();
		if(block.getType() != Material.JUKEBOX) return;
		ItemStack itemStack = event.getItemInHand();
		if(!itemStack.getItemMeta().getPersistentDataContainer().has(gMusicMain.getJukeBoxService().getJukeBoxKey())) return;
		gMusicMain.getJukeBoxService().setJukebox(block);
	}

	@EventHandler (ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void blockBreakEvent(BlockBreakEvent event) {
		handleBlockBreak(event.getBlock());
	}

	@EventHandler (ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void blockExplodeEvent(BlockExplodeEvent event) {
		for(Block block : event.blockList()) handleBlockBreak(block);
	}

	@EventHandler (ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void entityExplodeEvent(EntityExplodeEvent event) {
		for(Block block : event.blockList()) handleBlockBreak(block);
	}

	private void handleBlockBreak(Block block) {
		if(block.getType() != Material.JUKEBOX) return;
		if(gMusicMain.getJukeBoxService().getJukeBoxId(block) == null) return;
		gMusicMain.getJukeBoxService().removeJukebox(block);
		block.setType(Material.AIR);
		block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0, 0.5), gMusicMain.getJukeBoxService().createJukeBoxItem());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void blockRedstoneEvent(BlockRedstoneEvent event) {
		Block block = event.getBlock();
		if(block.getType() != Material.JUKEBOX) return;

		UUID uuid = gMusicMain.getJukeBoxService().getJukeBoxId(block);
		if(uuid == null) return;

		// レッドストーン信号がOFFからONに変わった時のみ反応
		if(event.getOldCurrent() == 0 && event.getNewCurrent() > 0) {
			GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
			if(playState == null) {
				// 再生中でなければランダム再生開始
				gMusicMain.getJukeBoxService().playBoxSong(uuid, gMusicMain.getPlayService().getRandomSong(uuid));
			} else if(playState.isPaused()) {
				// 一時停止中なら再開
				gMusicMain.getJukeBoxService().resumeBoxSong(uuid);
			} else {
				// 再生中なら一時停止
				gMusicMain.getJukeBoxService().pauseBoxSong(uuid);
			}
			GMusicGUI gui = GMusicGUI.getMusicGUI(uuid);
			if(gui != null) gui.setPauseResumeBar();
		}
	}

}