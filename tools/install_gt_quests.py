"""Install pack/ftbquests (the ported GregTech(TM) chapter group) into an ATM10 instance.

    py tools/install_gt_quests.py <pack/ftbquests dir> <instance minecraft dir>

Copies the chapters and reward tables, lists the group in chapter_groups.snbt after the Tech
group, and merges the lang fragments into the instance's split lang files the way ATM ships its
own chapters: chapter, chapter group and reward table names into lang/<loc>/{chapter,
chapter_group,reward_table}.snbt, quest and task text into a new lang/<loc>/chapters/<name>.snbt.
On a fresh (never launched) instance ftbquestslangsplitter then builds lang/<loc>.snbt from those
on first load. If the instance has already been launched, lang/<loc>.snbt and the .snbt_merged
copies are merged as well so the result does not depend on how the splitter reconciles them.

Every lang entry under one of the group's ids is replaced, or removed for locales with no ported
text (FTB Quests then falls back to English), because ATM10's lang files still carry ATM9 entries
under those ids. Unrelated lines are kept byte for byte. Re-running is safe.
"""
import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from port_gregtech_quests import GROUP_AFTER, GROUP_ID, Port  # noqa: E402

ID = re.compile(r'\bid: "([0-9A-F]{16})"')
KEY = re.compile(r'^\s*(([a-z_]+)\.([0-9A-F]{16})\.[a-z_]+)\s*:\s*(.*)$')


def read_fragment(path):
    """lang_fragments/<loc>.snbt -> {key: str | [str]} (the format Port.format_entry writes)."""
    entries, lines = {}, path.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        m = KEY.match(lines[i])
        i += 1
        if not m:
            continue
        key, rest = m.group(1), m.group(4).strip()
        if rest == "[":
            values = []
            while lines[i].strip() != "]":
                values.append(unquote(lines[i].strip()))
                i += 1
            i += 1
            entries[key] = values
        elif rest.startswith("["):
            entries[key] = [unquote(rest[1:-1])]
        else:
            entries[key] = unquote(rest)
    return entries


def unquote(s):
    # inverse of port_gregtech_quests.quote_lang
    assert s[0] == '"' and s[-1] == '"', s
    return re.sub(r'\\(.)', r'\1', s[1:-1])


class Merger:
    def __init__(self, group_ids):
        self.group_ids = group_ids

    merge_lang_file = Port.merge_lang_file
    format_entry = staticmethod(Port.format_entry)


def main():
    pack, mc = Path(sys.argv[1]), Path(sys.argv[2])
    src, quests = pack / "quests", mc / "config" / "ftbquests" / "quests"
    if not (quests / "chapter_groups.snbt").exists():
        sys.exit(f"{quests}: no chapter_groups.snbt")

    # which chapter owns each quest and task id, and every id the group owns
    owner, group_ids = {}, {GROUP_ID}
    for p in sorted((src / "chapters").glob("*.snbt")):
        ids = ID.findall(p.read_text(encoding="utf-8"))
        group_ids.update(ids)
        for i in ids:
            owner.setdefault(i, p.stem)
    for p in (src / "reward_tables").glob("*.snbt"):
        group_ids.update(ID.findall(p.read_text(encoding="utf-8")))

    for sub in ("chapters", "reward_tables"):
        for p in sorted((src / sub).glob("*.snbt")):
            shutil.copyfile(p, quests / sub / p.name)
    print(f"copied {len(list((src / 'chapters').glob('*.snbt')))} chapters, "
          f"{len(list((src / 'reward_tables').glob('*.snbt')))} reward tables")

    groups_path = quests / "chapter_groups.snbt"
    groups = groups_path.read_bytes().decode("utf-8")
    if GROUP_ID not in groups:
        line = next(l for l in groups.splitlines(keepends=True) if GROUP_AFTER in l)
        groups_path.write_text(groups.replace(line, line + line.replace(GROUP_AFTER, GROUP_ID), 1),
                               encoding="utf-8", newline="")
        print("listed the group in chapter_groups.snbt")

    merger = Merger(group_ids)
    lang = quests / "lang"
    for loc_dir in sorted(p for p in lang.iterdir() if p.is_dir()):
        loc = loc_dir.name
        frag = pack / "lang_fragments" / f"{loc}.snbt"
        entries = read_fragment(frag) if frag.exists() else {}
        by_target = {}
        for key, value in entries.items():
            kind, oid = key.split(".")[:2]
            target = {"chapter": "chapter", "chapter_group": "chapter_group",
                      "reward_table": "reward_table"}.get(kind) or f"chapters/{owner[oid]}"
            by_target.setdefault(target, {})[key] = value
        # every split file of the locale, so stale entries under the group's ids are cleared too
        targets = set(by_target) | {p.relative_to(loc_dir).as_posix().rsplit(".", 1)[0]
                                    for p in loc_dir.rglob("*") if p.suffix in (".snbt", ".snbt_merged")}
        stats = {"replaced": 0, "removed": 0, "files": 0}
        for target in sorted(targets):
            for suffix in (".snbt", ".snbt_merged"):
                path = loc_dir / f"{target}{suffix}"
                if path.exists() or (suffix == ".snbt" and target in by_target):
                    merger.merge_lang_file(path, path, by_target.get(target, {}), stats)
        combined = lang / f"{loc}.snbt"
        if combined.exists():
            merger.merge_lang_file(combined, combined, entries, stats)
        print(f"lang {loc}: {len(entries)} entries, stale replaced {stats['replaced']}, "
              f"removed {stats['removed']}, files written {stats['files']}")


if __name__ == "__main__":
    main()
