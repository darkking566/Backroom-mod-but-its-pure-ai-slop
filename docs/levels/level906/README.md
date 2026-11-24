# Level 906：天鵝座檔案館（Cygnus Archive）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level906`（程式放 `src/main/java/dev/noclip/backrooms/level906/`）
- **建議維度 ID**：`noclip_backrooms:level_906`
- **出口登記鍵（`LevelExits`）**：`"906"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

Wikidot 著名的浩瀚星空圖書館與古籍檔案館，由友善博學的實體「Blanche」掌管，收藏後室各地知識並提供庇護；部分社群衍生版本亦有無盡混凝土樓梯井詮釋。

## Wikidot / Fandom 設定對照

Wikidot 的天鵝座檔案館是後室少數高人氣「溫柔避風港＋劇情核心層」。

## 常見實體與威脅

Blanche（友善守護者，禁止攜帶敵意或破壞書冊）。

## 出入口關聯

入口：念出特定暗號或透過特定書本卡入。出口：向 Blanche 請求傳送至安全樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level906/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("906", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_906.json`、`dimension_type/level_906.json`、`worldgen/biome/level_906.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level906/`、`models/block/level906/`；測試放 `src/test` 與 `src/gametest` 的 `level906/`。
