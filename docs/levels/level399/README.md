# Level 399：霓虹天堂／安全巴士旅途

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level399`（程式放 `src/main/java/dev/noclip/backrooms/level399/`）
- **建議維度 ID**：`noclip_backrooms:level_399`
- **出口登記鍵（`LevelExits`）**：`"399"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

Wikidot 新版為行駛在雨夜霓虹公路上的安全客運巴士（Safe Passage）；舊版與部分 Fandom 分支則將 399 對應為霓虹街機天堂（現多整併至 Level 3999）。

## Wikidot / Fandom 設定對照

本專案同時保留 `level399` 與 `level3999`，讓「雨夜霓虹巴士」與「霓虹街機終局層」各有獨立空間不打架。

## 常見實體與威脅

巴士司機 Richard（友善引導者）。

## 出入口關聯

入口：特定公車站牌或公路層。出口：依車程停靠不同安全樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level399/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("399", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_399.json`、`dimension_type/level_399.json`、`worldgen/biome/level_399.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level399/`、`models/block/level399/`；測試放 `src/test` 與 `src/gametest` 的 `level399/`。
