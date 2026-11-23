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

## 動作記錄（Antigravity）

### 2026-11-23
- 使用 Blender 5.3（透過 MCP）製作第一隻怪物「獵犬（Hound）」並確立怪物統一風格標準（路線 A：精細扭曲純長方體骨骼 + 低飽和寫實髒污高解析貼圖），匯出交接包至 `exports/hound/` 供 Claude 測試導入遊戲：
  - `exports/hound/hound_minecraft_spec.json`（39 個關節、214 個純長方體、3 段 24fps 動畫 `idle_twitch` / `prowl_walk` / `attack_roar` 之 Minecraft `EntityModel` 座標與弧度規格）
  - `exports/hound/hound_texture.png`（512×512 寫實髒污 3×3 材質圖集）
  - `exports/hound/hound.blend`（Blender 5.3 原檔，含模型、動畫與 Level 0 比例尺參考）
  - `exports/hound/hound.glb`（僅含獵犬本體與動畫之 glTF 2.0 檔）
  - `exports/hound/hound_preview.png`、`exports/hound/hound_anim_sheet.png`、`exports/hound/README.md`
  - （Claude 註：這些檔案已搬進 `Backroom - Claude` 的 `art/monsters/hound/` 與 `assets/noclip_backrooms/`）
