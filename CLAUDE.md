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

## 動作記錄（Antigravity）

### 2026-11-23
- 使用 Blender 5.3（透過 MCP）製作第一隻怪物「獵犬（Hound）」並確立怪物統一風格標準（路線 A：精細扭曲純長方體骨骼 + 低飽和寫實髒污高解析貼圖），匯出交接包至 `exports/hound/` 供 Claude 測試導入遊戲：
  - `exports/hound/hound_minecraft_spec.json`（39 個關節、214 個純長方體、3 段 24fps 動畫 `idle_twitch` / `prowl_walk` / `attack_roar` 之 Minecraft `EntityModel` 座標與弧度規格）
  - `exports/hound/hound_texture.png`（512×512 寫實髒污 3×3 材質圖集）
  - `exports/hound/hound.blend`（Blender 5.3 原檔，含模型、動畫與 Level 0 比例尺參考）
  - `exports/hound/hound.glb`（僅含獵犬本體與動畫之 glTF 2.0 檔）
  - `exports/hound/hound_preview.png`、`exports/hound/hound_anim_sheet.png`、`exports/hound/README.md`
  - （Claude 註：這些檔案已搬進 `Backroom - Claude` 的 `art/monsters/hound/` 與 `assets/noclip_backrooms/`）
