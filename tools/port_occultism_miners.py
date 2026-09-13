"""Port ATM9's Occultism miner recipes for GregTech ores to Occultism 1.21 (NeoForge) format.

Usage: py tools/port_occultism_miners.py <ATM9 kubejs/data/occultism/recipes/miner> <output miner dir>

ATM9 (1.20) wrote {"result": {"item": ...}, "weight": N, "conditions": [forge:not tag_empty forge:ores/x]}.
Occultism 1.21 wants the weight inside an occultism:weighted_item result and neoforge:conditions. Paths
and weights are kept, so the seven files that share a path with an Occultism recipe (amethyst, certus
quartz, electrotine, lithium, malachite, opal, topaz ore) replace its tag-based result with the exact
GregTech ore, as they did in ATM9. Recipes are gated on GregTech being loaded and the result item
existing, instead of on an ore tag. Non-GregTech files (ATM nuggets, thorium, uraninite) are skipped.
"""
import json
import pathlib
import sys

# ATM9 ids that GTCEu 8 does not register, checked against a headless registry dump.
REMAP = {
    "gtceu:deepslate_pollusite_ore": "gtceu:deepslate_pollucite_ore",   # misspelled in ATM9
    "gtceu:endstone_plutonium_ore": "gtceu:endstone_plutonium_239_ore",  # renamed in GTCEu 8
}


def main(src: pathlib.Path, out: pathlib.Path) -> None:
    written = 0
    for path in sorted(src.rglob("*.json")):
        raw = path.read_bytes()
        if not raw.strip():
            continue  # ATM9 ships an empty ores/thorium_ore.json
        data = json.loads(raw.decode("utf-8-sig"))
        item = data["result"].get("item", "")
        if not item.startswith("gtceu:"):
            continue
        item = REMAP.get(item, item)
        miner_tag = data["ingredient"]["tag"]
        recipe = {
            "neoforge:conditions": [
                {"type": "neoforge:mod_loaded", "modid": "gtceu"},
                {"type": "neoforge:item_exists", "item": item},
                {"type": "neoforge:not", "value": {"type": "neoforge:tag_empty", "tag": miner_tag}},
            ],
            "type": "occultism:miner",
            "ingredient": {"tag": miner_tag},
            "result": {"type": "occultism:weighted_item", "stack": {"count": 1, "id": item}, "weight": data["weight"]},
        }
        dest = out / path.relative_to(src)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8", newline="\n")
        written += 1
    print(f"wrote {written} recipes to {out}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
