# Level 10：豐裕麥田（Field of Wheat / Bumper Crop）

- **分類**：主樓層（核心）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level10`（程式放 `src/main/java/dev/noclip/backrooms/level10/`）
- **建議維度 ID**：`noclip_backrooms:level_10`
- **出口登記鍵（`LevelExits`）**：`"10"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

陰沉灰白天空下無邊無際的黃金麥田，夾雜樹籬、泥土小徑、廢棄穀倉與池塘，環境平靜且食物與淡水充足，適合建立長期農業據點。

## Wikidot / Fandom 設定對照

兩邊皆為著名的安全戶外麥田層，偶爾會下起細雨或起濃霧。

## 常見實體與威脅

稀少（偶有被馴化或較溫和的無面靈與零星敵對實體）。

## 出入口關聯

入口：Level 9 郊區邊緣田路、Level 143 走出聖庭。出口：沿路進入城鎮區域通往 Level 11，或透過穀倉門前往其他層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level10/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("10", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_10.json`、`dimension_type/level_10.json`、`worldgen/biome/level_10.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level10/`、`models/block/level10/`；測試放 `src/test` 與 `src/gametest` 的 `level10/`。
