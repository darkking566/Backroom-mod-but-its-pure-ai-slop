# 畫面風格：寫實材質與攝影機濾鏡

目標是「找到的錄影帶」那種寫實後室畫面（使用者參考的是 CurseForge 模組包 Into The Backrooms - Found Footage）。
那個模組包的東西是 GPL 授權、又只能跑在 1.20.1，所以一律自己做，不搬它的素材或程式。

## 兩個部分，都可以關掉

| 部分 | 在哪 | 怎麼關 |
|---|---|---|
| 寫實材質包（目前只有 Level 0 的試做） | `src/main/resources/resourcepacks/realistic/`，由 `core/look/Look` 登記成內建資源包，預設開啟 | 選項 → 資源包 → 關掉「後室：寫實材質」，就回到原本畫的貼圖 |
| 攝影機濾鏡（鏡頭變形、色差、柔焦、燈光泛光、偏暖的錄影機色調、暗角、雜訊、掃描線、偶爾滾過的訊號線）與 REC 畫面字 | `post_effect/camcorder.json`、`shaders/post/camcorder.fsh`、`core/look/CamcorderOverlay` | F7（控制設定裡的「後室 → 攝影機濾鏡」）；只在後室各層開，回主世界自動關 |

- 資源包是「同名覆蓋」：原本 `make_textures.py` 畫的貼圖和工具都沒動，關掉資源包就回到原樣。
- 濾鏡用的是原版的 post effect（跟旁觀苦力怕時的濾鏡同一套）。post effect 讀不到遊戲時間，所以 `Look` 每 tick 把時間寫進一張 1×1 的貼圖（`textures/effect/camcorder_clock.png`），濾鏡從那裡讀，雜訊才會動。
- 打開 GameRenderer 的 post effect 要用 mixin（`mixin/client/GameRendererAccess`）。

## 材質怎麼做

`py tools/make_level0_realistic.py` 會寫出 512 像素的彩色貼圖和 labPBR 的 `_n`、`_s`（裝 Iris 光影時才看得到），以及新舊對照 `docs/look/level0_realistic.png`。

- 照片材質來自 ambientCG（全部 CC0，可用在不公開原始碼、商業的作品）：地毯 Carpet016、髒污 Fabric028、紙紋 Wallpaper001A、水漬與霉 Wallpaper002A/B/C。第一次跑會自動下載到 `tools/.cache/ambientcg/`（不進 git）。
- 程式自己畫的：壁紙印花（照 Level 0 原始照片：細珠鍊直線之間是疊起來的 V 形，隔一欄錯開半格）、天花板礦纖板（針孔、細裂紋、斜邊）、燈板（稜鏡擴散板、燈管、死蟲）、踢腳板、烤漆金屬。
- 不裝光影時遊戲不會用法線貼圖，所以凸起、凹陷的明暗（氣泡、天花板斜邊）也畫進彩色貼圖裡。
- 每種變體的意思跟原本的一樣（水漬、滴痕、霉、補丁、氣泡、破洞……），只是做成寫實的樣子；原本的出現機率也不變。
- `_n` 會稍微柔化並降低精度，不然照片的雜訊會讓檔案大一倍。

## 看效果

`.\gradlew.bat runClientGameTest -Plevels=look`：進 Level 0，同樣幾個角度各拍三張（原本的貼圖／寫實材質／寫實材質＋濾鏡），截圖在 `build/run/clientGameTest/screenshots/`。

## 之後

這是試做。使用者看過、確定方向後，再決定：要不要全部樓層都換、資源包預設開或關、濾鏡預設開或關、Level 0 是朋友負責的（要先跟他說）。
