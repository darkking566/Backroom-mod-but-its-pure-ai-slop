# Level !：快逃命（Run for Your Life!）

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level_run`（程式放 `src/main/java/dev/noclip/backrooms/level_run/`）
- **建議維度 ID**：`noclip_backrooms:level_run`
- **出口登記鍵（`LevelExits`）**：`"!"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

後室最著名的腎上腺素狂飆追逐戰：閃爍紅光與刺耳警報的 10 公里長醫院走廊，背後有數百隻獵犬、無面靈、笑魘與派對客狂追，只能一路跨過病床障礙衝到盡頭逃生門！

## Wikidot / Fandom 設定對照

兩邊皆為超高人氣的極限跑酷追逐層（在 Minecraft 裡做成筆直紅光醫院障礙跑道＋背後獵犬群追擊，體驗絕對炸裂！）。

## 常見實體與威脅

身後狂追的大量實體群（獵犬、笑魘、派對客、竊皮者等）。

## 出入口關聯

入口：極低機率在任意樓層隨機卡入（或特定紅色警報門）。出口：衝完走廊撞開盡頭 EXIT 雙開門，傳往安全樓層並永久不再回到此層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level_run/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("!", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_run.json`、`dimension_type/level_run.json`、`worldgen/biome/level_run.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level_run/`、`models/block/level_run/`；測試放 `src/test` 與 `src/gametest` 的 `level_run/`。
