# Level 14：天堂（Paradise）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level14`（程式放 `src/main/java/dev/noclip/backrooms/level14/`）
- **建議維度 ID**：`noclip_backrooms:level_14`
- **出口登記鍵（`LevelExits`）**：`"14"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

表面看似寧靜迷人、充滿詭異紅色或夢幻色彩的森林／療癒之地，實際上會逐步扭曲流浪者的認知並誘導其永遠留下。

## Wikidot / Fandom 設定對照

Wikidot 與 Fandom 歷經數次重寫（從醫療院到詭異紅林／極樂陷阱），共同核心是「極度危險的認知危害天堂」。

## 常見實體與威脅

極強環境精神影響，無需傳統怪物即可造成致命威脅。

## 出入口關聯

入口：多個樓層的異常夢幻入口。出口：極難逃離，需抵抗認知影響找到隱藏出口。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level14/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("14", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_14.json`、`dimension_type/level_14.json`、`worldgen/biome/level_14.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level14/`、`models/block/level14/`；測試放 `src/test` 與 `src/gametest` 的 `level14/`。
