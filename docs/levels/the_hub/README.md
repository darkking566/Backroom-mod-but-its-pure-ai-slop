# The Hub：樞紐

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.the_hub`（程式放 `src/main/java/dev/noclip/backrooms/the_hub/`）
- **建議維度 ID**：`noclip_backrooms:the_hub`
- **出口登記鍵（`LevelExits`）**：`"hub"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

寬闊、低矮的地下混凝土隧道與無盡成排標示著樓層編號的木門，是後室最核心的「跨樓層轉運站」，掌握路線的流浪者可由此快速往返已解鎖樓層。

## Wikidot / Fandom 設定對照

兩邊一致的核心傳送樞紐層；非常適合作為本模組多樓層串聯的中央交通站！

## 常見實體與威脅

幾乎無實體，是相對安全的交通中轉站。

## 出入口關聯

入口：在 Level 1 入口依特定左右步法轉向開啟隱藏門。出口：隧道兩側編號門直接連通已開放的各主樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/the_hub/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("hub", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/the_hub.json`、`dimension_type/the_hub.json`、`worldgen/biome/the_hub.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/the_hub/`、`models/block/the_hub/`；測試放 `src/test` 與 `src/gametest` 的 `the_hub/`。
