# Level 20：倉庫（The Warehouse）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level20`（程式放 `src/main/java/dev/noclip/backrooms/level20/`）
- **建議維度 ID**：`noclip_backrooms:level_20`
- **出口登記鍵（`LevelExits`）**：`"20"`
- **參考來源**：Fandom / 經典 Wikidot

## 環境與核心特色

無限延伸的現代大型工業倉庫，擺滿高聳貨架、木箱、老舊叉車與機油漬，物資豐富但貨架隨時可能坍塌且機油具危險性。

## Wikidot / Fandom 設定對照

Fandom 保留經典無盡倉庫設定；Wikidot 後來有過重寫條目（Boreas Structure），實作通常採經典倉庫較具辨識度。

## 常見實體與威脅

少量竊皮者、獵犬與機械異常。

## 出入口關聯

入口：Level 19 或倉儲類門。出口：員工裝卸門通往工業或城市類樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level20/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("20", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_20.json`、`dimension_type/level_20.json`、`worldgen/biome/level_20.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level20/`、`models/block/level20/`；測試放 `src/test` 與 `src/gametest` 的 `level20/`。
