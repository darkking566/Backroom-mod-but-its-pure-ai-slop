# 專案規範（Claude、Antigravity 共用）

這裡只放長期有效的規則、環境、踩雷與定案方向。現況、待辦與試玩方法在 `tasks/todo.md`；逐次細節（改了哪些檔、跑了哪些測試）在 `git log`。本檔維持 100 行內，過時的刪掉。

## 協作規則

- 大家（Claude、Antigravity）都在同一個資料夾輪流工作；同一時間只由一邊修改或執行 Gradle，避免互相覆蓋或鎖檔
- 動手前先看 `git status`、本檔與 `tasks/todo.md`；每完成一項只提交自己的改動，現況或待辦有變就更新 `tasks/todo.md`
- 不改寫歷史、不強制覆蓋、不發布；推送前先抓 GitHub 合併朋友的進度，經使用者同意才推
- 不碰 `run/saves`，不代為同意 EULA
- 程式和資源照樓層分資料夾（`level0`、`level1`、……），跨樓層共用的放 `core`，怪物放 `monsters`；什麼放哪見 `README.md`
- 做樓層畫面、方塊、道具、模型時照 `docs/畫面規範/風格規範.md`（整格的面 128、道具 64、labPBR、CC0、髒舊變體編號、燈的四種狀態、光影設定）與 `docs/畫面規範/每層流程.md`（每層的步驟與驗收）
- 企劃書全部在 `docs/企劃書/`（總目錄 `docs/企劃書/README.md`）：1 遊戲玩法、2 玩家能力值、3 合成、4 怪物、5 樓層；新企劃寫進對應章節，不要在 `docs/` 底下另開企劃檔；文件的檔名與資料夾用中文（`README.md` 與圖片檔名除外，樓層資料夾是 `Level<編號>-<中文名>`）
- 外部素材（音效、照片材質、模型）的出處與授權一律寫進 `CREDITS.md`
- 分工（2026-11-26 起，細節見 `docs/企劃書/README.md` 的「分工」）：
  - 朋友（GitHub）：Level 0 與之後的新樓層
  - Claude：物件建模（先做各層會敲掉變資源的家具與物品，之後才是武器、醫療）、物品在世界裡的分布與擺放（2026-11-27 起）、跨樓層出口、怪物 AI 與生怪、測試
  - Antigravity（Gemini）：合成系統、物品、工具、武器、寶箱、怪物模型

## 環境

- Fabric・Minecraft 26.2・Java 25・模組代號 `noclip_backrooms`
- 專案在兩台電腦上出現過：`Desktop\Backroom` 與 `D:\Workspace\Personal_Project\Backroom-mod-but-its-pure-ai-slop`，路徑以當下那台為準
- Java 25：`D:\Workspace` 這台是 `C:/Program Files/Eclipse Adoptium/jdk-25.0.5.10002-hotspot`（`JAVA_HOME` 已設）；`Desktop\Backroom` 那台最後記的是 `C:/Program Files/Microsoft/jdk-25.0.5.102-hotspot`
- Blender 5.3：`Desktop\Backroom` 那台在 `E:/SteamLibrary/steamapps/common/Blender/blender.exe`（`D:\Workspace` 這台沒找到）；使用者開著的那個連著 MCP，自己用背景模式另開，不動它
- 使用者本機 `run/` 開著 Iris＋Complementary Unbound（後室版）；推薦設定在 `docs/畫面規範/光影設定/`，光影包授權不允許修改或散佈
- 照片材質一律 ambientCG CC0，快取在 `tools/.cache/`（不進 git）

## 測試開關

- `gradlew build`：單元測試；`runClientGameTest -Plevels=0,1,0.1,2,3,4,look,horror,floors,field,frontrooms,film,itemlook,props,story`（依套件挑測試類別）
- `-Psections=a,b`：只跑某層的指定段落（Level 0.1、1、3 有分段）；`-Pshaders`：測試時複製 `run/` 的 Iris、Sodium、光影包並套推薦設定
- 舊的環境變數 `NOCLIP_TEST`、`NOCLIP_ONLY` 仍可用
- 已知偶發：Level 0 隨機事件檢查（黑影時有怪物、卡住的落點）、Level 1 `hounds`／`secrets` 段、Level 1 開頭閃爍牆；重跑通常會過

## 踩雷（會重犯的）

- **兩邊同時跑 Gradle 會弄壞 `build/`**（EOFException、`build/run` 被清掉、編譯讀到寫一半的類別）：一次只一邊跑；要截圖先複製出來
- **實體要先放進世界、再加標籤**：否則會被「讀檔時清掉舊實體」的事件當場刪掉（巡邏隊、微笑者、手臂、黑影都犯過）
- **方塊 ID 全站共用**：新方塊先查有沒有撞名（`corridor_floor`、`supply_crate`、`emergency_light`、`abandoned_car` 都撞過）
- 產生工具不要整支重跑：`make_level1.py` 已跟手放的素材脫節、`make_level0p1.py` 會重畫全部貼圖、`make_manila.py` 有路徑問題、`make_level0_realistic.py` 會覆寫 `pack.mcmeta`；只跑新函式或專用的小工具（如 `make_level1_texture_fixes.py`）
- 寫實貼圖規格：整格的面 128、道具 64（含 `_n`／`_s`）；朋友新加的 512 要用 `tools/resize_realistic.py` 縮
- 世界生成只放方塊狀態不建方塊實體：靠方塊實體畫的家具會隱形 → `BackroomsProps.register` 在區塊載入時補建
- 重生事件裡新身體的附加資料可能還沒複製：判斷「進過後室」要看舊身體
- Minecraft 26.2：彩色方塊 `Blocks.BED.yellow()`、創造分頁 `CreativeModeTabEvents`、開畫面 `client.gui.setScreen`；後製濾鏡讀不到深度（畫手前被清、且深度是反的），遠處變暗改用霧
- 物品模型長度上限 3 格（超過就縮小再用 display 放大）；實體繪製一次吃四點，三角形要送成重複第三點的四邊形；第三人稱握法冷兵器 `[-90,0,0]`、槍 `[-90,180,0]`
- Windows 寫檔偶爾 Errno 22，加重試；shell 指令裡的反引號會被當成指令執行
- Complementary 的彩色光只認它自己列出的方塊，本模組的紅燈、綠燈不會投出彩色光；要改得修改光影包，授權不允許，所以不做

## 使用者定下的方向

- 遊戲大方向（2026-11-25 起）：恐怖、逃離後室；玩家是掉進後室的 M.E.G. 調查員（企劃全在 `docs/企劃書/`）
- 建模順序：先做每層會敲掉變資源的物件（Level 0 先，以電影版為主），再做武器、醫療；合成表等建模定了再整個重做
- 物品風格：冷兵器與槍寫實；魔法類直接用 Gemini 原本的模型
- 血量：進過後室的人 10 血（主世界同），後室吃飽回血 ×0.25；每 5 級 +2 血、+10 理智；根治藥只補滿血，不把上限調回 20（2026-11-28）
- 企劃書與遊戲對不上時（2026-11-28 審查）：理智扣的速度照企劃書（數字是每秒）；派遣條件、怪物傷害、前室圍牆照遊戲；Level 0 EXIT 門照企劃書（程式已是每 8 區一扇，區從 560 格變 600 格是因為 `CELL` 從 7 改 10）
- 配額循環：5 遊戲日一期、只在家（前室／主世界）算時間、配額 100 每期 ×1.3、欠款每期 ×1.2、違約隔天日出強制再部署
- 前室研究所取代石造公會（舊公會在舊存檔自動拆除）；前室外是原版自然地形；Level 0 綠色 EXIT 門很稀有（每 8 區一扇，見上一條）；撤離可在任何組織的據點
- Gamma 狙擊手一槍 14 傷（大於 10 血），使用者決定維持
- 被慢慢拉進後室的機制先不加；地表不要變地毯
- 難度以原版亮度為準（光影版整體暗 30–40% 是光影包的曝光）
- 做到 200 層以上（2026-11-28，細節見 `docs/企劃書/5-樓層/README.md` 的「做到 200 層以上的技術規劃」）：方塊貼圖之後統一 32 像素（現有的換完之前照風格規範的 128／64）；樓層維度用到才建立、整隊離開就刪除；死亡學狐霖（不噴裝、死掉觀戰、隊友到下一層復活放煙火、整隊全滅才噴裝），安頓只用來回 M.E.G.；「樓層工具包」是備用企劃，等使用者問過朋友才定
