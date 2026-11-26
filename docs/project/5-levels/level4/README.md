# Level 4：廢棄辦公室（Abandoned Office）

- **分類**：主樓層（核心）
- **目前狀態**：已實作（第一版）
- **資料夾與 Java 套件**：`dev.noclip.backrooms.level4`（入口是 `Level4.java`）
- **維度 ID**：`noclip_backrooms:level_4`；出口登記鍵 `"4"`（Level 2 噴了「4」的門與 Level 3 電梯會直接接到這裡）
- **貼圖與模型**：方塊 `textures/block/level4/`、`models/block/level4/`（由 `tools/make_level4.py` 產生，寫實包由 `tools/make_level4_realistic.py` 產生並縮至 128／64，預覽在 [`textures.png`](textures.png) 與 [`docs/look/level4_realistic.png`](../../../look/level4_realistic.png)）；7 件專屬 3D 物品模型由 `tools/make_level4_items.py` 產生
- **參考來源**：[Backrooms 中文維基 Level 4](https://backrooms-wiki-cn.wikidot.com/level-4)（生存難度 1，杏仁水主要產區）
- **直接進入**：從 Level 2 噴「4」的門或 Level 3 黃銅電梯進入；或在測試世界用 `/execute in noclip_backrooms:level_4 run tp @s 17.5 33 17.5`

---

## 畫面規格（照 [`docs/look/SOP.md`](../../../look/SOP.md)）

| 項目 | 內容 |
|---|---|
| **材質** | 128×128 結構面＋labPBR `_n`／`_s`（寫實包使用 `Carpet016`、`Concrete019`、`Wood051`、`Metal049A`/`032`、`Fabric028` 微粒合成後經 `resize_realistic.py` 縮小）。地毯（4 變體＋4 種桌椅壓痕＋潮濕地毯）、灰白石膏牆（含單格踢腳板＋9 種輕微髒舊變體＋上層無踢腳板牆）、散熱片窗下牆、4 格一組礦棉吊頂＋通風口、橘木防火門。 |
| **顏色** | 照維基實景照取色：深灰藍商用短毛地毯、啞光冷灰白牆面與白色踢腳板、偏灰吊頂與橘木色防火門、塗黑安全窗 vs 刺眼白光陷阱窗、雨夜冷青玻璃。 |
| **尺寸** | `112×112` 區域（`16×16` 個 `7×7` 單元）：大跨距開放式辦公大廳淨高 **4 格**（`y=33..36`，柱距 7 格）、雨幕中庭挑高 **8 格**（`y=33..40`）。 |
| **燈** | 四態嵌入式格柵日光燈（`office_troffer_lit` 亮冷白微綠 64%、`office_troffer_dim` 昏黃 14%、`office_troffer_flicker` 閃爍 10%、`office_troffer_broken` 壞燈 12%）；暗翼區多數熄滅（配電箱可恢復）；Omega 基地全亮。 |
| **氣氛** | `Look.Mood.LEVEL_4(10, 48, 0.14F)`：開闊冷清的寫字樓霧距與柔和衰減。 |
| **光影** | `office_trap_window` 與 `office_troffer_*` 具 `light_emission`；潮濕地毯與 `almond_fountain_pool` 具高光滑度 `_s` 反光；`office_glass_wall` 為半透明辦公室玻璃隔間。 |
| **道具** | 飲水機（`office_water_cooler`，限裝 2 瓶）、杏仁水噴水池（`almond_fountain_pool`，限裝 2 瓶）、自動販賣機、配電箱、影印機、座機電話、三大據點家具，外加稀疏 3D 辦公穿模家具（`Level4Props`）與 7 件專屬 3D 物品（`Level4Items`）。 |
| **掉落** | 結構拆解／物件掉落對齊 [`by-level.md`](../../3-crafting/by-level.md)。 |

---

## 照 Wiki 做了什麼

| Wiki 描述 | 遊戲實作 |
|---|---|
| 空置現代辦公大樓，幾乎沒有家具，地毯留有曾有桌椅的凹陷 | `OPEN_OFFICE` 大跨距柱列空間、`SUITE` 玻璃隔間與防火門、`office_carpet_dent`（4 種壓痕變體）與 `Level4Props` 稀疏殘留辦公物件 |
| 大多數窗戶被塗黑；未塗黑的窗戶是陷阱（Entity 2） | `office_blacked_window`（安全）／`office_rain_window` vs `office_trap_window`（`Level4Windows`：靠近敲玻璃警告、過近拉扯傷血並致暗；可用黑染料／墨囊／木炭塗黑化解） |
| 冷水器、自動販賣機與儲存杏仁水的噴水池 | `office_water_cooler` 與中庭 `almond_fountain_pool`：持空玻璃瓶右鍵可裝 **最多 2 瓶** 杏仁水（空手右鍵可直接飲用一次）；`office_vending` 投 `lost_coin`／`alloy` 吐杏仁水 |
| 接近實體絕跡：僅獵犬、鈍人，以及未證實的笑魘目擊傳聞；因安全而有許多人在此游蕩、會面與尋求補給 | `Level4Monsters`：極低機率於暗處刷新 `HOUND` 與 `DULLER`（4 格高足以容納鈍人），極罕見在純黑角落閃現 4 秒即消失的 `SMILER` 傳聞彩蛋；`Level4Wanderers`：於開放式辦公區、隔間套房與中庭生成最多 5 位會自行漫步的友善流浪者 NPC，靠近時轉向玩家，右鍵可詢問最近 Omega 基地／中庭方向與樓層情報（附微幅回理智），並能以遺失舊幣、合金或紙張以物易物換取杏仁水、空玻璃瓶與繃帶；Omega 基地禁生怪 |
| **M.E.G. Omega 基地**（第二大主基地、守備嚴密、近 Level 5／6 入口） | 每 `2×2` 區域必有一座安全基地：防彈牆與護欄、行軍床（`Trips.settle` 安頓重生）、置物櫃、拆解台、守衛／醫護（免費滿血回理智）／軍需官／補給員，基地內常駐 Level 5 與 Level 6 樓梯 |
| **神愛之繁生（Amor Incrementum）**（崇拜農業異教徒、敵對，持宗教物品才交易） | 菌類培養溫室（Level 0/1 基質培養基＋發光菌類）。平常靠近會主動追打驅逐；手持專屬 3D 宗教聖物 **`brass_crucifix`（黃銅十字架）** 或 **`faded_icon_frame`（褪色聖像畫）** 立刻轉為平和並可交易純化杏仁精華／杏仁水 |
| **T.B.D.（懸而未決）**（6 人小團體、不理人、只賣沒用的奇特物品） | 堆物間成員無視玩家，右鍵可向其購買 5 款專屬 3D 奇物（`broken_nw_compass`、`calendar_1998`、`blank_stamp`、`unlabeled_tape`、`rain_glass_orb`） |
| 失去視野後出口可能突然消失 | `Level4VanishingExits`：野外的游離出口（樓梯／異常門／地板艙口）被看到後若轉身背對超過 4 秒即悄然消失成實牆；Omega 基地 5/6 樓梯與電梯保持常駐 |
| 微小心理恐怖與環境音 | 細雨拍窗與 60Hz 空調嗡鳴（偶爾雨聲驟停）、座機無源電話、影印機吐紙、配電箱復電、壓痕地毯拉椅幻聽 |

---

## 出入口一覽

| 方向 | 出口載體 | 目標鍵 | 性質 |
|---|---|---|---|
| **進入** | Level 2 噴紅「4」鋼門／Level 3 黃銅電梯 | `"4"` | 已接通 |
| **離開** | 辦公室電梯（`office_elevator`） | `"3"` | 常駐雙向回 Level 3 |
| **離開** | Omega 基地旁樓梯（`office_stairwell_5`／`6`） | `"5"`／`"6"` | 常駐（目標層開放即通） |
| **離開** | 野外游離樓梯／門／艙口 | `"5"`、`"6"`、`"71"`、`"4.3"`、`"11"`、`"2"`、`"13"` | **離開視野會消失**（其中 `"2"` 已開放） |

---

## 測試

- 單元測試 `Level4LayoutTest`：連通性驗證（12 種子 × 9 區域全格連通）、三大陣營與中庭／暗翼／出口皆出現、落點永遠安全。
- 實機測試 `Level4ClientTest`（`gradlew runClientGameTest -Plevels=4` / `-Plevels=4 -Pshaders`）：從 Level 2 進來、拍六大場景照、冷水機限裝 2 瓶、神愛之繁生敵對→亮十字架和平交易、陷阱窗塗黑、失去視野出口消失、全方塊與物品翻譯檢查。
