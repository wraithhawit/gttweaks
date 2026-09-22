# Refined Storage multiblock parts

GregTech's ME parts let an AE2 network feed a multiblock. These are the same thing for a Refined
Storage network. The plan is all eight: input bus, output bus, input hatch, output hatch, the two
stocking variants, and the pattern buffer with its proxy.

## Status

Six of the eight parts work end to end on the test server: the input and output buses, both fluid
hatches, and both stocking variants. Each one registers, joins a network through the capability,
moves what it should in the direction it should, keeps its configuration across a server restart,
and rejoins the network by itself afterwards.

The first four were checked on 2026-09-20 (0.30.0, md5 1aa035ec) with exact counts on both sides:
the input bus took 32 cobblestone from a 64-stack and left 32, the output bus handed over 17
diamonds and 5 gold ingots and emptied, the output hatch pushed 16000 mB of water, and the input
hatch pulled back exactly its configured 5000.

The two stocking parts were checked on 2026-09-21 (0.30.0, md5 cfa642075b6a63619ce0ca7f01f401ee);
see "The stocking side" below for what was proven and how.

None of the six is usable in normal play yet — the input parts cannot be configured without
`/gttweaks rspart`, and nothing here has a crafting recipe.

| Piece | State |
|---|---|
| `RSIntegration`, `GTTweaksRegistrate`, `GTTweaksGTAddon` | working |
| `RSMachines` eight parts, `RSCapabilities` seven (the proxy has no node) |
| `RSNodeHolder`, `RSPartActor`, `RSNetworkPart` | working |
| `RSConfiguredSlot`, `RSConfiguredItemSlot`, `RSConfiguredItemList` | working |
| `RSConfiguredFluidSlot`, `RSConfiguredFluidList`, `RSFluids` | working |
| `RSBusPartMachine`, `RSInputBusPartMachine`, `RSOutputBusPartMachine` | working |
| `RSHatchPartMachine`, `RSInputHatchPartMachine`, `RSOutputHatchPartMachine` | working |
| `RSPartCommand` (`/gttweaks rspart`) | working; `config` stands in for the UI, `extract` drives the recipe path |
| `RSConfigWidget`, `RSConfigSyncHandler`, `RSConfigActions` | written; compiles and the server starts clean, never opened in a client |
| `RSStockComponent` (`gttweaks:rs_held_stock`) | writing verified four ways on the test server; reading it back on placement never run |
| Recipes (`pack/kubejs/server_scripts/gttweaks/rs_part_recipes.js`) | all eight exist and resolve; the buffer and proxy's research recipes now exist too (`KubeResearchRecipes`), never run in a scanner or research station |
| `RSStockingItemSlot`, `RSStockingFluidSlot` | working |
| `RSStockingBusPartMachine`, `RSStockingHatchPartMachine` | working |
| `RSBufferSlot` and its recipe handlers | working; holds, persists and refunds |
| `RSPatternBufferPartMachine` | working; patterns, delivery and the recipe path all verified |
| `RSPatternBufferProxyPartMachine`, `RSProxyRecipeHandlerList` | links, grows the buffer, refunds on unlink; delegation still undriven |
| Crafting recipes | not started |
| Textures | placeholder copies under this mod's namespace, awaiting RS-styled art |

### The fluid side

The fluid parts are the item parts' shape, with two differences worth knowing before reading them.

`RSConfiguredSlot` turned out to be resource-agnostic already — it holds a `ResourceAmount` and
never asks what kind — so the fluid slots reuse it unchanged. But `CustomFluidTank` is a *class*
where `IItemHandlerModifiable` is an interface, so `RSConfiguredFluidSlot` cannot extend both it and
`RSConfiguredSlot`; it holds the configured slot as a private inner class instead. That asymmetry is
the only reason the two sides do not look identical.

`CustomFluidTank` also brings its own `INBTSerializable`, which would save the superclass's
permanently-empty tank over the real stock and wipe the part on reload. `RSConfiguredFluidSlot`
overrides `serializeNBT`/`deserializeNBT` to write the configured slot instead. Confirmed by
restarting the server with a stocked hatch: config and stock both came back.

Refined Storage gives `ItemResource` an `ofItemStack`/`toItemStack` pair and gives `FluidResource`
neither, so `RSFluids` is that missing pair. Going back to a `FluidStack` needs
`BuiltInRegistries.FLUID.wrapAsHolder(...)`; `Fluid.builtInRegistryHolder()` is deprecated.

### The stocking side

A mirror shows whatever the network has, so it asks for everything and reports the answer.
GregTech's mirror asks AE2 for `Long.MAX_VALUE`. Against Refined Storage that request reads as
**empty**, on both sides, and the reason is a narrowing cast in RS rather than anything here:

- `ItemHandlerExtractableStorage.extract` computes the amount still wanted as a `long` and passes it
  to `extractSlot(Action, IItemHandler, ItemStack, int, int)` as `(int) toExtract`. `Long.MAX_VALUE`
  arrives as `-1`, `extractItem(slot, -1, …)` yields nothing, and every slot is skipped — a chest
  behind an external storage mirrors as permanently empty.
- `VariantUtil.toFluidStack` does **not** clamp, which is the easy thing to assume from its warning.
  It logs `Truncating too large amount …` and then constructs the `FluidStack` with `(int) amount`
  regardless, so the fluid side arrives at `-1` mB and drains nothing either.

Both are read off RS 2.0.9's own sources, not inferred. `RSStockingBusPartMachine.MIRROR_REQUEST`
(`Integer.MAX_VALUE`) is what both stocking parts ask for instead. The cost is a mirror that cannot
display more than about 2.1 billion of one resource, which no caller could draw in one go anyway —
`extractItem` and `drain` both take an `int`.

`MIRROR_REQUEST` was written from those sources before either part had ever been run. The mirrors
do now report true amounts with it in place, but the pre-fix build was never run against this rig,
so the empty-mirror failure above is still a reading of RS's code and not something observed here.

**Checked on 2026-09-21** (0.30.0, md5 cfa642075b6a63619ce0ca7f01f401ee). Both parts joined the
network and went active the moment they were placed. Then, for the bus, against a 64-stack of
cobblestone in the chest behind the external storage:

| Step | Mirror reports | Chest holds |
|---|---|---|
| configured `1 x cobblestone` | 64 | 64 |
| `extract 0 16 true` → gave 16 | 64 | 64 |
| `extract 0 16 false` → gave 16 | 48 | 48 |
| block broken while mirroring 48 | — | 48 |

So the configured amount is ignored and the network's own total is reported; a simulated extract
answers the recipe without consuming anything; a real extract debits exactly once; and breaking a
mirror credits the network nothing, which is the duplication case that matters most here.

The hatch was checked the same way, with the verified **input hatch used as a measuring cup** —
configure it for more water than exists and it pulls the network dry, so its stock is an independent
readout of the network total that does not run any stocking code. (RS keeps storage contents in its
own saved data, not the block entity, so `/data get block` on a fluid storage block returns only a
storage id and cannot be used for this.) Network water measured 16000 mB, matching what the output
hatch pushed on 2026-09-20. The mirror read 16000 against its configured 1; a simulated drain of
1000 reported 1000 and left it at 16000; a real drain of 1000 took it to 15000, and an independent
re-measure returned **exactly 15000**, so the simulation took nothing and the real drain debited
once. Draining the network out from under the mirror dropped it to empty and returning the water
restored it to 15000, so it tracks down as well as up. Breaking it while it mirrored 15000 left the
network at 15000.

Across the whole run: 0 `ERROR` lines after startup, no `removed entries` exception, and no gttweaks
stack frame.

**Restart check**, same day. Both parts were configured with deliberately odd amounts — `7 x
cobblestone` and `3 mB water`, small enough that neither could be confused with a default or with
the amount being mirrored — and the server was stopped and started. Both came back with those
configurations intact, rejoined the network and went active by themselves. The mirrors were then
shown to be *live* rather than restored-stale: replacing the chest's stack with 20 cobblestone moved
the bus's mirror from 48 to 20. 0 `ERROR` lines after startup.

One transient to expect: queried about three seconds after `Done`, the bus reported `online: false`,
`active: false` and `network storage: unreachable` while still showing `joined`. Ten seconds later it
was fully online with no intervention. The network takes a moment to initialise and power after a
world load, and a part queried inside that window looks broken when it is not — worth knowing before
chasing it.

### The pattern buffer

Built, registered, and **partly verified**. It joins a network, holds and persists a slot's contents,
and gives them back on break; it has never received a real pattern, and no multiblock has drawn from
it. Read the status table above for the split.

**What GregTech's does.** `MEPatternBufferPartMachine` is an AE2 `ICraftingProvider`: it holds 27
encoded patterns, advertises them to the grid through `getAvailablePatterns()`, and when the grid
decides to craft one it calls `pushPattern(details, inputHolder)`. The buffer drops those ingredients
into one of nine `InternalSlot`s, each an item map and a fluid map keyed by stack with `long` amounts.
Each slot is then exposed to the multiblock as a **distinct** recipe handler (`isDistinct = true`), so
GregTech's recipe logic treats the nine as nine separate parallel input sets rather than one pooled
inventory — that distinctness is the whole point of the part. `MEPatternBufferProxyPartMachine` holds
no inventory of its own; it forwards to a buffer at a remembered position, which is how one buffer
feeds several multiblock structures.

**What the Refined Storage equivalent needs**, all of it public and `@API(status = STABLE)`:

- `PatternProviderNetworkNode(long energyUsage, int patterns)` — concrete and constructible, extends
  `SimpleNetworkNode`, so `RSNodeHolder` generalises to it by taking the node instead of building a
  `SimpleNetworkNode` itself.
- `node.setPattern(int, Pattern)` to publish each pattern, and
  `RefinedStorageApi.INSTANCE.getPattern(ItemStack, Level)` → `Optional<Pattern>` to turn a
  player-encoded pattern item into one. `PatternProviderItem.isValid(stack, level)` is the matching
  guard for the pattern inventory's filter.
- `node.setSink(PatternProviderExternalPatternSink)` — **we** supply the sink, and its
  `accept(Collection<ResourceAmount>, Action)` is where the ingredients arrive.
- `node.doWork()` from the part's tick, the way `refreshActive()` already runs there.

**The RS side is the easier of the two.** AE2 hands over a `KeyCounter[]` and expects the callee to
drive `pushInputsToExternalInventory`; RS hands over a `Collection<ResourceAmount>`, which is already
the shape an internal slot wants — split items from fluids by resource type and add. The `Action`
parameter carries the same simulate/execute distinction the stocking parts needed, and the same rule
applies: a simulated accept must not keep anything.

There is a second, quite different route worth naming so it is not rediscovered later.
`RefinedStorageApi.INSTANCE.addPatternProviderExternalPatternSinkFactory(...)` lets a mod register a
sink keyed by position and direction, so an ordinary RS **Autocrafter** placed against the part would
deliver into it with no pattern inventory of our own. That is less code and more idiomatic RS, but it
is not the same machine: the patterns would live in the autocrafter rather than in the buffer, one
buffer could not hold 27 of them, and it would not mirror the part this is meant to mirror. Worth
revisiting only if holding patterns ourselves turns out to be the expensive part.

**What is verified**, on the test server 2026-09-21 (0.30.0, md5 555fc41c). The part registers as a
block and item, places, joins a Refined Storage network through the capability and reports
`active: true`. A slot took 11 diamonds and 1234 mB of water together and reported both. Those
contents survived a full server stop and start unchanged. Breaking the part while it held them gave
the network back exactly 11 diamonds (chest 17 → 28) and exactly 1234 mB (network water 15000 →
16234, measured with the input hatch as a measuring cup). 0 `ERROR` lines after startup, no
`removed entries`, no gttweaks frame.

Pushing was done with a new `/gttweaks rspart <pos> buffer <slot> item|fluid …`, which reaches the
same `RSBufferSlot` the sink fills. It stands in for an autocrafting task, which cannot be raised
headlessly — an encoded pattern item needs a player at a Pattern Grid. So the holding, the refund and
the persistence are demonstrated; **the pattern publishing, the sink's own `accept` and the distinct
recipe matching are not.**

**A trap worth the whole section.** `@SaveField` on a `List<RSBufferSlot>` restored as *empty*: the
part came back from a restart with no slots and refused every delivery with "this part has 0". An
array of `INBTSerializable` round-trips through GregTech's save system and a `List` of them silently
does not. GregTech persists `InternalSlot[]` and derives its live workers from it, which reads like
style and is not — it is the only shape that survives. The slots here are now a persisted
`RSBufferSlot[]` with the live list rebuilt from it on load. Found by a restart, not by the compiler,
and it would have looked in game like a buffer that simply ignored the network.

### The proxy

A buffer can only belong to one structure. The proxy is a part in a *second* structure that points at
a buffer elsewhere and passes its multiblock's recipe questions along, so one buffer and one set of
patterns can serve several machines. It holds no patterns, no ingredients and **no network node** -
a cable laid against a proxy sees nothing, and that is why `RSCapabilities` registers seven parts and
not eight. GregTech's proxy has no grid node for the same reason.

`RSProxyRecipeHandlerList.handleRecipe` hands the whole question to the buffer's own
`RSBufferRecipeHandlerList` and returns its answer, rather than reimplementing it. The rule about a
recipe coming from one slot is subtle enough that a second copy would drift. An unlinked proxy, or one
whose buffer has been broken, returns the contents untouched - which reads to the multiblock as "this
part supplies nothing" rather than as an error.

Linking earns the buffer one more slot (`1 + proxies`, capped at the pattern count), so each
multiblock served has somewhere of its own and they do not queue behind each other. Unlinking drops
the last slot, and **its contents go back to the network rather than vanishing with it.**

GregTech links the two with a data stick, which needs a player, so `/gttweaks rspart <pos> link <x>
<y> <z>` reaches the same `setBuffer` for headless testing.

**Checked on 2026-09-21** (0.30.0, md5 a7839084). Buffer and proxy both place. Linking took the buffer
from 0 proxies and 1 slot to 1 proxy and 2 slots, and the proxy reported the link resolved. Both
survived a full server stop and start unchanged - the `Set<BlockPos>` of proxies persists where the
`List<RSBufferSlot>` did not, which fits the array-versus-list finding above rather than
contradicting it, since these elements are codec-backed positions and not `INBTSerializable`. Then 7
gold ingots were pushed into the proxy's slot and the proxy broken: the buffer went back to 0 proxies
and 1 slot, and the chest's gold went 5 to **12**, so the dropped slot was refunded exactly and not
lost. 0 `ERROR` lines after startup, no `removed entries`, no gttweaks frame.

Unverified here: no multiblock has drawn through a proxy, so the delegation in `handleRecipe` has
never actually run.

### Confirmed in a client: the network really does craft into the buffer

**2026-09-21, confirmed by the user** on the test server (0.30.0, md5 149d6093), from an ATM10(3)
client. A processing pattern of `4 iron ingot -> 1 netherite ingot` was encoded at a Pattern Grid and
loaded into the buffer with `/gttweaks rspart <pos> pattern 0`. The part reported `patterns held: 1`.
Requesting one netherite ingot from a Crafting Grid put **exactly 4 iron ingots into buffer slot 0**,
and the task then sat unfinished with nothing to complete it, which is the expected outcome with no
multiblock attached. The task was cancelled from an autocrafting monitor.

A later server restart showed the part still reporting `patterns held: 1` with the 4 iron ingots in
slot 0, so the pattern inventory and the delivered ingredients both persist — picked up incidentally
while installing the placeholder textures, not sought.

That closes the two largest unknowns in one run: Refined Storage **publishes our patterns** — it
believed it could make netherite because of one — and the **sink's `accept` delivers**, into the
right slot, in the right amount. Everything from an autocrafting request down to a filled buffer slot
now has evidence behind it.

What is still unproven is the last link only: **no multiblock has ever consumed from a slot.** The
distinct matching in `RSBufferRecipeHandlerList`, and the proxy's delegation to it, have never run
against a real recipe.

**The pattern inventory has no UI**, which is why the command above exists. Right-clicking a buffer
shows the bus base class's own inventory, not the 27 pattern slots, so without the command there is
no way to get a pattern into the part at all. The command is a testing stand-in and not the widget.

### Models and textures

**A machine needs a blockstate and two models, and this mod shipped none.** Registering a machine
through a `GTRegistrate` creates the block and item but generates no client assets; GregTech's own
machines have `assets/gtceu/blockstates/<name>.json`, `models/block/machine/<name>.json` and
`models/item/<name>.json` because its datagen writes them, and this project has no datagen at all.
The result was eight untextured parts, and it had nothing to do with which texture the model pointed
at — the model did not exist. The client log says so plainly, once anyone looks:
`Unable to load model: 'gttweaks:item/rs_pattern_buffer' ... FileNotFoundException:
gttweaks:models/item/rs_pattern_buffer.json`.

The three files per part are formulaic, so they are written directly rather than by standing up a
datagen run: a blockstate mapping the six `facing` values to one model with the rotations GregTech
uses (including the `gtceu:z: 180` it puts on `facing=up`), a block model using the `gtceu:machine`
loader with `machine` set to our id and the `hatch_machine` / `hatch_machine_color_ring` templates,
and an item model parented to the block model. Casings are `gtceu:block/casings/voltage/ev/*`,
matching the EV tier these parts register at — a tier mismatch here shows as the wrong hull colour
rather than as an error.

### Textures

The eight parts have their own texture files at
`assets/gttweaks/textures/block/overlay/rs/<machine name>.png`, and `RSMachines.overlay(...)` points
each model at its own. They are **placeholders**: every one is a copy of the GregTech AE2 overlay for
the part it mirrors, so an RS part currently looks like an ME part. RS-styled art is to come from
LavaSurf.

Copying the files rather than continuing to point at `gtceu:block/overlay/appeng/...` is the whole
value of this step. Replacing one becomes dropping a new PNG on top of the same path — no code
change, no registration change, nothing to rebuild but the resources. The stocking variants have
their own copies for exactly that reason, even though today they are byte-identical to the input
ones.

Two of them are animated and carry a `.png.mcmeta` beside them: the pattern buffer and its proxy are
16-frame 16x256 strips, at `frametime` 4 and 6 respectively with interpolation. A replacement for
either has to keep the strip shape or ship its own `.mcmeta`, or it will render as one squashed frame.

Note for anything public: these PNGs are GregTech's artwork, in a repository that is otherwise All
Rights Reserved to the author. They are fine as a private placeholder and should not outlive the
replacement.

### Proven: a multiblock runs from a buffer slot

**2026-09-21, on the test server** (0.30.0, md5 98feaf22). This was the last unproven link and it is
now closed, along with the distinctness claim the whole part exists for.

An Implosion Compressor was built entirely from `setblock`: a 3x3x3 shell of `gtceu:solid_machine_casing`
at x10-12, y100-102, z0-2, controller at `11 101 0` facing north, EV muffler at `11 102 1` facing up,
an **auto maintenance hatch**, an EV energy input hatch fed by a `gtceu:creative_energy` placed *in
front of* it, the **RS Pattern Buffer as its item input** and the **RS Output Bus as its item
output**, both reached by cables from the existing network. Its recipe, read with the new
`recipematch recipe <id>`, is `4x gtceu:flint_dust + 1x gtceu:industrial_tnt -> 3x minecraft:flint +
1x gtceu:dark_ash_dust` at 30 EU/t LV over 20 ticks.

The structure formed with both RS parts in it, and then:

| Check | Result |
|---|---|
| One recipe pushed into slot 0 | slot emptied, chest flint 9 → **12** |
| Recipe split across two slots (4 dust in 0, 1 ITNT in 1) | **did not run**, flint stayed 12, both slots untouched |
| Slot 0 completed with its own ITNT | ran, flint 12 → **15**, and **slot 1's ITNT untouched** |

The middle row is the one that matters. Those two slots together held exactly one recipe's
ingredients and the multiblock refused them, because no single slot could satisfy the recipe alone.
A pooled handler would have run it and left both patterns half-consumed. The third row shows the same
buffer running the moment one slot was complete, taking from that slot only. 0 `ERROR` lines after
startup, no `removed entries`, no gttweaks frame.

Products travelled the whole way back: the multiblock filled the RS Output Bus, which exported to the
Refined Storage network, which landed them in the chest behind the external storage. Together with
the client-confirmed delivery above, the full loop — autocrafting request, pattern published,
ingredients delivered to a slot, multiblock runs from that slot, products returned to the network —
is now evidenced end to end.

#### Three defects this found, none of which the compiler could

1. **A buffer must notify its aggregate handlers when a slot changes.** A multiblock subscribes its
   recipe logic to the tick through `RecipeHandlerList.subscribe`, driven by the handlers' own
   listeners, so a buffer that filled a slot silently would sit on perfectly good ingredients while
   the controller never looked again. `onSlotChanged` now calls `notifyListeners()` on both, as
   GregTech's does.
2. **Match on the inner ingredient, never on the `SizedIngredient`.** `SizedIngredient.test` checks
   the stack's *count* as well as its identity, and the stack being tested is a bare count-of-one
   built from the resource. Asking the sized form passed every ingredient needing exactly one and
   failed every ingredient needing more — a multiblock that ran single-item recipes and silently
   refused the rest. This is what the instrumented run caught: `handleRecipe` was being called, the
   slot matched, the 1x ITNT was satisfied and the 4x flint dust was left over every time.
3. **`maintenanceProblems: 0` means every problem is present, not none.**
   `MaintenanceHatchPartMachine.NO_PROBLEMS` is `63` and `hasMaintenanceProblems()` is `< 63`, so a
   fresh hatch reads `0` and blocks the machine. An **auto maintenance hatch** avoids the whole
   question and is what a test rig should use. This cost real time because a plain input bus failed
   in exactly the same way, which looked like proof that the buffer was innocent when in fact both
   were being blocked upstream.

Two further traps worth keeping: `data merge block` into a machine's inventory does **not** fire the
change notification, so items placed that way will not wake a multiblock on their own; and a headless
server gives no reason for an idle controller, where the controller's own UI would have named the
maintenance problem immediately.

**Expected order of work:** generalise `RSNodeHolder` to take a node; the internal slot (item and
fluid maps, `long` amounts, NBT) and its distinct recipe handlers, which is the bulk of it and is
where GregTech's `InternalSlot.handleItemInternal` is the reference; then the machine that owns the
pattern inventory, the node and the sink; then registration and the capability; then the proxy, which
needs none of the above beyond a remembered position and a forward.

### The config widget

The input bus, the input hatch and both stocking parts are configured from
`RSConfigWidget`, which replaces the bus base class's own screen. Until it existed the only way to
tell a part what to stock was `/gttweaks rspart <pos> config`, which is why the parts were
unusable in play however well the network side worked.

It is GregTech's `AEConfigWidget` re-expressed over `ResourceAmount`, deliberately down to the
controls: a grid of pairs, eight to a row, the standing order on top and the stock underneath;
left-click with something on the cursor to configure a slot from it, right-click to clear it,
left-click an empty hand on a configured slot to open the amount editor, scroll to step the amount
(ctrl doubles or halves it), and left-click the lower cell to take a stack of what the part holds.
Ingredients dragged from a recipe viewer land in the slot under the cursor, or in the first empty
one. A player who has used an ME bus has to learn nothing.

**The client never writes to a slot.** Every one of those gestures sends a synced action —
`rs_config_set`, `rs_config_clear`, `rs_config_amount`, `rs_stock_pickup`, `rs_config_set_ghost` —
which `RSConfigActions` applies on the server, and the result comes back through
`RSConfigSyncHandler`. The widget draws only what that handler has told it. This is not
belt-and-braces: a slot's configuration is the part's saved data and its stock is written by the
network sync, neither of which exists on a client, so there is nothing to draw but what was sent.

`RSConfigActions` re-checks on the server everything the widget already refuses, because a client
can be made to send anything. Two of those checks are the ones that matter. An index off the end of
the array is refused, and **a stocking part refuses both the amount change and the stock pickup**.
The second is an item-duplication hole rather than a tidiness rule: a stocking slot's stock is a
reading of what the network holds, not material the part is carrying, so handing it to a player
would create items from nothing and leave the network's own copy untouched. GregTech guards this in
its widget and not in its action; here it is guarded in both.

`isStocking()` is asked of the *list*, not of a slot, and that is forced by the fluid side.
`RSConfiguredFluidSlot` extends `CustomFluidTank` and only holds an `RSConfiguredSlot` as an inner
object, so a stocking fluid slot's stocking-ness lives on the outer tank where the configured slot
cannot see it. The list can see both, so the list is where the question is answered.

Three things are shared rather than rewritten, each for a reason:

- **`AEGuiHelper`**, GregTech's own, supplies the amount overlay, the selection overlay, and the
  formatting and parsing of amounts. Nothing in the methods used touches AE2 — they take longs and
  coordinates — and sharing them is what keeps a configured 64,000 reading the same on an RS part as
  on an ME one. `parseAmount` also evaluates expressions, so the editor accepts `64*9`, and
  duplicating that would be duplicating an expression parser.
- **Refined Storage's `ResourceRendering`**, fetched from `RefinedStorageClientApi` per resource
  class, draws the icon and builds the tooltip. This is the one place the widget is *simpler* than
  GregTech's, which branches on `isFluid` and unwraps an `AEItemKey` by hand: an item, a fluid and
  any resource type another mod registers all go through one call.
- **`ResourceCodecs.AMOUNT_STREAM_OPTIONAL_CODEC`** puts a slot on the wire, so a configured item
  keeps its data components across the network exactly as it keeps them across a save.

Two details worth knowing before touching this code. A scroll arrives in the screen's coordinates
and a click arrives in the widget's, so `onMouseScrolled` subtracts `getArea()` and `onMousePressed`
does not; GregTech's widget makes the same distinction, and getting it wrong moves the scroll target
by the widget's offset rather than failing outright. And the amount editor's panel is cached, so
`deleteCachedPanel()` before opening it is what stops the previous slot's number appearing in it.

### Carrying a broken part's stock

An input bus or hatch hands its stock back to the network when it is broken. When the network is
full or the part was not connected it cannot, and before this the stock was lost with the block.
**Dropping it into the world is not the fix** — a slot's amount is a `long` and a part can
legitimately hold hundreds of thousands of items, which as entities would be a far worse problem
than the loss. It rides on the machine item instead, in a `gttweaks:rs_held_stock` data component,
and goes back into the slots when the part is placed.

**The ordering is the whole design, and it is the difference between a fix and a duplication bug.**
GregTech builds the drops before it removes the block: `MetaMachineBlock.getDrops` calls
`modifyDrops` on the machine and then, for each dropped machine item, `saveToItem`, which reaches
`collectImplicitComponents`. The flush in `onMachineDestroyed` runs later still, from
`MetaMachineBlock.onRemove`. So the flush was always too late to affect what the item carried, and
writing the stock onto the item without moving the flush would have handed the player a copy of
everything the network was about to be given. `modifyDrops` is therefore overridden to flush there,
which leaves `collectImplicitComponents` looking at exactly the remainder the network refused.

Two consequences of flushing in `modifyDrops` are worth stating. It is harmless if something ever
calls `getDrops` without breaking the block: the slots empty into the network and the next sync
pulls the same amount back. And the later `onMachineDestroyed` flush is now a second attempt on
slots that hold only what the network already refused in the same tick, so it can only fail again —
nothing can free network space between the two calls.

**A stocking part is excluded**, and that exclusion is the same item-safety rule as everywhere else
in these parts: its stock is a reading of what the network holds, not material it carries, so
writing it onto the item would hand the player a second copy of items the network still has.
`RSStockComponent.collectInto` asks the slot list and does nothing when it says stocking.

Only the stock travels, never the configuration, and that is what makes the restored stock safe: a
placed part holding something it is not configured for sees it as excess and pushes it into the
network on its first sync. If the new network is full too, it keeps holding it and tries again, as
before. Carrying the configuration as well would be a usability improvement and a separate decision;
GregTech copies configuration with a data stick instead.

**Verified on the test server 2026-09-22** (0.30.0, md5 e90c3447), four cases, 0 `ERROR` lines after
`Done` and no gttweaks frame:

- *Network online.* Bus configured 32 cobblestone against a 64-stack behind an external storage,
  stocked 32, chest at 32. Broken: chest back to **64** and the dropped item carried **no**
  component at all — `{count: 1, id: "gttweaks:rs_input_bus"}`. The network got everything and the
  item got nothing, which is the case that would have duplicated.
- *Network stranded.* Same bus holding 32, then the controller, the cable and the external storage
  removed so it reported `online: false`, `network storage: unreachable` while still holding its
  32. Broken: the item carried `gttweaks:rs_held_stock` with `amount: 32L` of
  `minecraft:cobblestone` in slot 0 and fifteen empty slots after it.
- *Stocking bus.* Configured 1 cobblestone, mirroring 64, then stranded the same way. Broken: the
  item carried **no** component and the chest still held its **64**.
- *Fluid hatch.* Configured 5000 mB water, stocked 5000 from 16000 mB in the network, stranded,
  broken: the item carried `amount: 5000L` of `minecraft:water` across its eight slots. This is the
  case that exercises `RSConfiguredFluidList.getConfigurableSlot` reaching through
  `RSConfiguredFluidSlot.slot()`, which is the fluid side's one structural difference.

**The restore half is unverified.** Placing a block from an item that carries components needs a
player, which a headless server has not got, so `applyImplicitComponents` has never run. Vanilla
calls it from `BlockItem`'s placement path and GregTech's own override is empty, so nothing should
stand in the way — but nothing has demonstrated it either. Place a carried part back on a live
network and watch the stock return to the network on its first sync.

### Recipes

All eight parts are craftable as of 2026-09-22, in
`pack/kubejs/server_scripts/gttweaks/rs_part_recipes.js`. They mirror GregTech's own ME part
recipes, read out of `MetaTileEntityMachineRecipeLoader` (which builds them in Java rather than
shipping JSON), ingredient for ingredient and tier for tier:

| Part | Machine | Base part | Other GregTech | Refined Storage | Time / EU/t |
|---|---|---|---|---|---|
| input/output bus, input/output hatch | assembler | plain part, **EV** | — | 1 interface, 2 speed upgrade | 300t, VA[HV] |
| stocking bus | assembler | input bus, **IV** | IV conveyor, IV sensor | 1 interface, 4 speed upgrade | 300t, VA[IV] |
| stocking hatch | assembler | input hatch, **IV** | IV electric pump, IV sensor | 1 interface, 4 speed upgrade | 300t, VA[IV] |
| pattern buffer | assembly line | dual input hatch, **LuV** | LuV emitter, 4 LuV circuits, 3x32 fine europium wire, 576 soldering alloy, 500 lubricant | 3 autocrafter, 3 interface, 4 speed upgrade, 2 stack upgrade | 600t, VA[LuV] |
| pattern buffer proxy | assembly line | machine hull, **LuV** | 2 LuV sensors, 1 LuV circuit, 2x32 fine europium wire, 576 soldering alloy, 500 lubricant | 1 network transmitter, 2 network receiver | 600t, VA[ZPM] |

**The tiers are GregTech's, not ours.** Every part registers at EV — that is what the casing texture
and the energy tier say — so a stocking part costs an IV bus and the buffer costs a LuV dual hatch
while looking like EV machines. The user chose this on 2026-09-22 over flattening everything to EV:
what a part costs should track what it does, and these do what GregTech's ME parts cost that much to
do.

The five AE2 → RS swaps, all confirmed present in Refined Storage 2.0.9:

| AE2 | Refined Storage | Why |
|---|---|---|
| ME Interface (`AEParts.INTERFACE`, `AEBlocks.INTERFACE`) | `refinedstorage:interface` | RS has one interface where AE2 has a part and a block |
| Acceleration Card | `refinedstorage:speed_upgrade` | direct equivalent |
| Pattern Provider | `refinedstorage:autocrafter` | RS 2.0's autocrafter is what holds patterns and offers them to the network; the Pattern Grid only *encodes* them |
| Capacity Card | `refinedstorage:stack_upgrade` | closest equivalent |
| Quantum Link + Quantum Ring | `network_transmitter` + `network_receiver` | RS has no quantum bridge; this pair is how it joins two points of one network, which is what a proxy does |

**Verified on the test server 2026-09-22**, by `/gttweaks recipematch` after a `/reload` and again
after a full restart, 0 KubeJS errors: all eight recipes exist, with the tiers and durations above,
and every ingredient resolves — `#c:fine_wires/europium` to `gtceu:fine_europium_wire`,
`#gtceu:circuits/luv` to the four LuV circuit items, and all six Refined Storage ids. The three
separate 32-wire entries stay three separate assembly-line slots, as GregTech's do.

#### The research step was not craftable, and KubeJS was why

GregTech gates its pattern buffer behind a Scanner research and its proxy behind a Research Station
research, and `enableResearch` is `true` in this pack, so ours ask for the same. **Until
`KubeResearchRecipes` (below), the research items could not be made.** `scannerResearch`/`stationResearch` put the requirement on the assembly line
recipe, but the companion recipe that produces the data stick or data module is never emitted
through the KubeJS path: after a `/reload` and after a full server restart, the scanner recipe list
is unchanged at 24 entries and the research station list at 14, with nothing for our parts under
either namespace or either id spelling. GregTech's own research recipes are there because its
datagen writes them into the jar.

Two smaller findings from the same corner, both recorded so nobody repeats the hour:

- **A scanner research recipe is named after the item being scanned**, not after what is being
  researched: `gtceu:scanner/1_x_gtceu_luv_dual_input_hatch`. Researching our buffer from the same
  hatch GregTech researches its ME Pattern Buffer from would put two recipes under one id. The
  script uses the **LuV emitter** instead, which is in the recipe anyway, is the same tier, and is
  scanned by nothing else.
- **The scan's EU/t cannot be set from KubeJS at all.** `GTKubeRecipe` has both
  `scannerResearch(ItemStack)` and `scannerResearch(UnaryOperator)`; Rhino picks the ItemStack
  overload for a bare arrow function and fails to parse it, calls a real `UnaryOperator` adapter
  ambiguous between the two, and KubeJS's class filter blocks `java.lang.Class`, so reflection
  cannot name the builder form either. The ItemStack form applies the defaults: duration 1200, which
  is what GregTech sets, and `VA[HV]`, where GregTech sets `VA[LuV]`.

The choices were to drop the requirement, hand-write the research recipes as JSON, move the two
recipes into Java, or fix the KubeJS path itself for everyone. The last one is what was done; see
"The fix: `KubeResearchRecipes`" below.

#### This is a pack-wide bug, not ours

`GTKubeRecipe.applyResearchProperty` reads `enableResearch` and, when it is on, adds a
`ResearchCondition` to the recipe. It bails only when research is disabled. So the requirement is
applied and the companion recipe is not, and **every KubeJS recipe in this pack that asks for
research is uncraftable**, not just ours. Fourteen call sites across six scripts, twelve of them
ATM10's own (an earlier count of fifteen and thirteen took in a comment in our own script):

| Script | Recipes |
|---|---|
| `mods/gtceu/coil_multiblock_recipes.js` | superheated pyrolyzing oven, advanced cracking unit, highly reactive molten blast smeltery |
| `mods/gtceu/gtceu.js` | advanced large chemical reactor, neocube (researched from the assembly line), ore processing plant (from the large maceration tower) |
| `mods/gtceu/mega_fusion_recipes.js` | mega fusion reactor |
| `mods/gtceu/micro_universe_orb_recipes.js` | four recipes in the micro universe orb chain |
| `mods/gtceu/starforge_recipes.js` | star forge |
| `gttweaks/rs_part_recipes.js` | our pattern buffer and proxy |

Checked on the test server 2026-09-22, two ways. Not one of the eleven research stacks those scripts
ask for has a research station recipe, and neither of the two scanner stacks has a scanner recipe —
the twenty-one scanner and eleven research station recipes that exist are all GregTech's own, from
its datagen. And querying two directly by the id GregTech would give them,
`gtceu:scanner/1_x_gtceu_large_chemical_reactor` and
`gtceu:research_station/1x_gtceu_pyrolyse_oven`, returns nothing, while the assembly line recipes
that require them are present. The pack ships no hand-written JSON research recipes either, so it is
not working around this somewhere else.

What has *not* been done is watching a player fail to craft one of them in game. The evidence is
that the condition-applying code path runs and the recipes it depends on do not exist.

Those twelve call sites are in ATM-shipped scripts, which a pack update overwrites — see the
re-apply note in the repository. That is why the fix lives in this mod rather than in the scripts.

#### The fix: `KubeResearchRecipes`

GregTech's 1.20.1 port emitted these recipes from KubeJS's `injectRuntimeRecipes` plugin hook.
KubeJS 2101 has no such hook, and `GregTechKubeJSPlugin` in the 8.0 snapshot overrides nothing in
its place, so `GTKubeRecipe.researchRecipeEntries()` — where every `scannerResearch` and
`stationResearch` call leaves its entry — is a public getter nothing reads.

`KubeJSResearchRecipesMixin` injects at the tail of `RecipesKubeEvent.applyChanges`. That method
replaces the datapack recipe map with every original and script-added recipe as JSON, and
`RecipeManager.apply` parses that map straight after, so its tail is the last point where both the
script-built recipes and the map are in hand. For each surviving `GTKubeRecipe` with research
entries, `KubeResearchRecipes` calls GregTech's own
`ResearchManager.createDefaultResearchRecipe` — the call its datagen makes — with a `RecipeOutput`
that collects the result, encodes it with `Recipe.CODEC` over KubeJS's registry-aware JSON ops, and
puts it into the map unless the id is already there. The recipe id derives from the research id
alone, so an id already present is research something else already provides.

- **Look the recipe type up in `BuiltInRegistries.RECIPE_TYPE`**, as GregTech's own recipe codec does.
  `GTRegistries.RECIPE_TYPES.get(gtceu:assembly_line)` returned null at this point, and the first
  build skipped every entry with "is not a GregTech recipe type".
- The data stick is copied before the call, because `createDefaultResearchRecipe` writes the
  `gtceu:research_item` component onto the stack it is given, and that stack is the one the
  assembly line's `ResearchCondition` holds.
- Each generated recipe's JSON is logged at DEBUG as `[gttweaks] Research recipe <id>: <json>`, and a
  one-line INFO summary reports entries, recipes added, and how many were already present.

**Verified on the test server 2026-09-22** (0.30.0, md5 395825ea): `KubeJS research: 14 entries, 14
recipes added, 0 already present`, on a full start and again after `/reload`. Twelve research
station recipes and two scanner recipes, one per call site. `recipematch` finds
`gtceu:scanner/1_x_gtceu_large_chemical_reactor` and `gtceu:scanner/1_x_gtceu_luv_emitter` (480
EU/t, 1200 ticks, data stick + scanned item → data stick) and
`gtceu:research_station/1x_gttweaks_rs_pattern_buffer` (30720 EU/t, 128000 CWU, data orb +
buffer → data orb); `recipematch output gtceu:data_module` lists the ATM ones, `1x_gtceu_star_forge`
and `1x_gtceu_assembly_line` among them. In the logged JSON the output stick carries
`gtceu:research_item {research_id: "1x_gtceu_luv_emitter", research_type: "gtceu:assembly_line"}`,
built from the same `ResearchEntry` the assembly line's condition holds, so the two ids agree by
construction.

**Verified end to end in game 2026-09-22** by the user on the test server, for the scanner half: the
generated scanner recipe turned a data stick and a LuV emitter into a stick carrying
`1x_gtceu_luv_emitter`, and with that stick in a data access hatch the assembly line crafted the RS
Pattern Buffer. It first needed a second fix, `DataItemBankCheckMixin`: the 8.0 snapshot's
`ResearchManager.isStackDataItem` has one branch flipped against 7.x, so a plain data access hatch
ignored every data stick and orb, GregTech's own included. **Not verified:** the research station
half, the proxy's data orb. The scan still runs at `VA[HV]` where GregTech would use `VA[LuV]`, which is the
KubeJS overload problem above and not something this fix touches.

## How the two sides join

**GregTech.** `@GTAddon("gttweaks")` on a class implementing `IGTAddon` is all it takes to be an
addon; `AddonFinder` scans mod files for it. Machines register through a `GTRegistrate` exactly as
`GTAEMachines` does. Two things about GTCEu 8 that the older code around does not tell you:

- `MetaMachine` **is** the `BlockEntity` (it extends `ManagedSyncBlockEntity`). There is no separate
  holder to reach through, which is what makes the capability registration below possible.
- Each `MachineDefinition` owns its own `BlockEntityType`, reachable as
  `definition.getBlockEntityType()`.

GregTech does **not** register an addon's registrate on the mod bus for it — `CommonProxy.init` only
does its own. `GTTweaks` calls `registerEventListeners(modBus)` itself, and creates the registrate
with `registerEvents = false` so the convenience path (which looks the bus up by mod id and only logs
when it misses) is never taken.

**Build the machines on the first `RegisterEvent`, not earlier.** Building one calls
`MachineBuilder.colorOverlayTieredHullModel`, which class-loads GregTech's `GTMachineModels`, whose
static initialiser reads GregTech's casing blocks. Before those exist the server dies during mod
construction with

```
java.lang.NullPointerException: null key in entry: null=gtceu:block/casings/solid/machine_casing_bronze_plated_bricks
    at GTMachineModels.lambda$static$34(GTMachineModels.java:508)
    at RSMachines.<clinit>
```

Both obvious places do it: the mod constructor, and `IGTAddon.gtInitComplete()` — which sounds late
but still runs before `GTBlocks.init()`. `ordering="AFTER"` on the gtceu dependency does not save you
either; it orders event dispatch, not this.

GregTech puts `GTBlocks.init()` and its own `GTMachines.init()` in the same `RegisterEvent` handler,
in that order, so `RSMachines.initOnce()` runs on that first dispatch too. The listener that calls it
is added **before** `REGISTRATE.registerEventListeners(modBus)`, so on that dispatch the order is
GregTech's handler, GregTech's registrate, ours, our registrate — the parts exist by the time our
registrate goes looking.

**Refined Storage.** RS does not scan for nodes. `PlatformImpl.getContainerProviderSafely` resolves a
NeoForge block capability against the block entity at each neighbouring position, so
`RSCapabilities` — registering
`RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability()` against each part's
block entity type — is what makes a cable see the part at all. Without it the node exists and is on
no network, silently. rsmbac learned this the same way.

The node lifecycle rhymes with GregTech's `GridNodeHolder`: both are `MachineTrait`s that create on
machine load and destroy on machine unload. The differences that cost time in rsmbac and are already
accounted for here:

- RS can put a container on a network **without being asked**, when a neighbouring cable initialises
  and discovers it. So "did we join" is not a flag we own; every removal path asks
  `node.getNetwork()`.
- Removing a container RS never received throws out of `NetworkBuilderImpl.remove`. Hence the guard.
- A node only does work while `setActive(true)`, which has to track network power. `refreshActive()`
  runs from the part's tick.
- **Leave from `onMachineUnload` only.** `MetaMachine.setRemoved()` is final and calls `onUnload()`,
  so that one hook covers a broken block as well as an unloading chunk. Also leaving from
  `onMachineDestroyed` — which reads as the natural place — throws
  `IllegalArgumentException: The removed container should be present in the removed entries, but
  isn't` out of breaking the block. The order is the opposite of what the names suggest:
  `MetaMachineBlock.onRemove` calls `onMachineDestroyed()` and *then*
  `level.removeBlockEntity(pos)`, so the destroy hook runs first and the failing call was the first
  attempt, not a second one. Why RS accepts the removal from `onUnload` and not from
  `onMachineDestroyed` is not established — at both points the block state is already replaced — so
  treat this as an observation. `RSNodeHolder.left` stops a second attempt whatever the reason, since
  a queued removal leaves `node.getNetwork()` set for another tick.

## Behaviour

Mirrors GregTech's ME bus: 16 slots, each a configured resource and amount, kept stocked from the
network, with excess handed back. `RSConfiguredSlot.requestStack()` / `exceedStack()` are that logic.

Two deliberate departures from GregTech:

1. **No voiding.** When the ME bus has excess to return and the network refuses it, GregTech deletes
   the excess to free the slot. Here the stock stays and the slot retries. A full network should
   stall a bus, not eat what it holds. The cost is a slot that keeps old contents until there is
   room.
2. **`ResourceAmount` cannot hold zero.** AE2's `GenericStack` can, and GregTech leans on it: when
   stock already equals config it asks for zero and lets the network no-op. RS rejects a non-positive
   amount in the constructor, so those cases return `null` and callers treat null as "skip".

**Never account for a network move through the item handler.** Debit the stock with
`RSConfiguredSlot.debitStock(long)`, which works in the resource's own units. `extractItem` takes an
`int`, so an insert above `Integer.MAX_VALUE` credits the network more than it debits the slot and
*creates* items; and it refuses any resource that is not an item, so a fluid that reached a slot would
be inserted into the network over and over while the stock never moved. Both were live in the first
version, found by the Astra review, and both amounts are reachable — nothing bounds a configured
amount to an `int`. The item handler stays for what the recipe logic does; the network accounting does
not use it.

## Verified API

- `storage.insert(ResourceKey, long, Action, Actor)` / `.extract(...)`, both returning `long`
  (`rsperf`'s `InventoryInterfaceTarget`).
- `network.getComponent(StorageNetworkComponent.class)`; `StorageNetworkComponent extends RootStorage`.
- `ItemResource.ofItemStack(ItemStack)`, `resource.toItemStack(long)`.
- `ResourceAmount` is `(resource(), amount())` and rejects a non-positive amount.
- `ResourceCodecs.AMOUNT_OPTIONAL_CODEC` — used here for slot persistence, so a configured item keeps
  its data components without this mod knowing what components are.
- `containerProvider.initialize(level, null)` / `.remove(level)`; `node.setActive(boolean)`.
- `MetaMachine.getOffsetTimer()`, `subscribeServerTick(subscription, runnable)`, `autoIOSubs`.

`Action` is `com.refinedmods.refinedstorage.api.core.Action`, not `…api.storage.Action`, which is the
one place the RS package layout does not read the way you would guess.

## Testing without a client

`/gttweaks rspart <pos>` prints what the node believes — joined, active, how many resource types the
network holds — and the configured slots. `/gttweaks rspart <pos> config <slot> item <item> <amount>`,
`… config <slot> fluid <fluid id> <amount>` and `… config <slot> clear` set a slot, standing in for
the UI. Items and fluids are separate subcommands because there is no vanilla fluid argument to pair
with `ItemArgument`; a fluid is named by id and looked up in the registry.

`/gttweaks rspart <pos> extract <slot> <amount> <simulate>` drives the one path a multiblock drives,
and exists because the `simulate` flag cannot be reached any other way. GregTech runs a simulated
recipe pass before a real one, so a stocking slot that ignored the flag would consume the inputs of
every recipe it merely *considered*, and the network would drain with nothing to show for it —
invisible in game until the storage is gone. Nothing a player or a hopper can do asks for a
simulated extraction, so the flag is exposed here instead. The command goes through the slot's own
`extractItem` / `drain` rather than through the part, because that is the method GregTech's recipe
logic calls and the one a stocking slot overrides to reach the network.

A network that can be built entirely from commands, which is what the checks above used:

```
forceload add -16 -16 32 32
setblock 0 100 0 refinedstorage:creative_controller
setblock 1 100 0 refinedstorage:cable
setblock 2 100 0 refinedstorage:cable
setblock 3 100 0 refinedstorage:cable
setblock 1 101 0 refinedstorage:external_storage[direction=up]
setblock 1 102 0 minecraft:chest
setblock 3 100 1 refinedstorage:creative_fluid_storage_block
setblock 2 101 0 gttweaks:rs_input_bus
setblock 3 101 0 gttweaks:rs_output_bus
setblock 2 99 0 gttweaks:rs_input_hatch
setblock 3 99 0 gttweaks:rs_output_hatch
item replace block 1 102 0 container.0 with minecraft:cobblestone 64
```

The external storage makes the chest's contents the network's contents, so a `/data get block` on the
chest is a direct readout of what a bus took or gave back. Four gotchas: the property is `direction`
(not `facing`); with no players online the chunk does not tick, so `forceload add` the area or
nothing moves; **a network with no fluid storage silently refuses every fluid insert**, which
looks exactly like a broken output hatch — the creative fluid storage block above is what makes the
fluid half testable at all, and without it the hatch correctly holds its contents and nothing appears
to happen; and every slot is filled from the part's own tick, not from the command that configured
it, so an `rspart` query issued immediately after a `config` reports `stock -` and has to be repeated.
That empty first reading is not a failure, and reading it as one will send you looking for a bug that
is not there.

The creative fluid storage block has infinite *capacity*, not infinite contents, so the network's
water total is finite and known — which is what makes the fluid checks possible. To read that total
without running the code under test, configure the **input hatch** for more water than exists and let
it pull the network dry: its stock is then the network total. RS keeps storage contents in its own
saved data rather than the block entity, so `/data get block` on a storage block yields only a
storage id (`sid`) and cannot serve as that readout.

The output parts have no configuration to set, so they are driven by writing into the inventory the
multiblock would have filled:

```
data merge block 3 101 0 {inventory:{storage:{Size:16,Items:[{Slot:0b,id:"minecraft:diamond",count:17}]}}}
data merge block 3 99 0 {tank:{storages:[{Fluid:{id:"minecraft:water",amount:16000}},{},{},{}]}}
```

Both are read back by the live trait, not just stored as text, so `/data get block` a second later
shows the part emptied and the chest or fluid storage holding what it had.

## Still unchecked

- **The config widget in a client.** It compiles and a headless server starts clean, but a headless
  server never opens a GUI, so not one line of `RSConfigWidget.draw` or of the five synced actions
  has ever run. Everything in that section is written from GregTech's widget and the two APIs it
  sits on, not from having seen it. Until someone right-clicks a part, treat the whole of it as
  unverified — including whether the grid is the right size for the panel it is placed in.
- **The restore half of the stock carry.** Writing the stock onto a broken part's item is verified
  four ways on the test server; reading it back has never run, because placing a block from an item
  that carries components needs a player and a headless server has not got one. See "Carrying a
  broken part's stock".
- **The research station half of the research step.** The scanner half — stick made, recipe unlocked,
  buffer crafted — is verified in game; the proxy's data orb from a research station is not.
- The proxy's `handleRecipe` delegation, which has never been driven by a real multiblock. The proxy
  has only been linked and unlinked, never made part of a second structure with a recipe running
  through it.

Settled, and not a risk: GregTech does not drop a part's contents on break. `MetaMachine.modifyDrops`
is empty, neither `ItemBusPartMachine` nor `FluidHatchPartMachine` nor `TieredIOPartMachine`
overrides it, and `MetaMachineBlock.onRemove` only calls `onMachineDestroyed` and removes the block
entity. So the flush to the network cannot double up with a drop. What GregTech *does* provide, and
what the stock carry relies on, is the `modifyDrops`-then-`saveToItem` ordering inside `getDrops`.

## Next

1. **All eight parts exist and work, the input parts can be configured by hand, and a broken part no
   longer loses what the network would not take back.** The chain is evidenced end to end: an
   autocrafting request publishes a pattern, the sink delivers its ingredients into a slot, a
   multiblock runs from that slot, and the products return to the network. What remains is not
   correctness but finishing.
2. **Open the config widget in a client** and check the four parts it serves — set a resource from
   the cursor, clear one, edit an amount both ways, drag one in from EMI, take a stack back off an
   input bus, and confirm a stocking part refuses the last two. While in there, place a carried part
   back and watch its stock return to the network, which closes the one half of the stock carry a
   headless server cannot reach. Nothing below is worth starting first, because both of those
   sections are written from the APIs rather than from having seen them run.
3. **Run the research station half** — the proxy's data orb. The scanner half is verified end to end
   (see "The fix: `KubeResearchRecipes`").
4. RS-styled textures to replace the placeholders, still to come from LavaSurf.
5. **The pattern buffer still has no UI for its pattern inventory**, so patterns can only be loaded
   with `/gttweaks rspart <pos> pattern <slot>`. That, not the recipe path, is what keeps the buffer
   out of normal play, and it is the one part the config widget does not serve.
