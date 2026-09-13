# Cleanroom preview defaults

## 0.27 correction

The user confirmed that 0.26 still showed glass edges. Runtime logs show the injection executed but replaced zero blocks. The candidate matcher used `gtceu:plastcrete`; GTBlocks bytecode registers the PLASTCRETE field as **`gtceu:plascrete`**. 0.27 corrects that ID at all three lookup sites. Its in-game result remains unverified.

## Evidence, September 12, 2026

ATM10 latest.log identifies gttweaks 0.24.0. At 07:12:21.130 it logs `capping expandable preview fill: max 3 -> 1` for `Ability{input_energy}`. This confirms the count injection executes. The log is intentionally emitted only once per process.

The user supplied a screenshot showing glass strips at the vertical edges and hatches on the sides. Inspection of the exact staged GT jar, `8.0.0-SNAPSHOT+20260822-141542-ef045f0`, explains both:

- `CleanroomMachine.lambda$getPattern$5` adds a separate glass predicate to the vertical edge predicate. It is visited before the nested structural candidate containing Plastcrete. Count limiting does not change that unlimited candidate selection.
- The floor and wall predicates both include energy abilities. Side hatches are allowed by this version's pattern; floor-only placement is a preferred example layout, not a formation requirement.
- The existing memory's claim that the floor only admits casing and tagged utility blocks is incomplete for this snapshot: the bottom face uses the face predicate, which also includes abilities.

## Candidate implementation

`ExpandablePreviewFillMixin` invokes `CleanroomPreviewDefaults` from `AbstractStructureHelper.populate`, immediately before `fixRotationsAndFacing`. This means moved hatches receive GT's normal facing adjustment at their new positions.

Only expandable, upright previews containing `gtceu:cleanroom` are normalized. Any explicit block/count/position preferences disable normalization. Glass is replaced only if Plastcrete is among the position's pattern candidates. Energy hatch identification uses GT's energy ability candidates; a hatch moves only to a floor position offering that same block, and its former position must offer Plastcrete. The actual pattern is not modified.

The 0.25 menu rewrite is reverted to the backed-up 0.24 implementation on this branch. Its known-broken source is retained in Git history. Its mock tests were removed with the implementation they tested; there are no automated visual tests.

## Verification still required

The 0.26.0 build compiles successfully, and the injection descriptor matches the inspected bytecode. This does not establish that the injection applies in the running client.

After restarting with 0.26.0, open a fresh cleanroom preview and check:

1. A `cleanroom preview defaults` log entry reports replaced glass and floor placement.
2. The outer shell is Plastcrete, the roof remains filter casing with the controller, and the energy hatch is in the floor.
3. Changing dimensions rebuilds the default layout correctly.
4. Explicit block/count selections are respected.
5. The previous 0.24 menu/camera behavior is retained; unresolved older menu/offset bugs may remain.
