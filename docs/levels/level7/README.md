# Level 7：深海恐懼症（Thalassophobia）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level7`（程式放 `src/main/java/dev/noclip/backrooms/level7/`）
- **建議維度 ID**：`noclip_backrooms:level_7`
- **出口登記鍵（`LevelExits`）**：`"7"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

入口是一間有重力異常的昏暗房屋，開門後即面對無邊無際的冰冷海洋與零星島嶼，水面下潛伏著極深的海溝與巨型水下威脅。

## Wikidot / Fandom 設定對照

兩邊皆為入口屋＋無盡海洋；Wikidot 著名實體為「七層之物（Tiny）」，Fandom 亦保留冰冷深海與海底洞穴入口。

## 常見實體與威脅

水下巨型掠食實體（七層之物）與極少數海洋異常。

## 出入口關聯

入口：Level 6 下行階梯。出口：潛入海底山洞裂縫通往 Level 8，或從極遠海域漂流至其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level7/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("7", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_7.json`、`dimension_type/level_7.json`、`worldgen/biome/level_7.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level7/`、`models/block/level7/`；測試放 `src/test` 與 `src/gametest` 的 `level7/`。
