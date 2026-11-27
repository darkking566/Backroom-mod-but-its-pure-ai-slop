@AGENTS.md

# 專案紀錄（精簡版）

2026-11-28 從 1083 行濃縮成這份；逐次細節（改了哪些檔、跑了哪些測試）都在 git 歷史（`git log`、`git show`），這裡只留現況、決定與踩雷。本檔維持 200 行內：新紀錄一項一行，同一主題合併，過時的刪掉。

## 環境

- 專案在兩台電腦上出現過：`Desktop\Backroom` 與 `D:\Workspace\Personal_Project\Backroom-mod-but-its-pure-ai-slop`，路徑以當下那台為準
- Java 25：`D:\Workspace` 這台是 `C:/Program Files/Eclipse Adoptium/jdk-25.0.5.10002-hotspot`（`JAVA_HOME` 已設）；`Desktop\Backroom` 那台最後記的是 `C:/Program Files/Microsoft/jdk-25.0.5.102-hotspot`
- Blender 5.3：`Desktop\Backroom` 那台在 `E:/SteamLibrary/steamapps/common/Blender/blender.exe`（`D:\Workspace` 這台沒找到）；使用者開著的那個連著 MCP，自己用背景模式另開，不動它
- 使用者本機 `run/` 開著 Iris＋Complementary Unbound（後室版）；推薦設定在 `docs/look/shaders/`，光影包授權不允許修改或散佈
- 照片材質一律 ambientCG CC0，快取在 `tools/.cache/`（不進 git）；出處寫進 `CREDITS.md`
- 單一資料夾 `Desktop\Backroom`（`main`）輪流工作；推送前先抓 GitHub 合併朋友的進度，經使用者同意才推，不強制推送、不改寫歷史

## 測試開關

- `gradlew build`：單元測試；`runClientGameTest -Plevels=0,1,0.1,look,horror,floors,field,frontrooms,film,itemlook,guild,props,...`（依套件挑測試類別）
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
- 授權：Found Footage／SP-Backrooms 是 GPL-3.0，不能只註明出處就搬，要不要整個模組改 GPL 由使用者與朋友決定（未決）

## 使用者定下的方向

- 遊戲大方向：恐怖、逃離後室；玩家是掉進後室的 M.E.G. 調查員（企劃全在 `docs/project/`）
- 分工見 `AGENTS.md`：朋友做 Level 0 與新樓層；Claude 做物件建模、物品擺放（2026-11-27 起）、跨樓層出口、怪物 AI 與生怪、測試；Gemini 做合成、物品、工具、武器、寶箱、怪物模型
- 建模順序：先做每層會敲掉變資源的物件（Level 0 先，以電影版為主），再做武器、醫療；合成表等建模定了再整個重做
- 物品風格：冷兵器與槍寫實；魔法類直接用 Gemini 原本的模型
- 血量：進過後室的人 10 血（主世界同），後室吃飽回血 ×0.25；每 5 級 +2 血、+10 理智
- 配額循環：5 遊戲日一期、只在家（前室／主世界）算時間、配額 100 每期 ×1.3、欠款每期 ×1.2、違約隔天日出強制再部署
- 前室研究所取代石造公會（舊公會在舊存檔自動拆除）；前室外是原版自然地形；Level 0 綠色 EXIT 門很稀有；撤離可在任何組織的據點
- Gamma 狙擊手一槍 14 傷（大於 10 血），使用者決定維持
- 被慢慢拉進後室的機制先不加；地表不要變地毯

## 各區現況

- **Level 0**（朋友）：電影級大改版（淨高 5 格、`CELL=10`、5×5 扇區一張 Stage）；環面空間 `Level0Space`＋完整鏡像 `Level0Mirror`（方塊、實體、分身）。擺東西要照範本座標（`template()`），不能照區塊掃描。還沒鏡像：音效、粒子、箱子內容與告示牌字
- **Level 0 電影物件**（Claude）：68 個物件＋VHS 錄影帶（`level0/film/`、`tools/film/`）；沒做：穿模、倒放、地毯吞沒、藍色膠帶門框；擺進生成器現在歸 Claude
- **Level 0.1 天頂站**（Claude 起、朋友續做）：球殼、房間、機器人、停電、寫實材質；審查報告全部處理完
- **Level 1 停車場**：六區段、據點、居民、出口、怪物；審查報告全部處理完；Gemini 加了四批興趣點與彩蛋
- **Level 2、3**：Claude 首版；朋友加寫實材質、怪物、據點、Gamma 大型基地（生存模式打不壞、探測門看得到袋子裡的武器）
- **Level 4**（朋友）：辦公室、三大勢力、陷阱窗、會消失的出口、雨景、10 種建築結構
- **恐怖系統**（`core/trip`、`core/quota`、`core/guild`、`core/field`、`core/locker`）：安頓、撤離倒數、死亡規則、隊伍落點、任務報告、配額、委託、無線電、異常詞綴、野戰工具、按趟加工、武器平衡、雙結局（Gemini 補完）
- **前室**（`frontrooms/`，Claude）：研究所照電影結尾重做（三層、園區、318 件 Blender 物件）、眷舍小鎮、398 的夜晚。沒做：398 好結局怎麼接結局 B、小女孩、槍店、時鐘變慢、收容區的怪物
- **寫實物品包**（`resourcepacks/realistic_items/`）：83 件（冷兵器、槍、配件、家具）；Blender 曲面網格只試做 3 件（騎士長劍、9mm 手槍、辦公椅），等使用者看過再推廣
- **畫面**：寫實材質包（Level 0、0.1、1、2、3、4）、攝影機濾鏡（F7）、每層 `Look.Mood`、規範 `docs/look/STYLE_GUIDE.md`、`SOP.md`
- 企劃待做（只寫了文件）：第一趟「跟著纜線走」與 42 句塗鴉、M.E.G. 任務主線與 Boss、研究所跟著配額升級的後續
- Gemini 留的待辦：「首次合成新物品給經驗」還沒接進合成台

## 動作記錄（Claude）

- 11-23：Level 1 停車場首版（地形、出口、閃爍牆、停電、補給箱）；合併朋友的 Level 0；11 種出口與 `core/LevelExits`；事件、據點、道具、怪物；照樓層整理資料夾（`level0/`、`level1/`、`core/`、`monsters/`）；放進獵犬與 `BoxRig`；照中文維基補完 Level 1（除怪物）；自製人形、居民走動；走廊房間變體與自製小道具
- 11-24：Level 0 巨構房間三種版型；樓層索引擴到 316 層；Level 2、Level 3 首版與路標；合併朋友的停車場大改版；子樓層共用做法與 Level 0.1 天頂站首版；寫實材質包與攝影機濾鏡（Level 0 變暗、燈減半）；`-Plevels`／`-Psections`
- 11-25：層數系統 `core/floors`、遠處落點 `LevelExits.anchor`；光影搭配與 `-Pshaders`；Level 0、0.1、1、2 的審查報告逐批修完；Level 0.1、1 寫實材質；石造公會、永久帳戶與商店（後來改成前室）；寫實物品包三批與 Blender 烘焙／網格試作；恐怖方向數值（血量、理智、怪物 ×2.5、受傷撤退）
- 11-26：安頓、撤離、被拉回、委託、經驗；EXIT 門變稀有；審查 Gemini 的現實排斥；企劃書整理成 `docs/project/`、配方文件與網狀圖產生工具；配額循環；Gamma 審查修正；合成台 Shift 吃材料、同層換床刷經驗修正；前室研究所與流程第一大批、後室進階玩法第二大批；世界生成家具補建方塊實體；Level 0 電影物件 68 個
- 11-27：Level 0 環面與完整鏡像；研究所照電影重做（建築＋室內物件）；前室眷舍小鎮、398 的夜晚與變暗；第一趟纜線企劃（只寫文件）
- 11-28：拉取上游（`30a8b740`），把本檔從 1083 行濃縮到 200 行內
- 每次推送都經使用者同意，推送前抓 GitHub 合併；沒有強制推送、沒有改寫歷史

## 動作記錄（Antigravity）

- 11-23～24：獵犬模型與怪物風格標準（路線 A 長方體骨骼）；60 層資料夾骨架；合成企劃 7 份；牧羊蛇低頭；Level 0.1 打磨（機器人生怪蛋、停電、冷熱區外觀、四種房間擺設、細節、實驗室、窗外房間海、房間版型變化）；7 隻新怪物；RPG 能力值、背包角色面板、理智幻覺；合成系統與 JEI（工作站、327 項物品、五大武器體系、3D 模型與穿戴防具、槍械瞄準與準心）；Level 2 怪物、EL3A、鐵路、異常區、雜物與工業細節、場景變化、寫實材質、隱藏出口
- 11-24～25：Blender 真曲面武器與三階進化；格線合成台（3×3～9×9）與左側配方書；多格聯動家具與拆解掉落；Level 0 家具散布（後來停用，等 Claude 建模）；Level 0 特殊大區與一般區興趣點、配電箱、Hub／Fun 預告門；現實排斥與地表滲透（後來被配額循環取代）；Level 1 興趣點四批
- 11-26：Level 3 寫實材質、四態燈、怪物生態、四個據點、Gamma 基地首版；企劃拍板（前室、雙結局）；補完 Claude 報告剩下的 5 項（場景照、偽裝人影與掃描儀、紅霧紅室、研究瓶解鎖、雙結局）；Level 0 電影級大改版；朋友實測回饋調整（停用隨機家具與巡邏隊、破壞特效、道具說明、事件降頻、區域化紅霧、大廳回 Level 0 傳送台、分層幻覺與 CC0 音效）；Level 4 首版與原文還原五項
- 11-27：Level 4 雨景、窗戶怪物、物件降頻、流浪者、牆角裂縫、建築結構四批；135 項家具貼合碰撞框 `PropShapes`；手電筒翻修（電量 5 格、汲能手電筒未放進世界、留給 Claude 擺）；濾鏡與光影相容、F7 零延遲；核心玩法順位一（雙軸難度、防刷、保險箱、中繼投放、投保、合約擴充）、順位二（隱藏能力值、四維進階、高風險武器扣血、流派徽章與武人 NPC、雙分頁面板）、順位三（樓層門禁 `LevelGates`、寶箱分級、黑市、鑑定、受困倖存者支線）；非光影模式夜視修正
