package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GNotePart;
import dev.geco.gmusic.object.GPlayListMode;
import dev.geco.gmusic.object.GPlayMode;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GPlayState;
import dev.geco.gmusic.object.GSong;
import dev.geco.gmusic.object.gui.GMusicGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

public class JukeBoxService {

	private final GMusicMain gMusicMain;
	private final NamespacedKey jukeBoxKey;
	// 再生スレッドからも読むため並行対応の Map
	private final Map<Block, UUID> jukeBoxBlocks = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<Block, UUID> radioJukeBoxBlocks = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<UUID, Long> emptyRangeStartTime = new java.util.concurrent.ConcurrentHashMap<>();
	// メインスレッドで数 tick ごとに計算した「範囲内のプレイヤー」（音用・パーティクル用）
	private final Map<UUID, Map<Player, Double>> listenerCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<UUID, Map<Player, Double>> particleListenerCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final Random random = new Random();

	public JukeBoxService(GMusicMain gMusicMain) {
		this.gMusicMain = gMusicMain;
		jukeBoxKey = new NamespacedKey(gMusicMain, GMusicMain.NAME + "_juke_box");
	}

	public void createTables() {
		try {
			gMusicMain.getDataService().execute("CREATE TABLE IF NOT EXISTS gmusic_juke_box (uuid TEXT, world TEXT, x INTEGER, y INTEGER, z INTEGER);");
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not create juke box database tables!", e); }
	}

	public NamespacedKey getJukeBoxKey() { return jukeBoxKey; }

	public ItemStack createJukeBoxItem() {
		ItemStack itemStack = new ItemStack(Material.JUKEBOX);
		itemStack.setAmount(1);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("Items.jukebox-title"));
		// 改行対応: ||で分割して複数行のloreにする
		String description = gMusicMain.getMessageService().getMessage("Items.jukebox-description");
		itemMeta.setLore(List.of(description.split("\\|\\|")));
		itemMeta.getPersistentDataContainer().set(jukeBoxKey, PersistentDataType.BOOLEAN, true);
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}

	public UUID getJukeBoxId(Block block) { return jukeBoxBlocks.get(block); }

	/** ジュークボックスの位置。見つからなければ null（以前は NoSuchElementException で落ちていた）。 */
	public Location getJukeBoxLocation(UUID uuid) {
		for(Map.Entry<Block, UUID> entry : jukeBoxBlocks.entrySet()) if(entry.getValue().equals(uuid)) return entry.getKey().getLocation();
		return null;
	}

	/** メインスレッドから呼ぶ。再生中のジュークボックスについて範囲内のプレイヤーを計算しておく。 */
	public void refreshListeners() {
		for(Map.Entry<Block, UUID> entry : jukeBoxBlocks.entrySet()) {
			UUID uuid = entry.getValue();
			GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
			if(playState == null || playState.isPaused()) { listenerCache.remove(uuid); particleListenerCache.remove(uuid); continue; }
			GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(uuid);
			if(playSettings == null) continue;
			Location location = entry.getKey().getLocation();
			listenerCache.put(uuid, getPlayersInRange(location, playSettings.getRange()));
			particleListenerCache.put(uuid, getPlayersInRange(location, gMusicMain.getConfigService().JUKEBOX_PARTICLE_RANGE));
		}
		listenerCache.keySet().removeIf(uuid -> !jukeBoxBlocks.containsValue(uuid));
		particleListenerCache.keySet().removeIf(uuid -> !jukeBoxBlocks.containsValue(uuid));
	}

	public void loadJukeboxes(World world) {
		jukeBoxBlocks.clear();
		radioJukeBoxBlocks.clear();
		gMusicMain.getTaskService().runDelayed(() -> {
			try {
				try(ResultSet jukeBoxData = gMusicMain.getDataService().executeAndGet("SELECT * FROM gmusic_juke_box")) {
					while(jukeBoxData.next()) {
						String worldName = jukeBoxData.getString("world");
						World jukeBoxWorld = Bukkit.getWorld(worldName);
						if(jukeBoxWorld == null || (world != null && world.equals(jukeBoxWorld))) continue;

						Location location = new Location(jukeBoxWorld, jukeBoxData.getInt("x"), jukeBoxData.getInt("y"), jukeBoxData.getInt("z"));

						UUID uuid = UUID.fromString(jukeBoxData.getString("uuid"));

						Block block = location.getBlock();
						if(block.getType() != Material.JUKEBOX) {
							gMusicMain.getDataService().execute("DELETE FROM gmusic_juke_box WHERE uuid = ?", uuid.toString());
							continue;
						}

						GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);

						jukeBoxBlocks.put(block, uuid);
						if(playSettings.getPlayListMode() == GPlayListMode.RADIO) radioJukeBoxBlocks.put(block, uuid);
						else if(playSettings.isPlayOnJoin()) {
							if(gMusicMain.getPlayService().hasPlayingSong(uuid)) resumeBoxSong(uuid);
							else {
								GSong song = playSettings.getCurrentSong() != null ? gMusicMain.getSongService().getSongById(playSettings.getCurrentSong()) : null;
								playBoxSong(uuid, song != null ? song : gMusicMain.getPlayService().getRandomSong(uuid));
							}
						}

						new GMusicGUI(uuid, GMusicGUI.MenuType.JUKEBOX);
					}
				}
			} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not load jukeboxes", e); }
		}, 0);
	}

	public void setJukebox(Block block) {
		try {
			UUID uuid = UUID.randomUUID();
			gMusicMain.getDataService().execute("INSERT INTO gmusic_juke_box (uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?)",
					uuid.toString(),
					block.getWorld().getName(),
					block.getX(),
					block.getY(),
					block.getZ()
			);
			GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
			playSettings.setRange(gMusicMain.getConfigService().JUKEBOX_RANGE);
			gMusicMain.getPlaySettingsService().savePlaySettings(uuid, playSettings);
			if(playSettings.getPlayListMode() == GPlayListMode.RADIO) radioJukeBoxBlocks.put(block, uuid);
			jukeBoxBlocks.put(block, uuid);
			new GMusicGUI(uuid, GMusicGUI.MenuType.JUKEBOX);
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not set jukebox", e); }
	}

	public void removeJukebox(Block block) {
		UUID uuid = jukeBoxBlocks.get(block);
		if(uuid == null) return;
		stopBoxSong(uuid);
		gMusicMain.getPlaySettingsService().savePlaySettings(uuid, null);
		GMusicGUI musicGUI = GMusicGUI.getMusicGUI(uuid);
		if(musicGUI != null) musicGUI.close(true);
		radioJukeBoxBlocks.remove(block);
		jukeBoxBlocks.remove(block);
		try {
			gMusicMain.getDataService().execute("DELETE FROM gmusic_juke_box WHERE uuid = ?", uuid.toString());
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not remove jukebox", e); }
	}

	public HashMap<Player, Double> getPlayersInRange(Location location, long range) {
		HashMap<Player, Double> playerRangeMap = new HashMap<>();
		if(gMusicMain.getConfigService().WORLDBLACKLIST.contains(location.getWorld().getName())) return playerRangeMap;
		try {
			for(Player player : location.getWorld().getPlayers()) {
				double distance = location.distance(player.getLocation());
				GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
				if(playSettings != null && distance <= range && !playSettings.isToggleMode()) playerRangeMap.put(player, distance);
			}
		} catch(Throwable ignored) { }
		return playerRangeMap;
	}

	public void playBoxSong(UUID uuid, GSong song) { playBoxSong(uuid, song, 0); }

	private void playBoxSong(UUID uuid, GSong song, long delay) {
		if(song == null) return;

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);

		GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
		if(playState != null) playState.getTimer().cancel();

		Location jukeBoxLocation = getJukeBoxLocation(uuid);
		if(jukeBoxLocation == null) return;

		Timer timer = new Timer("GMusic-jukebox", true);
		playState = new GPlayState(song, timer, playSettings.isReverseMode() ? song.getLength() + delay : -delay);
		gMusicMain.getPlayService().setPlayState(uuid, playState);

		playSettings.setCurrentSong(song.getId());

		if(gMusicMain.getConfigService().A_SHOW_MESSAGES) {
			for(Player player : playersNear(uuid, playSettings.getRange())) {
				GPlaySettings playerSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
				// 「他人のスピーカーをミュート」している人にはアクションバーも出さない（1.3.0 から）
				if(playerSettings != null && playerSettings.isMuteSpeakers()) continue;
				gMusicMain.getMessageService().sendActionBarMessage(
						player,
						"Messages.actionbar-playing",
						"%Song%", song.getId(),
						"%SongTitle%", song.getTitle(),
						"%Author%", song.getAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author") : song.getAuthor(),
						"%OAuthor%", song.getOriginalAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor") : song.getOriginalAuthor()
				);
			}
		}

		playBoxTimer(uuid, song, timer);
	}

	private void playBoxTimer(UUID uuid, GSong song, Timer timer) {
		GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);

		Location boxLocation = getJukeBoxLocation(uuid);
		if(boxLocation == null) return;
		// 再生開始直後にも聞こえるよう、最初の一回はここで用意する（メインスレッドから呼ばれた場合）
		if(Bukkit.isPrimaryThread()) {
			listenerCache.put(uuid, getPlayersInRange(boxLocation, gMusicMain.getPlaySettingsService().getPlaySettings(uuid).getRange()));
			particleListenerCache.put(uuid, getPlayersInRange(boxLocation, gMusicMain.getConfigService().JUKEBOX_PARTICLE_RANGE));
		}

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (gMusicMain.getPlayService().getPlayState(uuid) != playState || !jukeBoxBlocks.containsValue(uuid)) { timer.cancel(); return; }

				long z = playState.getTickPosition();

				GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(uuid);

				if (playSettings != null) {
					List<GNotePart> lnp = song.getContent().get(z);

					// 以前は 1ms ごとにワールドの全プレイヤーを 2 回走査していた。今はメインスレッドで計算済みのものを読む
					Map<Player, Double> pl = listenerCache.getOrDefault(uuid, Map.of());

					// プレイヤーが範囲内にいない場合は5分後に停止
					if (pl.isEmpty()) {
						long now = System.currentTimeMillis();
						if (!emptyRangeStartTime.containsKey(uuid)) {
							emptyRangeStartTime.put(uuid, now);
						} else {
							long emptyTime = now - emptyRangeStartTime.get(uuid);
							if (emptyTime >= gMusicMain.getConfigService().JUKEBOX_AUTO_STOP_DELAY) {
								timer.cancel();
								emptyRangeStartTime.remove(uuid);
								gMusicMain.getPlayService().removePlayState(uuid);
								updatePauseResumeBar(uuid);
								return;
							}
						}
					} else {
						// プレイヤーが範囲内にいる場合はタイマーをリセット
						emptyRangeStartTime.remove(uuid);
					}

					// パーティクル用に広い範囲のプレイヤーを取得
					Map<Player, Double> particlePlayers = particleListenerCache.getOrDefault(uuid, Map.of());

					if (lnp != null && playSettings.getVolume() > 0) {
						// ジュークボックスから常にパーティクルを出す（広い範囲のプレイヤーに表示）
						Location particleLocation = boxLocation.clone().add(0.5 + random.nextDouble() * 0.5 - 0.25, 1.2, 0.5 + random.nextDouble() * 0.5 - 0.25);
						for (Player P : particlePlayers.keySet()) {
							P.spawnParticle(Particle.NOTE, particleLocation, 0, random.nextDouble(), random.nextDouble(), random.nextDouble(), 1);
						}

						for (GNotePart np : lnp) {
							for (Player P : pl.keySet()) {
								// プレイヤーが「他人のスピーカーをミュート」している場合はスキップ
								GPlaySettings playerSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(P.getUniqueId());
								if (playerSettings == null || playerSettings.isMuteSpeakers() || !P.isOnline()) continue;

								if (np.getSound() != null) {
									Double distance = pl.get(P);
									if (distance == null) continue;
									// 距離に対して二乗で減衰させる（1.3.0 から）
									float t = (float) (distance / playSettings.getRange());
									float volume = (1.0f - t) * (1.0f - t) * playSettings.getFixedVolume() * np.getVolume();

									Location location = np.getDistance() == 0 ? P.getLocation() : gMusicMain.getSteroNoteUtil().convertToStero(P.getLocation(), np.getDistance());

									if (!gMusicMain.getConfigService().ENVIRONMENT_EFFECTS)
										P.playSound(location, np.getSound(), song.getSoundCategory(), volume, np.getPitch());
									else {
										if (gMusicMain.getEnvironmentUtil().isPlayerSwimming(P))
											P.playSound(location, np.getSound(), song.getSoundCategory(), volume > 0.4f ? volume - 0.3f : volume, np.getPitch() - 0.15f);
										else
											P.playSound(location, np.getSound(), song.getSoundCategory(), volume, np.getPitch());
									}
								} else if (np.getStopSound() != null)
									P.stopSound(np.getStopSound(), song.getSoundCategory());
							}
						}
					}

					if (z == (playSettings.isReverseMode() ? 0 : song.getLength())) {
						if (playSettings.getPlayMode() == GPlayMode.LOOP && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
							z = playSettings.isReverseMode() ? song.getLength() + gMusicMain.getConfigService().PS_TIME_UNTIL_REPEAT : -gMusicMain.getConfigService().PS_TIME_UNTIL_REPEAT;
							playState.setTickPosition(z);
						} else {
							timer.cancel();

							if (playSettings.getPlayMode() == GPlayMode.CONTINUE && playSettings.getPlayListMode() != GPlayListMode.RADIO)
								playBoxSong(uuid, gMusicMain.getPlayService().getContinueSong(uuid, song), gMusicMain.getConfigService().PS_TIME_UNTIL_CONTINUE);
							else if (playSettings.getPlayMode() == GPlayMode.SHUFFLE && playSettings.getPlayListMode() != GPlayListMode.RADIO)
								playBoxSong(uuid, gMusicMain.getPlayService().getRandomSong(uuid), gMusicMain.getConfigService().PS_TIME_UNTIL_SHUFFLE);
							else {
								gMusicMain.getPlayService().removePlayState(uuid);
								updatePauseResumeBar(uuid);
							}
						}
					} else {
						playState.setTickPosition(playSettings.isReverseMode() ? z - 1 : z + 1);
					}
				} else timer.cancel();
			}
		}, 0, 1);
	}

	/** メッセージ送信用。メインスレッドなら今の位置で数え、再生スレッドなら計算済みのものを使う。 */
	private java.util.Set<Player> playersNear(UUID uuid, long range) {
		if(!Bukkit.isPrimaryThread()) return listenerCache.getOrDefault(uuid, Map.of()).keySet();
		Location location = getJukeBoxLocation(uuid);
		return location == null ? java.util.Set.of() : getPlayersInRange(location, range).keySet();
	}

	private void updatePauseResumeBar(UUID uuid) {
		gMusicMain.getTaskService().run(() -> {
			GMusicGUI m = GMusicGUI.getMusicGUI(uuid);
			if (m != null) m.setPauseResumeBar();
		});
	}

	public GSong getNextSong(UUID uuid) {
		GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
		return playState != null ? gMusicMain.getPlayService().getContinueSong(uuid, playState.getSong()) : gMusicMain.getPlayService().getRandomSong(uuid);
	}

	public void stopBoxSong(UUID uuid) {
		GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
		if(playState == null) return;

		playState.getTimer().cancel();
		emptyRangeStartTime.remove(uuid);

		gMusicMain.getPlayService().removePlayState(uuid);

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		playSettings.setCurrentSong(null);

		if(gMusicMain.getConfigService().A_SHOW_MESSAGES) {
			for (Player player : gMusicMain.getJukeBoxService().playersNear(uuid, playSettings.getRange())) {
				GPlaySettings playerSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
				// 「他人のスピーカーをミュート」している人にはアクションバーも出さない（1.3.0 から）
				if(playerSettings != null && playerSettings.isMuteSpeakers()) continue;
				gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-stop");
			}
		}
	}

	public void pauseBoxSong(UUID uuid) {
		GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
		if(playState == null) return;

		playState.getTimer().cancel();
		playState.setPaused(true);

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		if(gMusicMain.getConfigService().A_SHOW_MESSAGES) {
			for (Player player : gMusicMain.getJukeBoxService().playersNear(uuid, playSettings.getRange())) {
				GPlaySettings playerSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
				// 「他人のスピーカーをミュート」している人にはアクションバーも出さない（1.3.0 から）
				if(playerSettings != null && playerSettings.isMuteSpeakers()) continue;
				gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-pause");
			}
		}
	}

	public void resumeBoxSong(UUID uuid) {
		GPlayState playState = gMusicMain.getPlayService().getPlayState(uuid);
		if(playState == null) return;

		playState.setTimer(new Timer("GMusic-jukebox", true));
		playState.setPaused(false);

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		if(gMusicMain.getConfigService().A_SHOW_MESSAGES) {
			for (Player player : gMusicMain.getJukeBoxService().playersNear(uuid, playSettings.getRange())) {
				GPlaySettings playerSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
				// 「他人のスピーカーをミュート」している人にはアクションバーも出さない（1.3.0 から）
				if(playerSettings != null && playerSettings.isMuteSpeakers()) continue;
				gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-resume");
			}
		}

		playBoxTimer(uuid, playState.getSong(), playState.getTimer());
	}


}