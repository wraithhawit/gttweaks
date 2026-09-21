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
| `RSMachines`, `RSCapabilities` | working, six parts registered |
| `RSNodeHolder`, `RSPartActor`, `RSNetworkPart` | working |
| `RSConfiguredSlot`, `RSConfiguredItemSlot`, `RSConfiguredItemList` | working |
| `RSConfiguredFluidSlot`, `RSConfiguredFluidList`, `RSFluids` | working |
| `RSBusPartMachine`, `RSInputBusPartMachine`, `RSOutputBusPartMachine` | working |
| `RSHatchPartMachine`, `RSInputHatchPartMachine`, `RSOutputHatchPartMachine` | working |
| `RSPartCommand` (`/gttweaks rspart`) | working; `config` stands in for the UI, `extract` drives the recipe path |
| Config UI (ModularUI widget over RS resources) | not started — the input parts cannot be configured yet |
| `RSStockingItemSlot`, `RSStockingFluidSlot` | working |
| `RSStockingBusPartMachine`, `RSStockingHatchPartMachine` | working |
| Pattern buffer and proxy | not started |
| Crafting recipes | not started |
| Own textures | not started; the models point at GregTech's AE2 overlays |

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

- Anything in a client. Nothing here has been opened in a GUI, so `ItemBusPartMachine`'s default UI
  rendering a 16-slot extract-only handler, and `FluidHatchPartMachine`'s rendering a tank whose
  capacity is `Integer.MAX_VALUE`, are both unverified.
- Whether a real multiblock draws from the slots, in simulation and in execution. The recipe path is
  GregTech's own and the handlers are shaped like the ME parts', but it has not been demonstrated.
- What happens when the network refuses the stock a broken input part is handing back. It is kept and
  then lost with the block. **Dropping it into the world is not the fix** — a slot's amount is a
  `long` and a part can legitimately hold hundreds of thousands of items, which as entities would be
  a far worse problem than the loss. Returning it to the network is the right behaviour; the gap is
  only the offline-or-full case, and the answer there is probably to carry the stock on the dropped
  machine item through `collectImplicitComponents`/`applyImplicitComponents`, so it comes back when
  the part is placed again.

Settled, and not a risk: GregTech does not drop a part's contents on break. `MetaMachine.modifyDrops`
is empty, neither `ItemBusPartMachine` nor `FluidHatchPartMachine` nor `TieredIOPartMachine`
overrides it, and `MetaMachineBlock.onRemove` only calls `onMachineDestroyed` and removes the block
entity. So the flush to the network cannot double up with a drop.

## Next

1. The pattern buffer and its proxy — the last two of the eight.
2. The config widget (ModularUI, over `ResourceAmount`). Until it exists the input parts can only be
   told what to stock by command, so they are not usable in play. One widget serves the input bus,
   the input hatch and both stocking parts, which is why it is worth doing after them rather than
   between them.
3. Crafting recipes and textures of their own.

Still outstanding whatever the order: nothing here has been opened in a client, and no real
multiblock has drawn from any of these parts. The `extract` command drives the same slot methods
GregTech's recipe logic calls, which is the closest stand-in available without a client, but it is
not the recipe path itself.
