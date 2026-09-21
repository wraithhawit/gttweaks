# Refined Storage multiblock parts

GregTech's ME parts let an AE2 network feed a multiblock. These are the same thing for a Refined
Storage network. The plan is all eight: input bus, output bus, input hatch, output hatch, the two
stocking variants, and the pattern buffer with its proxy.

## Status

Four of the eight parts work end to end on the test server (0.30.0, md5 1aa035ec): the input and
output buses and both fluid hatches. Each one registers, joins a network through the capability,
moves what it should in the direction it should, keeps its configuration and stock across a server
restart, and rejoins the network afterwards. Checked on 2026-09-20 with exact counts on both sides:
the input bus took 32 cobblestone from a 64-stack and left 32, the output bus handed over 17
diamonds and 5 gold ingots and emptied, the output hatch pushed 16000 mB of water, and the input
hatch pulled back exactly its configured 5000.

Two more — the stocking bus and stocking hatch — are written, compile and are registered, but have
**not been run at all**. Treat them as unverified until the checks under "Next" have been done.

None of the six is usable in normal play yet — the input parts cannot be configured without
`/gttweaks rspart`, and nothing here has a crafting recipe.

| Piece | State |
|---|---|
| `RSIntegration`, `GTTweaksRegistrate`, `GTTweaksGTAddon` | working |
| `RSMachines`, `RSCapabilities` | working, four parts registered |
| `RSNodeHolder`, `RSPartActor`, `RSNetworkPart` | working |
| `RSConfiguredSlot`, `RSConfiguredItemSlot`, `RSConfiguredItemList` | working |
| `RSConfiguredFluidSlot`, `RSConfiguredFluidList`, `RSFluids` | working |
| `RSBusPartMachine`, `RSInputBusPartMachine`, `RSOutputBusPartMachine` | working |
| `RSHatchPartMachine`, `RSInputHatchPartMachine`, `RSOutputHatchPartMachine` | working |
| `RSPartCommand` (`/gttweaks rspart`) | working; stands in for the UI |
| Config UI (ModularUI widget over RS resources) | not started — the input parts cannot be configured yet |
| `RSStockingItemSlot`, `RSStockingFluidSlot` | written, compiles, NOT yet run |
| `RSStockingBusPartMachine`, `RSStockingHatchPartMachine` | written, compiles, NOT yet run |
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
chest is a direct readout of what a bus took or gave back. Three gotchas: the property is `direction`
(not `facing`); with no players online the chunk does not tick, so `forceload add` the area or
nothing moves; and **a network with no fluid storage silently refuses every fluid insert**, which
looks exactly like a broken output hatch. The creative fluid storage block above is what makes the
fluid half testable at all — without it the hatch correctly holds its contents and nothing appears to
happen.

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

1. **Verify the two stocking parts on the test server.** They compile and are registered; nothing
   has been run. The rig and commands are under "Testing without a client" above. What to prove, in
   order: a configured slot mirrors the network amount without the network losing anything; a
   simulated extract takes nothing; a real extract debits the network exactly once; breaking a
   stocked part gives the network nothing back.
2. The pattern buffer and its proxy.
3. The config widget (ModularUI, over `ResourceAmount`). Until it exists the input parts can only be
   told what to stock by command, so they are not usable in play. One widget serves the input bus,
   the input hatch and both stocking parts, which is why it is worth doing after them rather than
   between them.
4. Crafting recipes and textures of their own.
