# Level 11：無盡城市（The Endless City）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level11`（程式放 `src/main/java/dev/noclip/backrooms/level11/`）
- **建議維度 ID**：`noclip_backrooms:level_11`
- **出口登記鍵（`LevelExits`）**：`"11"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

無止盡延伸的現代大都市，包含摩天大樓、商店街、公園與瀝青公路，建築內部有時呈現非歐幾何結構，是後室最大的人類貿易與文明聚落中心。

## Wikidot / Fandom 設定對照

兩邊皆為後室最著名的核心城市層（B.N.T.G. 與 M.E.G. 重要據點）；實體（如獵犬與無面靈）在此常呈現受「Level 11 效應」影響而轉趨溫和。

## 常見實體與威脅

受溫和化影響的無面靈與獵犬（仍有少數建築內潛伏敵對實體）。

## 出入口關聯

入口：Level 9 主幹道、Level 10 城鎮邊界。出口：多棟特定建築與地鐵站可通往數十個其他樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level11/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("11", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_11.json`、`dimension_type/level_11.json`、`worldgen/biome/level_11.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level11/`、`models/block/level11/`；測試放 `src/test` 與 `src/gametest` 的 `level11/`。
