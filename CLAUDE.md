@AGENTS.md

## 動作記錄（Claude）

### 2026-11-23
- 建立 Claude 工作資料夾 `Desktop\Backroom - Claude`（git worktree，`claude` 分支，追蹤 `origin/claude`）
- 依 Backrooms Wikidot 的 Level 1 做第一版「停車場」（細節見 `docs/level1.md`）：
  - 新增 `src/main/java/dev/noclip/backrooms/level1/`（地形規劃、生成器、28 種方塊、閃爍牆入口、停電、補給箱、音效）
  - 新增 Level 1 的維度、維度類型、生態域、`level_0_walls` 標籤、戰利品表
  - 新增 `tools/make_level1.py`，產生 `textures/block/level1/`、`models/block/level1/`、Level 1 的 blockstates 與物品模型、`docs/level1_textures.png`
  - 新增單元測試 `Level1LayoutTest`、地圖輸出工具 `MapDump`、真實客戶端測試 `Level1ClientTest`
  - 動到的共用檔案：`BackroomsMod.java`（多一行註冊）、`lang/en_us.json`、`lang/zh_tw.json`、`sounds.json`、`mineable/pickaxe.json`、`mineable/axe.json`、`src/gametest/resources/fabric.mod.json`、`NEXT.md`、`CREDITS.md`
- 把朋友在 GitHub `origin/main` 的 9 個 Level 0 提交（手電筒、Edge Shift、紅房間困死、陰影幻覺、深坑效果、樓梯間等）合併進 `claude` 分支：
  - 衝突只在 `BackroomsMod.java`、兩個語言檔、`mineable/axe.json`，兩邊內容都保留
  - 配合 Edge Shift：閃爍牆被改掉時，改到別處重新出現（沒有改朋友的程式）
  - 配合紅房間困死：`level_0_walls` 標籤拿掉紅房間的牆，閃爍牆不會變成紅房間的逃生口；加入朋友新增的粗糙熄燈壁紙
  - Level 1 自動測試先關掉 Edge Shift 遊戲規則
  - 合併後：`gradlew build`（含全部單元測試）、Level 0 貼圖檢查、`runClientGameTest`（Level 0 與 Level 1）全部通過
- 使用者同意後，把 `claude` 分支快轉合併進 `main`，並把 `main` 與 `claude` 推送到 GitHub（`origin`）；沒有強制推送、沒有改寫歷史
- 使用者要求停車場像「狐霖的後室 RPG」一樣有很多通往其他樓層的出口（出口表從之前那次對話的紀錄還原出的攻略取得）：
  - 新增 11 種出口（`Level1Exit`、`HallPlan`、`Level1Exits`）：往上的樓梯→0、地窖門→1.01、獨立門框→1.1、往下的樓梯→2、純白門→4.3、加固門→54、宮殿畫→143、插座牆→188、發光自動門→197、十字鎬門→389、裂開的柱子→960
  - 樓層越低越難找：每塊停車區的出現機率 0.06＋0.18×log10(1＋樓層)
  - 新增共用的 `dev.noclip.backrooms.levels`（`LevelExits` 出口登記處、`Level0Arrival`）；沒登記的樓層，出口會顯示「尚未開放」
  - 閃爍牆改難找：4～7 分鐘、16～40 格外
  - 新增 17 個方塊、相應貼圖模型與語言條目、`level_0_red_walls` 標籤；更新單元測試與客戶端測試，`gradlew build`、`runClientGameTest` 全部通過
- 使用者同意後，再次把 `claude` 快轉合併進 `main`，並把 `main` 與 `claude` 推送到 GitHub
- 使用者要求繼續做停車場（事件、據點、道具、怪物四塊全做），並要求每次都先把朋友在 GitHub 的進度抓下來合併：
  - 事件：收費亭（抽屜給 Level 0 的手電筒）、發電機房（停電時扳拉桿恢復照明、不受停電影響的工作燈、轟隆聲）、有眼睛的箱子、補給箱旁會逃跑的小貓（`HallPlan.Prop`、`Level1Props`、`Strays`）
  - 據點：M.E.G.「第一道光」與殖民者「眨眼」（`BasePlan`、`Level1Bases`）；行軍床／睡袋設重生點（安頓）、補給置物櫃（5 分鐘一份，合金可立刻換）、不受停電影響的探照燈與油桶火堆；Level 1 出口門改成：重生點在 Level 1 據點時帶你回據點
  - 道具：杏仁水、合金、磷粉（`Level1Items`），磷粉記號方塊；補給箱、有眼睛的箱子、置物櫃加入這些道具；4 合金合成 1 鐵錠
  - 怪物：掠奪者（原版劫掠者／衛道士換名、掉合金，`Marauders`）、停電時出現的微笑者（顯示實體發光笑臉，被光源、跑步、跳躍吸引，`Smilers`）
  - 更新 `docs/level1.md`、單元測試與客戶端測試
- 提交前抓 GitHub，合併朋友新推的 Level 0 進度（85598bc 房間、天花板、地板與區域變化）：沒有衝突；`gradlew build`（含全部單元測試）與 `runClientGameTest` 全部通過
- 合併朋友新推的 a9dc5b4（Level 0 高度擴成 128 格：超大廳、斷崖、多層房間），沒有衝突；Level 0 主地板仍在 y=32
- 使用者要求照樓層整理資料夾結構（方便閱覽、修改、新增，並解決跨樓層的東西要放哪）：
  - 程式：Level 0 全部移到 `level0/`（新增入口檔 `Level0.java`，原本 `BackroomsMod` 裡的註冊搬進去，`LEVEL_0` 也移到這裡），Level 1 在 `level1/`，跨樓層的出口登記處移到 `core/LevelExits.java`；`Level0Arrival` 移到 `level0/`，由 Level 0 自己登記「0」；`BackroomsMod` 只剩依序註冊各層與共用的 `MOD_ID`、`id()`
  - 資源：Level 0 的方塊／物品貼圖與模型、音效檔移到各自的 `level0/` 子資料夾，所有 blockstates、物品、模型、`sounds.json` 的路徑一併改寫；方塊、物品、音效、維度 ID 全部不變（存檔不受影響）
  - 測試：`src/test`、`src/gametest` 也分成 `level0/`、`level1/`，更新遊戲測試登記
  - 工具：`make_textures.py` 改寫到 `textures/block/level0/`、預覽改到 `docs/levels/level0/`；`make_level1.py` 跟著調整
  - 文件：新增根目錄 `README.md`（資料夾地圖、跨樓層規則、新增一層的步驟、ID 規則）；Level 1 說明移到 `docs/levels/level1/README.md`，截圖與預覽移到 `docs/levels/`；`AGENTS.md` 加一行指向 README；`CREDITS.md`、`NEXT.md` 路徑更新
- 使用者決定以後都在 `Backroom - Claude` 這一個資料夾工作，不再照 `AGENTS.md` 分兩個資料夾；`AGENTS.md` 的分工規則改成「大家都在這個資料夾」
- 把 Antigravity 做的獵犬（Hound）放進遊戲：
  - 交接包從主資料夾的 `exports/hound/` 搬進來：規格檔 → `assets/noclip_backrooms/monsters/hound.json`、貼圖 → `textures/entity/hound.png`，Blender 原檔、glb、預覽圖與說明 → `art/monsters/hound/`（主資料夾那份還在，沒有刪）
  - 新增 `monsters/` 套件（怪物可能出現在好幾層）：`Monsters.java`（實體、生怪蛋、音效）、`monsters/hound/Hound.java`（追人、蹲下→撲起→咬，咬的時間對準動畫，蹲的時候退開就會撲空）
  - 新增客戶端程式 `src/client/`：`BackroomsClient`、`BoxRig`（直接讀 Blender 匯出的規格檔：關節樹、長方體、三段動畫，用 Catmull-Rom 補間），`HoundModel`、`HoundRenderer`（眼睛在黑暗中微亮）；`fabric.mod.json` 加 client 入口
  - 停車場停電時會有獵犬從身後的走道出來（`level1/Hounds.java`），燈亮後附近沒人的會離開；`Marauders.findSpot` 改成可共用
  - 新增 `tools/make_hound.py`（眼睛發光貼圖、生怪蛋圖示與模型），音效與語言檔加獵犬條目，`Level1ClientTest` 加獵犬測試
- 使用者同意後，把 `claude` 快轉合併進 `main`，並把 `main` 與 `claude` 推送到 GitHub（停車場事件／據點／道具／怪物、資料夾照樓層整理、獵犬）；合併前主資料夾 `CLAUDE.md` 裡 Antigravity 未提交的紀錄已確認全部在本檔，才還原該檔（備份在暫存區）；主資料夾的 `exports/hound/` 沒有刪；沒有強制推送、沒有改寫歷史

### 2026-11-24
- 改在單一資料夾 `Desktop\Backroom`（`main`）工作；先把朋友在 GitHub 新推的 3 個提交（Level 0 馬尼拉房間、多層樓梯間、斷路器與筆記）快轉合併進來，沒有衝突
- 使用者要求在 Gemini（Antigravity）規劃的 60 層之外，再找其他來源、把樓層企劃擴充到 200～300 層：
  - 查證來源：Backrooms Wikidot 樓層總表（0～199 主樓層、0～99 子樓層、無編號樓層）、後室中文維基原創層級、B 站後室 Wiki、Escape the Backrooms 攻略；Fandom 擋自動讀取，沒辦法查證
  - `docs/levels/README.md` 加「第二批擴充」：新增 227 層（Wikidot 0～99 主樓層 75、子樓層 39、100～199 主樓層 91、中文原創 22），合計 287 層；另列 52 個候補、名稱對照表（第一批和 Wikidot 名稱不同的 13 處）、Escape the Backrooms 關卡玩法參考
  - 第二批只列在索引，不預先建立各層資料夾與 Java 套件（開工時再建）
- 使用者提到看過「1980 阿帕拉契的荒涼」：查到是 Fandom 的 Level 161（Appalachian Desolation）與 161.1（Appalachian Lodge），Wikidot 的 161 是另一層；`docs/levels/README.md` 加「Fandom 版本」29 層（從搜尋結果確認得到標題的，和 Wikidot 同號的資料夾加 `_fd`），合計 316 層
- 使用者定下分工：Claude 先把主樓層做好，Gemini（Antigravity）做合成表；讀了 Antigravity 的 `docs/PROJECT_PLAN_FOR_CLAUDE.md`，回覆寫在 `docs/PROJECT_PLAN_REPLY_FROM_CLAUDE.md`（五個問題的答覆、建議都在 `main` 小步提交、配方只在伺服器端等技術提醒）；`AGENTS.md` 加一行分工
- 使用者同意後，把兩邊未提交的企劃（Antigravity 的 60 層骨架、合成表企劃、協作企劃書；Claude 擴充的樓層索引與回覆）一起提交（`cd71cdb`）
- 使用者要求開始做 Level 2，並要求每一層都參考網路上的圖：
  - 讀 Wikidot Level 2（Abandoned Utility Halls）全文，下載頁面上的實景照片照著做
  - 新增 `level2/`：地圖規劃（`Level2Plan`、`Level2Layout`：5 格單元迷宮、寬 3 高 4 的走道、一側整排管線、轉角切斜、房間與門、悶熱區、窗戶）、地形生成、47 個方塊、14 種出口（`Level2Exit`，越低樓層越難找）、串聯電路（打破一盞燈整組熄）、悶熱區（飢餓、虛弱）、窗戶（靠近會受傷）、暗處的獵犬、環境音
  - 新增 Level 2 的維度、維度類型、生態域、掉落物表、語言檔、音效、挖掘標籤；`BackroomsMod` 加一行註冊；Level 1 往下的樓梯從此接到 Level 2
  - 新增 `tools/make_level2.py`（貼圖、模型、blockstates、物品模型、掉落物表）、`docs/levels/level2/README.md`（取代企劃稿）與貼圖預覽
  - 新增單元測試 `Level2LayoutTest`、`MapDump`，實機測試 `Level2ClientTest`
  - 順手修正朋友的馬尼拉房間踢腳板模型：還指向資料夾整理前的舊路徑（`block/baseboard_wall` → `block/level0/baseboard_wall`，`tools/make_manila.py` 一併改）
  - 修正 `core/LevelExits` 的錯誤：「尚未開放」的提示用伺服器 tick 計時防止洗版，但換世界後 tick 從 0 重算、記錄卻沒清，會讓提示很久都不出現；改成 tick 倒退時不擋
  - 管線依照片改成連續長管（拿掉一節一節的箍環、紅管只留閥門），截圖確認
  - 驗證：`gradlew build`（34 個單元測試）與 `runClientGameTest`（Level 0、1、2）全部通過；中途朋友的 Level 0 Edge Shift 測試偶爾逾時一次（看隨機），重跑通過
- 使用者同意後，把 `main`（企劃書 `cd71cdb`、Level 2 `83e2946`）推送到 GitHub；沒有強制推送、沒有改寫歷史
- 使用者要求接著做 Level 3：
  - 讀 Wikidot Level 3（Electrical Station）全文；出口一節 WebFetch 只拿到摘要，改直接下載頁面 HTML 讀原文確認（電梯→4 或 5、Base Gamma 的門→11、車→69）；下載 17 張照片照著做
  - 新增 `level3/`：地圖規劃（`Level3Plan`、`Level3Layout`：6 格單元迷宮，每格隨機寬／一般／窄／矮四種尺寸，矮的要蹲；多出來的通道一半用打不壞的鐵柵擋住，少數必經路上是可撬鎖的柵門）、六種大廳（`Level3Halls`：發電機房、鍋爐房、組裝線、聖所、車子房、Base Gamma 據點）、地形生成、51 個方塊、三種出口、危險（斷線觸電、黑泥燙傷、插座電人、燈管爆裂、發電機過載爆炸）、被窺視感熱點、天使嚇走獵犬、暗處的獵犬群
  - 新增 Level 3 的維度、維度類型、生態域、掉落物表、語言檔、音效、挖掘標籤；`BackroomsMod` 加一行註冊；Level 2 噴了「3」的門從此接到 Level 3
  - 新增 `tools/make_level3.py`、`docs/levels/level3/README.md`（取代企劃稿）與貼圖預覽；更新樓層索引、`README.md`、`NEXT.md`
  - 新增單元測試 `Level3LayoutTest`、`MapDump`，實機測試 `Level3ClientTest`；看截圖後把紅磚調亮、鐵柵的斑點減少
- 使用者問各層之間好不好走：用暫時的量測程式實測（量完已刪除），Level 1→2 最短路線中位數約 143 格、Level 2→3 約 196 格
- 使用者要求縮短 Level 0 的等待、照 Wiki 加路標（回頭路先不做，使用者要和朋友討論）：
  - 閃爍牆出現的時間從 4～7 分鐘改成 1.5～3 分鐘（`FlickeringWalls`）
  - Level 2：依 Wiki「一般出口常有前人留下的記號」，在噴了 1／3／4 的門附近 7 格內的直走道牆上，加紅色噴漆的樓層號與箭頭（`Level2Plan.markTo`、`Level2Layout.mark`、新方塊 `wanderer_mark`）
  - Level 3：依 Wiki「M.E.G. 在 Base Gamma 周圍立了許多路標」與「所有已知熱點都有警告標誌」，加藍色 Base Gamma 路標（14 格內）與黃黑熱點警告牌（`Level3Plan.baseTo`、`Level3Layout.sign`、新方塊 `meg_arrow_sign`、`meg_warning_sign`）
  - 單元測試確認順著箭頭一定走得到它指的門或據點、記號和路標背後一定是牆；實機測試加截圖；更新各層說明與 `NEXT.md`

## 動作記錄（Antigravity）

### 2026-11-23
- 使用 Blender 5.3（透過 MCP）製作第一隻怪物「獵犬（Hound）」並確立怪物統一風格標準（路線 A：精細扭曲純長方體骨骼 + 低飽和寫實髒污高解析貼圖），匯出交接包至 `exports/hound/` 供 Claude 測試導入遊戲：
  - `exports/hound/hound_minecraft_spec.json`（39 個關節、214 個純長方體、3 段 24fps 動畫 `idle_twitch` / `prowl_walk` / `attack_roar` 之 Minecraft `EntityModel` 座標與弧度規格）
  - `exports/hound/hound_texture.png`（512×512 寫實髒污 3×3 材質圖集）
  - `exports/hound/hound.blend`（Blender 5.3 原檔，含模型、動畫與 Level 0 比例尺參考）
  - `exports/hound/hound.glb`（僅含獵犬本體與動畫之 glTF 2.0 檔）
  - `exports/hound/hound_preview.png`、`exports/hound/hound_anim_sheet.png`、`exports/hound/README.md`
  - （Claude 註：這些檔案已搬進 `Backroom - Claude` 的 `art/monsters/hound/` 與 `assets/noclip_backrooms/`）

### 2026-11-24
- 確認 `main`、`claude`、`origin/main`、`origin/claude` 已由 Claude 同步合併於 `cdd4d1c`
- 經使用者確認同意後：
  - 移除舊的 git worktree 資料夾 `Desktop\Backroom - Claude`（含其內測試存檔）
  - 刪除主資料夾重複的暫存目錄 `exports/`（正式檔案均已在 `art/monsters/hound/` 與 `assets/noclip_backrooms/`）
  - 更新 `AGENTS.md`，改為 Claude 與 Antigravity 統一在 `Desktop\Backroom` 單一資料夾輪流工作
- 依使用者選定規格預先建立 60 個後室樓層資料夾骨架（整合 Level 1 出口表與收費亭已點名的 13 層，以及 Wikidot / Fandom / 衍生社群經典主樓層、子樓層、負樓層與隱秘樓層）：
  - 命名規則：一般層 `level<編號>`、子樓層小數點用 `p`（`level1p01`、`level4p3`）、負樓層用 `neg`（`level_neg1`）、隱秘層用英文小寫底線（`the_hub`、`level_run`、`level_fun`、`level_lx`）
  - 新增總覽索引 `docs/levels/README.md` 與 59 份各樓層企劃 `docs/levels/<層>/README.md`（保留既有的 `docs/levels/level1/README.md` 與 `level0/` 截圖不動）
  - 新增 58 個尚未開發樓層的 Java 套件資料夾 `src/main/java/dev/noclip/backrooms/<層>/.gitkeep`（既有 `level0/`、`level1/` 不動）
- 規劃完整大型合成、拆解、怪物掉落與全風格武器系統藍圖，新增 `docs/crafting/` 共 7 份規格文件：
  - `docs/crafting/README.md`（Tier 0–4 進度科技樹、五大工作站、雙軌破壞規則、遊戲內獨立《後室合成圖鑑》UI 與相容 JEI 規格）
  - `docs/crafting/01_materials_and_props.md`（既有 Level 0/1 共 42 種方塊拆解規則、24 種異常穿模家具、76 種基礎與加工素材）
  - `docs/crafting/02_monsters_bosses_loot.md`（26 種原生後室實體統一 `BoxRig` 規劃與掉落、10 隻樓層 Boss、5 種樓層寶箱池）
  - `docs/crafting/03_tools_and_stations.md`（5 座工作站、Tier 1–4 階梯式特殊破壞工具 11 種、4 套功能防具與探險消耗品配方）
  - `docs/crafting/04_weapons_melee.md`（冷兵器鍛造零件系統，含中世紀鈍器 12 把、西式冷兵器 14 把、中式冷兵器 16 把）
  - `docs/crafting/05_weapons_firearms.md`（槍械精密組裝零件樹、18 種土製／正規／異常實驗槍械、10 種細緻彈藥與 6 種戰術配件）
  - `docs/crafting/06_weapons_magic_and_chests.md`（19 件魔法與異常超自然武器、寶箱與 Boss 取得管道對照總表）
- 建立給 Claude 閱讀與技術對齊的總體企劃與協作交接書 `docs/PROJECT_PLAN_FOR_CLAUDE.md`（整合核心玩法願景、Antigravity 與 Claude 分工邊界、合成表 v1 資料驅動套件分流提案、多樓層網狀出口擴充建議、單一資料夾短期分支策略與 5 項待對齊問題）
