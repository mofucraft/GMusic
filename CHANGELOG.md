# GMusic 変更履歴

## 2025年12月4日の変更点

### 修正内容

#### 1. GUIレイアウトの最適化（54スロット対応）
- **問題**: 元々63スロットで設計されていたが、Bukkit/Paperの制限により54スロット（6行×9列）が上限
- **解決策**: インベントリサイズを54スロットに変更し、レイアウトを再設計
  - スロット0-35: 曲表示エリア（36曲）
  - スロット36-44: カテゴリボタン（最大8カテゴリ）
  - スロット45-50: 制御ボタン（一時停止、停止、スキップ、ランダム、プレイリスト、オプション）
  - スロット51: お気に入りボタン
  - スロット52: 前のページボタン
  - スロット53: 次のページボタン

#### 2. カテゴリシステムの実装
- **機能**: 曲をカテゴリ別に分類・表示
- **実装内容**:
  - 8種類の色付き染料でカテゴリを視覚的に区別（WHITE_DYE, ORANGE_DYE, MAGENTA_DYE, LIGHT_BLUE_DYE, YELLOW_DYE, LIME_DYE, PINK_DYE, GRAY_DYE）
  - スロット36に「全曲」ボタン（BOOKSHELF）を配置
  - スロット37-44に各カテゴリボタンを配置
  - `currentCategory`フィールドで選択中のカテゴリを追跡
  - `GPlaySettings`に`currentCategory`フィールドを追加
  - データベーステーブル`gmusic_play_settings`に`currentCategory`カラムを追加

#### 3. カテゴリフィルタリングの修正
- **問題**: カテゴリを選択しても曲が表示されない
- **原因**: `setPage()`メソッドでフィルタリングの順序が誤っていた
- **解決策**: 
  - カテゴリが選択されている場合は最初に`getSongsByCategory()`で絞り込み
  - その後、FAVORITESモードの場合はカテゴリ内のお気に入り曲のみを表示
  - 検索キーワードがある場合は最後に検索フィルタを適用

#### 4. 戻るボタンの表示修正
- **問題**: 戻るボタンが表示されない
- **原因**: `setPage()`メソッドで`optionState ? 52 : 53`という誤った条件分岐
- **解決策**: ページが2ページ以上ある場合は常にスロット52に戻るボタンを配置

#### 5. お気に入り機能の追加
- **機能**: 好きな曲をお気に入りに追加・削除
- **実装内容**:
  - スロット51にNETHER_STARアイコンで表示
  - お気に入りに登録されている場合はエンチャント効果を追加
  - クリックで追加/削除を切り替え
  - 曲リスト内の曲をミドルクリックでもお気に入り追加/削除可能

#### 6. スピーカーモード機能の追加
- **機能**: 自分が再生している音楽を周囲のプレイヤーにも聞かせる
- **実装内容**:
  - オプション画面に「スピーカーモード」ボタンを追加（JUKEBOX）
  - デフォルトの範囲: 20ブロック（設定可能）
  - 最大範囲: 100ブロック
  - `config.yml`に`speaker-mode: false`と`speaker-default-range: 20`を追加
  - `GPlaySettings`に`speakerMode`と`speakerRange`フィールドを追加

#### 7. スピーカーミュート機能の追加
- **機能**: 他のプレイヤーのスピーカーモードをミュート
- **実装内容**:
  - オプション画面スロット48に「スピーカーミュート」ボタンを追加（IRON_BARS）
  - デフォルトでオフ
  - `config.yml`に`mute-speakers: false`を追加
  - `GPlaySettings`に`muteSpeakers`フィールドを追加

#### 8. 不要な機能の削除
- **削除項目**:
  - 検索ボタン（スロット51から削除）
  - Welcome Musicオプション（オプション画面から削除）
  - Particlesオプション（削除）
  - Reverseオプション（削除）

#### 9. プレイモードの変更
- **変更内容**: `GPlayMode.PLAYLIST`を`GPlayMode.CATEGORY`に変更
- **影響範囲**:
  - `GPlayMode.java`: PLAYLIST(3) → CATEGORY(3)
  - `PlayService.java`: 関連するロジックを更新
  - `GMusicGUI.java`: カテゴリモードに対応

#### 10. 日本語ローカライゼーション
- **実装内容**:
  - `config.yml`のデフォルト言語を`ja_jp`に設定
  - `resources/lang/ja_jp.yml`に完全な日本語翻訳を追加
  - 参照元: `ja_jp-mofu.yml`
- **追加された翻訳**:
  - `music-category-all`: "全曲"
  - `music-options-play-mode-category`: "カテゴリ"
  - `music-options-speaker-mode`: "スピーカーモード"
  - `music-options-mute-speakers`: "スピーカーミュート"
  - その他、全メッセージの日本語化

#### 11. 設定の自動マージ機能
- **機能**: 既存の`config.yml`を保持しながら新しい設定項目のみを追加
- **実装内容**:
  - `ConfigService.java`で設定の読み込み時にデフォルト値をフォールバック
  - プラグイン更新時にユーザーの設定が失われない

#### 12. 逆再生の無効化
- **問題**: 全ての曲が逆再生または違う曲になっていた
- **原因**: `reverse`設定がデフォルトで有効だった可能性
- **解決策**: `config.yml`に`reverse: false`を明示的に追加

### ファイル変更一覧

#### 修正されたファイル
1. `core/src/main/java/dev/geco/gmusic/object/gui/GMusicGUI.java`
   - インベントリサイズを54に変更
   - カテゴリバーの実装（`setCategoryBar()`メソッド）
   - カテゴリフィルタリングロジックの修正
   - 戻るボタンの表示ロジック修正
   - お気に入りボタンの追加（スロット51）
   - スピーカーミュートボタンの追加（オプション画面スロット48）
   - 検索ボタンとWelcome Musicオプションの削除

2. `core/src/main/java/dev/geco/gmusic/object/GPlaySettings.java`
   - `currentCategory`フィールドを追加
   - `speakerMode`フィールドを追加
   - `speakerRange`フィールドを追加
   - `muteSpeakers`フィールドを追加
   - 対応するgetter/setterメソッドを追加

3. `core/src/main/java/dev/geco/gmusic/enums/GPlayMode.java`
   - `PLAYLIST(3)`を`CATEGORY(3)`に変更

4. `core/src/main/java/dev/geco/gmusic/service/PlayService.java`
   - `GPlayMode.CATEGORY`への参照を更新

5. `core/src/main/java/dev/geco/gmusic/service/ConfigService.java`
   - `PS_D_SPEAKER_MODE`フィールドを追加
   - `PS_D_MUTE_SPEAKERS`フィールドを追加
   - 設定読み込みロジックに`speaker-mode`と`mute-speakers`を追加

6. `core/src/main/java/dev/geco/gmusic/service/PlaySettingsService.java`
   - `generateDefaultPlaySettings()`メソッドでデフォルト値を設定に反映

7. `resources/config.yml`
   - `lang: ja_jp`を設定
   - `speaker-mode: false`を追加
   - `speaker-default-range: 20`を追加
   - `max-speaker-range: 100`を追加
   - `mute-speakers: false`を追加
   - `reverse: false`を追加
   - `disable-favorites: false`を追加
   - `disable-options: false`を設定

8. `resources/lang/ja_jp.yml`
   - 完全な日本語翻訳を追加
   - カテゴリ関連のメッセージを追加
   - スピーカーモード関連のメッセージを追加

9. `core/src/main/java/dev/geco/gmusic/listener/PlayerEventHandler.java`
   - 自動再生機能を無効化（40-44行目をコメントアウト）

### データベーススキーマの変更
- `gmusic_play_settings`テーブルに以下のカラムを追加:
  - `speakerMode` (BOOLEAN)
  - `speakerRange` (INTEGER)
  - `muteSpeakers` (BOOLEAN)
  - `currentCategory` (TEXT)

### 既知の制約
- Bukkit/Paper APIの制限により、インベントリは最大54スロット（6行×9列）
- カテゴリは最大8個まで表示可能（スロット37-44）
- 曲の表示は1ページあたり36曲まで

### 技術スタック
- **プラットフォーム**: Bukkit/Paper API 1.21.4
- **ビルドシステム**: Gradle
- **言語**: Java 17+
- **データベース**: SQLite
- **設定形式**: YAML

### ビルド方法
```bash
./gradlew clean build
```

生成されたプラグインファイル: `build/libs/GMusic-<version>.jar`

### 今後の改善案
1. カテゴリの動的追加・削除機能
2. カテゴリ表示順のカスタマイズ
3. プレイリストの保存・読み込み機能
4. 曲の検索機能の復活（改善版）
5. スピーカーモードの範囲表示UI改善

---

## 作業ログ

### セッション1: 初期ビルドとエラー修正
1. プロジェクトの初回ビルド実行 ✓
2. ArrayIndexOutOfBoundsException修正（スロット54エラー）✓
3. インベントリサイズを63から54に変更 ✓
4. ログイン時の自動再生を無効化 ✓

### セッション2: 機能実装
1. カテゴリシステムの実装 ✓
2. お気に入り機能の追加 ✓
3. スピーカーモードの追加 ✓
4. 不要な機能の削除（Particle、Reverse） ✓
5. PLAYLISTモードをCATEGORYモードに変更 ✓

### セッション3: 日本語化
1. `ja_jp.yml`に完全な日本語翻訳を追加 ✓
2. `config.yml`のデフォルト言語を`ja_jp`に設定 ✓

### セッション4: バグ修正
1. カテゴリフィルタリングバグ修正 ✓
   - `currentCategory`フィールドを`searchKey`から分離
   - `setPage()`メソッドのフィルタリング順序を修正

### セッション5: 最終調整
1. スピーカーミュート機能の追加 ✓
2. 検索ボタンとWelcome Musicボタンの削除 ✓
3. 戻るボタンの表示修正 ✓
4. カテゴリフィルタリングロジックの再修正 ✓
5. 逆再生問題の修正（`reverse: false`を追加）✓

### セッション6: UIの改善とバグ修正（2025年12月4日 - 第2回）
1. **カテゴリクリック処理の修正** ✓
   - 問題: カテゴリボタンをクリックしても曲が表示されない
   - 原因: `searchKey`を使用していたが、`currentCategory`フィールドを使うべきだった
   - 解決: カテゴリクリック時に`currentCategory`と`playSettings.setCurrentCategory()`を使用するように修正

2. **逆再生問題の完全修正** ✓
   - 問題: 全ての曲が逆再生されてしまう
   - 原因: `PlaySettingsService.generateDefaultPlaySettings()`で`PS_D_REVERSE`を使用していた
   - 解決: デフォルト設定で`false`（逆再生無効）にハードコーディング

3. **前のページボタンの修正** ✓
   - 問題: 前のページボタンが機能しない
   - 原因: スロット52のクリックイベントに`if(optionState)`条件があった
   - 解決: 条件を削除し、常に前のページに戻れるように修正

4. **オプション中のUI改善** ✓
   - 問題: オプション画面を開いているときも曲再生ボタンや次のページボタンが使えてしまう
   - 解決: オプション中はスロット53（次のページボタン）を非表示にするように修正
   - 実装: `setPage()`メソッドで`if(!optionState)`条件を追加し、オプション中は`inventory.setItem(53, null)`で非表示化

5. **スピーカーモードの説明追加** ✓
   - `ja_jp.yml`の`music-options-speaker-mode`に「(周りの人に聞かせることができます)」という説明を追加
   - ユーザーがスピーカーモードの機能を理解しやすくなった

6. **音量ボタンの操作説明追加** ✓
   - `ja_jp.yml`の`music-options-volume`に「(左クリック:+5 右クリック:-5)」という操作説明を追加
   - 音量の上げ下げ方法が一目で分かるようになった

### 技術的な詳細

#### 修正されたコード箇所

**GMusicGUI.java**
- スロット36-44のカテゴリクリック処理:
  ```java
  // 修正前: searchKey = categories.get(categoryIndex);
  // 修正後: 
  currentCategory = categories.get(categoryIndex);
  playSettings.setCurrentCategory(currentCategory);
  ```

- スロット52の前のページボタン処理:
  ```java
  // 修正前: if(optionState) { setPage(page - 1); }
  // 修正後: setPage(page - 1);  // 常に動作
  ```

- スロット53の次のページボタン処理:
  ```java
  // 修正前: if(!optionState) { setPage(page + 1); }
  // 修正後: setPage(page + 1);  // クリック処理は常に動作
  ```

- `setPage()`メソッドの次のページボタン表示:
  ```java
  if(!optionState) {
      // 通常時: 次のページボタンを表示
      if(page < getMaxPageSize(songs.size())) { ... }
  } else {
      // オプション中: 非表示
      inventory.setItem(53, null);
  }
  ```

**PlaySettingsService.java**
- `generateDefaultPlaySettings()`メソッド:
  ```java
  // 修正前: gMusicMain.getConfigService().PS_D_REVERSE,
  // 修正後: false,  // 逆再生を無効化
  ```

**ja_jp.yml**
- スピーカーモード:
  ```yaml
  music-options-speaker-mode: "&aスピーカーモード:&6 %SpeakerMode%&7 (周りの人に聞かせることができます)"
  ```

- 音量:
  ```yaml
  music-options-volume: "&a音量:&b %Volume%%&7 (左クリック:+5 右クリック:-5)"
  ```

### セッション7: UIとバグ修正の追加改善（2025年12月4日 - 第3回）
1. **カテゴリ選択時のバグ修正** ✓
   - 問題: カテゴリボタンをクリックしても曲が表示されない（再発）
   - 原因: `searchKey`変数に代入していたが、`currentCategory`と`playSettings.setCurrentCategory()`を使用すべきだった
   - 解決: カテゴリクリック時に正しく`currentCategory`を設定するように修正（GMusicGUI.java:103-113行目）

2. **オプション画面のUI改善** ✓
   - 問題: オプション画面を開いているときに曲リストやカテゴリボタンが表示されたまま
   - 解決: オプション画面を開く際に曲リスト（スロット0-35）とカテゴリボタン（スロット36-44）を非表示にするように修正
   - 実装: `setOptionsBar()`メソッドでスロット0-44をクリア（GMusicGUI.java:381-384行目）
   - オプションから戻る際に`setPage(page)`を呼び出して曲リストを再表示（GMusicGUI.java:129行目）

3. **ドキュメント更新** ✓
   - ja_jp.ymlに既に以下の説明が追加されていることを確認:
     - スピーカーモード: "(周りの人に聞かせることができます)"
     - 音量: "(左クリック:+5 右クリック:-5)"
   - 前のページボタンは既に修正済み（CHANGELOG 230-234行目参照）
   - 逆再生問題は既に修正済み（PlaySettingsService.java:101行目でfalseにハードコーディング）

### セッション8: スピーカーモード機能の完全実装（2025年12月4日 - 第4回）
1. **スピーカーモードの完全実装** ✓
   - 問題: スロット47がパーティクル設定になっていて、スピーカーモードが機能しなかった
   - 解決: スロット47をスピーカーモードの切り替えと範囲調整に変更（GMusicGUI.java:145-170行目）
   - 実装内容:
     - 左/右クリック: スピーカーモードのON/OFF切り替え
     - Shift+左/右クリック: 範囲を±5ブロック調整
     - ミドルクリック: デフォルト設定にリセット
     - アイコンをJUKEBOXに変更し、範囲と操作方法を表示

2. **スピーカーモードの範囲表示改善** ✓
   - setOptionsBarメソッドで範囲を動的に表示（GMusicGUI.java:415-430行目）
   - ja_jp.ymlのスピーカーモード説明に範囲を追加: "(範囲: %Range%ブロック)"
   - アイテムのLoreに以下の情報を追加:
     - 範囲: X ブロック
     - 左/右クリック: ON/OFF
     - Shift+左/右: 範囲±5
     - ミドルクリック: リセット

3. **デフォルト設定の変更** ✓
   - スピーカーモードのデフォルト範囲を20から30に変更（config.yml:32行目）
   - パーティクル設定をconfig.ymlに追加（後方互換性のため）

4. **技術的な修正**
   - スピーカーモードのクリック処理を完全に再実装
   - 範囲調整時に最小値5ブロック、最大値100ブロックの制限を適用
   - クリック時にsetOptionsBar()を呼び出して表示を即座に更新
