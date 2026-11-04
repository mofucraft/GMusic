package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GNotePart;
import dev.geco.gmusic.object.GPlayListMode;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GPlayState;
import dev.geco.gmusic.object.GSong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class RadioService {

	private final GMusicMain gMusicMain;
	private final Random random = new Random();
	private UUID radioUUID;
	// GUI（メイン）と再生スレッドの両方から触るので並行対応の Set（1.3.0 から）
	private final Set<Player> radioPlayers = new CopyOnWriteArraySet<>();

	public RadioService(GMusicMain gMusicMain) {
		this.gMusicMain = gMusicMain;
	}

	public void startRadio() {
		radioUUID = UUID.randomUUID();

		for(Player player : Bukkit.getOnlinePlayers()) {
			GPlaySettings playerPlaySettings = gMusicMain.getPlaySettingsService().getPlaySettings(player.getUniqueId());
			if(playerPlaySettings.getPlayListMode() == GPlayListMode.RADIO) {
				radioPlayers.add(player);
			}
		}

		playRadioSong(gMusicMain.getPlayService().getRandomSong(radioUUID), 0);
	}

	public void playRadioSong(GSong song, long delay) {
		if(song == null) return;

		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(radioUUID);

		GPlayState playState = gMusicMain.getPlayService().getPlayState(radioUUID);
		if(playState != null) playState.getTimer().cancel();

		Timer timer = new Timer("GMusic-radio", true);
		playState = new GPlayState(song, timer, playSettings.isReverseMode() ? song.getLength() + delay : -delay);
		gMusicMain.getPlayService().setPlayState(radioUUID, playState);

		playSettings.setCurrentSong(song.getId());

		if(gMusicMain.getConfigService().A_SHOW_MESSAGES) {
			for(Player radioPlayer : radioPlayers) {
				if(radioPlayer == null) continue;
				gMusicMain.getMessageService().sendActionBarMessage(
						radioPlayer,
						"Messages.actionbar-play",
						"%Song%", song.getId(),
						"%SongTitle%", song.getTitle(),
						"%Author%", song.getAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author") : song.getAuthor(),
						"%OAuthor%", song.getOriginalAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor") : song.getOriginalAuthor()
				);
			}
		}

		playTimer(song, timer);
	}

	private void playTimer(GSong song, Timer timer) {
		GPlayState playState = gMusicMain.getPlayService().getPlayState(radioUUID);
		GPlaySettings playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(radioUUID);

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					if(gMusicMain.getPlayService().getPlayState(radioUUID) != playState) { timer.cancel(); return; }

					long position = playState.getTickPosition();

					List<GNotePart> noteParts = song.getContent().get(position);

					List<Player> players = new ArrayList<>(radioPlayers);
					players.removeIf(p -> !p.isOnline());

					if(noteParts != null && playSettings.getVolume() > 0 && !players.isEmpty()) {
						for(Player player : players) {
							// 再生スレッドから DB を読まないよう、キャッシュだけを見る
							GPlaySettings playerPlaySettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
							if(playerPlaySettings == null || !playerPlaySettings.isShowingParticles()) continue;
							player.spawnParticle(Particle.NOTE, player.getEyeLocation().clone().add(random.nextDouble() - 0.5, 0.3, random.nextDouble() - 0.5), 0, random.nextDouble(), random.nextDouble(), random.nextDouble(), 1);
						}

						for(GNotePart notePart : noteParts) {
							for(Player player : players) {
								if(notePart.getSound() != null) {
									GPlaySettings playerPlaySettings = gMusicMain.getPlaySettingsService().getCachedPlaySettings(player.getUniqueId());
									if(playerPlaySettings == null) continue;
									float volume = playerPlaySettings.getFixedVolume() * notePart.getVolume();

									Location location = notePart.getDistance() == 0 ? player.getLocation() : gMusicMain.getSteroNoteUtil().convertToStero(player.getLocation(), notePart.getDistance());

									if(!gMusicMain.getConfigService().ENVIRONMENT_EFFECTS)
										player.playSound(location, notePart.getSound(), song.getSoundCategory(), volume, notePart.getPitch());
									else {
										if(gMusicMain.getEnvironmentUtil().isPlayerSwimming(player))
											player.playSound(location, notePart.getSound(), song.getSoundCategory(), volume > 0.4f ? volume - 0.3f : volume, notePart.getPitch() - 0.15f);
										else
											player.playSound(location, notePart.getSound(), song.getSoundCategory(), volume, notePart.getPitch());
									}
								} else if(notePart.getStopSound() != null) player.stopSound(notePart.getStopSound(), song.getSoundCategory());
							}
						}
					}

					if(position == (playSettings.isReverseMode() ? 0 : song.getLength())) {
						timer.cancel();
						playRadioSong(gMusicMain.getPlayService().getShuffleSong(radioUUID, song), gMusicMain.getConfigService().PS_TIME_UNTIL_SHUFFLE);
					} else {
						playState.setTickPosition(playSettings.isReverseMode() ? position - 1 : position + 1);
						// 以前は 1ms ごとに送っていた。1 秒に 1 回にする
						if(gMusicMain.getConfigService().A_SHOW_WHILE_PLAYING && PlayService.isActionBarTick(position)) {
							for(Player radioPlayer : players) {
								gMusicMain.getMessageService().sendActionBarMessage(
										radioPlayer,
										"Messages.actionbar-play",
										"%Song%", song.getId(),
										"%SongTitle%", song.getTitle(),
										"%Author%", song.getAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author") : song.getAuthor(),
										"%OAuthor%", song.getOriginalAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor") : song.getOriginalAuthor()
								);
							}
						}
					}
				} catch(Throwable ignored) { }
			}
		}, 0, 1);
	}

	public void stopRadio() {
		gMusicMain.getPlaySettingsService().removePlaySettingsCache(radioUUID);

		GPlayState playState = gMusicMain.getPlayService().getPlayState(radioUUID);
		if(playState == null) return;

		playState.getTimer().cancel();

		gMusicMain.getPlayService().removePlayState(radioUUID);
	}

	public void removeRadioPlayer(Player player) { if(player != null) radioPlayers.remove(player); }

	public void addRadioPlayer(Player player) { if(player != null) radioPlayers.add(player); }

}