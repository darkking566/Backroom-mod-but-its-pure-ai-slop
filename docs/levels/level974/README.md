# Level 974：Kitty 的家（Kitty's House）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level974`（程式放 `src/main/java/dev/noclip/backrooms/level974/`）
- **建議維度 ID**：`noclip_backrooms:level_974`
- **出口登記鍵（`LevelExits`）**：`"974"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

一棟裝潢成夢幻粉紅、粉藍與大量可愛兒童小物風格的溫馨住宅，屋內住著身高超過 3 公尺、全身漆黑修長卻毫無惡意的實體「Kitty」。

## Wikidot / Fandom 設定對照

兩邊人氣極高且設定一致：攜帶可愛物品有機會卡入，把該可愛物品送給 Kitty 並在屋內入睡即可安全離開。

## 常見實體與威脅

Kitty（約 3.2 公尺高黑色細長人形，中立友善、會默默跟隨觀察）。

## 出入口關聯

入口：持有「可愛物品」卡出時低機率進入。出口：贈與 Kitty 可愛物品後入睡安全傳往安全層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level974/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("974", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_974.json`、`dimension_type/level_974.json`、`worldgen/biome/level_974.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level974/`、`models/block/level974/`；測試放 `src/test` 與 `src/gametest` 的 `level974/`。
