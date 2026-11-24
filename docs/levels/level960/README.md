# Level 960：混凝土迷宮（Maze of Concrete）

- **分類**：主樓層（Level 1 已預留出口）
- **目前狀態**：未開放（Level 1 裂開柱子已預留出口）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level960`（程式放 `src/main/java/dev/noclip/backrooms/level960/`）
- **建議維度 ID**：`noclip_backrooms:level_960`
- **出口登記鍵（`LevelExits`）**：`"960"`
- **參考來源**：Fandom / 狐霖後室

## 環境與核心特色

無限延伸、高度壓抑的非歐幾何灰白混凝土迷宮，分為多層結構，缺乏補給且結構冷硬空曠，容易讓人失去方向感。

## Wikidot / Fandom 設定對照

Wikidot 英文主站目前常標記為 `[NO DATA]`；Fandom 設定為「混凝土迷宮（Maze of Concrete）」，符合 Level 1 裂開柱子的入口風格（已預留出口 `"960"`）。

## 常見實體與威脅

低度至中度敵對實體，以迷宮困死與環境消耗為主。

## 出入口關聯

入口：Level 1 停車區／倉庫區裂開的柱子（已預留出口 `"960"`）。出口：迷宮深處的混凝土樓梯或裂縫通往他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level960/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("960", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_960.json`、`dimension_type/level_960.json`、`worldgen/biome/level_960.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level960/`、`models/block/level960/`；測試放 `src/test` 與 `src/gametest` 的 `level960/`。
