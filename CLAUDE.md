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

- 照 Backrooms 中文維基 Level 1 原文補板條箱與地標（完整讀過原文，含折疊的實體表與六個區段分頁）：
  - 板條箱：新增繃帶（`bandage`，吃了回 2 顆心）、皮靴、皮護腿、書與筆、腐肉／玻璃片／鐵粒／火藥等垃圾與化學品；8% 是整箱 4 瓶杏仁水；徒手撬箱 20% 被割傷（拿斧頭或劍不會）
  - 定居點地標（`SettlementMarkerBlock`、`Markers`）：柱子上掛橘布、瓶子與紙條，右鍵拿一次補給並讀紙條，指出最近據點的方向與距離
  - 更新 `tools/make_level1.py`（貼圖、模型、blockstate）、語言檔、戰利品表、`docs/levels/level1/README.md`、`Level1ClientTest`；`gradlew build` 與 `runClientGameTest` 通過
  - 還沒做（wiki 有）：撬棍與殘骸堆、福友玉／滋水槍／遷躍漿果、醫務室與裝飾房、其餘區段、更多實體（手臂、鈍人、窃皮者等）、更多據點、Level 2

- 走廊區新增兩種房間（照 wiki 的小徑房間）：醫務室（`Room.MEDICAL`，中間病床加四角桌子）與掛畫房（`Room.GALLERY`，牆壁是掛畫的方塊）；新增方塊 `hospital_bed`、`gallery_wall`、對應貼圖／模型／戰利品表／語言條目，新增 `Tile.BED`、`TABLE`、`GALLERY`；房間種類機率調整（辦公室 35%、儲藏室 25%、空房 10%、醫務室 15%、掛畫房 15%，只影響 2×2 房，迷宮與房間位置不變）；`gradlew build`（含 160 個種子的連通測試）與 `runClientGameTest` 通過
  - 還沒做：橡膠房的椅子嵌進地板、地板上的畫、小徑門前會消失的畫

- 使用者下目標「把 Level 1 原文內容除了怪物以外全部做進模組」，照中文維基原文（含折疊內容）分批做完，每批都 `gradlew build` 與 `runClientGameTest` 通過並提交（細節見 `docs/levels/level1/README.md` 的「照中文維基原文補上的內容」）：
  - 道具：撬棍、殘骸堆、福友玉、滋水槍、遷躍漿果（吃了被甩走，10 分鐘出口打不開，`LevelExits.lock`）、C.L.I.P.P. 卡、杏仁水加夜視；箱子內容更完整
  - 房間：橡膠房椅子、掛畫房地上的畫、門前會消失的畫、門邊地上的地標
  - 六個區段（`Level1Layout.sector`、`Level1Sectors`）與花園段變植物（`Gardens`）；躍金段補給箱 3 倍快
  - 五種新據點（商人之家、希波克拉底-1、Tom 的餐館、速切玩家報名點、丰饒角）與居民（`Residents`）、櫃檯（付合金）與診所病床（免費治療）；出口機率整體減半（0.03＋0.09×log10）
  - 新出口：1.2、19、22、128、154、159、218、740、800、998、998.2；通往 Level 2 的管線區（`Thresholds`）；非歐幾何傳送（`Warps`）
  - 用到 Blender 的地方：沒有，方塊模型都是 JSON 方框（Blender 在 Steam 目錄下找得到，之後做怪物可以用）
  - 停止鉤子指出還有缺口後又補上：M.E.G. 巡邏隊護送新人（`Patrols`、`Walkers`）、銜尾段施工的影子（`Workers`）、營火的煙（`Smoke`）、天然的鎖與鎖死的門（`Locks`、`NaturalLockBlock`）、樞紐順序門（`HubRoute`、`LevelExits.onUse`）、Level 38 故障小徑、Level 1.5「顛倒的牆」與 √2 的門、Alpha 基地南邊通往 1.1 的門框、往 Level 2 的管線區加儀表與粗管；每個據點、居民、鎖、施工影子都有遊戲畫面確認
  - 修掉自己的兩個 bug：巡邏隊一生出來就被「清掉舊巡邏」的載入事件刪掉（標籤要在加入世界後才加）；巡邏隊從「找人」切到「帶路」那一刻拿玩家位置當終點，立刻判定已抵達
  - 沒做：怪物（照指示）

- 居民、巡邏隊、施工影子換成自製人形（用 Blender，沿用獵犬的盒子骨架格式）：
  - 新增 `core/people/`（`Person`、`People`：八種造型各一個實體型別）與客戶端 `PersonModel`、`PersonRenderer`；`BoxRig` 多支援貼圖解析度倍率（4 倍，臉 32 像素）與依造型顯示／隱藏配件
  - `tools/make_people.py` 產生骨架 `monsters/people.json`（兩段手腳、帽子、背包、外套、圍裙、護目鏡、工具、待機／走路／揮動三段動畫）與八張 512 像素貼圖；`tools/blender_people.py` 在 Blender 5.3 無頭模式組出 `art/people/people.blend` 與八張預覽圖
  - `Residents`、`Patrols`、`Workers` 改用新實體（敵對的正義使者仍是原版掠奪者）；施工影子不再穿下界合金裝備
  - `Level1ClientTest` 新增 `people`（八種造型一排、近照、背面）

- 居民會走動（`Residents.walk`）：沒有 AI，沿用施工影子的走路程式（`Walkers`），在據點中間一次走幾格、停 1～3 秒，玩家走近 4 格內就停下轉向；客戶端測試 `people` 段檢查 30 秒內有走動
- 客戶端測試可只跑指定段落：先設環境變數 `NOCLIP_ONLY`（例如 `people,secrets`），不設就跑全部；段落名見 `Level1ClientTest.runTest`

- 走廊區新增 7 種房間變體（`CorridorPlan.Room`）：教室、圖書室、宿舍、食堂、隔間辦公室、柱廳、候診室；新增方塊書架（`book_shelf`）與隔間板（`cubicle_panel`）（128 像素貼圖、`Tile.BOOKSHELF`／`PARTITION`）；房間擺設用獨立的亂數串，迷宮和房間位置不變；`Level1ClientTest` 新增 `rooms` 段（每種房間一張照片）
  - 注意：`tools/make_level1.py` 已跟 Antigravity 手工放進去的素材脫節，整支重跑會覆蓋他的貼圖與模型（我跑完後用 git 還原了），而且會在他的 `vending_machine` 模型驗證失敗；之後新增素材請只跑新函式或先對齊
- 隔間板改成 1 格高、頂面加鋁邊、側面 4 種圖案隨機（便條紙、日曆與相框、長條圖、磨損）；新增 `tools/make_room_props.py`，只重畫書架與隔間板，不會動到其他素材
- 隔間加小東西：桌上依位置隨機放電腦（62%）、枯萎盆栽（14%）、蕨類盆栽（10%）或空桌（14%）；隔間角落 40% 放垃圾桶（原版鍋釜，新增 `Tile.BIN`）；垃圾桶後來換成自製模型（見下一條）；Antigravity 當時未提交的 Duller 改動曾讓客戶端崩潰，之後已恢復
- 自製垃圾桶方塊 `wastebasket`（穿孔鋼桶往上外擴、桶口鋁邊、裡面一堆紙團）：`tools/make_wastebasket.py` 寫方塊模型（箱子清單是唯一來源）、三張貼圖、blockstate、物品、戰利品表與語言條目；`tools/blender_wastebasket.py` 讀同一份規格在 Blender 5.3 組出 `art/props/wastebasket.blend` 與預覽圖；`Level1Blocks.WASTEBASKET` 取代原版鍋釜；`Level1ClientTest` 的 `rooms` 段加垃圾桶近照，`gradlew build` 與 `NOCLIP_ONLY=rooms` 客戶端測試通過
- 桌上盆栽也換成自製（`desk_plant_dead` 枯枝、`desk_plant_leafy` 綠葉；陶盆＋兩片交叉透明貼圖）：`tools/make_desk_plants.py`、`tools/blender_desk_plants.py`（`art/props/desk_plants.blend` 與預覽）；`Level1ChunkGenerator.deskTop` 改用這兩個方塊。`gradlew build` 通過；遊戲畫面還沒確認（客戶端測試跟別的同時跑的測試撞到同一個 build 資料夾）。注意：多個人同時跑 gradle 會互相弄壞 `build/`，單元測試曾因此出現 EOFException，單獨重跑就過
### 2026-11-24
- Level 0 巨構房間新增三種版型（每個 16×16 格的巨構區隨機選一種，`MegahallPlan.Style`）：柱廊大廳（`COLONNADE`，整片密集的 2×2 柱子、無隔牆）、分隔房間群（`ROOM_GRID`，每條大廳邊界都是牆，只在中間開 3 格寬的門）、階梯下沉劇場（`TERRACED`，越往中間越低，每圈降 1 格，最低低 3 格，沒有樓梯方塊）；原本的大廳分區維持 40%
- 新增測試 `megahallStylesAllAppearAndInteriorStaysWalkable`（四種版型都出現；內部每個可走方塊都走得到）
- `gradlew build` 通過；`runClientGameTest` 失敗在 Level 1 的「橡膠房椅子 `sunken_chair` 掉落物不對」（`b623c12` 沒有附戰利品表，與本次改動無關，尚未修）
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

- 使用者要求加入子樓層，讀 Backrooms 中文維基 Level 0.1 原文後規劃；使用者決定：先把子樓層共用做法做好、機器人全部當中立環境角色（建模要在 Blender）、入口兩種都做、球殼要又大又深又壯觀（高度與深度要夠）：
  - 共用做法（`core/`）：`Sublevels`（登記子層級：父層、隨機切出權重）、`LevelExitBlock`（右鍵出口方塊：`to`／`leave`／`cutOut`）、`Landing`（落點檢查）；根目錄 `README.md` 加「子樓層」一節；維度 ID 照規則用句點 `level_0.1`，套件 `level0p1`
  - Level 0.1 天頂站（`level0p1/`，細節見 `docs/levels/level0p1/README.md`）：維度高度 1024（`min_y=-512`），真的做成半徑 480 的球殼——外殼、約 290 格厚的甲板殼體（每 8 格一層、約 120 層）、隔艙壁、環廊、玻璃內殼、半徑 176 的中央空洞（奇點、電漿圓盤、四條輻條、大樑環、冷卻水湖）；`ZenithLayout` 是純函式，`ZenithChunkGenerator` 只負責轉成方塊
  - 殼體內容：16×16 模組（四格寬走廊＋10×10 房間）、10 種房間（臥室、廚房、健身室、水培溫室、控制室、機房、儲藏室、空房、毀損房、氣閘室）、每 48×48 角落一座螺旋梯（每圈上一層）、約十分之一的 48×48×48 水培大廳（苔蘚、池塘、杜鵑樹、天橋、光球）、冷／熱／暗的損壞區、自動門（靠近開、離開關）、站內廣播與音效
  - 出入口：Level 0 每 1 公里見方有一半會藏一扇白色艙門（區塊載入時放進去，不改變能走的路）；B.N.T.G. 信標（末影珍珠＋2 合金＋紅石）在 Level 0 直接進站；氣閘室的黃色門框回 Level 0；毀損房的雜訊裂縫隨機切出（85% Level 1、15% Level 0）
  - 55 個新方塊與貼圖、模型、blockstates、物品、掉落物表、語言、音效、挖掘標籤，全由 `tools/make_level0p1.py` 產生，預覽 `docs/levels/level0p1/textures.png`；注意：方塊 ID 全站共用，`corridor_floor`、`supply_crate` 已被 Level 1 用掉，天頂站的改叫 `station_corridor_floor`、`station_crate`（第一次跑腳本時蓋掉過 Level 1 的檔案，已用 git 還原並刪掉多餘檔案）
  - 中立機器人（`level0p1/robots/`、`src/client/.../level0p1/robots/`）：七足清洗機器人、廚房機器人、壞掉的服務機器人；`tools/make_robots.py` 產生 BoxRig 規格檔 `monsters/robots.json`、貼圖與三段動畫，`tools/blender_robots.py` 用 Blender 5.3 的 bpy 組出 `art/robots/robots.blend` 與三張預覽（容器裡用 `pip install bpy` 在虛擬環境跑，沒有 GUI）；機器人不攻擊、會閒逛、遠離玩家會消失，`ZenithRobots` 依房間在玩家附近生出（廚房→廚師、毀損／暗區→服務機器人、其他→清洗機器人）
  - 測試：`ZenithLayoutTest`（上樓、走廊到中央平台、降落點、氣閘出口、外殼封閉、區塊生成速度）；`Level0p1ClientTest`（用 Xvfb 虛擬螢幕真的跑遊戲客戶端：用信標進站、每種房間、樓梯、中庭、環廊窗、輻條與奇點、機器人、自動門、回 Level 0、切出、艙門）；這次在雲端容器裡裝了 Java 25（`/tmp/jdk25`）才能編譯與測試
  - 還沒做：站內材料的合成（Antigravity）、視聽幻覺與導航失效、機器人生怪蛋、站內更多事件

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
- 為牧蛇（`ShepherdSnake`）新增動態天花板高度適應與低頭動畫系統（修正 Level 0 三格高走廊卡牆窒息受傷）：
  - `assets/noclip_backrooms/monsters/shepherd_snake.json`：新增 `low_ceiling_stoop` 循環低頭動畫片段（於 Blender `art/monsters/shepherd_snake/shepherd_snake.blend` 測試確認姿勢與總高 `<= 2.61` 格，僅控制 `Neck_1..3` 與 `Head`，保留身體與尾部波浪擺動及下顎咬合動畫）
  - `monsters/shepherdsnake/ShepherdSnake.java`：新增 `DATA_STOOP` 同步、`CROUCH_SIZE`（`1.9×2.35`，眼高 `1.75`）、生成第一 tick 立即於 `isInWall()` 前切換姿勢避免出生窒息、站起前碰撞檢查、以及依低頭程度限制 `bulldozeObstacles` 高度避免拆毀天花板
  - `ShepherdSnakeRenderer.java`、`ShepherdSnakeModel.java`：同步 `stoop` 權重並混合 `low_ceiling_stoop`，低頭時限制抬頭仰角
  - `Level1ClientTest.java`：新增 `shepherdSnake` 實機測試段（驗證 Level 0 滿血不窒息低頭與 Level 1 五格大廳站立），`gradlew build` 與 `NOCLIP_ONLY=shepherdSnake gradlew runClientGameTest`（含 Level 0、Level 1 該段、Level 2）全部通過

- 依使用者要求同步本機與 GitHub 進度：
  - 將主工作目錄切回 `main`，在 `.gitignore` 加入 `logs/`、`__pycache__/`、`*.blend1`
  - 提交工作區既有的 Level 1 薄型相連辦公隔間板改動（`CubiclePanelBlock`、`make_cubicle_models.py`、模型與 blockstate，`b9121b0`）
  - 將 `claude` 分支尚未進主線的提交 `bdad687`（Level 0 巨構房間新增柱廊、分隔房間群與階梯下沉劇場三種版型及測試）合併進 `main`，通過 `gradlew build` 後將 `main` 與 `claude` 推送至 GitHub（無強制推送、無改寫歷史）
- Level 0.1 打磨：新增三種機器人生怪蛋（`Robots.register`、`tools/make_robot_eggs.py`、語言檔）；`gradlew build` 通過（要用 Java 25：`JAVA_HOME=C:/Program Files/Java/jdk-25.0.5.0.3`），遊戲畫面尚未確認
- Level 0.1 站內停電事件（`ZenithBlackouts`：警報＋三次閃爍→熄燈 20～50 秒→恢復；樓梯芯、欄杆燈、光球等自帶電的不熄；原本就壞的燈不會被修好；廣播三句；語言檔）；`Level0p1ClientTest` 新增 `blackout` 段，通過
- 客戶端測試現在可以只跑一部分：新增 `TestFilter`，`NOCLIP_TEST=level0p1`（逗號分隔 level0、level1、level2、level0p1）選樓層，`NOCLIP_ONLY=blackout` 選樓層內的段落（Level 1、Level 0.1 有分段；Level 0、Level 2 目前只能整層跑或不跑）；都不設就跟以前一樣全跑。只跑停電段約 40 秒
- Level 0.1 停電再打磨：停電時生出的一律是壞掉的服務機器人（`ZenithRobots.spawn`），停電時身邊偶爾有電線噴火花的聲音（`ZenithAmbience`）；`robots,blackout` 兩段通過
- Level 0.1 畫面質感：六種牆／地板／天花板各 3 個隨機變體加污漬擦痕、寢室改暖色、環境光色由深藍改成中性灰藍、天花板燈 14→13（細節見 `docs/levels/level0p1/README.md` 的「畫面質感」）；`rooms` 段實機截圖確認。重跑 `make_level0p1.py` 會重畫全部貼圖而非只有這幾張，我跑完用 git 還原了其他的
- Level 0.1 重拍水培大廳、天橋、輻條門洞、奇點的截圖（新環境光色下），換掉 `docs/levels/level0p1/screens/` 裡對應四張
- Level 0.1 奇點與電漿盤重調：球改近黑深紫帶亮脈、赤道一圈白熱邊、圓盤環距 5→6 格、紋理加亮絲暗隙；`gradlew build` 與 `middle` 段實機截圖確認
- Level 0.1 暗房與損壞區：損壞牆 3 變體（洞位置不同、一個無洞）、提亮加刮痕、垂下的電纜發光 6；`gradlew build` 與 `rooms` 段實機截圖確認
- `Level0p1ClientTest` 新增 `zones` 段（冷、熱、暗區各拍走廊與房間，`NOCLIP_ONLY=zones`）；看過截圖：冷區、熱區、暗區的牆面長得一樣（都是損壞牆），只有行為不同，看不出冷或熱，尚未處理（截圖在 `docs/levels/level0p1/screens/zone_*.png`）
- Level 0.1 冷區與熱區各有自己的牆：新增方塊 `frost_wall`、`scorched_wall`（含貼圖 3 變體、語言、掉落物），`ZenithLayout.wallKind` 改傳區域編號，冷區 2/3 結霜牆、熱區 2/3 焦黑牆、1/3 仍是損壞牆；`gradlew build` 與 `zones` 段實機截圖確認。注意：加了兩個 `Kind`，舊存檔裡已生成的區塊不變，新生成的才會有
- Level 0.1 冷區、熱區加地板外觀：新增方塊 `frost_floor`、`scorched_floor`（`ZenithLayout.floor` 改傳區域編號：1/4 磨損、3/4 之中的 2/3 結冰或焦黑）；`gradlew build` 與 `zones` 段截圖確認。`make_level0p1.py` 在 Windows 上偶爾寫檔報 Errno 22，重跑一次就好
- Level 0.1 冷區、熱區加天花板外觀：新增方塊 `frost_ceiling`、`scorched_ceiling`（`ZenithLayout.ceiling` 改傳區域編號，3/4 的板換成結霜或焦黑，燈位置不變）；`zones` 段多拍天花板，`gradlew build` 通過
- Level 0.1 完整客戶端測試（`NOCLIP_TEST=level0p1`，全部段落）在牆／地板／天花板／環境光／奇點改動後通過，約 2 分 23 秒、40 張截圖
