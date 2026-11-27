"""Writes the crafting part of the plan straight from the game's data, so it never drifts from what is in the game:

  docs/企劃書/3-合成/配方與物品索引.md       every recipe, grouped by where it is made, and an index of every item
                                           (what makes it, what it goes into, which monsters drop it)
  docs/企劃書/3-合成/合成網狀圖.svg the whole network (vector: zoom in a browser): monsters and furniture on the left, materials, parts,
                                           and finished gear further right; lines coloured by workstation

Reads data/noclip_backrooms/backrooms_recipes/station_recipes.json (the workstations), data/noclip_backrooms/recipe/
(the vanilla crafting table), loot_table/entities/ (monster drops) and the zh_tw language file for names.

Run from the repository root after recipes change:  py -I tools/make_recipe_docs.py
"""
import collections
import glob
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data/noclip_backrooms"
LANG = ROOT / "src/main/resources/assets/noclip_backrooms/lang/zh_tw.json"
OUT = ROOT / "docs/企劃書/3-合成"

STATIONS = {
    "crafting_table": ("原版工作台 3×3", "#7f7f7f"),
    "salvage_bench": ("後室拆解台", "#8c564b"),
    "workshop_bench": ("後室改裝鍛造台 5×5", "#1f77b4"),
    "almond_distiller": ("杏仁蒸餾化學組 3×3", "#2ca02c"),
    "anomaly_altar": ("異常煉成壇 3×3", "#9467bd"),
    "spiderweb_crafting_table": ("初級蜘蛛網合成台 7×7", "#d62728"),
    "ultimate_spiderweb_crafting_table": ("終極蜘蛛網合成台 9×9", "#ff7f0e"),
}
VANILLA = {
    "amethyst_shard": "紫水晶碎片", "blaze_powder": "烈焰粉", "charcoal": "木炭", "coal": "煤炭",
    "copper_ingot": "銅錠", "diamond": "鑽石", "ender_pearl": "終界珍珠", "glass": "玻璃", "glass_bottle": "玻璃瓶",
    "gold_ingot": "金錠", "iron_ingot": "鐵錠", "oak_planks": "橡木材", "obsidian": "黑曜石", "paper": "紙",
    "redstone": "紅石粉", "string": "線", "torch": "火把",
}

zh = json.load(open(LANG, encoding="utf8"))


def name(item):
    ns, path = item.split(":", 1)
    if ns == "minecraft":
        return VANILLA.get(path, path)
    return zh.get(f"item.noclip_backrooms.{path}") or zh.get(f"block.noclip_backrooms.{path}") or path


def monster_name(entity):
    return zh.get(f"entity.noclip_backrooms.{entity}", entity)


# ---------------------------------------------------------------- read recipes

Recipe = collections.namedtuple("Recipe", "station category inputs outputs grid")
recipes = []
for r in json.load(open(DATA / "backrooms_recipes/station_recipes.json", encoding="utf8"))["recipes"]:
    inputs = collections.Counter()
    if "pattern" in r:
        for row in r["pattern"]:
            for ch in row:
                if ch != " ":
                    spec = r["key"][ch]
                    inputs[spec["item"]] += spec.get("count", 1)
        grid = "×".join([str(len(r["pattern"][0])), str(len(r["pattern"]))])
    else:
        for x in r["inputs"]:
            inputs[x["item"]] += x.get("count", 1)
        grid = ""
    outputs = collections.Counter({x["item"]: x.get("count", 1) for x in r["outputs"]})
    recipes.append(Recipe(r["station"], r.get("category", ""), dict(inputs), dict(outputs), grid))

for f in sorted(glob.glob(str(DATA / "recipe/*.json"))):
    j = json.load(open(f, encoding="utf8"))
    inputs = collections.Counter()
    if j["type"] == "minecraft:crafting_shaped":
        for row in j["pattern"]:
            for ch in row:
                if ch != " ":
                    spec = j["key"][ch]
                    inputs[spec if isinstance(spec, str) else spec.get("item", str(spec))] += 1
        grid = "×".join([str(len(j["pattern"][0])), str(len(j["pattern"]))])
    else:
        for spec in j["ingredients"]:
            inputs[spec if isinstance(spec, str) else spec.get("item", str(spec))] += 1
        grid = "無序"
    res = j["result"]
    recipes.append(Recipe("crafting_table", j.get("category", ""), dict(inputs), {res["id"]: res.get("count", 1)}, grid))

drops = collections.defaultdict(set)  # item -> monsters
for f in glob.glob(str(DATA / "loot_table/entities/*.json")):
    entity = Path(f).stem
    for item in re.findall(r'"name":\s*"((?:minecraft|noclip_backrooms):[a-z0-9_]+)"', open(f, encoding="utf8").read()):
        drops[item].add(entity)

made_by = collections.defaultdict(list)
used_in = collections.defaultdict(list)
for i, r in enumerate(recipes):
    for o in r.outputs:
        made_by[o].append(i)
    for x in r.inputs:
        used_in[x].append(i)


def fmt(counter):
    return "、".join(f"{name(k)} ×{v}" for k, v in sorted(counter.items(), key=lambda kv: name(kv[0])))


# ---------------------------------------------------------------- 配方與物品索引.md

OUT.mkdir(parents=True, exist_ok=True)
lines = ["# 全部配方與物品索引（由 `tools/make_recipe_docs.py` 從遊戲資料產生，不要手改）", "",
         "配方改了以後執行 `py -I tools/make_recipe_docs.py` 重新產生。網狀圖見 [合成網狀圖.svg](合成網狀圖.svg)（用瀏覽器打開可以放大、可以用 Ctrl+F 找物品）。", ""]
count = collections.Counter(r.station for r in recipes)
lines += ["## 各工作站的配方數", "", "| 工作站 | 配方數 |", "| --- | --- |"]
for st, (label, _) in STATIONS.items():
    lines.append(f"| {label}（`{st}`） | {count.get(st, 0)} |")
lines += ["", f"合計 {len(recipes)} 條。", ""]

lines += ["## 配方（依工作站）", ""]
for st, (label, _) in STATIONS.items():
    rows = [r for r in recipes if r.station == st]
    if not rows:
        continue
    lines += [f"### {label}", "", "| 做出 | 材料 | 格子 | 分類 |", "| --- | --- | --- | --- |"]
    for r in sorted(rows, key=lambda r: (r.category, name(next(iter(r.outputs))))):
        lines.append(f"| {fmt(r.outputs)} | {fmt(r.inputs)} | {r.grid} | {r.category} |")
    lines.append("")

lines += ["## 物品索引", "", "每一樣模組裡會出現在配方或怪物掉落的東西：怎麼來、拿去做什麼。", "",
          "| 物品 | ID | 怎麼來 | 拿去做什麼 |", "| --- | --- | --- | --- |"]
items = sorted(set(made_by) | set(used_in) | set(drops), key=name)
for item in items:
    how = sorted({STATIONS[recipes[i].station][0] for i in made_by.get(item, [])})
    if item in drops:
        how.append("怪物掉落：" + "、".join(sorted(monster_name(e) for e in drops[item])))
    if not how:
        how = ["原版取得" if item.startswith("minecraft:") else "拆解家具／寶箱／世界中撿到"]
    used = sorted({name(o) for i in used_in.get(item, []) for o in recipes[i].outputs})
    used_text = "、".join(used[:12]) + (f" 等 {len(used)} 樣" if len(used) > 12 else "")
    lines.append(f"| {name(item)} | `{item.split(':')[1]}` | {'；'.join(how)} | {used_text or '—'} |")
(OUT / "配方與物品索引.md").write_text("\n".join(lines) + "\n", encoding="utf8")

# ---------------------------------------------------------------- 合成網狀圖.svg

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import networkx as nx
from matplotlib import font_manager

for path in ("C:/Windows/Fonts/msjh.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"):
    if os.path.exists(path):
        font_manager.fontManager.addfont(path)
        plt.rcParams["font.family"] = font_manager.FontProperties(fname=path).get_name()
        break
plt.rcParams["axes.unicode_minus"] = False
plt.rcParams["svg.fonttype"] = "none"  # text stays text: sharp at any zoom, and searchable

g = nx.DiGraph()
for r in recipes:
    for x in r.inputs:
        for o in r.outputs:
            if x != o:
                g.add_edge(x, o, station=r.station)
for item, monsters in drops.items():
    if item in g:
        for m in monsters:
            g.add_edge("monster:" + m, item, station="drop")

# Layers: longest path from the sources, with loops (e.g. alloy <-> iron ingot) collapsed.
cond = nx.condensation(g)
depth = {}
for c in nx.topological_sort(cond):
    preds = list(cond.predecessors(c))
    depth[c] = 0 if not preds else 1 + max(depth[p] for p in preds)
layer = {n: depth[cond.graph["mapping"][n]] for n in g}

by_layer = collections.defaultdict(list)
for n, l in layer.items():
    by_layer[l].append(n)
label = lambda n: monster_name(n.split(":", 1)[1]) + "（怪）" if n.startswith("monster:") else name(n)
pos = {}
tallest = max(len(v) for v in by_layer.values())
for l, nodes in by_layer.items():
    nodes.sort(key=lambda n: (not n.startswith("monster:"), not n.startswith("minecraft:"), label(n)))
    offset = (tallest - len(nodes)) / 2
    for i, n in enumerate(nodes):
        pos[n] = (l * 10.0, -(i + offset))

fig_h = max(20, tallest * 0.24)
fig, ax = plt.subplots(figsize=(6 + 5.5 * (max(by_layer) + 1), fig_h))
colours = {k: c for k, (_, c) in STATIONS.items()}
colours["drop"] = "#444444"
for u, v, d in g.edges(data=True):
    (x0, y0), (x1, y1) = pos[u], pos[v]
    ax.plot([x0, x1], [y0, y1], color=colours.get(d["station"], "#999999"), alpha=0.18, linewidth=0.5)
for n, (x, y) in pos.items():
    colour = "#444444" if n.startswith("monster:") else ("#555555" if n.startswith("minecraft:") else "#222222")
    ax.plot(x, y, "o", markersize=3, color=colour)
    ax.text(x + 0.25, y, label(n), fontsize=8, va="center", ha="left", color=colour)
for l in by_layer:
    ax.text(l * 10.0, 1.5, f"第 {l} 層" if l else "來源", fontsize=16, ha="left", weight="bold")
handles = [plt.Line2D([0], [0], color=c, lw=3, label=STATIONS[k][0] if k in STATIONS else "怪物掉落")
           for k, c in colours.items()]
ax.legend(handles=handles, loc="upper right", fontsize=14, frameon=True)
ax.set_title("後室合成網狀圖：左邊是來源（怪物、原版材料、家具），越往右做得越深；線的顏色是在哪個工作站做的", fontsize=20)
ax.axis("off")
fig.tight_layout()
fig.savefig(OUT / "合成網狀圖.svg")
print(f"{len(recipes)} recipes, {len(items)} items, {g.number_of_nodes()} nodes, {max(by_layer) + 1} layers -> {OUT}")
