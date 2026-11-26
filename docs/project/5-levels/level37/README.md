# Level 37：泳池房（The Poolrooms / Sublimity）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level37`（程式放 `src/main/java/dev/noclip/backrooms/level37/`）
- **建議維度 ID**：`noclip_backrooms:level_37`
- **出口登記鍵（`LevelExits`）**：`"37"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

後室人氣最高的代表樓層之一：無盡延伸的白色瓷磚室內泳池、拱廊、沉水階梯與溫熱微霧的淺水池，迴盪著空靈水滴回音，整體平靜療癒但深暗水區藏有風險。

## Wikidot / Fandom 設定對照

Wikidot 正式編號為 Level 37（Sublimity）；Fandom 亦廣為流傳且延伸出許多夢核／暗池子層。非常適合搭配本模組的 128px PBR 反光水磚材質！

## 常見實體與威脅

明池區幾乎無實體（部分版本深暗水域存在水瘟／細菌危害）。

## 出入口關聯

入口：多層的水下通道或瓷磚門。出口：潛入特定水下拱道通往 Level 37.1 或其他水系樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level37/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("37", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_37.json`、`dimension_type/level_37.json`、`worldgen/biome/level_37.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level37/`、`models/block/level37/`；測試放 `src/test` 與 `src/gametest` 的 `level37/`。
