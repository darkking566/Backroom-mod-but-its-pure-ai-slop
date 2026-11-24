# Level 998：天頂／向死之路（Zenith）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level998`（程式放 `src/main/java/dev/noclip/backrooms/level998/`）
- **建議維度 ID**：`noclip_backrooms:level_998`
- **出口登記鍵（`LevelExits`）**：`"998"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

Wikidot 第一集群倒數第二層：隨著行走距離推進，環境依序經過「白域（平靜高危幻境）→ 灰域（崩塌廢墟）→ 黑域（極端敵對地獄）」，通往 Level 999 或更深集群。

## Wikidot / Fandom 設定對照

Wikidot 經典分區遞進式地獄長途考驗層；Fandom 亦有末日焦土變體。

## 常見實體與威脅

隨深入白／灰／黑域呈指數上升的高威脅實體群。

## 出入口關聯

入口：高編號樓層深處異常通道。出口：穿越極限區域通往 Level 999。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level998/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("998", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_998.json`、`dimension_type/level_998.json`、`worldgen/biome/level_998.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level998/`、`models/block/level998/`；測試放 `src/test` 與 `src/gametest` 的 `level998/`。
