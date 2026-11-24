# Level 3：發電站（Electrical Station）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level3`（程式放 `src/main/java/dev/noclip/backrooms/level3/`）
- **建議維度 ID**：`noclip_backrooms:level_3`
- **出口登記鍵（`LevelExits`）**：`"3"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

昏暗狹窄的工業磚牆與發電設備迷宮，充斥高壓電箱、變壓器、轟鳴的發電機與交錯銅管，環境吵雜且極度危險。

## Wikidot / Fandom 設定對照

兩邊皆設定為高危險的電力設施迷宮；Wikidot 強調 M.E.G. 在此有零星據點與豐富物資，Fandom 著重狹窄磚牆走廊與高密度怪物。

## 常見實體與威脅

獵犬、無面靈、竊皮者、死亡飛蛾、笑魘、肢團等大量敵對實體。

## 出入口關聯

入口：Level 2 未上鎖的門、Level 1 電梯。出口：電梯通往 Level 4 或 Level 5，部分木門或裂縫通往其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level3/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("3", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_3.json`、`dimension_type/level_3.json`、`worldgen/biome/level_3.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level3/`、`models/block/level3/`；測試放 `src/test` 與 `src/gametest` 的 `level3/`。
