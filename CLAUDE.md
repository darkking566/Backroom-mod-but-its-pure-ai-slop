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
