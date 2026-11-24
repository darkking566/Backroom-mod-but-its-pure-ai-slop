# Level 12：矩陣（Matrix）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level12`（程式放 `src/main/java/dev/noclip/backrooms/level12/`）
- **建議維度 ID**：`noclip_backrooms:level_12`
- **出口登記鍵（`LevelExits`）**：`"12"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

一間極度單調的純白小房間，內有一張桌子、椅子與一台電腦，所有攝影或錄音設備在此都會出現嚴重噪點與花屏異常。

## Wikidot / Fandom 設定對照

兩邊核心皆為純白訊號干擾室；可作為極簡心理異常或過渡解謎層。

## 常見實體與威脅

無已知實體。

## 出入口關聯

入口：特定樓層卡出或進入純白門。出口：依特定規則操作或卡牆離開。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level12/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("12", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_12.json`、`dimension_type/level_12.json`、`worldgen/biome/level_12.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level12/`、`models/block/level12/`；測試放 `src/test` 與 `src/gametest` 的 `level12/`。
