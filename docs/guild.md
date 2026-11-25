# 主世界據點：後室探險公會

規格書（`docs/企劃書/backrooms_mod_design_spec.md`）2.3、4.1 節的「主世界據點、商人、永久帳戶」。

## 玩家怎麼用

1. **先找村莊。** 每個村莊旁邊都有一座公會（平原、沙漠、莽原、雪原、針葉林的村莊都有）。
2. **公會在村莊外圍**，離村莊中心大約 90 格。煙囪上有狼煙，從村莊就看得到一道白煙；門口有一條小路通往村莊，村莊那頭立著指路牌「前方 後室探險公會」。
3. **公會就是據點**：床可以設重生點，箱子隨你放，後面一排是 Gemini 的四座工作台（拆解台、改裝鍛造台、杏仁蒸餾器、異常煉成壇）加工作台、熔爐、鐵砧、砂輪、鍛造台。裡面不會生怪物。
4. **右鍵商人**打開商店；**右鍵會長**聽他介紹公會。

| 誰 | 在哪 | 做什麼 |
| --- | --- | --- |
| 收購商 | 進門左手邊前段 | 收購後室帶回來的材料、怪物素材、Boss 核心 |
| 補給商 | 左手邊後段 | 賣繃帶、杏仁水、食物、火把、手電筒、電池、撬棍 |
| 軍需官 | 右手邊前段 | 賣冷兵器、彈藥、護甲；好槍要「錢＋稀有材料」 |
| 公會會長 | 最裡面的桌子後面 | 介紹公會（不賣東西） |

## 錢：永久帳戶

- 錢是記在玩家身上的「帳戶」，**死掉不會掉**、進出後室也不會掉（規格書 4.1）。
- 後室撿到的錢：**失落的舊硬幣**（10 點）、**雜訊錄音帶**（40 點），拿在手上**右鍵就存進帳戶**。
- 停車場（Level 1）的補給箱、有眼睛的箱子、殘骸堆，Level 2 的 EL3A 儲物箱與礦車，Level 3 的 M.E.G. 補給箱都會開到。Level 0 是朋友的，沒有加。
- 背包和商店畫面右上角看得到帳戶餘額。
- 測試指令（要有管理員權限）：`/backrooms money get`、`/backrooms money add <數量>`、`/backrooms money set <數量>`。

## 商店的貨單（改價格只要改 JSON）

貨單在 `src/main/resources/data/noclip_backrooms/shops/<角色>.json`：`buyer`（收購商）、`supplier`（補給商）、`quartermaster`（軍需官）。

```json
{
  "sells": [
    {"item": "noclip_backrooms:bandage", "count": 2, "price": 20},
    {"item": "noclip_backrooms:gun_pipe_pistol", "count": 1, "price": 220,
     "extra": {"item": "noclip_backrooms:scrap_metal", "count": 6}}
  ],
  "buys": [
    {"item": "noclip_backrooms:scrap_metal", "count": 4, "price": 8}
  ]
}
```

- `sells`：商人賣給玩家。`count` 一份幾個、`price` 一份幾點；`extra` 是另外要交的材料（可省略）。
- `buys`：商人跟玩家收。`count` 一份要交幾個，`price` 給幾點。
- 物品 ID 找不到（改名、刪掉）時，那一行會跳過，並在記錄檔寫警告 `noclip_backrooms/shops`。Gemini 改物品名稱時要一起改這裡。
- 交易一律由伺服器檢查：要站在那位商人 8 格內、帳戶夠、身上東西夠。Shift＋點按一次交易 8 份。

## 怎麼蓋出來的（技術）

- 公會是一個自訂結構（`core/guild/GuildStructure`），放置規則**完全照抄原版村莊**（`worldgen/structure_set/guilds.json`：同一個鹽值 10387312、間距 34、最小間隔 8），所以只會在「村莊起點」那個區塊被問到。
- 它先確認那裡的生態域真的會長村莊（`#noclip_backrooms:has_structure/guild` ＝ 五種村莊生態域），再往村莊的東南西北其中一邊找位置：離村莊起點 88～96 格（村莊的房子都在起點 80 格內，所以不會蓋到房子上）、左右偏移 ±16 格。
- 量 6 個點的地面高度：有水就換一邊；高低差 4 格內就用，不然 8 次裡選最平的；最平的也超過 10 格就不蓋。地形會像村莊一樣被抹平（`beard_thin`）。
- 建築（`GuildPiece`）用程式蓋，不用 NBT 結構檔；材料依旁邊的村莊換（`GuildPalette`：平原橡木、沙漠砂岩、莽原金合歡、雪原與針葉林雲杉）。門永遠朝向村莊。
- 門口的小路是另一塊（`GuildRoadPiece`），沿著地面鋪 4 格寬的土徑（沙漠用平滑砂岩），每 9 格一支燈柱，盡頭立指路牌。
- 商人是 `core/people` 的人形（收購商穿商人裝、補給商穿醫護裝、軍需官穿 M.E.G. 巡邏裝、會長穿殖民者裝），世界生成時各生一次，不會動、打不死、不會消失，頭會轉向走近的玩家；身上的標籤決定是哪個角色（`ShopRole`）。
- 木牌的字在生成階段不能直接寫（會去通知一個還不存在的世界），所以用「讀入存檔資料」的方式寫進去。

## 程式在哪

| 東西 | 檔案 |
| --- | --- |
| 帳戶、錢幣、測試指令 | `src/main/java/dev/noclip/backrooms/core/economy/`（`Wallet`、`CoinItem`、`Economy`） |
| 商店（貨單、交易、右鍵商人） | `src/main/java/dev/noclip/backrooms/core/trade/`（`Shops`、`ShopRole`、`ShopOffer`、兩個封包） |
| 商店畫面 | `src/client/java/dev/noclip/backrooms/core/trade/`（`ShopScreen`、`ShopClient`） |
| 公會結構、建築、小路 | `src/main/java/dev/noclip/backrooms/core/guild/` |
| 世界生成資料 | `data/noclip_backrooms/worldgen/structure/guild.json`、`worldgen/structure_set/guilds.json`、`tags/worldgen/...` |
| 錢幣圖示 | `tools/make_economy_items.py`（預覽 `docs/guild/items.png`） |
| 實機測試 | `src/gametest/java/dev/noclip/backrooms/guild/GuildClientTest.java`，單獨跑：`gradlew runClientGameTest -Plevels=guild` |

## 還沒做

- 鎖匠（規格書提到的開鎖與鑰匙卡）、公會委託任務、依層數或距離變動的價格。
- 地圖或指南針指向最近的公會。
- 公會之間共用的倉庫。
