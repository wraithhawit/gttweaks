# Refined Storage multiblock parts

GregTech's ME parts let an AE2 network feed a multiblock. These are the same thing for a Refined
Storage network. The plan is all eight: input bus, output bus, input hatch, output hatch, the two
stocking variants, and the pattern buffer with its proxy.

## Status

The RS Input Bus works end to end on the test server (0.30.0, md5 53c1d9ed): it registers, joins a
network through the capability, stocks its configured slots, hands back what it should not hold,
keeps its configuration and stock across a restart, and returns its stock when broken. It is not
usable in normal play yet — there is no way to configure a slot without `/gttweaks rspart`, and no
recipe to craft one.

| Piece | State |
|---|---|
| `RSIntegration`, `GTTweaksRegistrate`, `GTTweaksGTAddon` | working |
| `RSMachines` (RS Input Bus only), `RSCapabilities` | working |
| `RSNodeHolder`, `RSPartActor` | working |
| `RSConfiguredSlot`, `RSConfiguredItemSlot`, `RSConfiguredItemList` | working |
| `RSBusPartMachine`, `RSInputBusPartMachine` | working |
| `RSPartCommand` (`/gttweaks rspart`) | working; stands in for the UI |
| Config UI (ModularUI widget over RS resources) | not started — the bus cannot be configured yet |
| Output bus, hatches, stocking variants, pattern buffer | not started |
| Own textures | not started; the model points at GregTech's AE2 overlay |

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
  so that one hook covers a broken block as well as an unloading chunk. Leaving from
  `onMachineDestroyed` too — which reads as the natural place — throws
  `IllegalArgumentException: The removed container should be present in the removed entries, but
  isn't` out of breaking the block: it runs from the block's `onRemove`, after the level has dropped
  the block entity, so RS's scan of that position finds nothing; and the first removal is queued
  rather than immediate, so `node.getNetwork()` is still set and does not guard the second attempt.
  `RSNodeHolder.left` guards it instead.

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
network holds — and the configured slots. `/gttweaks rspart <pos> config <slot> <item> <amount>` and
`… config <slot> clear` set a slot, standing in for the UI.

A network that can be built entirely from commands, which is what the checks above used:

```
setblock 0 100 0 refinedstorage:creative_controller
setblock 1 100 0 refinedstorage:cable
setblock 2 100 0 gttweaks:rs_input_bus
setblock 1 101 0 refinedstorage:external_storage[direction=up]
setblock 1 102 0 minecraft:chest
item replace block 1 102 0 container.0 with minecraft:cobblestone 64
```

The external storage makes the chest's contents the network's contents, so a `/data get block` on the
chest is a direct readout of what the bus took or gave back. Two gotchas: the property is `direction`
(not `facing`), and with no players online the chunk does not tick, so `forceload add` the area or
nothing moves.

## Still unchecked

- `ItemBusPartMachine`'s default UI rendering a 16-slot extract-only handler — nothing here has run in
  a client.
- Whether a real multiblock draws from the slots. The recipe path is GregTech's own and the handler is
  shaped like the ME bus's, but "reads as an input bus" has not been demonstrated against a recipe.
- What happens when the network refuses the stock a broken part is handing back. It is kept and then
  lost with the block; the fix is probably `modifyDrops`.

## Next

1. The config widget (ModularUI, over `ResourceAmount`). Until it exists the bus can only be told what
   to stock by command, so it is not usable in play.
2. A crafting recipe and textures of its own.
3. Output bus, then the fluid hatches on the same slot model, then stocking, then the pattern buffer.
