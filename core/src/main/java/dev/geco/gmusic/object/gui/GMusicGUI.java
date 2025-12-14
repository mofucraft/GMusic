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
import org.bukkit.event.inventory.InventoryOpenEvent;
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
		// ジュークボックスとプレイヤーGUIで異なるタイトルを使用
		String guiTitle = type == MenuType.JUKEBOX
			? gMusicMain.getMessageService().getMessage("MusicGUI.jukebox-title")
			: gMusicMain.getMessageService().getMessage("MusicGUI.title");
		inventory = Bukkit.createInventory(new InventoryHolder() {

			@Override
			public @NotNull Inventory getInventory() { return inventory; }

		}, 6 * 9, guiTitle);

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
					// カテゴリエリア: slots 27-39 (2行、カテゴリ用)
					case 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39 -> {
						// カテゴリ切り替え処理
						if(slot == 27) {
							// 全曲ボタン
							currentCategory = null;
							playSettings.setCurrentCategory(null);
							setPage(1);
							setDefaultBar();
						} else {
							// カテゴリボタン (slot 28-39 = 12カテゴリ)
							List<String> categories = gMusicMain.getSongService().getCategories();
							int categoryIndex;
							if(slot <= 35) {
								categoryIndex = slot - 28; // 28-35 → 0-7
							} else {
								categoryIndex = 8 + (slot - 36); // 36-39 → 8-11
							}
							if(categoryIndex >= 0 && categoryIndex < categories.size()) {
								currentCategory = categories.get(categoryIndex);
								playSettings.setCurrentCategory(currentCategory);
								setPage(1);
								setDefaultBar();
							}
						}
					}
					// 設定エリア: slots 40-42 (カテゴリバー2列目右側)
					case 40 -> {
						// 音量設定
						int volumn = playSettings.getVolume();
						int step = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT ? SHIFT_VOLUME_STEPS : VOLUME_STEPS;
						boolean isDecrease = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
						int newVolumn = click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_VOLUME : (isDecrease ? Math.max(volumn - step, 0) : Math.min(volumn + step, 100));
						playSettings.setVolume(newVolumn);
						setDefaultBar();
					}
					case 41 -> {
						// 可聴範囲設定（ジュークボックスのみ）/ 再生モード（プレイヤーGUI）
						if(type == MenuType.JUKEBOX) {
							long range = playSettings.getRange();
							long step = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT ? SHIFT_RANGE_STEPS : RANGE_STEPS;
							long maxRange = gMusicMain.getConfigService().MAX_JUKEBOX_RANGE;
							boolean isDecrease = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
							long newRange = click == ClickType.MIDDLE ? gMusicMain.getConfigService().JUKEBOX_RANGE : (isDecrease ? Math.max(range - step, 1) : Math.min(range + step, maxRange));
							playSettings.setRange(newRange);
							setDefaultBar();
						} else {
							// プレイヤーGUIでは再生モード設定
							if(playSettings.getPlayListMode() == GPlayListMode.RADIO) return;
							int playModeId = playSettings.getPlayMode().getId();
							GPlayMode playMode = GPlayMode.byId(click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_PLAY_MODE : (click == ClickType.RIGHT ? (playModeId - 1 < 0 ? GPlayMode.values().length - 1 : playModeId - 1) : (playModeId + 1 > GPlayMode.values().length - 1 ? 0 : playModeId + 1)));
							playSettings.setPlayMode(playMode);
							setDefaultBar();
						}
					}
					case 42 -> {
						// 再生モード設定（ジュークボックスのみ）
						if(type == MenuType.JUKEBOX) {
							if(playSettings.getPlayListMode() == GPlayListMode.RADIO) return;
							int playModeId = playSettings.getPlayMode().getId();
							GPlayMode playMode = GPlayMode.byId(click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_PLAY_MODE : (click == ClickType.RIGHT ? (playModeId - 1 < 0 ? GPlayMode.values().length - 1 : playModeId - 1) : (playModeId + 1 > GPlayMode.values().length - 1 ? 0 : playModeId + 1)));
							playSettings.setPlayMode(playMode);
							setDefaultBar();
						}
					}
					// ページナビゲーション: slots 43-44
					case 43 -> {
						setPage(page - 1);
					}
					case 44 -> {
						setPage(page + 1);
					}
					// コントロールバー: slots 45-51
					case 45 -> {
						// 一時停止/再開
						GPlayState songSettings = gMusicMain.getPlayService().getPlayState(uuid);
						if(songSettings == null) return;
						if(type == MenuType.JUKEBOX) {
							if(songSettings.isPaused()) gMusicMain.getJukeBoxService().resumeBoxSong(uuid);
							else gMusicMain.getJukeBoxService().pauseBoxSong(uuid);
						} else {
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							if(songSettings.isPaused()) gMusicMain.getPlayService().resumeSong(target);
							else gMusicMain.getPlayService().pauseSong(target);
						}
					}
					case 46 -> {
						// 停止
						if(type == MenuType.JUKEBOX) {
							gMusicMain.getJukeBoxService().stopBoxSong(uuid);
						} else {
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							gMusicMain.getPlayService().stopSong(target);
						}
					}
					case 47 -> {
						// スキップ
						if(type == MenuType.JUKEBOX) {
							gMusicMain.getJukeBoxService().playBoxSong(uuid, gMusicMain.getJukeBoxService().getNextSong(uuid));
						} else {
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							gMusicMain.getPlayService().playSong(target, gMusicMain.getPlayService().getNextSong(target));
						}
					}
					case 48 -> {
						// ランダム再生
						if(playSettings.getPlayListMode() == GPlayListMode.RADIO) return;
						if(gMusicMain.getConfigService().G_DISABLE_RANDOM_SONG) return;
						if(type == MenuType.JUKEBOX) {
							gMusicMain.getJukeBoxService().playBoxSong(uuid, gMusicMain.getPlayService().getRandomSong(uuid));
						} else {
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							gMusicMain.getPlayService().playSong(target, gMusicMain.getPlayService().getRandomSong(uuid));
						}
					}
					case 49 -> {
						// プレイリストモード切り替え
						int playListModeId = playSettings.getPlayListMode().getId();
						GPlayListMode playListMode = GPlayListMode.byId(click == ClickType.MIDDLE ? gMusicMain.getConfigService().PS_D_PLAYLIST_MODE : (click == ClickType.RIGHT ? (playListModeId - 1 < 0 ? GPlayListMode.values().length - 1 : playListModeId - 1) : (playListModeId + 1 > GPlayListMode.values().length - 1 ? 0 : playListModeId + 1)));
						playSettings.setPlayListMode(playListMode);
						// モードが変わった場合は曲リストを更新
						if(playListMode.getId() != playListModeId) {
							setPage(1);
							// プレイヤーGUIの場合のみ曲を停止
							if(type != MenuType.JUKEBOX) {
								Player target = Bukkit.getPlayer(uuid);
								if(target != null) gMusicMain.getPlayService().stopSong(target);
							}
						}
						// ラジオモードの処理（プレイヤーGUIのみ）
						if(type != MenuType.JUKEBOX) {
							Player target = Bukkit.getPlayer(uuid);
							if(playListMode == GPlayListMode.RADIO) {
								gMusicMain.getRadioService().addRadioPlayer(target);
							} else {
								gMusicMain.getRadioService().removeRadioPlayer(target);
							}
						}
						setDefaultBar();
					}
					case 50 -> {
						// 検索ボタン（オプションボタンは削除）
						// 現在は未使用
					}
					case 51 -> {
						// お気に入り追加/削除
						if(!gMusicMain.getConfigService().G_DISABLE_FAVORITES && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
							GPlayState songState = gMusicMain.getPlayService().getPlayState(uuid);
							if(songState != null && songState.getSong() != null) {
								GSong currentSong = songState.getSong();
								boolean wasAdded = false;
								if(playSettings.getFavorites().contains(currentSong)) {
									playSettings.getFavorites().remove(currentSong);
								} else {
									playSettings.getFavorites().add(currentSong);
									wasAdded = true;
								}
								// 効果音を再生（クリックしたプレイヤーに対して）
								if(clicker instanceof Player clickPlayer) {
									if(wasAdded) {
										clickPlayer.playSound(clickPlayer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
									} else {
										clickPlayer.playSound(clickPlayer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
									}
								}
								// データベースに保存
								gMusicMain.getPlaySettingsService().savePlaySettings(uuid, playSettings);
								// 曲リストを更新（お気に入りハートの表示更新のため）
								setPage(page);
								setDefaultBar();
							}
						}
					}
					// スピーカーモード/ミュート: slots 52-53 (右下)
					case 52 -> {
						// スピーカーモード切り替え / 範囲調整
						if(!gMusicMain.getConfigService().G_DISABLE_SPEAKER_MODE) {
							if(click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
								// Shift+クリック: 範囲調整
								long range = playSettings.getSpeakerRange();
								long maxRange = gMusicMain.getConfigService().MAX_SPEAKER_RANGE;
								boolean isDecrease = click == ClickType.SHIFT_RIGHT;
								long newRange = isDecrease ? Math.max(range - SHIFT_RANGE_STEPS, 1) : Math.min(range + SHIFT_RANGE_STEPS, maxRange);
								playSettings.setSpeakerRange(newRange);
							} else {
								// 通常クリック: ON/OFF切り替え
								playSettings.setSpeakerMode(!playSettings.isSpeakerMode());
							}
							setDefaultBar();
						}
					}
					case 53 -> {
						// 他人のスピーカーミュート切り替え
						playSettings.setMuteSpeakers(!playSettings.isMuteSpeakers());
						setDefaultBar();
					}
					// 曲リストエリア: slots 0-26
					default -> {
						if(slot < 0 || slot > 26) return;
						GSong song = pageSongs.get(slot);
						if(song == null) return;

						if(type == MenuType.JUKEBOX) {
							// ジュークボックスの場合
							if(click == ClickType.MIDDLE) {
								// お気に入りトグル
								boolean wasAdded = false;
								if(playSettings.getFavorites().contains(song)) {
									playSettings.getFavorites().remove(song);
								} else {
									playSettings.getFavorites().add(song);
									wasAdded = true;
								}
								// 効果音を再生
								if(clicker instanceof Player clickPlayer) {
									if(wasAdded) {
										clickPlayer.playSound(clickPlayer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
									} else {
										clickPlayer.playSound(clickPlayer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
									}
								}
								gMusicMain.getPlaySettingsService().savePlaySettings(uuid, playSettings);
								setPage(page);
								return;
							}
							// ジュークボックスで曲を再生
							gMusicMain.getJukeBoxService().playBoxSong(uuid, song);
						} else {
							// 通常のプレイヤーGUI
							Player target = Bukkit.getPlayer(uuid);
							if(target == null) return;
							if(click == ClickType.MIDDLE) {
								boolean wasAdded = false;
								if(playSettings.getFavorites().contains(song)) {
									playSettings.getFavorites().remove(song);
								} else {
									playSettings.getFavorites().add(song);
									wasAdded = true;
								}
								// 効果音を再生
								if(wasAdded) {
									target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
								} else {
									target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
								}
								// データベースに保存
								gMusicMain.getPlaySettingsService().savePlaySettings(uuid, playSettings);
								setPage(page);
								return;
							}
							gMusicMain.getPlayService().playSong(target, song);
						}
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
			public void inventoryOpenEvent(InventoryOpenEvent event) {
				if(!event.getInventory().equals(inventory)) return;
				// GUIを開いた時に表示を更新（お気に入りボタン、音量表示など）
				setPage(page);
				setDefaultBar();
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

	private void clearBar() {
		// コントロールバー（45-51）をクリア、スピーカー/ミュート（52-53）は維持
		for(int slot = 45; slot <= 51; slot++) inventory.setItem(slot, null);
	}

	public void setDefaultBar() {
		optionState = false;

		clearBar();

		ItemStack itemStack;
		ItemMeta itemMeta;

		// 音量設定（slot 40）
		itemStack = new ItemStack(Material.MAGMA_CREAM);
		itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-volume", "%Volume%", "" + playSettings.getVolume()));
		List<String> volumeLore = new ArrayList<>();
		volumeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7左クリック: +10"));
		volumeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7右クリック: -10"));
		volumeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+クリック: ±1"));
		itemMeta.setLore(volumeLore);
		itemStack.setItemMeta(itemMeta);
		inventory.setItem(40, itemStack);

		// 可聴範囲設定（slot 41）- ジュークボックスのみ / 再生モード - プレイヤーGUI
		if(type == MenuType.JUKEBOX) {
			itemStack = new ItemStack(Material.REDSTONE);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-range", "%Range%", "" + playSettings.getRange()));
			List<String> rangeLore = new ArrayList<>();
			rangeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7左クリック: +1"));
			rangeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7右クリック: -1"));
			rangeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+クリック: ±10"));
			itemMeta.setLore(rangeLore);
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(41, itemStack);

			// 再生モード設定（slot 42）- ジュークボックス
			if(playSettings.getPlayListMode() != GPlayListMode.RADIO) {
				itemStack = new ItemStack(Material.BLAZE_POWDER);
				itemMeta = itemStack.getItemMeta();
				String playModeKey = switch(playSettings.getPlayMode()) {
					case DEFAULT -> "MusicGUI.music-options-play-mode-once";
					case SHUFFLE -> "MusicGUI.music-options-play-mode-shuffle";
					case LOOP -> "MusicGUI.music-options-play-mode-repeat";
					case CONTINUE -> "MusicGUI.music-options-play-mode-continue";
					case CATEGORY -> "MusicGUI.music-options-play-mode-category";
				};
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playModeKey));
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(42, itemStack);
			}
		} else {
			// プレイヤーGUIでは再生モード設定のみ（slot 41）
			if(playSettings.getPlayListMode() != GPlayListMode.RADIO) {
				itemStack = new ItemStack(Material.BLAZE_POWDER);
				itemMeta = itemStack.getItemMeta();
				String playModeKey = switch(playSettings.getPlayMode()) {
					case DEFAULT -> "MusicGUI.music-options-play-mode-once";
					case SHUFFLE -> "MusicGUI.music-options-play-mode-shuffle";
					case LOOP -> "MusicGUI.music-options-play-mode-repeat";
					case CONTINUE -> "MusicGUI.music-options-play-mode-continue";
					case CATEGORY -> "MusicGUI.music-options-play-mode-category";
				};
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playModeKey));
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(41, itemStack);
			}
		}

		// ランダムボタン（slot 48）
		if(!gMusicMain.getConfigService().G_DISABLE_RANDOM_SONG && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			itemStack = new ItemStack(Material.ENDER_PEARL);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-random"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(48, itemStack);
		}

		// プレイリストモードボタン（slot 49）
		if(!gMusicMain.getConfigService().G_DISABLE_PLAYLIST) {
			itemStack = new ItemStack(Material.NOTE_BLOCK);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playSettings.getPlayListMode() == GPlayListMode.DEFAULT ? "MusicGUI.music-playlist-mode-default" : playSettings.getPlayListMode() == GPlayListMode.FAVORITES ? "MusicGUI.music-playlist-mode-favorites" : "MusicGUI.music-playlist-mode-radio"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(49, itemStack);
		}

		// slot 50 は空（オプションボタンは削除）

		// お気に入り追加ボタン（slot 51）
		if(!gMusicMain.getConfigService().G_DISABLE_FAVORITES && playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			GPlayState songState = gMusicMain.getPlayService().getPlayState(uuid);
			GSong currentSong = songState != null ? songState.getSong() : null;
			itemStack = new ItemStack(Material.NETHER_STAR);
			itemMeta = itemStack.getItemMeta();
			// 明示的にUUIDから設定を取得してお気に入り状態を確認（ジュークボックスの場合はジュークボックスの設定を使用）
			GPlaySettings currentSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);
			boolean isFavorite = currentSong != null && currentSettings.getFavorites().contains(currentSong);
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(isFavorite ? "MusicGUI.music-remove-favorite" : "MusicGUI.music-add-favorite"));
			if(isFavorite) {
				itemMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
				itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(51, itemStack);
		}

		// スピーカーモードボタン（slot 52、右下）- ジュークボックスGUIでは非表示
		if(type != MenuType.JUKEBOX && !gMusicMain.getConfigService().G_DISABLE_SPEAKER_MODE) {
			itemStack = new ItemStack(Material.JUKEBOX);
			itemMeta = itemStack.getItemMeta();
			// 範囲説明付きのスピーカーモード表示
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-speaker-mode",
				"%SpeakerMode%", gMusicMain.getMessageService().getMessage(playSettings.isSpeakerMode() ? "MusicGUI.music-options-true" : "MusicGUI.music-options-false"),
				"%Range%", "" + playSettings.getSpeakerRange()));
			List<String> speakerLore = new ArrayList<>();
			speakerLore.add(gMusicMain.getMessageService().toFormattedMessage("&7クリック: ON/OFF"));
			speakerLore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+左: 範囲+10"));
			speakerLore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+右: 範囲-10"));
			itemMeta.setLore(speakerLore);
			if(playSettings.isSpeakerMode()) {
				itemMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
				itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(52, itemStack);
		}

		// ミュートボタン（slot 53、右下）- ジュークボックスGUIでは非表示
		if(type != MenuType.JUKEBOX) {
			itemStack = new ItemStack(Material.IRON_BARS);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-mute-speakers",
				"%MuteSpeakers%", gMusicMain.getMessageService().getMessage(playSettings.isMuteSpeakers() ? "MusicGUI.music-options-true" : "MusicGUI.music-options-false")));
			if(playSettings.isMuteSpeakers()) {
				itemMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
				itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(53, itemStack);
		}

		// カテゴリーボタン（スロット27-42）
		setCategoryBar();

		setPauseResumeBar();
	}

	public void setPauseResumeBar() {
		if(playSettings.getPlayListMode() == GPlayListMode.RADIO) return;

		GPlayState songSettings = gMusicMain.getPlayService().getPlayState(uuid);

		if(songSettings != null) {
			// 再生中: コントロールボタンをslot 45-47に配置
			// 45: 一時停止/再開
			ItemStack itemStack = new ItemStack(Material.END_CRYSTAL);
			ItemMeta itemMeta = itemStack.getItemMeta();
			if(songSettings.isPaused()) {
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-resume"));
			} else {
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-pause"));
			}
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(45, itemStack);

			// 46: 停止
			itemStack = new ItemStack(Material.BARRIER);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-stop"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(46, itemStack);

			// 47: スキップ
			itemStack = new ItemStack(Material.FEATHER);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-skip"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(47, itemStack);

			return;
		}

		// 曲が再生されていない場合はコントロールボタンをクリア
		inventory.setItem(45, null);
		inventory.setItem(46, null);
		inventory.setItem(47, null);
	}

	public void setOptionsBar() {
		optionState = true;

		clearBar();

		// オプション中は曲リスト、カテゴリ、ページナビゲーションを非表示にする
		for(int slot = 0; slot < 45; slot++) {
			inventory.setItem(slot, null);
		}

		ItemStack itemStack = new ItemStack(Material.CHEST);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-back"));
		itemStack.setItemMeta(itemMeta);
		inventory.setItem(45, itemStack);

		// 音量設定（slot 46）
		itemStack = new ItemStack(Material.MAGMA_CREAM);
		itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-volume", "%Volume%", "" + playSettings.getVolume()));
		List<String> volumeLore = new ArrayList<>();
		volumeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7左クリック: +10"));
		volumeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7右クリック: -10"));
		volumeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+クリック: ±1"));
		itemMeta.setLore(volumeLore);
		itemStack.setItemMeta(itemMeta);
		inventory.setItem(46, itemStack);

		// 再生モード設定（slot 47）- スピーカーモード/ミュートは重複なので削除
		if(playSettings.getPlayListMode() != GPlayListMode.RADIO) {
			itemStack = new ItemStack(Material.BLAZE_POWDER);
			itemMeta = itemStack.getItemMeta();
			String playModeKey = switch(playSettings.getPlayMode()) {
				case DEFAULT -> "MusicGUI.music-options-play-mode-once";
				case SHUFFLE -> "MusicGUI.music-options-play-mode-shuffle";
				case LOOP -> "MusicGUI.music-options-play-mode-repeat";
				case CONTINUE -> "MusicGUI.music-options-play-mode-continue";
				case CATEGORY -> "MusicGUI.music-options-play-mode-category";
			};
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage(playModeKey));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(47, itemStack);
		}

		// 可聴範囲設定（slot 48）- ジュークボックスのみ
		if(type == MenuType.JUKEBOX) {
			itemStack = new ItemStack(Material.REDSTONE);
			itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-options-range", "%Range%", "" + playSettings.getRange()));
			List<String> rangeLore = new ArrayList<>();
			rangeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7左クリック: +1"));
			rangeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7右クリック: -1"));
			rangeLore.add(gMusicMain.getMessageService().toFormattedMessage("&7Shift+クリック: ±10"));
			itemMeta.setLore(rangeLore);
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(48, itemStack);
		}
	}

	public void setPage(int newPage) {
		List<GSong> songs = new ArrayList<>();

		// 常に正しいUUIDから設定を取得（キャッシュから取得されるが、UUIDベースで確実に正しい設定を参照）
		GPlaySettings currentSettings = gMusicMain.getPlaySettingsService().getPlaySettings(uuid);

		if(currentSettings.getPlayListMode() != GPlayListMode.RADIO) {
			// カテゴリが選択されている場合は最初にカテゴリで絞り込む
			if(currentCategory != null && !currentCategory.isEmpty()) {
				songs = gMusicMain.getSongService().getSongsByCategory(currentCategory);
				// FAVORITESモードの場合は、カテゴリ内のお気に入りのみ
				if(currentSettings.getPlayListMode() == GPlayListMode.FAVORITES) {
					List<GSong> favoriteSongs = new ArrayList<>();
					for(GSong song : songs) {
						if(currentSettings.getFavorites().contains(song)) {
							favoriteSongs.add(song);
						}
					}
					songs = favoriteSongs;
				}
			} else {
				// カテゴリが選択されていない場合はプレイリストモードに応じて取得
				songs = currentSettings.getPlayListMode() == GPlayListMode.FAVORITES ? currentSettings.getFavorites() : gMusicMain.getSongService().getSongs();
			}

			// 検索キーワードがある場合は検索でフィルタ
			if(searchKey != null && !searchKey.isEmpty()) {
				songs = gMusicMain.getSongService().filterSongsBySearch(songs, searchKey);
			}
		}

		if(newPage > getMaxPageSize(songs.size())) newPage = getMaxPageSize(songs.size());
		if(newPage < 1) newPage = 1;

		page = newPage;

		// 曲リストエリア: slots 0-26 (27スロット、3行)
		for(int slot = 0; slot < 27; slot++) inventory.setItem(slot, null);

		pageSongs.clear();

		if(!songs.isEmpty()) {
			for(int songPosition = (page - 1) * 27; songPosition < 27 * page && songPosition < songs.size(); songPosition++) {
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
				if(currentSettings.getFavorites().contains(song)) description.add(gMusicMain.getMessageService().getMessage("MusicGUI.disc-favorite"));
				itemMeta.setLore(description);
				pageSongs.put(songPosition % 27, song);
				itemMeta.addItemFlags(ItemFlag.values());
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(songPosition % 27, itemStack);
			}
		}

		// ページナビゲーション: slots 43-44
		if(page > 1) {
			ItemStack itemStack = new ItemStack(Material.ARROW);
			ItemMeta itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.last-page"));
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(43, itemStack);
		} else {
			ItemStack itemStack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
			ItemMeta itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(" ");
			itemStack.setItemMeta(itemMeta);
			inventory.setItem(43, itemStack);
		}

		if(!optionState) {
			if(page < getMaxPageSize(songs.size())) {
				ItemStack itemStack = new ItemStack(Material.ARROW);
				ItemMeta itemMeta = itemStack.getItemMeta();
				itemMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.next-page"));
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(44, itemStack);
			} else {
				ItemStack itemStack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
				ItemMeta itemMeta = itemStack.getItemMeta();
				itemMeta.setDisplayName(" ");
				itemStack.setItemMeta(itemMeta);
				inventory.setItem(44, itemStack);
			}
		} else {
			// オプション中は次のページボタンを非表示
			inventory.setItem(44, null);
		}
	}

	private int getMaxPageSize(int songCount) { return (songCount / 27) + (songCount % 27 == 0 ? 0 : 1); }

	private void setCategoryBar() {
		// カテゴリバーをクリア（2段: slots 27-35, 36-39）※40-42は設定ボタン用
		for(int i = 27; i <= 39; i++) {
			inventory.setItem(i, null);
		}

		// お気に入りモード時はカテゴリを非表示にする
		if(playSettings.getPlayListMode() != GPlayListMode.FAVORITES) {
			// スロット27: 全曲ボタン
			ItemStack allSongs = new ItemStack(Material.BOOKSHELF);
			ItemMeta allMeta = allSongs.getItemMeta();
			allMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-category-all"));
			allSongs.setItemMeta(allMeta);
			inventory.setItem(27, allSongs);

			// スロット28-39: カテゴリボタン（2段で最大12個）
			List<String> categories = gMusicMain.getSongService().getCategories();
			Material[] dyes = {
				Material.WHITE_DYE, Material.ORANGE_DYE, Material.MAGENTA_DYE, Material.LIGHT_BLUE_DYE,
				Material.YELLOW_DYE, Material.LIME_DYE, Material.PINK_DYE, Material.GRAY_DYE,
				Material.CYAN_DYE, Material.PURPLE_DYE, Material.BLUE_DYE, Material.BROWN_DYE
			};

			for(int i = 0; i < Math.min(categories.size(), 12); i++) {
				String category = categories.get(i);
				ItemStack catItem = new ItemStack(dyes[i % dyes.length]);
				ItemMeta catMeta = catItem.getItemMeta();
				String displayCategory = "uncategorized".equals(category)
					? gMusicMain.getMessageService().getMessage("MusicGUI.music-category-uncategorized")
					: category;
				catMeta.setDisplayName(gMusicMain.getMessageService().getMessage("MusicGUI.music-category", "%Category%", displayCategory));
				catItem.setItemMeta(catMeta);
				// 28-35 (8個) + 36-39 (4個) = 12個
				int slot = 28 + i;
				if(slot > 35) slot = 36 + (i - 8); // 2段目
				inventory.setItem(slot, catItem);
			}
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