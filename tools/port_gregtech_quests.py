"""Port All the Mods 9's GregTech(TM) FTB Quests chapter group to All the Mods 10.

ATM9 (FTB Quests 2001, Minecraft 1.20.1) keeps quest text as {atm9.quest...} keys resolved from
KubeJS lang JSON and items as 1.20 NBT. ATM10 (FTB Quests 2101 + ftbquestslangsplitter) keeps text
in config/ftbquests/quests/lang and items as 1.21 data components. This reads the ATM9 group and
writes ATM10-format files into a staging copy of the quests folder; installing is a separate copy.

    py tools/port_gregtech_quests.py <atm9 minecraft dir> <atm10 minecraft dir> <staging dir> <ids file> [tag map json]

<ids file> lists every registered item/fluid id and item tag in the ATM10 instance, one per line;
it is only used to report references that would not resolve.
"""
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

GROUP_ID = "1DA67E79B40AB130"
GROUP_AFTER = "2B51AC12041E3F89"  # ATM9 placed GregTech right after the Tech group
CHAPTERS = [
    "an_introduction", "steam_age", "low_voltage", "medium_voltage", "high_voltage",
    "extreme_voltage", "insane_voltage", "ludicrous_voltage", "zero_point_module",
    "ultimate_voltage", "ultra_high_voltage", "gregstar", "circuits",
]
REWARD_TABLES = [
    "mythic_gt_rewards", "gt_uv_basic_rewards", "gt_uhv_basic_rewards", "gt_steam_basic_rewards",
    "gt_lv_basic_rewards", "gt_ev_basic_rewards", "gt_zpm", "gt_iv_basic_rewards",
    "gt_luv_rewards", "gt_mv_basic_rewards", "gt_hv_basic_rewards", "legendary_gt_rewards",
]
# ATM9 translations that ATM10's quest lang folder also has; other locales fall back to en_us.
LOCALES = ["en_us", "es_es", "fr_fr", "it_it", "ja_jp", "ko_kr", "nb_no", "pt_br"]

# Items that do not exist in ATM10, mapped to what ATM10's own GregTech KubeJS port uses instead.
ID_REMAP = {
    "gtceu:raw_gold": "minecraft:raw_gold",
    "gtceu:endstone_plutonium_ore": "gtceu:endstone_plutonium_239_ore",
    "gtceu:raw_plutonium": "gtceu:raw_plutonium_239",
    "chemlib:carbon_dust": "gtceu:carbon_dust",
    "chemlib:coal_dust": "gtceu:coal_dust",
    "chemlib:sulfur_dust": "gtceu:sulfur_dust",
    # GTCEu 8 names: GCyM's mega blast furnace, and 7.x's oil_medium is raw_oil; trinium has no ore
    "gtceu:mega_blast_furnace": "gtceu:rotary_hearth_furnace",
    "gtceu:oil_medium_bucket": "gtceu:raw_oil_bucket",
    "gtceu:raw_trinium": "gtceu:trinium_dust",
    # the exchanger chain the GregStar needed is Pocket Storage Units in ATM10
    "exchangers:eio_exchanger_core_tier3": "pocketstorage:psu_3",
    # Industrial Foregoing registers no black holes on 1.21 (only models/lang remain); the gregstar
    # recipe takes 8x quantum barrels instead
    "industrialforegoing:black_hole_controller": "modern_industrialization:quantum_barrel",
    # starforge_recipes.js: the gregstar recipe's 8x end steel exchanger became 8x psu_4
    "exchangers:end_steel_exchanger": "pocketstorage:psu_4",
    # GTCEu 8 dropped the coin; pack/kubejs/startup_scripts/gttweaks/doge_coin.js registers it again
    "gtceu:doge_coin": "kubejs:doge_coin",
    # GTCEu 8 names the bucket after the fluid, which lost its molten_ prefix.
    "gtceu:molten_trinaquadalloy_bucket": "gtceu:trinaquadalloy_bucket",
}
# starforge_recipes.js dropped infinite_polonium: no chemical infinity cell exists in ATM10.
UNOBTAINABLE = {"expatternprovider:infinity_cell"}
UNOBTAINABLE_TASK_TITLE = "Infinite Polonium Cell (not in ATM10, check off by hand)"
# The End Steel Exchanger also needed EnderIO's prescient and weather crystals; the Pocket Storage
# Unit that replaced it does not, so those two prerequisite quests go, and the remaining one moves
# into the gap they leave.
REMOVED_QUESTS = {"5AAD0E516762BA53", "3673893C88EB5EDE"}
# Task ids whose item task becomes a fluid task: (fluid id, amount in mB).
TASK_REPLACEMENTS = {}
QUEST_POSITIONS = {"70B518EC050678AE": ("9.5d", "-6.0d")}
# English text for quests whose items were substituted; their ATM9 translations describe the old
# items, so those locales fall back to this text.
TEXT_OVERRIDES = {
    "quest.7C8DE42ADB6E1AA4.quest_subtitle": "Pocket Storage Units",
    "quest.7C8DE42ADB6E1AA4.quest_desc": [
        "Craft a Tier 4 Pocket Storage Unit to be used in the production of the GregStar. "
        "ATM10 has no Exchangers mod, so the GregStar takes these in place of End Steel Exchangers."],
    "quest.70B518EC050678AE.quest_subtitle": "Tier 3 Storage Unit",
    "quest.70B518EC050678AE.quest_desc": [
        "A Tier 3 Pocket Storage Unit, one step below the Tier 4 units the GregStar needs."],
    "quest.3BF90C250AC1ADF3.quest_subtitle": "Quantum Barrel",
    "quest.3BF90C250AC1ADF3.quest_desc": [
        "Lets add in some Modern Industrialization Quantum Barrels, just for good measure.",
        "",
        "This way, we know that the resulting Star can be contained, and its immense power wont run rampantly out of control."],
}


# ---------------------------------------------------------------------------------------------
# SNBT as FTB Library reads and writes it: newline- or comma-separated entries, typed numbers.

class Num:
    __slots__ = ("text",)

    def __init__(self, text):
        self.text = text

    def as_int(self):
        return int(re.match(r"-?\d+", self.text).group())


TOKEN = re.compile(r"[A-Za-z0-9_.+\-]+")
NUMBER = re.compile(r"-?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?[bBsSlLfFdD]?")
SIMPLE_KEY = re.compile(r"[A-Za-z0-9_.+\-]+")


class Parser:
    def __init__(self, text):
        self.s = text
        self.i = 0

    def skip(self):
        while self.i < len(self.s) and self.s[self.i] in " \t\r\n,":
            self.i += 1

    def parse(self):
        value = self.value()
        self.skip()
        if self.i != len(self.s):
            raise ValueError(f"trailing data at {self.i}")
        return value

    def value(self):
        self.skip()
        c = self.s[self.i]
        if c == "{":
            return self.compound()
        if c == "[":
            return self.list()
        if c == '"':
            return self.string()
        m = TOKEN.match(self.s, self.i)
        if not m:
            raise ValueError(f"unexpected {c!r} at {self.i}")
        self.i = m.end()
        tok = m.group()
        if tok in ("true", "false"):
            return tok == "true"
        if NUMBER.fullmatch(tok):
            return Num(tok)
        raise ValueError(f"bare word {tok!r} at {m.start()}")

    def compound(self):
        self.i += 1
        out = {}
        while True:
            self.skip()
            if self.s[self.i] == "}":
                self.i += 1
                return out
            if self.s[self.i] == '"':
                key = self.string()
            else:
                m = TOKEN.match(self.s, self.i)
                key = m.group()
                self.i = m.end()
            self.skip()
            if self.s[self.i] != ":":
                raise ValueError(f"expected ':' at {self.i}")
            self.i += 1
            out[key] = self.value()

    def list(self):
        self.i += 1
        if re.match(r"[BIL];", self.s[self.i:self.i + 2]):
            raise ValueError(f"typed array at {self.i} is not supported")
        out = []
        while True:
            self.skip()
            if self.s[self.i] == "]":
                self.i += 1
                return out
            out.append(self.value())

    def string(self):
        self.i += 1
        buf = []
        while True:
            c = self.s[self.i]
            if c == "\\":
                nxt = self.s[self.i + 1]
                buf.append({"n": "\n", "t": "\t"}.get(nxt, nxt))
                self.i += 2
            elif c == '"':
                self.i += 1
                return "".join(buf)
            else:
                buf.append(c)
                self.i += 1


def quote(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t") + '"'


def write(value, indent=0):
    pad = "\t" * (indent + 1)
    if isinstance(value, dict):
        if not value:
            return "{ }"
        lines = ["{"]
        for key in sorted(value):
            name = key if SIMPLE_KEY.fullmatch(key) else quote(key)
            lines.append(f"{pad}{name}: {write(value[key], indent + 1)}")
        lines.append("\t" * indent + "}")
        return "\n".join(lines)
    if isinstance(value, list):
        if not value:
            return "[ ]"
        if len(value) == 1 and not isinstance(value[0], (dict, list)):
            return f"[{write(value[0], indent)}]"
        lines = ["["] + [pad + write(v, indent + 1) for v in value] + ["\t" * indent + "]"]
        return "\n".join(lines)
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, Num):
        return value.text
    if isinstance(value, str):
        return quote(value)
    raise TypeError(type(value))


def read_snbt(path):
    return Parser(path.read_text(encoding="utf-8")).parse()


# ---------------------------------------------------------------------------------------------

class Port:
    def __init__(self, atm9, atm10, staging, ids_file, tag_map):
        self.q9 = atm9 / "config/ftbquests/quests"
        self.q10 = atm10 / "config/ftbquests/quests"
        self.out = staging
        self.known = set(Path(ids_file).read_text(encoding="utf-8").split())
        self.tag_map = tag_map
        self.lang9 = {}
        for loc in LOCALES:
            p = atm9 / f"kubejs/assets/kubejs/lang/{loc}.json"
            self.lang9[loc] = json.loads(p.read_text(encoding="utf-8-sig")) if p.exists() else {}
        # lang[locale][target file][key] = str | list[str]; target is "chapters/<name>", "chapter", ...
        self.lang = {loc: {} for loc in LOCALES}
        self.problems = []
        self.notes = []
        self.dropped_nbt = {}
        self.used_items = set()
        self.used_tags = set()
        self.used_fluids = set()
        self.table_refs = set()
        self.page_links = set()
        self.existing_ids = set()
        own_tables = {f"{name}.snbt" for name in REWARD_TABLES}
        for p in self.q10.rglob("*.snbt"):
            if "lang" in p.parts:
                continue
            text = p.read_text(encoding="utf-8")
            # re-running against an instance that already has the port: its own files are not a clash,
            # and leaving them out keeps generated image/reward ids stable between runs
            if (p.parent.name == "chapters" and f'group: "{GROUP_ID}"' in text) or \
                    (p.parent.name == "reward_tables" and p.name in own_tables):
                continue
            self.existing_ids.update(re.findall(r'"([0-9A-F]{16})"', text))
        self.new_ids = set()
        # Every id the ported group owns. ATM10's lang files still carry stale entries under some of
        # these (ATM9 quest ids reused for other text, e.g. chapter.415BA265E2C00859 = "Getting Started").
        self.group_ids = {GROUP_ID}
        for name in CHAPTERS:
            self.group_ids.update(re.findall(r'"([0-9A-F]{16})"', (self.q9 / f"chapters/{name}.snbt").read_text(encoding="utf-8")))
        for name in REWARD_TABLES:
            self.group_ids.update(re.findall(r'id: "([0-9A-F]{16})"', (self.q9 / f"reward_tables/{name}.snbt").read_text(encoding="utf-8")))
        self.lang_stats = {}

    # -- text ---------------------------------------------------------------------------------

    KEY = re.compile(r"\{(atm9\.[^{}]+)\}")

    def resolve(self, text, loc):
        """Resolve ATM9 {key} references. Returns None for a non-English locale that lacks a key or
        when the text has no keys at all, so FTB Quests falls back to the English entry."""
        if loc != "en_us" and not self.KEY.search(text):
            return None
        missing = []

        def sub(m):
            v = self.lang9[loc].get(m.group(1))
            if v is None:
                missing.append(m.group(1))
                return m.group(0)
            return v

        out = self.KEY.sub(sub, text)
        if missing:
            if loc == "en_us":
                self.problems.append(f"no English text for {missing}")
            else:
                return None
        # FTB Quests lang files store line breaks as a literal backslash-n
        return out.replace("\r\n", "\n").replace("\n", "\\n")

    def add_text(self, target, key, value):
        if value in (None, "", []):
            return
        if key in TEXT_OVERRIDES:
            self.lang["en_us"].setdefault(target, {})[key] = TEXT_OVERRIDES[key]
            self.notes.append(f"{key}: English text replaced for a substituted item, translations dropped")
            return
        for loc in LOCALES:
            if isinstance(value, list):
                lines = [self.resolve(v, loc) if self.KEY.search(v) or loc == "en_us" else v for v in value]
                if loc != "en_us" and (None in lines or not any(self.KEY.search(v) for v in value)):
                    continue
                resolved = lines
            else:
                resolved = self.resolve(value, loc)
                if resolved is None:
                    continue
            self.lang[loc].setdefault(target, {})[key] = resolved

    def pop_text(self, obj, target, prefix, fields):
        for field, suffix in fields:
            if field in obj:
                self.add_text(target, f"{prefix}.{suffix}", obj.pop(field))

    # -- items --------------------------------------------------------------------------------

    def convert_tag(self, tag):
        if tag in self.tag_map:
            return self.tag_map[tag]
        return "c:" + tag[len("forge:"):] if tag.startswith("forge:") else tag

    def convert_item(self, item, where):
        if isinstance(item, str):
            item_id, count, nbt = item, 1, {}
        else:
            item_id = item["id"]
            count = item["Count"].as_int() if "Count" in item else 1
            nbt = item.get("tag", {})
            extra = set(item) - {"id", "Count", "tag", "ForgeCaps"}
            if extra:
                self.problems.append(f"{where}: unhandled item fields {sorted(extra)}")
        components = {}
        handled = {"display", "Unbreakable"}

        if item_id == "itemfilters:tag":
            tag = self.convert_tag(nbt["value"])
            self.used_tags.add(tag)
            components["ftbfiltersystem:filter"] = f"item_tag({tag})"
            item_id = "ftbfiltersystem:smart_filter"
            handled.add("value")
        elif item_id == "itemfilters:or":
            ids = []
            for entry in nbt["items"]:
                if entry.get("tag"):
                    self.problems.append(f"{where}: or-filter entry with NBT {entry['id']}")
                mapped = ID_REMAP.get(entry["id"], entry["id"])
                if mapped not in ids:
                    ids.append(mapped)
            self.used_items.update(ids)
            components["ftbfiltersystem:filter"] = "or(" + "".join(f"item({i})" for i in ids) + ")"
            item_id = "ftbfiltersystem:smart_filter"
            handled.add("items")
        elif item_id == "evilcraft:dark_tank":
            fluid = nbt.get("Fluid", {})
            if "capacity" in nbt:
                components["cyclopscore:capacity"] = Num(str(nbt["capacity"].as_int()))
            if fluid and fluid.get("FluidName") != "minecraft:empty" and fluid["Amount"].as_int() > 0:
                components["cyclopscore:fluid_content"] = {
                    "amount": Num(str(fluid["Amount"].as_int())), "id": fluid["FluidName"]}
                self.used_fluids.add(fluid["FluidName"])
            handled |= {"Fluid", "capacity"}
        elif item_id == "ftbquests:custom_icon":
            components["ftbquests:icon"] = nbt["Icon"]
            handled.add("Icon")

        display = nbt.get("display", {})
        if "Name" in display:
            components["minecraft:custom_name"] = display["Name"]
        if set(display) - {"Name"}:
            self.problems.append(f"{where}: unhandled display fields {sorted(set(display) - {'Name'})}")
        if "Unbreakable" in nbt and nbt["Unbreakable"].as_int() == 1:
            components["minecraft:unbreakable"] = {}
        dropped = sorted(set(nbt) - handled)
        if dropped:
            self.dropped_nbt.setdefault(item_id, set()).update(dropped)

        item_id = ID_REMAP.get(item_id, item_id)
        self.used_items.add(item_id)
        out = {"count": Num(str(count)), "id": item_id}
        if components:
            out["components"] = components
        return out

    # -- quest objects ------------------------------------------------------------------------

    def new_id(self, seed):
        n = 0
        while True:
            h = int(hashlib.sha1(f"gttweaks:{seed}:{n}".encode()).hexdigest()[:16], 16) & 0x7FFFFFFFFFFFFFFF
            hid = format(h, "016X")
            if h > 1 and hid not in self.existing_ids and hid not in self.new_ids:
                self.new_ids.add(hid)
                return hid
            n += 1

    def convert_task(self, task, target, where):
        where = f"{where} task {task['id']}"
        self.pop_text(task, target, f"task.{task['id']}", [("title", "title")])
        if "icon" in task:
            task["icon"] = self.convert_item(task["icon"], where + " icon")
        if task["id"] in TASK_REPLACEMENTS:
            fluid, amount = TASK_REPLACEMENTS[task["id"]]
            task = {k: v for k, v in task.items() if k not in ("item", "count", "type")}
            # the compound form FluidTask.writeData saves
            task.update({"type": "fluid", "fluid": {"amount": Num(str(amount)), "id": fluid}})
            self.used_fluids.add(fluid)
            self.notes.append(f"{where}: replaced with a fluid task for {amount} mB of {fluid}")
        if task["type"] == "item":
            raw_id = task["item"] if isinstance(task["item"], str) else task["item"]["id"]
            if raw_id in UNOBTAINABLE:
                self.notes.append(f"{where}: {raw_id} has no ATM10 equivalent; task is now a checkmark")
                task = {k: v for k, v in task.items() if k not in ("item", "count")}
                task["type"] = "checkmark"
                self.add_text(target, f"task.{task['id']}.title", UNOBTAINABLE_TASK_TITLE)
            else:
                task["item"] = self.convert_item(task["item"], where)
        elif task["type"] not in ("checkmark", "dimension", "fluid"):
            self.problems.append(f"{where}: unhandled task type {task['type']}")
        return task

    def convert_reward(self, reward, target, where):
        where = f"{where} reward {reward.get('id')}"
        if "id" in reward:
            self.pop_text(reward, target, f"reward.{reward['id']}", [("title", "title")])
        if "icon" in reward:
            reward["icon"] = self.convert_item(reward["icon"], where + " icon")
        kind = reward.get("type", "item")
        if kind == "item":
            reward["item"] = self.convert_item(reward["item"], where)
        elif kind in ("loot", "random"):
            self.table_refs.add(reward["table_id"].as_int())
        elif kind not in ("xp", "xp_levels", "command"):
            self.problems.append(f"{where}: unhandled reward type {kind}")
        return reward

    def collect_page_links(self, lines):
        for line in lines:
            self.page_links.update(re.findall(r'change_page\\?",\s*\\?"value\\?":\s*\\?"([0-9A-F]{16})', line))
            self.page_links.update(re.findall(r'"change_page",\s*"value":\s*"([0-9A-F]{16})"', line))

    def convert_chapter(self, name):
        chapter = read_snbt(self.q9 / f"chapters/{name}.snbt")
        if chapter.get("group") != GROUP_ID or chapter.get("filename") != name:
            raise ValueError(f"{name}: unexpected group or filename")
        cid = chapter["id"]
        target = f"chapters/{name}"
        self.pop_text(chapter, "chapter", f"chapter.{cid}", [("title", "title")])
        if "subtitle" in chapter:
            sub = chapter.pop("subtitle")
            self.add_text("chapter", f"chapter.{cid}.chapter_subtitle", sub if isinstance(sub, list) else [sub])
        if "icon" in chapter:
            chapter["icon"] = self.convert_item(chapter["icon"], f"{name} icon")
        for index, image in enumerate(chapter.get("images", [])):
            image.setdefault("id", self.new_id(f"image:{cid}:{index}"))
            if "hover" in image:
                image["hover"] = [self.lang9["en_us"].get(h, None) or (self.resolve(h, "en_us") or h).replace("\\n", "\n")
                                  for h in image["hover"]]
        removed = [q["id"] for q in chapter.get("quests", []) if q["id"] in REMOVED_QUESTS]
        if removed:
            chapter["quests"] = [q for q in chapter["quests"] if q["id"] not in REMOVED_QUESTS]
            self.notes.append(f"{name}: removed quests {removed}")
        for quest in chapter.get("quests", []):
            if "dependencies" in quest:
                quest["dependencies"] = [d for d in quest["dependencies"] if d not in REMOVED_QUESTS]
                if not quest["dependencies"]:
                    del quest["dependencies"]
            if quest["id"] in QUEST_POSITIONS:
                quest["x"], quest["y"] = (Num(v) for v in QUEST_POSITIONS[quest["id"]])
        for quest in chapter.get("quests", []):
            qid = quest["id"]
            where = f"{name} quest {qid}"
            if "description" in quest:
                self.collect_page_links(quest["description"])
            self.pop_text(quest, target, f"quest.{qid}",
                          [("title", "title"), ("subtitle", "quest_subtitle"), ("description", "quest_desc")])
            if "icon" in quest:
                quest["icon"] = self.convert_item(quest["icon"], where + " icon")
            quest["tasks"] = [self.convert_task(t, target, where) for t in quest.get("tasks", [])]
            if "rewards" in quest:
                quest["rewards"] = [self.convert_reward(r, target, where) for r in quest["rewards"]]
        return chapter

    def convert_table(self, name):
        table = read_snbt(self.q9 / f"reward_tables/{name}.snbt")
        tid = table["id"]
        self.pop_text(table, "reward_table", f"reward_table.{tid}", [("title", "title")])
        if "icon" in table:
            table["icon"] = self.convert_item(table["icon"], f"table {name} icon")
        for index, reward in enumerate(table["rewards"]):
            reward.setdefault("id", self.new_id(f"table:{tid}:{index}"))
            self.convert_reward(reward, "reward_table", f"table {name}")
        return table

    # -- lang files ---------------------------------------------------------------------------

    @staticmethod
    def format_entry(key, value):
        if isinstance(value, list):
            if len(value) == 1:
                return [f"\t{key}: [{quote_lang(value[0])}]"]
            return [f"\t{key}: ["] + [f"\t\t{quote_lang(v)}" for v in value] + ["\t]"]
        return [f"\t{key}: {quote_lang(value)}"]

    def merge_lang_file(self, source, dest, entries, stats):
        """Merge entries into an FTB lang file, keeping every unrelated line as it is. Existing
        entries under the group's ids are replaced by the ported text, or removed when there is no
        ported text for this locale so FTB Quests falls back to English. Writes only on change."""
        # Lines are split on "\n" only and keep any "\r", so untouched lines, mixed line endings,
        # a BOM and trailing blank lines are written back byte for byte.
        eol = "\r"  # new files follow ATM10's CRLF quest files
        blocks = []
        text = None
        head, opening, preamble, closing, tail = [], "{", [], "}", [""]
        if source.exists():
            text = source.read_bytes().decode("utf-8")
            eol = "\r" if "\r\n" in text else ""
            lines = text.split("\n")
            filled = [i for i, l in enumerate(lines) if l.strip().lstrip("﻿")]
            first, last = filled[0], filled[-1]
            # Some shipped translations are hand-edited: space indents, "key :", an indented "}".
            if lines[first].strip().lstrip("﻿") != "{" or lines[last].strip() != "}":
                raise ValueError(f"{source}: not an SNBT lang file")
            head, opening, closing, tail = lines[:first], lines[first], lines[last], lines[last + 1:]
            for line in lines[first + 1:last]:
                m = re.match(r"\s*([a-z_]+\.[0-9A-F]{16}\.[a-z_]+)\s*:", line)
                if m:
                    blocks.append([m.group(1), [line]])
                elif blocks:
                    blocks[-1][1].append(line)
                else:
                    preamble.append(line)
        kept = []
        for key, block_lines in blocks:
            if key.split(".")[1] in self.group_ids:
                stats["replaced" if key in entries else "removed"] += 1
                continue
            kept.append([key, block_lines])
        for key in sorted(entries):
            block = [key, [line + eol for line in self.format_entry(key, entries[key])]]
            pos = next((i for i, b in enumerate(kept) if b[0] > key), len(kept))
            kept.insert(pos, block)
        body = [line for b in kept for line in b[1]]
        if text is None:
            opening, closing = opening + eol, closing + eol
        result = "\n".join(head + [opening] + preamble + body + [closing] + tail)
        if result == text or (text is None and not entries):
            return
        stats["files"] += 1
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(result, encoding="utf-8", newline="")

    # -- run ----------------------------------------------------------------------------------

    def run(self):
        if self.out.exists():
            shutil.rmtree(self.out)
        chapters = {name: self.convert_chapter(name) for name in CHAPTERS}
        tables = {name: self.convert_table(name) for name in REWARD_TABLES}
        group_title = self.lang9["en_us"]["atm9.chapters.group.6."]
        for loc in LOCALES:
            v = self.lang9[loc].get("atm9.chapters.group.6.")
            if v:
                self.lang[loc].setdefault("chapter_group", {})[f"chapter_group.{GROUP_ID}.title"] = v

        for name, chapter in chapters.items():
            dest = self.out / f"chapters/{name}.snbt"
            existing = self.q10 / f"chapters/{name}.snbt"
            if existing.exists() and read_snbt(existing).get("id") != chapter["id"]:
                raise ValueError(f"ATM10 has a different chapter at chapters/{name}.snbt")
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(write(chapter) + "\n", encoding="utf-8", newline="\r\n")  # ATM10's files are CRLF
        for name, table in tables.items():
            dest = self.out / f"reward_tables/{name}.snbt"
            existing = self.q10 / f"reward_tables/{name}.snbt"
            if existing.exists() and read_snbt(existing).get("id") != table["id"]:
                raise ValueError(f"ATM10 has a different reward table at reward_tables/{name}.snbt")
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(write(table) + "\n", encoding="utf-8", newline="\r\n")

        groups = (self.q10 / "chapter_groups.snbt").read_bytes().decode("utf-8")
        if GROUP_ID not in groups:  # already listed when re-running against an installed port
            line = next(l for l in groups.splitlines(keepends=True) if GROUP_AFTER in l)
            groups = groups.replace(line, line + line.replace(GROUP_AFTER, GROUP_ID), 1)
            (self.out / "chapter_groups.snbt").write_text(groups, encoding="utf-8", newline="")

        fragments = {}
        lang_root = self.q10 / "lang"
        # every locale ATM10 ships, so stale entries are cleared even where ATM9 had no translation
        for loc in sorted(p.stem for p in lang_root.glob("*.snbt")):
            by_target = self.lang.get(loc, {})
            targets = set(by_target)
            if (lang_root / loc).is_dir():
                targets |= {p.relative_to(lang_root / loc).as_posix()[:-len(".snbt_merged")]
                            for p in (lang_root / loc).rglob("*.snbt_merged")}
            stats = self.lang_stats[loc] = {"replaced": 0, "removed": 0, "files": 0}
            everything = {}
            for target in sorted(targets):
                entries = by_target.get(target, {})
                everything.update(entries)
                merged = f"lang/{loc}/{target}.snbt_merged"
                self.merge_lang_file(self.q10 / merged, self.out / merged, entries, stats)
            self.merge_lang_file(lang_root / f"{loc}.snbt", self.out / f"lang/{loc}.snbt", everything, stats)
            fragments[loc] = everything
        self.validate(chapters, tables)
        self.report(chapters, tables, group_title, fragments)
        return fragments

    def validate(self, chapters, tables):
        quest_ids = {q["id"] for c in chapters.values() for q in c.get("quests", [])}
        all_ids = set()
        for c in chapters.values():
            all_ids.add(c["id"])
            for q in c.get("quests", []):
                all_ids.add(q["id"])
                for dep in q.get("dependencies", []):
                    if dep not in quest_ids:
                        self.problems.append(f"quest {q['id']} depends on {dep}, not in the group")
        clash = all_ids & self.existing_ids
        if clash:
            self.problems.append(f"ids already used in ATM10: {sorted(clash)}")
        for link in self.page_links - quest_ids:
            self.problems.append(f"description links to missing quest {link}")
        table_ids = {int(t["id"], 16) for t in tables.values()}
        for p in (self.q10 / "reward_tables").glob("*.snbt"):
            table_ids.add(int(read_snbt(p)["id"], 16))
        for ref in self.table_refs - table_ids:
            self.problems.append(f"loot reward points at missing table {ref:016X}")
        for item in sorted(self.used_items - self.known):
            self.problems.append(f"item not in ATM10: {item}")
        for tag in sorted(self.used_tags - self.known):
            self.problems.append(f"item tag not in ATM10: {tag}")
        for fluid in sorted(self.used_fluids - self.known):
            self.problems.append(f"fluid not in ATM10: {fluid}")

    def report(self, chapters, tables, group_title, fragments):
        quests = sum(len(c.get("quests", [])) for c in chapters.values())
        print(f"group {GROUP_ID} ({group_title}): {len(chapters)} chapters, {quests} quests, {len(tables)} reward tables")
        for loc, stats in self.lang_stats.items():
            print(f"  lang {loc}: {len(fragments[loc])} entries, stale replaced {stats['replaced']}, "
                  f"stale removed {stats['removed']}, files written {stats['files']}")
        print(f"new generated ids: {len(self.new_ids)}")
        print("item NBT not carried over (no 1.21 equivalent needed for quest matching/display):")
        for item, keys in sorted(self.dropped_nbt.items()):
            print(f"  {item}: {', '.join(sorted(keys))}")
        for note in self.notes:
            print("NOTE", note)
        for problem in self.problems:
            print("PROBLEM", problem)
        print(f"{len(self.problems)} problem(s)")


def quote_lang(s):
    # values are already FTB-escaped for line breaks; only quotes and real backslashes need escaping
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    atm9, atm10, staging, ids = (Path(a) for a in sys.argv[1:5])
    tag_map = json.loads(Path(sys.argv[5]).read_text(encoding="utf-8")) if len(sys.argv) > 5 else {}
    port = Port(atm9, atm10, staging, ids, tag_map)
    fragments = port.run()
    for loc, entries in fragments.items():
        if not entries:
            continue
        dest = staging.parent / "lang_fragments" / f"{loc}.snbt"
        dest.parent.mkdir(parents=True, exist_ok=True)
        lines = [l for key in sorted(entries) for l in Port.format_entry(key, entries[key])]
        dest.write_text("\n".join(["{"] + lines + ["}"]) + "\n", encoding="utf-8", newline="\n")
    return 1 if port.problems else 0


if __name__ == "__main__":
    sys.exit(main())
