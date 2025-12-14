package dev.geco.gmusic.object;

import java.util.List;
import java.util.UUID;

public class GPlaySettings {

	private final UUID uuid;
	private GPlayListMode playlistMode;
	private int volume;
	private boolean playOnJoin;
	private GPlayMode playMode;
	private boolean showParticles;
	private boolean reverseMode;
	private boolean toggleMode;
	private long range;
	private String currentSong;
	private List<GSong> favorites;
	private boolean speakerMode;
	private long speakerRange;
	private long uniformRadius;
	private boolean muteSpeakers;
	private String currentCategory;

	public GPlaySettings(
			UUID uuid,
			GPlayListMode playlistMode,
			int volume,
			boolean playOnJoin,
			GPlayMode playMode,
			boolean showParticles,
			boolean reverseMode,
			boolean toggleMode,
			long range,
			String currentSong,
			List<GSong> favorites,
			boolean speakerMode,
			long speakerRange,
			boolean muteSpeakers,
			String currentCategory
	) {
		this.uuid = uuid;
		this.playlistMode = playlistMode;
		this.volume = volume;
		this.playOnJoin = playOnJoin;
		this.playMode = playMode;
		this.showParticles = showParticles;
		this.reverseMode = reverseMode;
		this.toggleMode = toggleMode;
		this.range = range;
		this.currentSong = currentSong;
		this.favorites = favorites;
		this.speakerMode = speakerMode;
		this.speakerRange = speakerRange;
		this.uniformRadius = 0;
		this.muteSpeakers = muteSpeakers;
		this.currentCategory = currentCategory;
	}

	public UUID getUUID() { return uuid; }

	public GPlayListMode getPlayListMode() { return playlistMode; }

	public void setPlayListMode(GPlayListMode playlistMode) { this.playlistMode = playlistMode; }

	public int getVolume() { return volume; }

	public float getFixedVolume() { return (float) (volume * 2) / 100; }

	public void setVolume(int volume) { this.volume = volume; }

	public boolean isPlayOnJoin() { return playOnJoin; }

	public void setPlayOnJoin(boolean playOnJoin) { this.playOnJoin = playOnJoin; }

	public GPlayMode getPlayMode() { return playMode; }

	public void setPlayMode(GPlayMode playMode) { this.playMode = playMode; }

	public boolean isShowingParticles() { return showParticles; }

	public void setShowParticles(boolean showParticles) { this.showParticles = showParticles; }

	public boolean isReverseMode() { return reverseMode; }

	public void setReverseMode(boolean reverseMode) { this.reverseMode = reverseMode; }

	public boolean isToggleMode() { return toggleMode; }

	public void setToggleMode(boolean toggleMode) { this.toggleMode = toggleMode; }

	public long getRange() { return range; }

	public void setRange(long range) { this.range = range; }

	public String getCurrentSong() { return currentSong; }

	public void setCurrentSong(String currentSong) { this.currentSong = currentSong; }

	public List<GSong> getFavorites() { return favorites; }

	public void setFavorites(List<GSong> favorites) { this.favorites = favorites; }

	public void addFavoriteSong(GSong song) { favorites.add(song); }

	public void removeFavoriteSong(GSong song) { favorites.remove(song); }

	public boolean isSpeakerMode() { return speakerMode; }

	public void setSpeakerMode(boolean speakerMode) { this.speakerMode = speakerMode; }

	public long getSpeakerRange() { return speakerRange; }

	public void setSpeakerRange(long speakerRange) { this.speakerRange = speakerRange; }

	public long getUniformRadius() { return uniformRadius; }

	public void setUniformRadius(long uniformRadius) { this.uniformRadius = uniformRadius; }

	public boolean isMuteSpeakers() { return muteSpeakers; }

	public void setMuteSpeakers(boolean muteSpeakers) { this.muteSpeakers = muteSpeakers; }

	public String getCurrentCategory() { return currentCategory; }

	public void setCurrentCategory(String currentCategory) { this.currentCategory = currentCategory; }

}