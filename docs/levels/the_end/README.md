# The End：終點（陷阱圖書館）

- **分類**：隱秘樓層（Enigmatic）
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.the_end`（程式放 `src/main/java/dev/noclip/backrooms/the_end/`）
- **建議維度 ID**：`noclip_backrooms:the_end`
- **出口登記鍵（`LevelExits`）**：`"end"`
- **參考來源**：Wikidot / Fandom 共通

## 環境與核心特色

外觀像一座極度安靜的現代圖書館與電腦室，螢幕上顯示「這就是終點，輸入指令即可回到現實」——實際上是騙人的假終點陷阱，久留會引來大量實體與假現實幻覺。

## Wikidot / Fandom 設定對照

兩邊皆為經典「假結局陷阱層」，在終端電腦執行不同程式（如 `reallifesoha.exe`、`level922...`）會把玩家送往完全不同的樓層！

## 常見實體與威脅

關燈事件時湧出的大量笑魘、獵犬與無面靈。

## 出入口關聯

入口：從多層的偽出口門誤入。出口：操作電腦終端機執行特定檔案，跳轉至其他指定樓層。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/the_end/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("end", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/the_end.json`、`dimension_type/the_end.json`、`worldgen/biome/the_end.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/the_end/`、`models/block/the_end/`；測試放 `src/test` 與 `src/gametest` 的 `the_end/`。
