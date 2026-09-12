// Ported from All the Mods 9 to GTCEu 8 + KubeJS 2101. Original by AllTheMods Staff / community
// contributor. Two changes:
//
//   .inputFluids('gtceu:soldering_alloy 1152', ...) -> .inputFluids(Fluid.of('...', 1152), ...)
//       The "id amount" string form came from FluidStackJS, which KubeJS 2101 deleted. A fluid with
//       no amount in ATM9 meant 1000, so gtceu:vanadium_gallium is now Fluid.of(..., 1000).
//   Trailing commas before the closing paren of .itemInputs(...) removed - Rhino is stricter here.
//
// .CWUt(16, 32000) is NOT a break: StationRecipeBuilder still has the two-arg form (cwut, totalCWU).

ServerEvents.recipes(event => {
 event.recipes.gtceu.assembly_line('superheated_pyrolyzing_oven')
     .itemInputs('6x #gtceu:circuits/luv',
      '3x gtceu:luv_electric_piston',
      '2x gtceu:luv_electric_pump',
      '1x gtceu:luv_machine_hull',
      '4x gtceu:osmiridium_gear',
   '2x gtceu:niobium_nitride_quadruple_cable'
     )
     .inputFluids(Fluid.of('gtceu:soldering_alloy', 1152), Fluid.of('gtceu:vanadium_gallium', 1000))
     .itemOutputs('gtceu:superheated_pyrolyzing_oven')
     .duration(750)
     .EUt(GTValues.VA[GTValues.ZPM]/2)
     .stationResearch(b => b.researchStack(Item.of('gtceu:pyrolyse_oven')).EUt(GTValues.VA[GTValues.ZPM]/2).CWUt(16, 32000))
  event.recipes.gtceu.assembly_line('advanced_cracking_unit')
     .itemInputs('2x #gtceu:circuits/luv',
      '8x gtceu:hssg_coil_block',
      '3x gtceu:luv_electric_pump',
      '1x gtceu:luv_machine_hull',
      '8x gtceu:tungsten_carbide_huge_fluid_pipe',
   '4x gtceu:indium_tin_barium_titanium_cuprate_double_wire'
     )
     .inputFluids(Fluid.of('gtceu:soldering_alloy', 1152), Fluid.of('gtceu:light_fuel', 6000))
     .itemOutputs('gtceu:advanced_cracking_unit')
     .duration(600)
     .EUt(GTValues.VA[GTValues.ZPM]/2)
     .stationResearch(b => b.researchStack(Item.of('gtceu:cracker')).EUt(30720).CWUt(20, 32000))
 event.recipes.gtceu.assembly_line('highly_reactive_molten_blast_smeltery')
     .itemInputs('16x #gtceu:circuits/uhv',
      '8x gtceu:uv_field_generator',
      '4x gtceu:blacklight',
      '2x gtceu:tritanium_coil_block',
      '16x gtceu:high_temperature_smelting_casing',
      '1x gtceu:auto_maintenance_hatch',
   '14x gtceu:ruthenium_trinium_americium_neutronate_quadruple_wire',
   '64x gtceu:fine_americium_wire',
   '12x gtceu:neutronium_frame'
     )
     .inputFluids(Fluid.of('gtceu:soldering_alloy', 1152), Fluid.of('gtceu:tritanium', 1152), Fluid.of('gtceu:naquadria', 5000))
     .itemOutputs('gtceu:highly_reactive_molten_blast_smeltery')
     .duration(600)
     .EUt(GTValues.VA[GTValues.UHV]/2)
     .stationResearch(b => b.researchStack(Item.of('gtceu:alloy_blast_smelter')).EUt(2097152).CWUt(144, 32000))

     })
