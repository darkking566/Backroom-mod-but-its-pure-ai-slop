# Th3 Sh4dy Gr3y：陰影灰域

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.th3_sh4dy_gr3y`（程式放 `src/main/java/dev/noclip/backrooms/th3_sh4dy_gr3y/`）
- **建議維度 ID**：`noclip_backrooms:th3_sh4dy_gr3y`
- **出口登記鍵（`LevelExits`）**：`"shady_grey"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

由多層遞進式「零階至五階」組成的極端恐怖灰階崩壞領域，天空灰暗、建築褪色且結構逐漸扭曲瓦解，被視為後室中最絕望的單程死地之一。

## Wikidot / Fandom 設定對照

Wikidot 與 Fandom 皆以此層的極端壓抑灰白濾鏡與多階段精神折磨聞名。

## 常見實體與威脅

房東（The Landlord）與灰域異常實體。

## 出入口關聯

入口：從特定崩壞層誤入灰色異常入口。出口：幾乎無已知穩定出口（極難逃離）。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/th3_sh4dy_gr3y/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("shady_grey", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/th3_sh4dy_gr3y.json`、`dimension_type/th3_sh4dy_gr3y.json`、`worldgen/biome/th3_sh4dy_gr3y.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/th3_sh4dy_gr3y/`、`models/block/th3_sh4dy_gr3y/`；測試放 `src/test` 與 `src/gametest` 的 `th3_sh4dy_gr3y/`。
