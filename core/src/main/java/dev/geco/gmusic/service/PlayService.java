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

public class PlayService {

	private final GMusicMain gMusicMain;
	private final Random random = new Random();
	private final HashMap<UUID, GPlayState> playStates = new HashMap<>();

	public PlayService(GMusicMain gMusicMain) {
		this.gMusicMain = gMusicMain;
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

		Timer timer = new Timer();
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
						.getPlaySettings(nearbyPlayer.getUniqueId());

				if (nearbySettings.isMuteSpeakers() || nearbySettings.isToggleMode())
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
				long position = playState.getTickPosition();

				List<GNotePart> noteParts = song.getContent().get(position);

				if (noteParts != null && playSettings.getVolume() > 0) {
					boolean isSpeakerMode = playSettings.isSpeakerMode();
					HashMap<Player, Double> speakerListeners = isSpeakerMode
							? getSpeakerListeners(player, playSettings.getSpeakerRange())
							: new HashMap<>();

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
									double distance = speakerListeners.get(listener);
									GPlaySettings listenerSettings = gMusicMain.getPlaySettingsService()
											.getPlaySettings(listener.getUniqueId());

									float baseVolume = (float) ((distance - playSettings.getSpeakerRange())
											* playSettings.getFixedVolume() / (double) -playSettings.getSpeakerRange());
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
					} else if (playSettings.getPlayMode() == GPlayMode.CATEGORY) {
						playSong(player, getNextSongInPlaylist(uuid, song),
								gMusicMain.getConfigService().PS_TIME_UNTIL_SHUFFLE);
					} else {
							playStates.remove(uuid);
							GMusicGUI musicGUI = GMusicGUI.getMusicGUI(uuid);
							if (musicGUI != null)
								musicGUI.setPauseResumeBar();
						}
					}
					return;
				}

				playState.setTickPosition(playSettings.isReverseMode() ? position - 1 : position + 1);

				if (gMusicMain.getConfigService().A_SHOW_WHILE_PLAYING) {
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
		for (Map.Entry<UUID, GPlayState> playState : playStates.entrySet()) {
			playState.getValue().getTimer().cancel();

			GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(playState.getKey());
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

		playState.setTimer(new Timer());
		playState.setPaused(false);

		if (gMusicMain.getConfigService().A_SHOW_MESSAGES)
			gMusicMain.getMessageService().sendActionBarMessage(player, "Messages.actionbar-resume");

		startSong(player, playState.getSong(), playState.getTimer());
	}

}