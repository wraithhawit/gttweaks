// Ported from All the Mods 9 to GTCEu 8 + KubeJS 2101. Original by AllTheMods Staff / community
// contributor; the license note from the original is kept in place below.
//
//   const [ULV, LV, ...] = GTValues.VA  ->  explicit const per tier
//       The bare voltage names in this file were never globals - the original destructured them out
//       of GTValues.VA itself. That is a Java int[], and Rhino does not reliably destructure one, so
//       the same names are now bound explicitly. Nothing else in the file needed to change for it.
//   .chancedOutput(stack, chance, tierBoost) -> .chancedOutput(stack, chance)   (7 sites)
//   #forge:... -> #c:...                                                        (2 sites)
//   Item.of('expatternprovider:infinity_cell', '{record: {...}}') -> 'kubejs:<fluid>_cell'  (15)
//       Extended AE renamed to `extendedae` and moved cell contents from per-stack NBT to an AEKey
//       field on the item, so each cell is its own registered item now. gt_infinity_cells.js
//       registers the 14 GregTech fluids; minecraft:lava reuses ATM10's own kubejs:lava_cell.
//   The awakened alloy block's NBT -> 1.21 components
//       Copied verbatim from ATM10's own star_altar.js rather than hand-converted, so the two
//       recipes are matching the exact same item the pack already builds.
//
// Two recipes were DROPPED for good, marked in place below: infinite_polonium and
// infinite_plutonium - no chemical infinity cell exists, and the user chose not to build one.
// The gregstar recipe's 8x exchangers:end_steel_exchanger became 8x pocketstorage:psu_4.
//
//   atm_star_shard_1..5: NEW, five recipes not in ATM9 at all. They are the altar's fragment
//       recipes, added so the GregTech route to an ATM Star is self-contained rather than
//       depending on Modern Industrialization's star altar for its inputs.
//   atm_star_from_shards: 54x atm_star_shard + patrick_star -> the star altar's five-fragment
//       recipe (2026-08-31, asked for in game). ATM10 moved the ATM Star behind
//       kubejs:atm_star_shard_1..5 and an Infused Patrick Star; the GregTech route now costs the
//       same as the altar route instead of a flat pile of shards.
//
// Note: ATM10 already builds an atm_star through Modern Industrialization's star altar. The
// star_forge recipe here is a second, GregTech-native route to the same item - which is the point
// of the port, not a duplicate to remove.

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
ServerEvents.recipes(allthemods => {
    // Declared INSIDE the callback on purpose. KubeJS server scripts share one Rhino top-level
    // scope, so these names collided with the identical block in neural_node_recipes.js, which
    // loads first alphabetically - this whole file died on "redeclaration of const UHV" and the
    // Star Forge had no recipes at all. Function scope keeps each file's copy to itself.
    const ULV = GTValues.VA[GTValues.ULV], LV = GTValues.VA[GTValues.LV], MV = GTValues.VA[GTValues.MV],
          HV = GTValues.VA[GTValues.HV], EV = GTValues.VA[GTValues.EV], IV = GTValues.VA[GTValues.IV],
          LuV = GTValues.VA[GTValues.LuV], ZPM = GTValues.VA[GTValues.ZPM], UV = GTValues.VA[GTValues.UV],
          UHV = GTValues.VA[GTValues.UHV], UEV = GTValues.VA[GTValues.UEV], UIV = GTValues.VA[GTValues.UIV],
          UXV = GTValues.VA[GTValues.UXV], OpV = GTValues.VA[GTValues.OpV], MAX = GTValues.VA[GTValues.MAX]

    function starForge(id, duration, eu, output, item, fluid) {
		allthemods.recipes.gtceu.star_forge(id)
			.itemInputs(item)
			.inputFluids(fluid)
			.itemOutputs(output)
			.duration(duration)
			.EUt(eu)
		if(fluid = '') {
			fluid = null
		}
	} 
	// Gregstar Components with fluids
	starForge('robust_star_housing', 4000, ZPM, 'kubejs:star_housing', ['allthetweaks:patrick_star', '32x gtceu:double_tungsten_steel_plate', '64x gtceu:tungsten_steel_screw'], [Fluid.of('gtceu:oxygen_plasma', 2880), Fluid.of('gtceu:nitrogen_plasma', 2880), Fluid.of('gtceu:argon_plasma', 2880), Fluid.of('gtceu:helium_plasma', 2880)])
	starForge('absolute_reaction_plating', 1000, ZPM, 'kubejs:absolute_reaction_plating', ['gtceu:neutronium_block', '16x gtceu:fusion_coil', '16x gtceu:fusion_casing_mk3', '16x gtceu:fusion_glass'], Fluid.of('gtceu:uranium_235', 2000))
	starForge('superthermal_transference_coil', 1000, ZPM, 'kubejs:superthermal_transference_coil', ['16x gtceu:uv_voltage_coil', '16x gtceu:tritanium_coil_block', '4x gtceu:uv_naquadria_battery'], Fluid.of('gtceu:europium', 2000))
	starForge('cable_of_hyperconductivity', 1000, ZPM, 'kubejs:cable_of_hyperconductivity', ['8x gtceu:manganese_phosphide_hex_wire', '8x gtceu:magnesium_diboride_hex_wire', '8x gtceu:mercury_barium_calcium_cuprate_hex_wire', '8x gtceu:uranium_triplatinum_hex_wire', '8x gtceu:samarium_iron_arsenic_oxide_hex_wire', '8x gtceu:indium_tin_barium_titanium_cuprate_hex_wire', '8x gtceu:uranium_rhodium_dinaquadide_hex_wire','8x gtceu:enriched_naquadah_trinium_europium_duranide_hex_wire'], [Fluid.of('gtceu:styrene_butadiene_rubber', 16000), Fluid.of('gtceu:silicone_rubber', 32000), Fluid.of('gtceu:rubber', 64000)])
	
	// star compression module
	allthemods.recipes.gtceu.star_forge('star_compression_module')
		.itemInputs(['allthetweaks:atm_star', '16x gtceu:energy_cluster', '4x gtceu:uv_transformer_16a', '4x gtceu:uv_energy_input_hatch_16a'])
		.itemOutputs('kubejs:star_compression_module')
		.duration(1000)
		.EUt(ZPM)

	// Micro Universe Catalyst
	starForge('micro_universe_catalyst', 2000, UV, 'kubejs:micro_universe_catalyst', ['16x gtceu:naquadria_plate', '32x gtceu:uv_electric_piston', '8x gtceu:gravi_star'], [Fluid.of('gtceu:neutronium', 144 * 32), Fluid.of('gtceu:star_matter_plasma', 10000)])

	// Gregstar
	starForge('gregstar', 1280000, ULV, 'allthetweaks:greg_star', 
		[
			'kubejs:star_housing',
			'gtceu:nan_certificate',
			'kubejs:absolute_reaction_plating',
			'kubejs:star_compression_module',
			'kubejs:superthermal_transference_coil', 
			'kubejs:cable_of_hyperconductivity', 
			'32x mekanism:pellet_antimatter', 
			// ATM9 used 8x industrialforegoing:black_hole_controller. Industrial Foregoing has not ported
			// black holes to 1.21 at all - the jar still carries their models and lang keys, but nothing
			// is registered, controller or unit. Replaced with Modern Industrialization's quantum barrel,
			// the pack's remaining "absurd amount of one item" storage block.
			'8x modern_industrialization:quantum_barrel',
			'16x ironfurnaces:unobtainium_furnace',
			'32x computercraft:computer_advanced',
			// ATM9 used 8x exchangers:end_steel_exchanger carrying '{Energy:50000000}'. The Exchangers
			// mod is not in ATM10, so this is 8x pocketstorage:psu_4 instead. The stored energy is not
			// carried over: an empty PSU is the sane input, and on 1.21 a charged one would need an
			// energy data component rather than the old NBT.
			'8x pocketstorage:psu_4'
		], 
		[
			Fluid.of('gtceu:europium', 10000)
		]
	)

	// Gregstar shards
	allthemods.recipes.gtceu.macerator('gregstar_shards')
		.itemInputs('allthetweaks:greg_star')
		.itemOutputs('5x kubejs:greg_star_shard')
		.chancedOutput(Item.of('4x kubejs:greg_star_shard'), 5000)
		.chancedOutput(Item.of('3x kubejs:greg_star_shard'), 2500)
		.chancedOutput(Item.of('2x kubejs:greg_star_shard'), 1250)
		.duration(120)
		.EUt(ZPM)

	// Gregstar creative uses
	// DROPPED IN THE PORT: infinite_polonium and infinite_plutonium.
	// Their output was an infinity cell holding a Mekanism CHEMICAL. Extended AE now stores the
	// cell contents as an AEKey field on the item itself, so every cell must be a registered item -
	// see gt_infinity_cells.js, which registers the 14 GregTech FLUID cells this file needs.
	// InfinityCellBuilder exposes fluidType/itemType but no chemical variant, so a polonium or
	// plutonium cell needs MekanismKey.of(ChemicalStack) through the generic type(Supplier)
	// overload. Dropped for good by the user's call 2026-08-27, not deferred - do not re-add.
	
	allthemods.recipes.gtceu.star_forge('infinite_chlorine')
		.inputFluids(Fluid.of('gtceu:chlorine', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:chlorine_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_sulfuric_acid')
		.inputFluids(Fluid.of('gtceu:sulfuric_acid', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:sulfuric_acid_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_hydrogen')
		.inputFluids(Fluid.of('gtceu:hydrogen', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:hydrogen_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_oxygen')
		.inputFluids(Fluid.of('gtceu:oxygen', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:oxygen_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_styrene')
		.inputFluids(Fluid.of('gtceu:styrene_butadiene_rubber', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:styrene_butadiene_rubber_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_lava')
		.inputFluids(Fluid.of('minecraft:lava', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:lava_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_ptfe')
		.inputFluids(Fluid.of('gtceu:polytetrafluoroethylene', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:polytetrafluoroethylene_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_pbi')
		.inputFluids(Fluid.of('gtceu:polybenzimidazole', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:polybenzimidazole_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_ethylene')
		.inputFluids(Fluid.of('gtceu:ethylene', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:ethylene_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_lubricant')
		.inputFluids(Fluid.of('gtceu:lubricant', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:lubricant_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_air')
		.inputFluids(Fluid.of('gtceu:air', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:air_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_nether_air')
		.inputFluids(Fluid.of('gtceu:nether_air', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:nether_air_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_end_air')
		.inputFluids(Fluid.of('gtceu:ender_air', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:ender_air_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_fluorine')
		.inputFluids(Fluid.of('gtceu:fluorine', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:fluorine_cell')
		.duration(4000)
		.EUt(ZPM)
	
	allthemods.recipes.gtceu.star_forge('infinite_solder')
		.inputFluids(Fluid.of('gtceu:soldering_alloy', 10000000))
		.itemInputs(['2x kubejs:greg_star_shard', 'ae2:fluid_cell_housing'])
		.itemOutputs('kubejs:soldering_alloy_cell')
		.duration(4000)
		.EUt(ZPM)

	// Refined Storage equivalents of the infinity cells above: Refined Storage - Additional Disks
	// infinite disks (startup_scripts/gtceu/gt_infinite_disks.js). The AE2 cells take a fluid cell
	// housing and the disks a storage housing, so the two recipes for one fluid never share inputs.
	if (Platform.isLoaded('rsadditionaldisks')) {
		allthemods.recipes.gtceu.star_forge('infinite_chlorine_disk')
			.inputFluids(Fluid.of('gtceu:chlorine', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:chlorine_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_sulfuric_acid_disk')
			.inputFluids(Fluid.of('gtceu:sulfuric_acid', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:sulfuric_acid_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_hydrogen_disk')
			.inputFluids(Fluid.of('gtceu:hydrogen', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:hydrogen_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_oxygen_disk')
			.inputFluids(Fluid.of('gtceu:oxygen', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:oxygen_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_styrene_disk')
			.inputFluids(Fluid.of('gtceu:styrene_butadiene_rubber', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:styrene_butadiene_rubber_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_lava_disk')
			.inputFluids(Fluid.of('minecraft:lava', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:lava_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_ptfe_disk')
			.inputFluids(Fluid.of('gtceu:polytetrafluoroethylene', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:polytetrafluoroethylene_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_pbi_disk')
			.inputFluids(Fluid.of('gtceu:polybenzimidazole', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:polybenzimidazole_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_ethylene_disk')
			.inputFluids(Fluid.of('gtceu:ethylene', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:ethylene_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_lubricant_disk')
			.inputFluids(Fluid.of('gtceu:lubricant', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:lubricant_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_air_disk')
			.inputFluids(Fluid.of('gtceu:air', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:air_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_nether_air_disk')
			.inputFluids(Fluid.of('gtceu:nether_air', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:nether_air_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_end_air_disk')
			.inputFluids(Fluid.of('gtceu:ender_air', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:ender_air_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_fluorine_disk')
			.inputFluids(Fluid.of('gtceu:fluorine', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:fluorine_disk')
			.duration(4000)
			.EUt(ZPM)

		allthemods.recipes.gtceu.star_forge('infinite_solder_disk')
			.inputFluids(Fluid.of('gtceu:soldering_alloy', 10000000))
			.itemInputs(['2x kubejs:greg_star_shard', 'refinedstorage:storage_housing'])
			.itemOutputs('kubejs:soldering_alloy_disk')
			.duration(4000)
			.EUt(ZPM)
	}

	// Other Star Forge Recipes
	allthemods.recipes.gtceu.star_forge('atm_star')
		.itemInputs(
			[
				'28x allthemodium:unobtainium_allthemodium_alloy_block',
				`allthemodium:unobtainium_vibranium_alloy_block[enchantments={levels:{"minecraft:unbreaking":1}},custom_name='{"extra":[{"italic":false,"text":"Awakened Unobtainium-Vibranium Alloy Block"}],"text":""}']`,
				`allthemodium:unobtainium_vibranium_alloy_block[enchantments={levels:{"minecraft:unbreaking":1}},custom_name='{"extra":[{"italic":false,"text":"Awakened Unobtainium-Vibranium Alloy Block"}],"text":""}']`,
				'allthetweaks:oblivion_shard',
				'allthetweaks:dragon_soul',
				'allthetweaks:withers_compass',
				'allthetweaks:pulsating_black_hole',
				'allthetweaks:nexium_emitter',
				'allthetweaks:patrick_star',
				'allthetweaks:dimensional_seed',
				'allthetweaks:philosophers_fuel',
				'allthetweaks:improbable_probability_device',
				'mysticalagradditions:creative_essence'				
			])
		.inputFluids(Fluid.of('gtceu:nether_star', 1574640))
		.itemOutputs('allthetweaks:atm_star')
		.chancedOutput(Item.of('allthetweaks:atm_star'), 20)
		.duration(432000)
		.EUt(ULV)
	
	// Inputs mirror ATM10's own star altar "ATM Star Shard" recipe rather than ATM9's 54 loose
	// shards: the pack now gates an ATM Star behind the five fragments, and the GregTech route
	// should cost the same. Copied verbatim from
	// server_scripts/modpack/runic_multis/recipes/star_altar.js, including the Infused Patrick
	// Star's components - that ingredient matches EXACTLY, so the string cannot drift from the
	// stack the altar produces.
	allthemods.recipes.gtceu.star_forge('atm_star_from_shards')
		.itemInputs(
			[
				'actuallyadditions:lens_of_the_killer',
				'kubejs:atm_star_shard_1',
				'kubejs:atm_star_shard_2',
				'kubejs:atm_star_shard_3',
				'kubejs:atm_star_shard_4',
				'kubejs:atm_star_shard_5',
				'forbidden_arcanus:mundabitur_dust',
				'forbidden_arcanus:corrupti_dust',
				`allthetweaks:patrick_star[enchantments={levels:{"minecraft:mending":1}},custom_name='{"extra":[{"italic":false,"text":"Infused Patrick Star"}],"text":""}']`
			])
		.itemOutputs('allthetweaks:atm_star')
		.chancedOutput(Item.of('allthetweaks:atm_star'), 20)
		.duration(108000)
		.EUt(ULV)
	
	// The five fragments the recipe above now needs, mirroring the star altar's own loop in
	// server_scripts/modpack/runic_multis/recipes/star_altar.js - same shard counts, same
	// Forbidden Arcanus prism per fragment, so neither route is cheaper than the other and the
	// GregTech path no longer depends on Modern Industrialization for its inputs.
	//
	// Duration is atm_star_from_shards' 108000 split five ways, so five fragments plus the star
	// costs the same wall clock as the single 54-shard recipe this replaced.
	const atmStarFragments = [
		{ fragment: 1, shards: 48, prism: 'forbidden_arcanus:soul_binding_crystal' },
		{ fragment: 2, shards: 52, prism: 'forbidden_arcanus:whirlwind_prism' },
		{ fragment: 3, shards: 52, prism: 'forbidden_arcanus:smelter_prism' },
		{ fragment: 4, shards: 52, prism: 'forbidden_arcanus:sea_prism' },
		{ fragment: 5, shards: 52, prism: 'forbidden_arcanus:terrastomp_prism' }
	]

	atmStarFragments.forEach(entry => {
		allthemods.recipes.gtceu.star_forge(`atm_star_shard_${entry.fragment}`)
			.itemInputs(
				[
					`${entry.shards}x allthetweaks:atm_star_shard`,
					entry.prism
				])
			.itemOutputs(`kubejs:atm_star_shard_${entry.fragment}`)
			.chancedOutput(Item.of(`kubejs:atm_star_shard_${entry.fragment}`), 20)
			.duration(21600)
			.EUt(ULV)
	})

	allthemods.recipes.gtceu.star_forge('greg_star_from_shards')
		.itemInputs(
			[
				'54x kubejs:greg_star_shard',
				'kubejs:star_housing'
			])
		.itemOutputs('allthetweaks:greg_star')
		.chancedOutput(Item.of('allthetweaks:greg_star'), 20)
		.duration(108000)
		.EUt(ULV)
	
	allthemods.recipes.gtceu.star_forge('patrick_star')
		.itemInputs(
			[
				'11x minecraft:pink_concrete',
				'5x minecraft:pink_concrete_powder',
				'13x minecraft:magenta_concrete',
				'8x minecraft:magenta_concrete_powder',
				'2x minecraft:lime_concrete',
				'8x minecraft:green_concrete',
				'8x minecraft:green_concrete_powder'
			])
		.itemOutputs('allthetweaks:patrick_star')
		.chancedOutput(Item.of('allthetweaks:patrick_star'), 20)
		.duration(432000)
		.EUt(ULV)
	
	// Star Forge Controller Block Recipe
	allthemods.recipes.gtceu.assembly_line('star_forge')
		.itemInputs(
			[
				'gtceu:uhv_machine_hull',
                '4x #gtceu:circuits/uhv',
				'4x gtceu:gravi_star',
				'4x gtceu:uv_field_generator',
				'64x gtceu:uhpic_chip',
				'64x gtceu:ruthenium_trinium_americium_neutronate_single_wire'
			])
		.inputFluids(
			[
				Fluid.of('gtceu:europium', 2592),
				Fluid.of('gtceu:soldering_alloy', 1152)
			])
		.itemOutputs('gtceu:star_forge')
        .stationResearch(b => b.researchStack(Item.of('gtceu:uv_field_generator')).CWUt(64).EUt(ZPM))
		.duration(2400)
		.EUt(ZPM)
	
	// Atomic Casing Recipes
	// Assembler Recipe
	allthemods.recipes.gtceu.assembler('atomic_casing')
		.itemInputs(
			[
				'gtceu:naquadah_alloy_frame',
				'6x gtceu:trinaquadalloy_plate'
			])
		.itemOutputs('2x gtceu:atomic_casing')
		.circuit(6)
		.duration(50)
		.EUt(16)
	// Shaped Crafting Grid Recipe
	allthemods.shaped('2x gtceu:atomic_casing', ['ABA', 'ACA', 'ADA'], {
		A: 'gtceu:trinaquadalloy_plate',
		B: '#c:tools/hammer',
		C: 'gtceu:naquadah_alloy_frame',
		D: '#c:tools/wrench',
	  }).id('gtceu:shaped/atomic_casing')
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
