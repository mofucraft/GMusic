package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GSong;
import dev.geco.gmusic.object.GPlayListMode;
import dev.geco.gmusic.object.GPlayMode;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlaySettingsService {

	private final GMusicMain gMusicMain;
	// 再生スレッドからも読まれるので ConcurrentHashMap（HashMap の同時アクセスは壊れることがある）
	private final Map<UUID, GPlaySettings> playSettingsCache = new ConcurrentHashMap<>();

	public PlaySettingsService(GMusicMain gMusicMain) {
		this.gMusicMain = gMusicMain;
	}

	public void createTables() {
		try {
			gMusicMain.getDataService().execute("CREATE TABLE IF NOT EXISTS gmusic_play_settings (uuid TEXT, playListMode INTEGER, volume INTEGER, playOnJoin INTEGER, playMode INTEGER, showParticles INTEGER, reverseMode INTEGER, toggleMode INTEGER, range INTEGER, currentSong TEXT, speakerMode INTEGER DEFAULT 0, speakerRange INTEGER DEFAULT 20, muteSpeakers INTEGER DEFAULT 0, currentCategory TEXT DEFAULT NULL, uniformRadius INTEGER DEFAULT 0);");
			gMusicMain.getDataService().execute("CREATE TABLE IF NOT EXISTS gmusic_play_settings_favorites (uuid TEXT, songId TEXT);");

			// Add new columns to existing tables if they don't exist
			try {
				gMusicMain.getDataService().execute("ALTER TABLE gmusic_play_settings ADD COLUMN speakerMode INTEGER DEFAULT 0;");
			} catch(Throwable ignored) {}
			try {
				gMusicMain.getDataService().execute("ALTER TABLE gmusic_play_settings ADD COLUMN speakerRange INTEGER DEFAULT 20;");
			} catch(Throwable ignored) {}
			try {
				gMusicMain.getDataService().execute("ALTER TABLE gmusic_play_settings ADD COLUMN muteSpeakers INTEGER DEFAULT 0;");
			} catch(Throwable ignored) {}
			try {
				gMusicMain.getDataService().execute("ALTER TABLE gmusic_play_settings ADD COLUMN currentCategory TEXT DEFAULT NULL;");
			} catch(Throwable ignored) {}
			try {
				gMusicMain.getDataService().execute("ALTER TABLE gmusic_play_settings ADD COLUMN uniformRadius INTEGER DEFAULT 0;");
			} catch(Throwable ignored) {}
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not create play settings database tables!", e); }
	}

	/** キャッシュにあるときだけ返す（DB は読まない）。再生スレッドからはこちらを使う。 */
	public GPlaySettings getCachedPlaySettings(UUID uuid) { return playSettingsCache.get(uuid); }

	public GPlaySettings getPlaySettings(UUID uuid) {
		GPlaySettings cached = playSettingsCache.get(uuid);
		if(cached != null) return cached;

		List<GSong> favorites = new ArrayList<>();
		List<String> unresolvedFavorites = new ArrayList<>();

		GPlaySettings playSettings = null;

		try {
			try(ResultSet playSettingsFavoritesData = gMusicMain.getDataService().executeAndGet("SELECT * FROM gmusic_play_settings_favorites WHERE uuid = ?", uuid.toString())) {
				while(playSettingsFavoritesData.next()) {
					String songId = playSettingsFavoritesData.getString("songId");
					GSong song = gMusicMain.getSongService().getSongById(songId);
					if(song != null) { if(!favorites.contains(song)) favorites.add(song); }
					// 曲が見つからなくても ID は捨てない（以前は次の保存で消えていた）
					else if(songId != null) unresolvedFavorites.add(songId);
				}
			}

			try(ResultSet playSettingsData = gMusicMain.getDataService().executeAndGet("SELECT * FROM gmusic_play_settings WHERE uuid = ?", uuid.toString())) {
				if(playSettingsData.next()) {
					playSettings = new GPlaySettings(
							uuid,
							GPlayListMode.byId(playSettingsData.getInt("playListMode")),
							playSettingsData.getInt("volume"),
							playSettingsData.getBoolean("playOnJoin"),
							GPlayMode.byId(playSettingsData.getInt("playMode")),
							playSettingsData.getBoolean("showParticles"),
							playSettingsData.getBoolean("reverseMode"),
							playSettingsData.getBoolean("toggleMode"),
							playSettingsData.getLong("range"),
							playSettingsData.getString("currentSong"),
							favorites,
							playSettingsData.getBoolean("speakerMode"),
							playSettingsData.getLong("speakerRange"),
							playSettingsData.getBoolean("muteSpeakers"),
							playSettingsData.getString("currentCategory")
					);
					try { playSettings.setUniformRadius(playSettingsData.getLong("uniformRadius")); } catch(Throwable ignored) { }
				}
			}
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not load play settings", e); }

		if(playSettings == null) playSettings = generateDefaultPlaySettings(uuid);

		playSettings.setFavorites(favorites);
		playSettings.setUnresolvedFavoriteIds(unresolvedFavorites);

		GPlaySettings previous = playSettingsCache.putIfAbsent(uuid, playSettings);
		return previous != null ? previous : playSettings;
	}

	private GPlaySettings generateDefaultPlaySettings(UUID uuid) {
		return new GPlaySettings(
				uuid,
				GPlayListMode.byId(gMusicMain.getConfigService().PS_D_PLAYLIST_MODE),
				gMusicMain.getConfigService().PS_D_VOLUME,
				gMusicMain.getConfigService().R_PLAY_ON_JOIN,
				GPlayMode.byId(gMusicMain.getConfigService().PS_D_PLAY_MODE),
				gMusicMain.getConfigService().PS_D_PARTICLES,
				false,  // 逆再生を無効化
				false,
				0,
				null,
				new ArrayList<>(),
				gMusicMain.getConfigService().PS_D_SPEAKER_MODE,
				gMusicMain.getConfigService().SPEAKER_DEFAULT_RANGE,
				gMusicMain.getConfigService().PS_D_MUTE_SPEAKERS,
				null
		);
	}

	public void savePlaySettings(UUID uuid, GPlaySettings playSettings) {
		try {
			// 削除と入れ直しをひとつのトランザクションにする。途中で失敗しても前の内容が残る
			gMusicMain.getDataService().transaction(() -> {
				gMusicMain.getDataService().execute("DELETE FROM gmusic_play_settings WHERE uuid = ?", uuid.toString());
				gMusicMain.getDataService().execute("DELETE FROM gmusic_play_settings_favorites WHERE uuid = ?", uuid.toString());

				if(playSettings == null) return;

				gMusicMain.getDataService().execute("INSERT INTO gmusic_play_settings (uuid, playListMode, volume, playOnJoin, playMode, showParticles, reverseMode, toggleMode, range, currentSong, speakerMode, speakerRange, muteSpeakers, currentCategory, uniformRadius) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
						uuid.toString(),
						playSettings.getPlayListMode().getId(),
						playSettings.getVolume(),
						playSettings.isPlayOnJoin(),
						playSettings.getPlayMode().getId(),
						playSettings.isShowingParticles(),
						playSettings.isReverseMode(),
						playSettings.isToggleMode(),
						playSettings.getRange(),
						playSettings.getCurrentSong(),
						playSettings.isSpeakerMode(),
						playSettings.getSpeakerRange(),
						playSettings.isMuteSpeakers(),
						playSettings.getCurrentCategory(),
						playSettings.getUniformRadius()
				);

				for(String favoriteId : playSettings.getFavoriteIdsForSave()) {
					gMusicMain.getDataService().execute("INSERT INTO gmusic_play_settings_favorites (uuid, songId) VALUES (?, ?)", uuid.toString(), favoriteId);
				}
			});

			if(playSettings == null) playSettingsCache.remove(uuid);
			else playSettingsCache.putIfAbsent(uuid, playSettings);
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not save play settings", e); }
	}

	/** GUI 操作などからの保存。DB 書き込みをメインスレッドの外へ出す。 */
	public void savePlaySettingsAsync(UUID uuid, GPlaySettings playSettings) {
		gMusicMain.getTaskService().run(() -> savePlaySettings(uuid, playSettings), false);
	}

	/**
	 * 退出時の保存。DB への書き込みはメインスレッドの外で行い（MSPT の山を避ける）、
	 * 書き終わってからキャッシュを外す。書き込み中に再参加しても同じ設定オブジェクトが使われる。
	 */
	public void savePlaySettingsAndUncacheAsync(UUID uuid) {
		GPlaySettings playSettings = playSettingsCache.get(uuid);
		if(playSettings == null) return;
		boolean save = gMusicMain.getConfigService().PS_SAVE_ON_QUIT;
		gMusicMain.getTaskService().run(() -> {
			if(save) savePlaySettings(uuid, playSettings);
			if(org.bukkit.Bukkit.getPlayer(uuid) == null) playSettingsCache.remove(uuid, playSettings);
		}, false);
	}

	/** 曲を読み直した後、キャッシュ中のお気に入りを新しい曲オブジェクトに付け替える。 */
	public void refreshFavorites() {
		for(GPlaySettings playSettings : playSettingsCache.values()) {
			List<GSong> resolved = new ArrayList<>();
			List<String> unresolved = new ArrayList<>();
			for(String id : playSettings.getFavoriteIdsForSave()) {
				GSong song = gMusicMain.getSongService().getSongById(id);
				if(song != null) resolved.add(song);
				else unresolved.add(id);
			}
			playSettings.setFavorites(resolved);
			playSettings.setUnresolvedFavoriteIds(unresolved);
		}
	}

	public void savePlaySettings() {
		for(Map.Entry<UUID, GPlaySettings> playSettings : new ArrayList<>(playSettingsCache.entrySet())) {
			savePlaySettings(playSettings.getKey(), playSettings.getValue());
		}

		playSettingsCache.clear();
	}

	public void removePlaySettingsCache(UUID uuid) { playSettingsCache.remove(uuid); }

}