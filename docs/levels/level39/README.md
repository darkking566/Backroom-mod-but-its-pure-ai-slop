# Level 39：魔法森林／巨物恐懼

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level39`（程式放 `src/main/java/dev/noclip/backrooms/level39/`）
- **建議維度 ID**：`noclip_backrooms:level_39`
- **出口登記鍵（`LevelExits`）**：`"39"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

Wikidot 為永恆黃昏下綿延的詭異寧靜橡樹林泥土小徑（Enchanted Forest）；Fandom 則為充滿超巨大比例建築的「巨物恐懼症（Megalophobia）」小鎮。

## Wikidot / Fandom 設定對照

兩邊主題完全不同：Wikidot 偏自然懷舊黃昏森林；Fandom 偏超巨型尺度建築壓迫感。開發時可擇一實作。

## 常見實體與威脅

Wikidot：極稀少（以失憶與懷舊沉溺為主）；Fandom：巨型環境異常。

## 出入口關聯

入口：自然林地門或特定戶外層。出口：沿著林間主徑走出或穿過小鎮邊界。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level39/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("39", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_39.json`、`dimension_type/level_39.json`、`worldgen/biome/level_39.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level39/`、`models/block/level39/`；測試放 `src/test` 與 `src/gametest` 的 `level39/`。
