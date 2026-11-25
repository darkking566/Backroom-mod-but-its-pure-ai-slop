# 物品、武器、裝備、家具的畫面規範

2026-11-25 使用者看過試作的並排比較後決定：物品改成跟樓層一樣的風格，由 Claude 依類別分批重做。原本 Gemini 的模型和貼圖都還在，這裡做的是蓋在上面的資源包。

## 風格

- **跟樓層是同一個世界**：舊、髒、磨損、褪色。金屬有鏽和刮痕，油漆會剝落，握把有手摸黑的痕跡，邊角比較髒。
- **顏色低飽和**：不用螢光色、霓虹色。特殊武器的「異常」感靠材質，例如發黑的鋼、乾掉的血、骨頭、結晶，不靠發光圈。
- **只有真的會發光的東西才發光，而且要暗**：日光燈管、螢幕、裂縫透出的微光，`light_emission` 最多 4～7。
- **真實比例**：1 格＝1 公尺。小刀約 0.9 格（14／16），長劍約 1.8 格，長柄武器 2.7 格以上；Minecraft 模型最長 3 格（48／16），超過的工具會等比例縮小再用顯示比例放大回來。

## 做法

- **方塊模型**（Minecraft 原本的畫法）：不用 Blender 曲面網格，不用另外寫繪製程式。
- **貼圖 64×64**，用 ambientCG 的 CC0 照片打底（鏽 `Rust004`、金屬 `Metal032`／`Metal049A`、木頭 `Wood051`、布 `Fabric028`、水泥 `Concrete034`），再加髒污、刮痕、剝落；每張附 labPBR `_n`、`_s`，開光影時有立體感和反光。
- **材質庫共用**：所有物品從同一個材質庫取材料（`tools/item_look/mats.py`），名稱如 `steel`、`steel_rusty`、`blade`、`blade_edge`、`brass`、`leather_brown`、`cord_red`、`bone`、`flesh`、`crystal_violet`……，所以整體一致，也不會每樣都長得一樣。
- **同一類共用組裝器**：例如所有劍都用同一套「柄頭、握把、護手、直刃／彎刃」零件，只換長短寬窄和材料（`tools/item_look/melee.py`）。

## 要新增或修改時

1. 在對應類別的檔案加一個函式（函式名就是物品 ID），回傳方塊清單和拿在手上的姿勢。
2. 執行 `py -I tools/make_realistic_items.py`。它每次都會整個重新產生資源包，並更新「要換掉哪些東西」的清單 `item_look.json`。
3. 執行 `gradlew runClientGameTest -Plevels=itemlook`，看箱子裡的圖示、拿在手上、第三人稱、家具的截圖，資源包關和開各一輪。

## 檔案

| 東西 | 位置 |
| --- | --- |
| 產生工具 | `tools/make_realistic_items.py`；`tools/item_look/`（`lib.py` 共用、`mats.py` 材質庫、`melee.py` 冷兵器、`guns.py` 槍彈藥配件、`ported.py` 試作的 8 樣） |
| 資源包 | `src/main/resources/resourcepacks/realistic_items/`（整個由工具產生，不要手改） |
| 開關與「誰被換掉」 | `src/client/java/dev/noclip/backrooms/core/look/ItemLook.java`；Gemini 的 `BlenderWeaponRenderer`、`BlenderPropRenderer` 遇到清單裡的東西時改畫一般模型 |
| 比較圖 | `docs/look/items/`（`icons_page*.png` 圖示、`melee_hands.png`、`guns_hands.png`、`melee_third_person.png`）、材質總覽 `docs/look/items_materials.png` |
| 實機測試 | `src/gametest/java/dev/noclip/backrooms/itemlook/ItemLookClientTest.java` |

## 進度

| 類別 | 數量 | 狀態 |
| --- | --- | --- |
| 冷兵器 | 41 | 完成 |
| 槍、彈藥、配件 | 19＋18 | 完成（土製管槍沿用試作版）；第三人稱 `[-90, 180, 0]`（槍口朝前、槍背朝上） |
| 法器 | 20 | 只有穿牆短杖（試作） |
| 工具、裝備道具、護甲 | 10＋29＋19 | 只有撬棍、杏仁藥劑（試作） |
| 材料 | 213 | 只有銅線（試作） |
| 家具與工作台 | 68 | 只有辦公椅、映像管電腦（試作） |
