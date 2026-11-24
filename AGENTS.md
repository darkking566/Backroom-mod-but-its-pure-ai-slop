# 協作規則

- 大家（Claude、Antigravity）統一都在 `Desktop\Backroom` 這個資料夾輪流工作（不再分兩個資料夾）；同一時間只由其中一邊修改或執行 Gradle，避免互相覆蓋或鎖檔。
- 動手前先看 git status 與 CLAUDE.md；每完成一項，只提交自己的改動並記錄於 CLAUDE.md。
- 不改寫歷史、不強制覆蓋、不推送、不發布。
- 不碰 run/saves，不代為同意 EULA。
- 程式和資源照樓層分資料夾（level0、level1、……），跨樓層共用的放 core，怪物放 monsters；什麼放哪見 README.md。
- 分工：朋友（GitHub）做 Level 0；Claude 做 Level 1 和之後的樓層、跨樓層出口、怪物 AI 與生怪、測試；Antigravity（Gemini）做合成系統、物品、工具、武器、家具、寶箱與怪物模型。細節見 `docs/PROJECT_PLAN_FOR_CLAUDE.md` 與 `docs/PROJECT_PLAN_REPLY_FROM_CLAUDE.md`。
