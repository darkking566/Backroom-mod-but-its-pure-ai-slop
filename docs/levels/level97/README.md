# Level 97：燈塔（The Lighthouse）

- **分類**：經典主樓層
- **目前狀態**：未開放
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level97`（程式放 `src/main/java/dev/noclip/backrooms/level97/`）
- **建議維度 ID**：`noclip_backrooms:level_97`
- **出口登記鍵（`LevelExits`）**：`"97"`
- **參考來源**：Wikidot / Fandom

## 環境與核心特色

Wikidot 為永夜濃霧中的孤島，中央矗立著一座不斷旋轉照射的白色燈塔，凡被燈塔光束直射者將遭受致命抹殺或嚴重異常；Fandom 則偏重田野校園閾限空間。

## Wikidot / Fandom 設定對照

Wikidot 的「躲避旋轉探照光束」極適合做成緊張刺激的潛行機制！

## 常見實體與威脅

燈塔異常光束危害與孤島邊緣的暗霧威脅。

## 出入口關聯

入口：海洋或濃霧類樓層漂流抵達。出口：躲過光束進入燈塔內部或找到冰封古船出口。

## 開發開工檢查表（依根目錄 README.md）

1. 在 `src/main/java/dev/noclip/backrooms/level97/` 建立入口類別並刪除 `.gitkeep`，定義維度 `ResourceKey<Level>` 與 `register()`。
2. 於 `BackroomsMod.onInitialize()` 註冊本層，並在 `register()` 呼叫 `LevelExits.open("97", ...)`。
3. 在 `src/main/resources/data/noclip_backrooms/` 新增 `dimension/level_97.json`、`dimension_type/level_97.json`、`worldgen/biome/level_97.json`。
4. 貼圖與模型放 `assets/noclip_backrooms/textures/block/level97/`、`models/block/level97/`；測試放 `src/test` 與 `src/gametest` 的 `level97/`。
