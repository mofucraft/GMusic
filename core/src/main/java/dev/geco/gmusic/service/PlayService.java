package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GPlayListMode;
import dev.geco.gmusic.object.GPlayMode;
import dev.geco.gmusic.object.gui.GMusicGUI;
import dev.geco.gmusic.object.GNotePart;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GSong;
import dev.geco.gmusic.object.GPlayState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayService {

	private final GMusicMain gMusicMain;
	private final Random random = new Random();
	// 再生スレッド（Timer）とメインスレッドの両方から触るので ConcurrentHashMap
	private final Map<UUID, GPlayState> playStates = new ConcurrentHashMap<>();
	// スピーカーモードの聞き手。メインスレッドで数 tick ごとに計算し、再生スレッドは読むだけ
	private final Map<UUID, Map<Player, Double>> speakerListenerCache = new ConcurrentHashMap<>();
	// スピーカーの実際に届く範囲（権限で頭打ちした値）。メインスレッドで決め、再生スレッドは読むだけ
	private final Map<UUID, Long> speakerRangeCache = new ConcurrentHashMap<>();
	/** 聞き手を計算し直す間隔（tick）。2 tick = 100ms */
	public static final long LISTENER_REFRESH_TICKS = 2;
	/** 「再生中」アクションバーを送る間隔（ms）。以前は 1ms ごと（毎秒 1000 回）送っていた */
	public static final long ACTIONBAR_INTERVAL_MS = 1000;
	private UUID listenerRefreshTask;

	public PlayService(GMusicMain gMusicMain) {
		this.gMusicMain = gMusicMain;
	}

	/** 再生スレッドで使う「近くのプレイヤー」をメインスレッドで用意する仕組みを動かす。 */
	public void startListenerRefresh() {
		stopListenerRefresh();
		listenerRefreshTask = gMusicMain.getTaskService().runAtFixedRate(() -> {
			refreshSpeakerListeners();
			gMusicMain.getJukeBoxService().refreshListeners();
		}, 0, LISTENER_REFRESH_TICKS);
	}

	public void stopListenerRefresh() {
		if(listenerRefreshTask != null) gMusicMain.getTaskService().cancel(listenerRefreshTask);
		listenerRefreshTask = null;
		speakerListenerCache.clear();
		speakerRangeCache.clear();
	}

	/**
	 * スピーカーの届く範囲。設定の上限（max-speaker-range）を超えず、管理者（GMusic.AMusic.*）でない人は
	 * 既定の範囲（speaker-default-range）まで。GUI の上限だけでは、前に広げた値がそのまま使われていた。
	 */
	static long clampSpeakerRange(long range, long maxRange, long defaultRange, boolean admin) {
		long limit = admin ? maxRange : Math.min(defaultRange, maxRange);
		return Math.max(1, Math.min(range, limit));
	}

	private void refreshSpeakerListeners() {
		for(UUID uuid : playStates.keySet()) {
			Player player = Bukkit.getPlayer(uuid);
			if(player == null) { speakerListenerCache.remove(uuid); speakerRangeCache.remove(uuid); continue; }
			GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(uuid);
			if(playSettings == null || !playSettings.isSpeakerMode()) { speakerListenerCache.remove(uuid); speakerRangeCache.remove(uuid); continue; }
			long range = clampSpeakerRange(
					playSettings.getSpeakerRange(),
					gMusicMain.getConfigService().MAX_SPEAKER_RANGE,
					gMusicMain.getConfigService().SPEAKER_DEFAULT_RANGE,
					gMusicMain.getPermissionService().hasPermission(player, "AMusic.*"));
			speakerRangeCache.put(uuid, range);
			speakerListenerCache.put(uuid, getSpeakerListeners(player, range));
		}
		speakerListenerCache.keySet().removeIf(uuid -> !playStates.containsKey(uuid));
		speakerRangeCache.keySet().removeIf(uuid -> !playStates.containsKey(uuid));
	}

	/** 再生位置が「再生中」アクションバーを送る位置かどうか（1 秒に 1 回）。 */
	public static boolean isActionBarTick(long position) {
		return Math.floorMod(position, ACTIONBAR_INTERVAL_MS) == 0;
	}

	/** 再生スレッドから GUI を触らないよう、メインスレッドへ回す。 */
	void updatePauseResumeBar(UUID uuid) {
		gMusicMain.getTaskService().run(() -> {
			GMusicGUI musicGUI = GMusicGUI.getMusicGUI(uuid);
			if(musicGUI != null) musicGUI.setPauseResumeBar();
		});
	}

	public void playSong(Player player, GSong song) {
		playSong(player, song, 0);
	}

	private void playSong(Player player, GSong song, long delay) {
		if (song == null)
			return;

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());
		if (playSettings.getPlayListMode() == GPlayListMode.RADIO)
			return;

		GPlayState playState = getPlayState(player.getUniqueId());
		if (playState != null)
			playState.getTimer().cancel();

		Timer timer = new Timer("GMusic-play", true);
		playState = new GPlayState(song, timer, playSettings.isReverseMode() ? song.getLength() + delay : -delay);
		setPlayState(player.getUniqueId(), playState);

		playSettings.setCurrentSong(song.getId());

		if (gMusicMain.getConfigService().A_SHOW_MESSAGES) {
			gMusicMain.getMessageService().sendActionBarMessage(
					player,
					"Messages.actionbar-play",
					"%Song%", song.getId(),
					"%SongTitle%", song.getTitle(),
					"%Author%",
					song.getAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author")
							: song.getAuthor(),
					"%OAuthor%",
					song.getOriginalAuthor().isEmpty()
							? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor")
							: song.getOriginalAuthor());
		}

		startSong(player, song, timer);
	}

	public GSong getRandomSong(UUID uuid) {
		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		List<GSong> songs = playSettings.getPlayListMode() == GPlayListMode.FAVORITES ? playSettings.getFavorites()
				: gMusicMain.getSongService().getSongs();
		return !songs.isEmpty() ? songs.get(random.nextInt(songs.size())) : null;
	}

	public GSong getContinueSong(UUID uuid, GSong song) {
		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		List<GSong> songs = playSettings.getPlayListMode() == GPlayListMode.FAVORITES ? playSettings.getFavorites() : gMusicMain.getSongService().getSongs();
		return !songs.isEmpty() ? songs.indexOf(song) + 1 == songs.size() ? songs.get(0) : songs.get(songs.indexOf(song) + 1) : null;
	}

	public GSong getShuffleSong(UUID uuid, GSong song) {
		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		List<GSong> songs;
		if (playSettings.getPlayListMode() == GPlayListMode.FAVORITES) {
			songs = playSettings.getFavorites();
		} else {
			String category = playSettings.getCurrentCategory();
			if (category != null) {
				songs = gMusicMain.getSongService().getSongsByCategory(category);
			} else {
				songs = gMusicMain.getSongService().getSongs();
			}
		}

		if (songs.isEmpty())
			return null;
		if (songs.size() == 1)
			return songs.get(0);

		GSong nextSong;
		do {
			nextSong = songs.get(random.nextInt(songs.size()));
		} while (nextSong.equals(song));

		return nextSong;
	}

	public GSong getNextSongInPlaylist(UUID uuid, GSong currentSong) {
		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		List<GSong> songs;
		if (playSettings.getPlayListMode() == GPlayListMode.FAVORITES) {
			songs = playSettings.getFavorites();
		} else {
			String category = playSettings.getCurrentCategory();
			if (category != null) {
				songs = gMusicMain.getSongService().getSongsByCategory(category);
			} else {
				songs = gMusicMain.getSongService().getSongs();
			}
		}

		if (songs.isEmpty())
			return null;
		if (songs.size() == 1)
			return songs.get(0);

		int currentIndex = songs.indexOf(currentSong);
		if (currentIndex == -1)
			return songs.get(0);

		int nextIndex = (currentIndex + 1) % songs.size();
		return songs.get(nextIndex);
	}

	private HashMap<Player, Double> getSpeakerListeners(Player sourcePlayer, long range) {
		HashMap<Player, Double> listeners = new HashMap<>();
		Location sourceLocation = sourcePlayer.getLocation();

		if (gMusicMain.getConfigService().WORLDBLACKLIST.contains(sourceLocation.getWorld().getName()))
			return listeners;

		try {
			for (Player nearbyPlayer : sourceLocation.getWorld().getPlayers()) {
				if (nearbyPlayer.getUniqueId().equals(sourcePlayer.getUniqueId()))
					continue;

				GPlaySettings nearbySettings = gMusicMain.getPlaySettingsService()
						.getCachedPlaySettings(nearbyPlayer.getUniqueId());

				if (nearbySettings == null || nearbySettings.isMuteSpeakers() || nearbySettings.isToggleMode())
					continue;

				double distance = sourceLocation.distance(nearbyPlayer.getLocation());
				if (distance <= range) {
					listeners.put(nearbyPlayer, distance);
				}
			}
		} catch (Throwable ignored) {
		}

		return listeners;
	}

	private void startSong(Player player, GSong song, Timer timer) {
		UUID uuid = player.getUniqueId();
		GPlayState playState = getPlayState(uuid);
		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				// 抜けたプレイヤーのタイマーが残らないようにする（スレッドと状態の取り残し防止）
				if (!player.isOnline() || getPlayState(uuid) != playState) {
					timer.cancel();
					playStates.remove(uuid, playState);
					return;
				}

				long position = playState.getTickPosition();

				List<GNotePart> noteParts = song.getContent().get(position);

				if (noteParts != null && playSettings.getVolume() > 0) {
					boolean isSpeakerMode = playSettings.isSpeakerMode();
					// 以前はここ（1ms ごと）でワールドの全プレイヤーを走査していた。今はメインスレッドで計算済みのものを読む
					Map<Player, Double> speakerListeners = isSpeakerMode
							? speakerListenerCache.getOrDefault(uuid, Map.of())
							: Map.of();

					// Play to source player with full volume
					// スピーカーモードON時は必ずパーティクルを表示
					if (playSettings.isSpeakerMode())
						player.spawnParticle(Particle.NOTE,
								player.getEyeLocation().add(random.nextDouble() - 0.5, 0.3, random.nextDouble() - 0.5),
								0, random.nextDouble(), random.nextDouble(), random.nextDouble(), 1);

					for (GNotePart notePart : noteParts) {
						if (notePart.getSound() != null) {
							float volume = playSettings.getFixedVolume() * notePart.getVolume();

							Location location = notePart.getDistance() == 0 ? player.getLocation()
									: gMusicMain.getSteroNoteUtil().convertToStero(player.getLocation(),
											notePart.getDistance());

							if (!gMusicMain.getConfigService().ENVIRONMENT_EFFECTS)
								player.playSound(location, notePart.getSound(), song.getSoundCategory(), volume,
										notePart.getPitch());
							else {
								if (gMusicMain.getEnvironmentUtil().isPlayerSwimming(player))
									player.playSound(location, notePart.getSound(), song.getSoundCategory(),
											volume > 0.4f ? volume - 0.3f : volume, notePart.getPitch() - 0.15f);
								else
									player.playSound(location, notePart.getSound(), song.getSoundCategory(), volume,
											notePart.getPitch());
							}
						} else if (notePart.getStopSound() != null)
							player.stopSound(notePart.getStopSound(), song.getSoundCategory());
					}

					// Broadcast to speaker listeners if speaker mode is enabled
					if (isSpeakerMode && !speakerListeners.isEmpty()) {
						Location particleLocation = player.getEyeLocation().add(random.nextDouble() - 0.5, 0.3,
								random.nextDouble() - 0.5);

						for (Player listener : speakerListeners.keySet()) {
							listener.spawnParticle(Particle.NOTE, particleLocation, 0, random.nextDouble(),
									random.nextDouble(), random.nextDouble(), 1);
						}

						for (GNotePart notePart : noteParts) {
							if (notePart.getSound() != null) {
								for (Player listener : speakerListeners.keySet()) {
									Double cachedDistance = speakerListeners.get(listener);
									GPlaySettings listenerSettings = gMusicMain.getPlaySettingsService()
											.getCachedPlaySettings(listener.getUniqueId());
									if (cachedDistance == null || listenerSettings == null || !listener.isOnline())
										continue;
									double distance = cachedDistance;

									// 均一音量半径＋二乗減衰
									long uniformRadius = playSettings.getUniformRadius();
									long maxRange = speakerRangeCache.getOrDefault(uuid, playSettings.getSpeakerRange());
									float baseVolume;
									if (distance <= uniformRadius) {
										baseVolume = playSettings.getFixedVolume();
									} else {
										float normalizedDistance = (float) (distance - uniformRadius) / (maxRange - uniformRadius);
										baseVolume = playSettings.getFixedVolume() / (1.0f + normalizedDistance * normalizedDistance);
									}
									float listenerVolume = baseVolume * notePart.getVolume()
											* listenerSettings.getVolume() / 100f;

									Location location = notePart.getDistance() == 0 ? listener.getLocation()
											: gMusicMain.getSteroNoteUtil().convertToStero(listener.getLocation(),
													notePart.getDistance());

									if (!gMusicMain.getConfigService().ENVIRONMENT_EFFECTS)
										listener.playSound(location, notePart.getSound(), song.getSoundCategory(),
												listenerVolume, notePart.getPitch());
									else {
										if (gMusicMain.getEnvironmentUtil().isPlayerSwimming(listener))
											listener.playSound(location, notePart.getSound(), song.getSoundCategory(),
													listenerVolume > 0.4f ? listenerVolume - 0.3f : listenerVolume,
													notePart.getPitch() - 0.15f);
										else
											listener.playSound(location, notePart.getSound(), song.getSoundCategory(),
													listenerVolume, notePart.getPitch());
									}
								}
							} else if (notePart.getStopSound() != null) {
								for (Player listener : speakerListeners.keySet()) {
									listener.stopSound(notePart.getStopSound(), song.getSoundCategory());
								}
							}
						}
					}
				}

				if (position == (playSettings.isReverseMode() ? 0 : song.getLength())) {
					if (playSettings.getPlayMode() == GPlayMode.LOOP) {
						position = playSettings.isReverseMode()
								? song.getLength() + gMusicMain.getConfigService().PS_TIME_UNTIL_REPEAT
								: -gMusicMain.getConfigService().PS_TIME_UNTIL_REPEAT;
						playState.setTickPosition(position);
					} else {
						timer.cancel();

					if (playSettings.getPlayMode() == GPlayMode.SHUFFLE) {
						playSong(player, getShuffleSong(uuid, song),
								gMusicMain.getConfigService().PS_TIME_UNTIL_SHUFFLE);
					} else if (playSettings.getPlayMode() == GPlayMode.CONTINUE) {
						playSong(player, getContinueSong(uuid, song),
								gMusicMain.getConfigService().PS_TIME_UNTIL_CONTINUE);
					} else if (playSettings.getPlayMode() == GPlayMode.CATEGORY) {
						playSong(player, getNextSongInPlaylist(uuid, song),
								gMusicMain.getConfigService().PS_TIME_UNTIL_CONTINUE);
					} else {
							playStates.remove(uuid, playState);
							updatePauseResumeBar(uuid);
						}
					}
					return;
				}

				playState.setTickPosition(playSettings.isReverseMode() ? position - 1 : position + 1);

				if (gMusicMain.getConfigService().A_SHOW_WHILE_PLAYING && isActionBarTick(position)) {
					gMusicMain.getMessageService().sendActionBarMessage(
							player,
							"Messages.actionbar-playing",
							"%Song%", song.getId(),
							"%SongTitle%", song.getTitle(),
							"%Author%",
							song.getAuthor().isEmpty()
									? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author")
									: song.getAuthor(),
							"%OAuthor%",
							song.getOriginalAuthor().isEmpty()
									? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor")
									: song.getOriginalAuthor());
				}
			}
		}, 0, 1);

	}

	public GPlayState getPlayState(UUID uuid) {
		return playStates.get(uuid);
	}

	public void removePlayState(UUID uuid) {
		playStates.remove(uuid);
	}

	public void setPlayState(UUID uuid, GPlayState playState) {
		playStates.put(uuid, playState);
	}

	public boolean hasPlayingSong(UUID uuid) {
		return getPlayState(uuid) != null;
	}

	public boolean hasPausedSong(UUID uuid) {
		GPlayState playState = getPlayState(uuid);
		return playState != null && playState.isPaused();
	}

	public GSong getPlayingSong(UUID uuid) {
		GPlayState playState = getPlayState(uuid);
		return playState != null ? playState.getSong() : null;
	}

	public GSong getNextSong(Player player) {
		GPlayState playState = getPlayState(player.getUniqueId());
		return playState != null ? getShuffleSong(player.getUniqueId(), playState.getSong())
				: getRandomSong(player.getUniqueId());
	}

	public void stopSongs() {
		for (Map.Entry<UUID, GPlayState> playState : new java.util.ArrayList<>(playStates.entrySet())) {
			playState.getValue().getTimer().cancel();

			GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(playState.getKey());
			if (playSettings != null)
				playSettings.setCurrentSong(null);

			Player player = Bukkit.getPlayer(playState.getKey());
			if (player != null && gMusicMain.getConfigService().A_SHOW_MESSAGES)
				gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-stop");
		}

		playStates.clear();
	}

	public void stopSong(Player player) {
		GPlayState playState = getPlayState(player.getUniqueId());
		if (playState == null)
			return;

		playState.getTimer().cancel();

		playStates.remove(player.getUniqueId());

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());
		playSettings.setCurrentSong(null);

		if (gMusicMain.getConfigService().A_SHOW_MESSAGES)
			gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-stop");
	}

	public void pauseSong(Player player) {
		GPlayState playState = getPlayState(player.getUniqueId());
		if (playState == null)
			return;

		playState.getTimer().cancel();
		playState.setPaused(true);

		if (gMusicMain.getConfigService().A_SHOW_MESSAGES)
			gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-pause");
	}

	public void resumeSong(Player player) {
		GPlayState playState = getPlayState(player.getUniqueId());
		if (playState == null)
			return;

		playState.setTimer(new Timer("GMusic-play", true));
		playState.setPaused(false);

		if (gMusicMain.getConfigService().A_SHOW_MESSAGES)
			gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-resume");

		startSong(player, playState.getSong(), playState.getTimer());
	}

}