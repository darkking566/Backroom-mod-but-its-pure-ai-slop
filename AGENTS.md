# 協作規則

- 大家（Claude、Antigravity）統一都在 `Desktop\Backroom` 這個資料夾輪流工作（不再分兩個資料夾）；同一時間只由其中一邊修改或執行 Gradle，避免互相覆蓋或鎖檔。
- 動手前先看 git status 與 CLAUDE.md；每完成一項，只提交自己的改動並記錄於 CLAUDE.md。
- 不改寫歷史、不強制覆蓋、不推送、不發布。
- 不碰 run/saves，不代為同意 EULA。
- 程式和資源照樓層分資料夾（level0、level1、……），跨樓層共用的放 core，怪物放 monsters；什麼放哪見 README.md。
- 做樓層畫面、方塊、道具、模型時照 `docs/look/STYLE_GUIDE.md`（規範：512／格、labPBR、CC0、髒舊變體編號、燈的四種狀態、光影設定）與 `docs/look/SOP.md`（每層的步驟與驗收）。
- 遊戲大方向（2026-11-25）：恐怖、逃離後室，見 `docs/HORROR_ESCAPE_PLAN.md`；跟舊企劃（`docs/GAMEPLAY_SYSTEM_PLAN.md` 等）衝突時以它為準。
- 分工：朋友（GitHub）做 Level 0；Claude 做 Level 1 和之後的樓層、跨樓層出口、怪物 AI 與生怪、測試；Antigravity（Gemini）做合成系統、物品、工具、武器、家具、寶箱與怪物模型。細節見 `docs/PROJECT_PLAN_FOR_CLAUDE.md` 與 `docs/PROJECT_PLAN_REPLY_FROM_CLAUDE.md`。
