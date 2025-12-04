package dev.geco.gmusic.object.gui;

import dev.geco.gmusic.api.event.GMusicReloadEvent;
import dev.geco.gmusic.object.GPlayListMode;
import dev.geco.gmusic.object.GPlayMode;
import dev.geco.gmusic.object.GPlaySettings;
import dev.geco.gmusic.object.GPlayState;
import dev.geco.gmusic.object.GSong;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.geco.gmusic.GMusicMain;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class GMusicGUI {

	private final GMusicMain gMusicMain = GMusicMain.getInstance();
	private final HashMap<Integer, GSong> pageSongs = new HashMap<>();
	private static final HashMap<UUID, GMusicGUI> musicGUIS = new HashMap<>();
	private static final int VOLUME_STEPS = 10;
	private static final int SHIFT_VOLUME_STEPS = 1;
	private static final long RANGE_STEPS = 1;
	private static final long SHIFT_RANGE_STEPS = 10;
	private final UUID uuid;
	private final MenuType type;
	private final Inventory inventory;
	private final Listener listener;
	private boolean optionState = false;
	private int page = 1;
	private boolean searchMode = false;
	private String searchKey = null;
	private String currentCategory = null;
	private final GPlaySettings playSettings;

	public GMusicGUI(UUID uuid, MenuType type) {
		this.uuid = uuid;
		this.type = type;

		musicGUIS.put(uuid, this);

		playSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
		inventory = Bukkit.createInventory(new InventoryHolder() {

			@Override
			public @NotNull Inventory getInventory() { return inventory; }

		}, 6 * 9, gMusicMain.getMessageService().getMessage("MusicGUI.title"));

		setPage(1);

		setDefaultBar();

		listener = new Listener() {

			@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
			public void ICliE(InventoryClickEvent event) {
				if(!event.getInventory().equals(inventory)) return;
				ClickType click = event.getClick();
				if(gMusicMain.getVersionManager().executeMethod(event.getView(), "getBottomInventory").equals(event.getClickedInventory())) {
					switch(click) {
						case SHIFT_RIGHT:
						case SHIFT_LEFT:
							event.setCancelled(true);
							break;
					}
					return;
				}
				if(!gMusicMain.getVersionManager().executeMethod(event.getView(), "getTopInventory").equals(event.getClickedInventory())) return;
				event.setCancelled(true);
				ItemStack itemStack = event.getCurrentItem();
				if(itemStack == null) return;
				ItemMeta itemMeta = itemStack.getItemMeta();
				HumanEntity clicker = event.getWhoClicked();
				int slot = event.getRawSlot();
				switch(slot) {
					case 36, 37, 38, 39, 40, 41, 42, 43, 44 -> {
						// カテゴリ切り替え処理
						if(!optionState) {
							if(slot == 36) {
								// 全曲ボタン
								currentCategory = null;
								playSettings.setCurrentCategory(null);
								setPage(1);
								setDefaultBar();
							} else {
								// カテゴリボタン
								List<String> categories = gMusicMain.getSongService().getCategories();
								int categoryIndex = slot - 37;
								if(categoryIndex < categories.size()) {
									currentCategory = categories.get(categoryIndex);
									playSettings.setCurrentCategory(currentCategory);
									setPage(1);
									setDefaultBar();
								}
							}
						}
					}
					case 45 -> {
						if(!optionState) {
							GPlayState songSettings = gMusicMain.getPlayService().getPlayState(uuid);
							Player target = Bukkit.getPlayer(uuid);
							if(songSettings == null || target == null) return;
							if(songSettings.isPaused()) gMusicMain.getPlayService().resumeSong(target);
							else gMusicMain.getPlayService().pauseSong(target);
						} else {
							setDefaultBar();
							setPage(page);  // オプションから戻る際に曲リストを再表示
						}
					}
					case 46 -> {
						if(!optionState) {
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							gMusicMain.getPlayService().stopSong(target);
						} else {
							int volumn = playSettings.getVolume();
							int step = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT ? SHIFT_VOLUME_STEPS : VOLUME_STEPS;
							int newVolumn = click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_VOLUME : (click == ClickType.RIGHT ? Math.max(volumn - step, 0) : Math.min(volumn + step, 100));
							playSettings.setVolume(newVolumn);
							itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-volume", "%Volume%", "" + newVolumn));
						}
					}
					case 47 -> {
						if(!optionState) {
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							gMusicMain.getPlayService().playSong(target, gMusicMain.getPlayService().getNextSong(target));
						} else {
							// スピーカーモードの切り替えと範囲調整
							if(!gMusicMain.getConfigService().G_DISABLE_SPEAKER_MODE) {
								if(click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
									// Shiftクリック: 範囲調整（±5ブロック）
									long range = playSettings.getSpeakerRange();
									long step = 5;
									long newRange = click == ClickType.SHIFT_RIGHT ? Math.max(range - step, 5) : Math.min(range + step, gMusicMain.getConfigService().MAX_SPEAKER_RANGE);
									playSettings.setSpeakerRange(newRange);
								} else if(click == ClickType.MIDDLE) {
									// ミドルクリック: デフォルトにリセット
									playSettings.setSpeakerMode(gMusicMain.getConfigService().PS_D_SPEAKER_MODE);
									playSettings.setSpeakerRange(gMusicMain.getConfigService().SPEAKER_DEFAULT_RANGE);
								} else {
									// 左/右クリック: ON/OFF切り替え
									playSettings.setSpeakerMode(!playSettings.isSpeakerMode());
								}
								setOptionsBar(); // オプションバーを再描画
							}
						}
					}
					case 48 -> {
						if(playSettings.getPlayListMode() == GPlayListMode.RADIO) return;
						if(!optionState) {
							if(gMusicMain.getConfigService().G_DISABLE_RANDOM_SONG) return;
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							gMusicMain.getPlayService().playSong(target, gMusicMain.getPlayService().getRandomSong(uuid));
						} else {
							playSettings.setMuteSpeakers(click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_MUTE_SPEAKERS : !playSettings.isMuteSpeakers());
							itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-mute-speakers", "%MuteSpeakers%", gMusicMain.getMessageService().getMessage(playSettings.isMuteSpeakers() ? "MusicGUI.music-options-true" : "MusicGUI.music-options-false")));
						}
					}
					case 49 -> {
						if(!optionState) {
							int playListModeId = playSettings.getPlayListMode().getId();
							GPlayListMode playListMode = GPlayListMode.byId(click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_PLAYLIST_MODE : (click == ClickType.RIGHT ? (playListModeId - 1 < 0 ? GPlayListMode.values().length - 1 : playListModeId - 1) : (playListModeId + 1 > GPlayListMode.values().length - 1 ? 0 : playListModeId + 1)));
							Player target = Bukkit.getPlayer(uuid);
							playSettings.setPlayListMode(playListMode);
							if(playListMode.getId() != playListModeId && target != null) {
								setPage(1);
								gMusicMain.getPlayService().stopSong(target);
							}
							if(playListMode == GPlayListMode.RADIO) {
								gMusicMain.getRadioService().addRadioPlayer(target);
							} else {
								gMusicMain.getRadioService().removeRadioPlayer(target);
							}
							setDefaultBar();
						} else {
							if(playSettings.getPlayListMode() == GPlayListMode.RADIO) return;
							int playModeId = playSettings.getPlayMode().getId();
							GPlayMode playMode = GPlayMode.byId(click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_PLAY_MODE : (click == ClickType.RIGHT ? (playModeId - 1 < 0 ? GPlayMode.values().length - 1 : playModeId - 1) : (playModeId + 1 > GPlayMode.values().length - 1 ? 0 : playModeId + 1)));
							playSettings.setPlayMode(playMode);
							itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playMode == GPlayMode.DEFAULT ? "MusicGUI.music-options-play-mode-once" : playMode == GPlayMode.SHUFFLE ? "MusicGUI.music-options-play-mode-shuffle" : playMode == GPlayMode.LOOP ? "MusicGUI.music-options-play-mode-repeat" : "MusicGUI.music-options-play-mode-category"));
						}
					}
					case 50 -> {
						if(!optionState) {
							setOptionsBar();
						}
					}
					case 51 -> {
						if(!optionState) {
							// お気に入り追加/削除
							if(!gMusicMain.getConfigService().G_DISABLE_FAVORITES && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
								GPlayState songState = gMusicMain.getPlayService().getPlayState(uuid);
								if(songState != null && songState.getSong() != null) {
									GSong currentSong = songState.getSong();
									if(playSettings.getFavorites().contains(currentSong)) {
										playSettings.getFavorites().remove(currentSong);
									} else {
										playSettings.getFavorites().add(currentSong);
									}
									setDefaultBar();
								}
							}
						} else {
							if(type != MenuType.JUKEBOX) return;
							long range = playSettings.getRange();
							long step = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT ? SHIFT_RANGE_STEPS : RANGE_STEPS;
							long newRange = click == ClickType.MIDDLE ? gMusicMain.getConfigService().JUKEBOX_RANGE : (click == ClickType.RIGHT ? Math.max(range - step, 0) : Math.min(range + step, gMusicMain.getConfigService().MAX_JUKEBOX_RANGE));
							playSettings.setRange(newRange);
							itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-range", "%Range%", "" + newRange));
						}
					}
					case 52 -> {
						setPage(page - 1);
					}
					case 53 -> {
						setPage(page + 1);
					}
					default -> {
						if(slot < 0 || slot > 35) return;
						GSong song = pageSongs.get(slot);
						Player target = Bukkit.getPlayer(uuid);
						if(target == null || song == null) return;
						if(click == ClickType.MIDDLE) {
							if(playSettings.getFavorites().contains(song)) playSettings.getFavorites().remove(song);
							else playSettings.getFavorites().add(song);
							setPage(page);
							return;
						}
						gMusicMain.getPlayService().playSong(target, song);
					}
				}
				setPauseResumeBar();
				itemStack.setItemMeta(itemMeta);
			}

			@EventHandler (ignoreCancelled = true, priority = EventPriority.LOWEST)
			public void inventoryDragEvent(InventoryDragEvent event) {
				if(!event.getInventory().equals(inventory)) return;
				for(int slot : event.getRawSlots()) {
					if(slot >= inventory.getSize()) continue;
					event.setCancelled(true);
					return;
				}
			}

			@EventHandler
			public void inventoryCloseEvent(InventoryCloseEvent Event) { if(Event.getInventory().equals(inventory)) close(false); }

			@EventHandler (ignoreCancelled = true)
			public void gMusicReloadEvent(GMusicReloadEvent Event) { if(Event.getPlugin().equals(gMusicMain)) close(true); }

			@EventHandler
			public void pluginDisableEvent(PluginDisableEvent Event) { if(Event.getPlugin().equals(gMusicMain)) close(true); }
		};

		Bukkit.getPluginManager().registerEvents(listener, gMusicMain);
	}

	public static GMusicGUI getMusicGUI(UUID uuid) { return musicGUIS.get(uuid); }

	public void close(boolean force) {
		if(force) for(HumanEntity entity : new ArrayList<>(inventory.getViewers())) entity.closeInventory();
		if(!force && (searchMode || type == MenuType.JUKEBOX)) return;
		musicGUIS.remove(uuid);
		HandlerList.unregisterAll(listener);
	}

	private IGMusicInputGUI getInputGUIInstance(IGMusicInputGUI.InputCallback call, IGMusicInputGUI.ValidateCallback validateCall) {
		try {
			Class<?> inputGUIClass = Class.forName(gMusicMain.getVersionManager().getPackagePath() + ".object.gui.GMusicInputGUI");
			return (IGMusicInputGUI) inputGUIClass.getConstructor(IGMusicInputGUI.InputCallback.class, IGMusicInputGUI.ValidateCallback.class).newInstance(call, validateCall);
		} catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not get input gui instance", e); }
		return null;
	}

	private void clearBar() { for(int slot = 45; slot < 52; slot++) inventory.setItem(slot, null); }

	public void setDefaultBar() {
		optionState = false;

		clearBar();

		ItemStack itemStack;
		ItemMeta itemMeta;

		if(!gMusicMain.getConfigService().G_DISABLE_RANDOM_SONG && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			itemStack = new ItemStack(Material.ENDER_PEARL);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-random"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(48, itemStack);
		}

		if(!gMusicMain.getConfigService().G_DISABLE_PLAYLIST) {
			itemStack = new ItemStack(Material.NOTE_BLOCK);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playSettings.getPlayListMode() == GPlayListMode.DEFAULT ? "MusicGUI.music-playlist-mode-default" : playSettings.getPlayListMode() == GPlayListMode.FAVORITES ? "MusicGUI.music-playlist-mode-favorites" : "MusicGUI.music-playlist-mode-radio"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(49, itemStack);
		}

		if(!gMusicMain.getConfigService().G_DISABLE_OPTIONS) {
			itemStack = new ItemStack(Material.HOPPER);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(50, itemStack);
		}

		// カテゴリーボタン（スロット36-44）
		setCategoryBar();

		// お気に入り追加ボタン（スロット51、disable-favoritesがfalseの場合のみ）
		if(!gMusicMain.getConfigService().G_DISABLE_FAVORITES && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			GPlayState songState = gMusicMain.getPlayService().getPlayState(uuid);
			if(songState != null && songState.getSong() != null) {
				GSong currentSong = songState.getSong();
				itemStack = new ItemStack(Material.NETHER_STAR);
				itemMeta = itemStack.getItemMeta();
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-add-favorite"));
				if(playSettings.getFavorites().contains(currentSong)) {
					itemMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
					itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				}
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(51, itemStack);
			}
		}

		setPauseResumeBar();
	}

	public void setPauseResumeBar() {
		if(optionState || playSettings.getPlayListMode() == GPlayListMode.RADIO) return;

		GPlayState songSettings = gMusicMain.getPlayService().getPlayState(uuid);

		if(songSettings != null) {
			ItemStack itemStack = new ItemStack(Material.END_CRYSTAL);
			ItemMeta itemMeta = itemStack.getItemMeta();
			if(songSettings.isPaused()) {
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-resume"));
			} else {
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-pause"));
			}
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(45, itemStack);

			itemStack = new ItemStack(Material.BARRIER);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-stop"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(46, itemStack);

			itemStack = new ItemStack(Material.FEATHER);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-skip"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(47, itemStack);

			return;
		}

		inventory.setItem(45, null);
		inventory.setItem(46, null);
		inventory.setItem(47, null);
	}

	public void setOptionsBar() {
		optionState = true;

		clearBar();

		// オプション中は曲リストとカテゴリボタンを非表示にする
		for(int slot = 0; slot < 45; slot++) {
			inventory.setItem(slot, null);
		}

		ItemStack itemStack = new ItemStack(Material.CHEST);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-back"));
		itemStack.setItemMeta(itemMeta);
		inventory.setItem(45, itemStack);
		
		itemStack = new ItemStack(Material.MAGMA_CREAM);
		itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-volume", "%Volume%", "" + playSettings.getVolume()));
		itemStack.setItemMeta(itemMeta);
		inventory.setItem(46, itemStack);
		
		if(!gMusicMain.getConfigService().G_DISABLE_SPEAKER_MODE) {
			itemStack = new ItemStack(Material.JUKEBOX);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-speaker-mode",
				"%SpeakerMode%", gMusicMain.getMessageService().getMessage(playSettings.isSpeakerMode() ? "MusicGUI.music-options-true" : "MusicGUI.music-options-false"),
				"%Range%", "" + playSettings.getSpeakerRange()));
			List<String> lore = new ArrayList<>();
			lore.add(gMusicMain.getMessageService().toFormattedMessage("&7範囲: &b" + playSettings.getSpeakerRange() + "&7 ブロック"));
			lore.add(gMusicMain.getMessageService().toFormattedMessage("&7左/右クリック: ON/OFF"));
			lore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+左/右: 範囲±5"));
			lore.add(gMusicMain.getMessageService().toFormattedMessage("&7ミドルクリック: リセット"));
			itemMeta.setLore(lore);
			itemMeta.addItemFlags(ItemFlag.values());
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(47, itemStack);
		}

		if(playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			itemStack = new ItemStack(Material.IRON_BARS);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-mute-speakers", "%MuteSpeakers%", gMusicMain.getMessageService().getMessage(playSettings.isMuteSpeakers() ? "MusicGUI.music-options-true" : "MusicGUI.music-options-false")));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(48, itemStack);
			
			itemStack = new ItemStack(Material.BLAZE_POWDER);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playSettings.getPlayMode() == GPlayMode.DEFAULT ? "MusicGUI.music-options-play-mode-once" : playSettings.getPlayMode() == GPlayMode.SHUFFLE ? "MusicGUI.music-options-play-mode-shuffle" : playSettings.getPlayMode() == GPlayMode.LOOP ? "MusicGUI.music-options-play-mode-repeat" : "MusicGUI.music-options-play-mode-category"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(49, itemStack);
		}

		if(type == MenuType.JUKEBOX) {
			itemStack = new ItemStack(Material.REDSTONE);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-range", "%Range%", "" + playSettings.getRange()));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(51, itemStack);
		}
	}

	public void setPage(int newPage) {
		List<GSong> songs = new ArrayList<>();

		if(playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			// カテゴリが選択されている場合は最初にカテゴリで絞り込む
			if(currentCategory != null && !currentCategory.isEmpty()) {
				songs = gMusicMain.getSongService().getSongsByCategory(currentCategory);
				// FAVORITESモードの場合は、カテゴリ内のお気に入りのみ
				if(playSettings.getPlayListMode() == GPlayListMode.FAVORITES) {
					List<GSong> favoriteSongs = new ArrayList<>();
					for(GSong song : songs) {
						if(playSettings.getFavorites().contains(song)) {
							favoriteSongs.add(song);
						}
					}
					songs = favoriteSongs;
				}
			} else {
				// カテゴリが選択されていない場合はプレイリストモードに応じて取得
				songs = playSettings.getPlayListMode() == GPlayListMode.FAVORITES ? playSettings.getFavorites() : gMusicMain.getSongService().getSongs();
			}
			
			// 検索キーワードがある場合は検索でフィルタ
			if(searchKey != null && !searchKey.isEmpty()) {
				songs = gMusicMain.getSongService().filterSongsBySearch(songs, searchKey);
			}
		}

		if(newPage > getMaxPageSize(songs.size())) newPage = getMaxPageSize(songs.size());
		if(newPage < 1) newPage = 1;

		page = newPage;

		for(int slot = 0; slot < 36; slot++) inventory.setItem(slot, null);

		pageSongs.clear();

		if(!songs.isEmpty()) {
			for(int songPosition = (page - 1) * 36; songPosition < 36 * page && songPosition < songs.size(); songPosition++) {
				GSong song = songs.get(songPosition);
				ItemStack itemStack = new ItemStack(song.getDiscMaterial());
				ItemMeta itemMeta = itemStack.getItemMeta();
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(
						"MusicGUI.disc-title",
						"%Title%", song.getTitle(),
						"%Author%", song.getAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-author") : song.getAuthor(),
						"%OAuthor%", song.getOriginalAuthor().isEmpty() ? gMusicMain.getMessageService().getMessage("MusicGUI.disc-empty-oauthor") : song.getOriginalAuthor()
				));
				List<String> description = new ArrayList<>();
				for(String descriptionRow : song.getDescription()) description.add(gMusicMain.getMessageService().toFormattedMessage("&6" + descriptionRow));
				if(playSettings.getFavorites().contains(song)) description.add(gMusicMain.getMessageService().getMessage("MusicGUI.disc-favorite"));
				itemMeta.setLore(description);
				pageSongs.put(songPosition % 36, song);
				itemMeta.addItemFlags(ItemFlag.values());
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(songPosition % 36, itemStack);
			}
		}

		if(page > 1) {
			ItemStack itemStack = new ItemStack(Material.ARROW);
			ItemMeta itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.last-page"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(52, itemStack);
		} else {
			ItemStack itemStack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
			ItemMeta itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(" ");
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(52, itemStack);
		}

		if(!optionState) {
			if(page < getMaxPageSize(songs.size())) {
				ItemStack itemStack = new ItemStack(Material.ARROW);
				ItemMeta itemMeta = itemStack.getItemMeta();
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.next-page"));
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(53, itemStack);
			} else {
				ItemStack itemStack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
				ItemMeta itemMeta = itemStack.getItemMeta();
				itemMeta.setDisplayName(" ");
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(53, itemStack);
			}
		} else {
			// オプション中は次のページボタンを非表示
			inventory.setItem(53, null);
		}
	}

	private int getMaxPageSize(int songCount) { return (songCount / 36) + (songCount % 36 == 0 ? 0 : 1); }

	private void setCategoryBar() {
		// スロット36: 全曲ボタン
		ItemStack allSongs = new ItemStack(Material.BOOKSHELF);
		ItemMeta allMeta = allSongs.getItemMeta();
		allMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-category-all"));
		allSongs.setItemMeta(allMeta);
		inventory.setItem(36, allSongs);

		// スロット37-44: カテゴリボタン（最大8個、異なる色の染料）
		List<String> categories = gMusicMain.getSongService().getCategories();
		Material[] dyes = {
			Material.WHITE_DYE, Material.ORANGE_DYE, Material.MAGENTA_DYE, Material.LIGHT_BLUE_DYE,
			Material.YELLOW_DYE, Material.LIME_DYE, Material.PINK_DYE, Material.GRAY_DYE
		};

		for(int i = 0; i < Math.min(categories.size(), 8); i++) {
			String category = categories.get(i);
			ItemStack catItem = new ItemStack(dyes[i % dyes.length]);
			ItemMeta catMeta = catItem.getItemMeta();
			catMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-category", "%Category%", category));
			catItem.setItemMeta(catMeta);
			inventory.setItem(37 + i, catItem);
		}
	}

	public UUID getOwner() { return uuid; }

	public MenuType getMenuType() { return type; }

	public GPlaySettings getPlaySettings() { return playSettings; }

	public Inventory getInventory() { return inventory; }

	public enum MenuType {

		DEFAULT,
		JUKEBOX
    }

}