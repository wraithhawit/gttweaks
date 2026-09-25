// gttweaks: crafting recipes for the eight Refined Storage multiblock parts.
//
// Mirrors GregTech's own ME part recipes exactly, ingredient for ingredient and tier for tier, with
// the AE2 parts swapped for their Refined Storage equivalents. Read out of GTCEu 8's
// MetaTileEntityMachineRecipeLoader, which builds them in Java rather than shipping JSON:
//
//   ME import/export bus + hatch  assembler,     EV base part,  300t, VA[HV]
//   ME stocking import bus/hatch  assembler,     IV base part,  300t, VA[IV]
//   ME pattern buffer             assembly line, LuV base part, 600t, VA[LuV], scanner research
//   ME pattern buffer proxy       assembly line, LuV hull,      600t, VA[ZPM], station research
//
// The tiers are GregTech's, not ours. Our parts all *register* at EV -- that is what the casing
// texture and the energy tier say -- so a stocking part costs an IV bus and the buffer costs a LuV
// dual hatch while looking like EV machines. That mismatch is deliberate and was the user's call on
// 2026-09-22: what a part costs should track what it does, and these do what GregTech's cost that
// much to do.
//
// The five AE2 -> RS swaps, all confirmed present in Refined Storage 2.0.9:
//
//   ME Interface (AEParts.INTERFACE / AEBlocks.INTERFACE) -> refinedstorage:interface
//       RS has one interface where AE2 has a part and a block; both map to it.
//   Acceleration Card (AEItems.SPEED_CARD)                -> refinedstorage:speed_upgrade
//   Pattern Provider (AEBlocks.PATTERN_PROVIDER)          -> refinedstorage:autocrafter
//       RS 2.0's autocrafter is what holds patterns and offers them to the network. The Pattern
//       Grid only *encodes* patterns and is not the equivalent.
//   Capacity Card (AEItems.CAPACITY_CARD)                 -> refinedstorage:stack_upgrade
//   Quantum Link + Quantum Ring                           -> refinedstorage:network_transmitter
//                                                            + refinedstorage:network_receiver
//       RS has no quantum bridge. The transmitter/receiver pair is its way of joining two points of
//       one network, which is what a pattern buffer proxy does.
//
// Everything else -- the GregTech base parts, the conveyors, pumps, sensors, emitters, circuits, the
// fine europium wire, the soldering alloy and lubricant, the durations, the EU/t and the research --
// is GregTech's own numbers, unchanged.

ServerEvents.recipes(event => {
    if (!Platform.isLoaded('refinedstorage')) return

    const gtr = event.recipes.gtceu

    const RS_INTERFACE = 'refinedstorage:interface'
    const RS_SPEED = 'refinedstorage:speed_upgrade'
    const RS_AUTOCRAFTER = 'refinedstorage:autocrafter'
    const RS_STACK = 'refinedstorage:stack_upgrade'
    const RS_TRANSMITTER = 'refinedstorage:network_transmitter'
    const RS_RECEIVER = 'refinedstorage:network_receiver'

    const SOLDERING_ALLOY = Fluid.of('gtceu:soldering_alloy', 576)
    const LUBRICANT = Fluid.of('gtceu:lubricant', 500)
    const EUROPIUM_FINE_WIRE = '32x #c:fine_wires/europium'

    // --- the four plain parts: EV base part, one interface, two speed upgrades -----------------

    function plainPart(output, basePart) {
        gtr.assembler(`gttweaks:${output}`)
            .itemInputs(basePart, RS_INTERFACE, `2x ${RS_SPEED}`)
            .itemOutputs(`gttweaks:${output}`)
            .duration(300)
            .EUt(GTValues.VA[GTValues.HV])
    }

    plainPart('rs_input_bus', 'gtceu:ev_input_bus')
    plainPart('rs_output_bus', 'gtceu:ev_output_bus')
    plainPart('rs_input_hatch', 'gtceu:ev_input_hatch')
    plainPart('rs_output_hatch', 'gtceu:ev_output_hatch')

    // --- the two stocking parts: IV base part, and the mover and sensor for that tier -----------
    // GregTech charges a conveyor for the item side and an electric pump for the fluid side, which
    // is the same distinction its plain buses and hatches make one tier down.

    function stockingPart(output, basePart, mover) {
        gtr.assembler(`gttweaks:${output}`)
            .itemInputs(basePart, RS_INTERFACE, mover, 'gtceu:iv_sensor', `4x ${RS_SPEED}`)
            .itemOutputs(`gttweaks:${output}`)
            .duration(300)
            .EUt(GTValues.VA[GTValues.IV])
    }

    stockingPart('rs_stocking_bus', 'gtceu:iv_input_bus', 'gtceu:iv_conveyor_module')
    stockingPart('rs_stocking_hatch', 'gtceu:iv_input_hatch', 'gtceu:iv_electric_pump')

    // --- the pattern buffer: assembly line, researched from the hatch it is built out of --------

    gtr.assembly_line('gttweaks:rs_pattern_buffer')
        .itemInputs('gtceu:luv_dual_input_hatch',
            'gtceu:luv_emitter',
            '4x #gtceu:circuits/luv',
            `3x ${RS_AUTOCRAFTER}`,
            `3x ${RS_INTERFACE}`,
            `4x ${RS_SPEED}`,
            `2x ${RS_STACK}`,
            EUROPIUM_FINE_WIRE,
            EUROPIUM_FINE_WIRE,
            EUROPIUM_FINE_WIRE)
        .inputFluids(SOLDERING_ALLOY, LUBRICANT)
        .itemOutputs('gttweaks:rs_pattern_buffer')
        .duration(600)
        .EUt(GTValues.VA[GTValues.LuV])
        // Researched from the LuV emitter, not the LuV dual input hatch GregTech uses, and the
        // reason is a hard collision rather than a preference. A scanner research recipe is named
        // after the item being scanned -- gtceu:scanner/1_x_gtceu_luv_dual_input_hatch -- so
        // researching our buffer from the same hatch GregTech researches its ME Pattern Buffer
        // from produces two recipes under one id, and one of the two data sticks stops existing.
        // The emitter is in this recipe anyway, is the same LuV tier, and nothing else scans it.
        //
        // The EU/t of the scan is GregTech's default for the step, VA[HV], where GregTech sets
        // VA[LuV] for its own. That one is a KubeJS limitation and not a choice: GTKubeRecipe has
        // both scannerResearch(ItemStack) and scannerResearch(UnaryOperator), Rhino cannot pick the
        // builder form -- a bare arrow function selects the ItemStack overload and fails to parse,
        // a real UnaryOperator adapter is called ambiguous, and the class filter blocks
        // java.lang.Class so reflection cannot name it either. The duration is 1200 either way,
        // which is what GregTech sets, and the scan gates nothing differently.
        .scannerResearch(Item.of('gtceu:luv_emitter'))

    // --- the proxy: assembly line, researched from the buffer itself ---------------------------
    // GregTech chains its proxy's research off its own pattern buffer, so ours chains off ours.
    // A proxy is useless without a buffer to point at, and this is what says so in progression.

    gtr.assembly_line('gttweaks:rs_pattern_buffer_proxy')
        .itemInputs('gtceu:luv_machine_hull',
            '2x gtceu:luv_sensor',
            '1x #gtceu:circuits/luv',
            `1x ${RS_TRANSMITTER}`,
            `2x ${RS_RECEIVER}`,
            EUROPIUM_FINE_WIRE,
            EUROPIUM_FINE_WIRE)
        .inputFluids(SOLDERING_ALLOY, LUBRICANT)
        .itemOutputs('gttweaks:rs_pattern_buffer_proxy')
        .duration(600)
        .EUt(GTValues.VA[GTValues.ZPM])
        .stationResearch(b => b
            .researchStack(Item.of('gttweaks:rs_pattern_buffer'))
            .CWUt(32))
})
