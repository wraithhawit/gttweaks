// Ported from All the Mods 9 to GTCEu 8 + KubeJS 2101. Original by AllTheMods Staff / community
// contributor; the license note from the original is kept in place below.
//
//   .notConsumable(Item.of('hostilenetworks:data_model', '{data_model: {id: "..."}}').weakNBT())
//     -> .notConsumableItem(`hostilenetworks:data_model[hostilenetworks:data_model="..."]`)
//       Three things changed at once. notConsumable is now the (TagPrefix, Material) form only;
//       NBT became data components; and weakNBT() is gone from KubeJS 2101. The bracket syntax is
//       what ATM10's own att_items.js already uses for data models, and it matters that it matches
//       PARTIALLY: a model a player has actually used also carries a hostilenetworks:data component
//       holding its kill count, and an exact match would reject every model except a pristine one.
//       That is exactly what ATM9's weakNBT() was for.
//       Note the component's value is a plain string on 1.21, not the {id: "..."} compound it was.
//
//   Voltage constants are now defined here.
//       The original used bare EV / IV / LuV / UV without ever declaring them. Nothing in GregTech
//       binds those as globals, so ATM9 must have been picking up another script's file-scope
//       consts through KubeJS's shared scope - which only works while the load order happens to
//       cooperate. Declaring them locally removes the dependency.

const $BuiltInRegistries_nn = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
const $ResourceLocation_nn = Java.loadClass('net.minecraft.resources.ResourceLocation')



// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

ServerEvents.recipes(allthemods => {
    // Declared INSIDE the callback on purpose. KubeJS server scripts share one Rhino top-level
    // scope, so a top-level `const UHV` here is a `const UHV` for every other script - and
    // starforge_recipes.js declares the same names, so it died on "redeclaration of const UHV"
    // and registered nothing at all. Function scope keeps each file's copy to itself.
    const ULV = GTValues.VA[GTValues.ULV], LV = GTValues.VA[GTValues.LV], MV = GTValues.VA[GTValues.MV],
          HV = GTValues.VA[GTValues.HV], EV = GTValues.VA[GTValues.EV], IV = GTValues.VA[GTValues.IV],
          LuV = GTValues.VA[GTValues.LuV], ZPM = GTValues.VA[GTValues.ZPM], UV = GTValues.VA[GTValues.UV],
          UHV = GTValues.VA[GTValues.UHV]

    // machine controller
    allthemods.shaped('gtceu:neural_node', ['AWA', 'ASA', 'WAW'],
        {
            A: 'gtceu:iv_field_generator',
            W: 'gtceu:samarium_iron_arsenic_oxide_hex_wire',
            S: 'gtceu:iv_machine_hull'
        }).id('gtceu:shaped/neural_node')

    // neural node recipes
    //
    // ATM9 had mods ATM10 does not - Croptopia, Thermal, Naturalist and four Farmer's Delight
    // addons - and a single missing drop aborts the WHOLE recipes callback, taking every recipe
    // below it. Each recipe is skipped if any of its items is absent, and the skips are counted so
    // a silently shrinking list cannot pass for success. A mob whose drops are missing has no data
    // model either, so the recipe could never have run.
    //
    // The guard can live inside the helper here because every argument is a plain string; where a
    // script passes Item.of(...) the arguments evaluate BEFORE the call and the guard has to sit at
    // the call site instead.
    let skipped = []
    let trimmed = []
    function itemPresent(spec) {
        if (typeof spec !== 'string') return true
        let s = spec.replace(/^[0-9]+x\s*/, '')
        if (s.startsWith('#')) return true
        let bracket = s.indexOf('[')
        if (bracket >= 0) s = s.substring(0, bracket)
        return $BuiltInRegistries_nn.ITEM.containsKey($ResourceLocation_nn.parse(s))
    }
    function allPresent(specs) {
        if (Array.isArray(specs)) return specs.every(itemPresent)
        return itemPresent(specs)
    }
    // Outputs are TRIMMED rather than all-or-nothing. Most of these recipes lose one modded reward
    // out of four - the pig gives porkchop, ham, a prediction and delightful:animal_fat, and only
    // the last is gone - so dropping the whole recipe would cost far more content than it saves.
    // A recipe is skipped only if an INPUT is missing or nothing at all survives.
    function presentOutputs(specs) {
        if (!Array.isArray(specs)) return itemPresent(specs) ? specs : null
        let keep = specs.filter(itemPresent)
        return keep.length > 0 ? keep : null
    }
    function neural_node(model, inputs, outputs, duration, power) {
        if (!allPresent(inputs)) {
            skipped.push(model)
            return
        }
        let outs = presentOutputs(outputs)
        if (outs === null) {
            skipped.push(model)
            return
        }
        if (Array.isArray(outputs) && outs.length < outputs.length) {
            trimmed.push(model + ' (-' + (outputs.length - outs.length) + ')')
        }
        allthemods.recipes.gtceu.neural_node('kubejs:' + model)
            .notConsumableItem(`hostilenetworks:data_model[hostilenetworks:data_model="hostilenetworks:${model}"]`)
            .itemInputs(inputs)
            .itemOutputs(outs)
            .duration(duration)
            .EUt(power)
    }

    let input = 'hostilenetworks:prediction_matrix'
    let time = 360 // 300 ticks in sim chamber + 60 in loot fab
    let ow = 'hostilenetworks:overworld_prediction'
    let nether = 'hostilenetworks:nether_prediction'
    let end = 'hostilenetworks:end_prediction'
    let tf = 'hostilenetworks:twilight_prediction'

    // mimic, twilight giant, lich, minoshroom, and redcap are excluded on purpose
    // piglich
    neural_node('allthemodium/piglich', input, ['4x allthemodium:piglich_heart', end], time, UV * 2)
    // wilden mobs
    neural_node('ars_nouveau/wilden_mobs', '3x ' + input, ['16x ars_nouveau:wilden_spike', '16x ars_nouveau:wilden_wing', '16x ars_nouveau:wilden_horn', '3x ' + ow], time * 3, IV * 2)
    // bat
    neural_node('bat', input, ['8x reliquary:bat_wing', ow], time, EV * 2)
    // blaze
    neural_node('blaze', '2x ' + input, ['16x minecraft:blaze_rod', '2x reliquary:molten_core', '2x ' + nether], time * 2, IV * 2)
    // chicken
    neural_node('chicken', '2x ' + input, ['32x minecraft:chicken', '24x minecraft:feather', '2x ' + ow], time * 2, EV * 2)
    // cod
    neural_node('cod', '3x ' + input, ['32x croptopia:roe', '32x minecraft:cod', '2x minecraft:bone_meal', '3x ' + ow], time * 3, EV)
    // cow
    neural_node('cow', '2x ' + input, ['32x minecraft:beef', '24x minecraft:leather', '2x ' + ow], time * 2, EV * 2)
    // creeper
    neural_node('creeper', '3x ' + input, ['32x minecraft:gunpowder', '4x minecraft:creeper_head', '2x reliquary:catalyzing_gland', '3x ' + ow], time * 3, IV)
    // drowned
    neural_node('drowned', '2x ' + input, ['64x minecraft:rotten_flesh', '8x minecraft:copper_ingot', '2x ' + ow], time * 2, IV)
    // elder guardian
    neural_node('elder_guardian', '8x ' + input, ['16x minecraft:cod', '2x minecraft:salmon', '2x minecraft:pufferfish', '2x minecraft:tropical_fish', '8x minecraft:prismarine_crystals', '24x minecraft:prismarine_shard', '32x minecraft:wet_sponge', '8x oceansdelight:elder_guardian_slab', '8x ' + ow], time * 8, LuV * 2)
    // ender dragon
    neural_node('ender_dragon', '7x ' + input, ['16x minecraft:dragon_breath', 'minecraft:dragon_egg', 'bhc:green_heart', '8x ends_delight:dragon_tooth', '4x ends_delight:dragon_leg', 'draconicevolution:dragon_heart', '2x mysticalagradditions:dragon_scale', '7x ' + end], time * 7, ZPM * 2)
    // enderman
    neural_node('enderman', '4x ' + input, ['16x minecraft:ender_pearl', 'minecraft:end_crystal', '4x evilcraft:ender_tear', '2x reliquary:nebulous_heart', '4x ' + end], time * 4, LuV)
    // endermite
    neural_node('endermite', '2x ' + input, ['8x ends_delight:raw_ender_mite_meat', 'endersdelight:mite_crust', '2x ' + end], time * 2, EV * 2)
    // evoker
    neural_node('evoker', '2x ' + input, ['2x minecraft:totem_of_undying', '16x minecraft:emerald', '2x ' + ow], time * 2, LuV * 2)
    // ghast
    neural_node('ghast', '3x ' + input, ['16x minecraft:ghast_tear', '32x minecraft:gunpowder', '2x reliquary:catalyzing_gland', '3x ' + nether], time * 3, IV * 2)
    // glow squid
    neural_node('glow_squid', '2x ' + input, ['32x croptopia:glowing_calamari', '32x minecraft:glow_ink_sac', '2x ' + ow], time * 2, EV)
    // guardian
    neural_node('guardian', '4x ' + input, ['24x minecraft:cod', '16x minecraft:prismarine_crystals', '2x reliquary:guardian_spike', '8x oceansdelight:guardian', '4x ' + ow], time * 4, IV * 2)
    // hoglin
    neural_node('hoglin', '5x ' + input, ['32x minecraft:porkchop', '16x minecraft:leather', '16x nethersdelight:hoglin_hide', '8x nethersdelight:hoglin_loin', '8x farmersdelight:ham', '5x ' + nether], time * 5, IV * 2)
    // iron golem
    neural_node('iron_golem', '2x ' + input, ['32x minecraft:iron_ingot', '24x minecraft:poppy', '2x ' + ow], time * 2, LuV * 2)
    // magma cube
    neural_node('magma_cube', '2x ' + input, ['32x minecraft:magma_cream', '2x reliquary:molten_core', '2x ' + nether], time * 2, IV * 2)
    // mooshroom
    neural_node('mooshroom', '4x ' + input, ['32x minecraft:beef', '24x minecraft:leather', '16x minecraft:red_mushroom', '16x minecraft:brown_mushroom', '4x ' + ow], time * 4, EV * 2)
    // phantom
    neural_node('phantom', input, ['8x minecraft:phantom_membrane', ow], time, IV)
    // pig
    neural_node('pig', '3x ' + input, ['32x minecraft:porkchop', '16x delightful:animal_fat', '8x farmersdelight:ham', '3x ' + ow], time * 3, EV * 2)
    // polar bear
    neural_node('polar_bear', '3x ' + input, ['32x minecraft:cod', '16x potionsmaster:gallbladder', '8x minecraft:salmon', '3x ' + ow], time * 3, EV * 2)
    // rabbit
    neural_node('rabbit', '3x ' + input, ['32x minecraft:rabbit_hide', '32x minecraft:rabbit', '8x minecraft:rabbit_foot', '3x ' + ow], time * 3, EV * 2)
    // sheep
    neural_node('sheep', '18x ' + input, ['32x minecraft:white_wool', '32x minecraft:orange_wool', '32x minecraft:magenta_wool', '32x minecraft:light_blue_wool', '32x minecraft:yellow_wool', '32x minecraft:lime_wool', '32x minecraft:pink_wool', '32x minecraft:gray_wool', '32x minecraft:light_gray_wool', '32x minecraft:cyan_wool', '32x minecraft:purple_wool', '32x minecraft:blue_wool', '32x minecraft:brown_wool', '32x minecraft:green_wool', '32x minecraft:red_wool', '32x minecraft:black_wool', '32x minecraft:mutton', '16x potionsmaster:bezoar', '18x ' + ow], time * 18, EV * 2)
    // shulker
    neural_node('shulker', '3x ' + input, ['8x minecraft:shulker_shell', '4x minecraft:end_rod', '8x ends_delight:shulker_meat', '3x ' + end], time * 3, LuV)
    // skeleton
    neural_node('skeleton', '5x ' + input, ['32x minecraft:arrow', '24x minecraft:bone', '4x minecraft:skeleton_skull', '2x reliquary:rib_bone', 'bhc:red_heart', '5x ' + ow], time * 5, IV * 2)
    // slime
    neural_node('slime', '3x ' + input, ['32x minecraft:slime_ball', '8x minecraft:slime_block', '2x reliquary:slime_pearl', '3x ' + ow], time * 3, IV * 2)
    // snow golem
    neural_node('snow_golem', '2x ' + input, ['32x minecraft:snowball', '2x reliquary:frozen_core', '2x ' + ow], time * 2, EV * 2)
    // spider
    neural_node('spider', '5x ' + input, ['32x minecraft:string', '16x minecraft:spider_eye', '4x minecraft:cobweb', '3x silentgear:fine_silk', '2x reliquary:chelicerae', '5x ' + ow], time * 5, IV)
    // squid
    neural_node('squid', '4x ' + input, ['32x minecraft:ink_sac', '32x croptopia:calamari', '2x reliquary:squid_beak', '2x oceansdelight:tentacles', '4x ' + ow], time * 4, EV)
    // basalz
    neural_node('thermal/basalz', input, ['16x thermal:basalz_rod', ow], time, LuV)
    // blitz
    neural_node('thermal/blitz', input, ['16x thermal:blitz_rod', ow], time, LuV)
    // blizz
    neural_node('thermal/blizz', input, ['16x thermal:blizz_rod', ow], time, LuV)
    // alpha yeti
    neural_node('twilightforest/alpha_yeti', '3x ' + input, ['32x twilightforest:ice_bomb', '4x twilightforest:alpha_yeti_trophy', '16x twilightforest:alpha_yeti_fur', '3x ' + tf], time * 3, ZPM * 2)
    // carminite golem
    neural_node('twilightforest/carminite_golem', '2x ' + input, ['32x twilightforest:towerwood', '16x minecraft:iron_ingot', '2x ' + tf], time * 2, LuV)
    // death tome
    neural_node('twilightforest/death_tome', '4x ' + input, ['32x minecraft:paper', '16x minecraft:book', '4x minecraft:writable_book', 'twilightforest:magic_map_focus', '4x ' + tf], time * 4, IV * 2)
    // deer
    neural_node('twilightforest/deer', '4x ' + input, ['32x twilightforest:raw_venison', '24x minecraft:leather', '32x naturalist:venison', '16x naturalist:antler', '4x ' + tf], time * 4, IV)
    // fire beetle
    neural_node('twilightforest/fire_beetle', '2x ' + input, ['32x minecraft:gunpowder', '2x reliquary:catalyzing_gland', '2x ' + tf], time * 2, LuV)
    // twilight goblin
    neural_node('twilightforest/goblin', input, ['16x twilightforest:armor_shard', tf], time, LuV)
    // helemt crab
    neural_node('twilightforest/helmet_crab', '2x ' + input, ['8x twilightforest:armor_shard', '24x minecraft:cod', '2x ' + tf], time * 2, LuV)
    // hydra
    neural_node('twilightforest/hydra', '3x ' + input, ['4x twilightforest:hydra_trophy', '24x twilightforest:hydra_chop', '16x twilightforest:fiery_blood', '3x ' + tf], time * 3, ZPM * 1.25)
    // kobold
    neural_node('twilightforest/kobold', '3x ' + input, ['32x minecraft:wheat', '16x minecraft:gold_nugget', '8x minecraft:candle', '3x ' + tf], time * 3, LuV)
    // minotaur
    neural_node('twilightforest/minotaur', '2x ' + input, ['32x twilightforest:raw_meef', 'twilightforest:maze_map_focus', '2x ' + tf], time * 2, LuV)
    // naga
    neural_node('twilightforest/naga', '2x ' + input, ['4x twilightforest:naga_trophy', '16x twilightforest:naga_scale', '2x ' + tf], time * 2, LuV * 2)
    // raven
    neural_node('twilightforest/raven', input, ['16x twilightforest:raven_feather', tf], time, IV * 2)
    // skeleton druid
    neural_node('twilightforest/skeleton_druid', '4x ' + input, ['32x twilightforest:torchberries', '32x minecraft:bone', '4x minecraft:skeleton_skull', '2x reliquary:rib_bone', '4x ' + tf], time * 4, LuV)
    // snow queen, no tri-bow
    neural_node('twilightforest/snow_queen', '3x ' + input, ['32x minecraft:snowball', '24x minecraft:packed_ice', '4x twilightforest:snow_queen_trophy', '3x ' + tf], time * 3, ZPM * 2)
    // twilight ice elemental
    neural_node('twilightforest/stable_ice_core', '2x ' + input, ['32x minecraft:snowball', '8x minecraft:packed_ice', '2x ' + tf], time * 2, IV * 2)
    // towerwood borer
    neural_node('twilightforest/towerwood_borer', input, ['8x twilightforest:borer_essence', tf], time, LuV)
    // troll
    neural_node('twilightforest/troll', input, ['4x twilightforest:magic_beans', tf], time, LuV * 2)
    // urghast
    neural_node('twilightforest/ur_ghast', '3x ' + input, ['32x twilightforest:carminite', '16x twilightforest:fiery_tears', '4x twilightforest:ur_ghast_trophy', '3x ' + tf], time * 3, UV)
    // winter wolf
    neural_node('twilightforest/winter_wolf', input, ['16x twilightforest:arctic_fur', tf], time, LuV * 2)
    // wraith
    neural_node('twilightforest/wraith', input, ['32x minecraft:glowstone_dust', tf], time, LuV)
    // yeti
    neural_node('twilightforest/yeti', input, ['32x twilightforest:arctic_fur', tf], time, LuV * 2)
    // vindicator, no axe
    neural_node('vindicator', input, ['8x minecraft:emerald', ow], time, LuV)
    // warden
    neural_node('warden', '5x ' + input, ['deeperdarker:heart_of_the_deep', 'deeperdarker:warden_carapace', 'apothic_enchanting:warden_tendril', '2x minecraft:echo_shard', 'bhc:blue_heart', '5x ' + end], time * 5, ZPM * 1.25)
    // witch
    neural_node('witch', '7x ' + input, ['16x minecraft:redstone', '16x minecraft:glowstone_dust', '32x minecraft:sugar', '32x minecraft:stick', '16x minecraft:spider_eye', '16x minecraft:glass_bottle', '2x reliquary:witch_hat', '7x ' + ow], time * 7, IV * 2)
    // wither
    neural_node('wither', '3x ' + input, ['minecraft:nether_star', 'bhc:yellow_heart', 'mysticalagradditions:withering_soul', '3x ' + end], time * 3, ZPM * 1.25)
    // wither skeleton
    neural_node('wither_skeleton', '6x ' + input, ['32x wstweaks:fragment', '24x minecraft:bone', '32x minecraft:coal', '3x minecraft:wither_skeleton_skull', '2x reliquary:withered_rib', '2x bhc:wither_bone', '6x ' + nether], time * 6, LuV * 1.5)
    // zombie
    neural_node('zombie', '6x ' + input, ['64x minecraft:rotten_flesh', '8x minecraft:iron_ingot', '16x minecraft:carrot', '16x minecraft:potato', '2x reliquary:zombie_heart', 'bhc:red_heart', '6x ' + ow], time * 6, IV * 2)
    // zombified piglin
    neural_node('zombified_piglin', '2x ' + input, ['64x minecraft:rotten_flesh', '8x minecraft:gold_ingot', '2x ' + nether], time * 2, IV * 2)


    // ===== Models this pack has that ATM9 did not =====
    //
    // Sixteen Eternal Starlight mobs (registered by ATM10's own Eternal Starlight/Datapacks.js,
    // which also adds the kubejs:starlight_prediction they drop) plus vanilla 1.21's breeze. ATM9
    // could not have covered these, so without this block they were the only models in the pack with
    // no Neural Node recipe - workable in HNN's own Simulation Chamber, but with no GregTech route.
    //
    // Outputs are each model's own fabricator_drops verbatim, plus one prediction per matrix. That
    // is exactly what ATM9 did: its blaze recipe is HNN's blaze fabricator_drops (16 rods, 2 molten
    // cores) and 2 nether predictions, and every other recipe checked follows the same shape. The
    // drops are therefore not invented - they are what the Loot Fabricator already gives.
    //
    // Power follows the sim_cost ladder ATM9 used throughout: 32 -> EV, 64 -> EV*2, 128 -> IV,
    // 256 -> IV*2, 512 -> LuV, 1024 -> LuV*2, 2560 -> ZPM, 4096 -> ZPM*2, 8192 -> UV. Matrix count
    // is the one genuinely free choice; it scales with how much loot comes out, as ATM9's did.
    let starlight = 'kubejs:starlight_prediction'

    // sim_cost 128 - passive or trivial
    neural_node('ent', '2x ' + input, ['32x minecraft:stick', '32x eternal_starlight:lunar_berries', '16x eternal_starlight:lunar_leaves', '2x ' + starlight], time * 2, IV)
    neural_node('gleech', '2x ' + input, ['8x eternal_starlight:gleech_egg', '2x ' + starlight], time * 2, IV)
    neural_node('luminaris', '2x ' + input, ['32x eternal_starlight:luminaris', '2x ' + starlight], time * 2, IV)
    neural_node('luminofish', '2x ' + input, ['32x eternal_starlight:luminofish', '2x ' + starlight], time * 2, IV)
    neural_node('ratlin', '2x ' + input, ['32x eternal_starlight:ratlin_meat', '32x eternal_starlight:cave_moss', '16x minecraft:leather', '2x ' + starlight], time * 2, IV)
    neural_node('yeti', '2x ' + input, ['32x minecraft:string', '16x eternal_starlight:white_yeti_fur', '2x ' + starlight], time * 2, IV)

    // sim_cost 256
    neural_node('breeze', '3x ' + input, ['16x minecraft:breeze_rod', '3x ' + ow], time * 3, IV * 2)
    neural_node('aurora_deer', '3x ' + input, ['24x eternal_starlight:aurora_deer_steak', '16x minecraft:leather', 'eternal_starlight:aurora_deer_antler', '3x ' + starlight], time * 3, IV * 2)
    neural_node('grimstone_golem', '3x ' + input, ['32x eternal_starlight:grimstone', '32x eternal_starlight:cobbled_grimstone', '3x ' + starlight], time * 3, IV * 2)
    neural_node('lonestar_skeleton', '3x ' + input, ['24x minecraft:bone', '16x eternal_starlight:deepsilver_nugget', '3x ' + starlight], time * 3, IV * 2)
    neural_node('nightfall_spider', '3x ' + input, ['32x minecraft:string', '16x eternal_starlight:nightfall_spider_eye', '3x ' + starlight], time * 3, IV * 2)

    // sim_cost 512
    neural_node('crystallized_moth', '3x ' + input, ['16x eternal_starlight:blue_starlight_crystal_shard', '16x eternal_starlight:red_starlight_crystal_shard', '4x eternal_starlight:shivering_gel', '3x ' + starlight], time * 3, LuV)
    neural_node('freeze', '3x ' + input, ['12x eternal_starlight:frozen_tube', '3x ' + starlight], time * 3, LuV)
    neural_node('tangled', '3x ' + input, ['24x minecraft:bone', '32x minecraft:vine', '4x eternal_starlight:tangled_skull', '3x ' + starlight], time * 3, LuV)
    neural_node('thirst_walker', '3x ' + input, ['8x eternal_starlight:tooth_of_hunger', '3x ' + starlight], time * 3, LuV)

    // Three ids in ATM10's own data models are stale and were corrected here: Eternal Starlight
    // 0.8.1 renamed swamp_silver_ingot/nugget to deepsilver_*, and trapped_soul no longer exists at
    // all (soul_dew is the nearest surviving soul item). The pack's Datapacks.js still lists the
    // dead ids, so its Loot Fabricator drops for these same mobs are missing them too.

    // sim_cost 8192 - the two Eternal Starlight bosses. The recipe type allows 19 item outputs
    // (setMaxIOSize(3, 19, 0, 0)), and lunar_monstrosity has 19 fabricator drops of its own, so its
    // plain arrows are dropped to make room for the prediction - the spectral and amaramber arrows
    // survive, which is the interesting half of that reward anyway.
    neural_node('lunar_monstrosity', '5x ' + input, ['64x minecraft:spectral_arrow', '32x eternal_starlight:amaramber_arrow', '24x eternal_starlight:tangled_skull', '16x minecraft:iron_ingot', '12x minecraft:gold_ingot', '16x eternal_starlight:seeking_eye', '16x minecraft:coal', '6x eternal_starlight:glacite_shard', '12x eternal_starlight:soul_dew', '16x eternal_starlight:lunar_berries', '16x eternal_starlight:tenacious_petal', '16x eternal_starlight:tenacious_vine', '6x eternal_starlight:deepsilver_ingot', '3x eternal_starlight:aethersent_ingot', '6x eternal_starlight:thermal_springstone_ingot', 'eternal_starlight:crescent_spear', 'eternal_starlight:wand_of_teleportation', 'eternal_starlight:moonring_bow', '5x ' + starlight], time * 5, UV)
    neural_node('starlight_golem', '5x ' + input, ['64x minecraft:spectral_arrow', '32x minecraft:arrow', '32x eternal_starlight:amaramber_arrow', '24x eternal_starlight:oxidized_golem_steel_ingot', '16x minecraft:iron_ingot', '12x minecraft:gold_ingot', '16x eternal_starlight:seeking_eye', '16x minecraft:coal', '6x eternal_starlight:glacite_shard', '12x eternal_starlight:forge_armor_trim_smithing_template', '16x eternal_starlight:lunar_berries', '6x eternal_starlight:deepsilver_ingot', '3x eternal_starlight:aethersent_ingot', '6x eternal_starlight:thermal_springstone_ingot', 'eternal_starlight:energy_sword', '5x ' + starlight], time * 5, UV)

    if (skipped.length > 0) {
        console.info('[neural-node] skipped ' + skipped.length + ' recipe(s) entirely: ' + skipped.join(', '))
    }
    if (trimmed.length > 0) {
        console.info('[neural-node] kept ' + trimmed.length + ' recipe(s) with missing outputs dropped: ' + trimmed.join(', '))
    }
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
