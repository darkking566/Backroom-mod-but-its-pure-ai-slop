# Hound（獵犬）模型與動畫匯出包（交接 Claude 測試）

此資料夾由 Antigravity 於 Blender 5.3 製作並匯出，作為本模組**第一隻怪物**與**後續所有怪物的統一風格基準**。

## 檔案清單

| 檔案 | 用途 |
| :--- | :--- |
| `hound_minecraft_spec.json` | **給 Claude 實作 Minecraft `26.2` 原生 `EntityModel` 的完整規格檔**：含所有關節父子樹（39 個關節）、214 個純長方體（`origin_mc`、`size_mc`、選用旋轉子部件）、材質 UV 區塊，以及 3 段關鍵影格動畫（已轉換為 Minecraft `ModelPart` 弧度與座標）。 |
| `hound_texture.png` | `512×512` 寫實髒污材質圖集（3×3 九宮格分區，對應 `hound_minecraft_spec.json` 內的 `texture_atlas.regions`）。 |
| `hound.blend` | Blender 5.3 原始檔，包含 `Hound_Entity`（含 144 格 FK 動畫）與 `Ref_Level0_Scale`（128px Level 0 走廊與 1.8m 玩家比例尺參考）。 |
| `hound.glb` | 僅含 `Hound_Entity` 本體與動畫的 glTF 2.0 3D 預覽檔。 |
| `hound_preview.png` | 靜態模型在真實 Level 0 環境內的參考渲染圖。 |
| `hound_anim_sheet.png` | 三段動畫 8 個代表性關鍵影格分鏡參考圖（Frames `1, 19, 55, 61, 108, 116, 122, 132`）。 |

## 怪物統一美術與技術標準

後續你或朋友新增怪物時，統一依循此標準：
- **模型架構**：路線 A — 精細扭曲純長方體骨骼（原生 Minecraft `26.2` `EntityModel` / `PartDefinition` / `CubeListBuilder`，不依賴第三方動畫函式庫）。
- **貼圖風格**：低飽和寫實髒污高解析質感（對齊 Level 0 的 128px 壁紙與地毯）。
- **尺寸上限**：需能順利通過 Level 0 最窄的 `2×3` 門洞。本獵犬靜態實測為寬 `1.04m × 長 1.77m × 高 0.95m`，建議實體 Hitbox 為 **`width = 0.95f, height = 1.05f`**。

## 給 Claude 的實作備忘

1. **座標系已轉換完成（切勿再鏡像 X）**：
   - `hound_minecraft_spec.json` 內的 `pivot_mc`、`origin_mc`、`size_mc`、`rest_rot_rad_mc` 與動畫關鍵影格 `rot_rad_mc` 均已透過行列式為 `+1` 的保手性轉換矩陣轉成 Minecraft `EntityModel` 座標（`16 px = 1 block`、Y 軸朝下地面在 `y = 24.0`、正面朝 `-Z`、歐拉順序 `XYZ` 對應 `ModelPart` 的 `xRot/yRot/zRot`）。
   - `needs_subpart: true` 的長方體帶有局部旋轉角，在 Minecraft 中需用子 `PartDefinition` 搭配 `PartPose.offsetAndRotation(...)` 建立，再於其下 `addBox(origin_mc, size_mc)`。
2. **三段內建動畫（24 fps）**：
   - `idle_twitch`（2.0s，循環）：待機低伏喘息＋突發歪頭抽搐。
   - `prowl_walk`（2.0s，循環，2 個完整步態）：四足對角潛行爬行。
   - `attack_roar`（2.0s，單次）：伏地蓄力 → 人立撲擊揮爪咆哮（甩開黑髮露眼與血牙）→ 落地回位。
3. **貼圖 UV**：
   - `LayerDefinition.create(mesh, 512, 512)` 搭配 `hound_texture.png`，每個長方體依 `uv_region: [u, v]` 呼叫 `texOffs(u, v)` 即可乾淨落入對應的 `152×152` 材質分區，不溢色。
   - 若想讓雙眼在昏暗區微亮，可為 `material == "eye"` 的兩個小方塊加上獨立的 `EyesLayer`（或 emissive render layer）。
