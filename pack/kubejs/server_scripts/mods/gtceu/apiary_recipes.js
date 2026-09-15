// priority: 10
// Ported from All the Mods 9. Original by AllTheMods Staff / community contributor.
//
// Three machines, all fed from Productive Bees' own data:
//   Apiary I   - one recipe per bee, cloned from productivebees:advanced_beehive.
//   Apiary II  - the same bee 32 times over, one per programmed-circuit setting, scaled by the
//                circuit number and gated on a perfect-gene bee.
//   Comb Processor - one recipe per productivebees:centrifuge recipe, plus a 4x "comb block" and
//                a 40960x UEV variant of each.
//
// This was the last and hardest of ATM9's thirteen GregTech scripts. What had to change:
//
//  1. **IngredientHelper is gone.** It was never a mod: KubeJS 2001 bound its own
//     IngredientPlatformHelper under that name, and KubeJS 2101 deleted the platform layer.
//     .tag(x) becomes Ingredient.of('#x'); .strongNBT(stack) on an output becomes the stack.
//
//  2. **weakNBT has no 1.21 replacement, so one was written.** A bee cage keeps the whole caged
//     entity under minecraft:custom_data, and every component ingredient in this pack -
//     NeoForge's DataComponentIngredient, GregTech's ExDataComponentIngredient, Productive Bees'
//     own ComponentIngredient - compares components for equality, which for custom_data means the
//     entire tag. gttweaks:partial_data (see PartialDataIngredient.java) matches a subset instead.
//     That is the only piece of this port that could not be done in a script.
//
//  3. **Cage keys survived; gene VALUES did not.** custom_data still holds `entity`, and `type`
//     when the entity is productivebees:configurable_bee - the same keys ATM9 wrote. But the gene
//     attributes moved to a NeoForge data attachment (productivebees:attributes_handler, written
//     into the entity save under "neoforge:attachments"), and GeneValue serialises as a string:
//     ATM9's `bee_productivity: 3` is now `bee_productivity: "productivity.very_high"`.
//
//     ATM9's `name:` key is deliberately NOT carried over. It was matched against ATM9's own
//     renamed bees ("CreeBee"), and the cage writes whatever the bee's display name is, so
//     requiring it here would match nothing. `entity` alone identifies a non-configurable bee.
//
//  4. **Productive Bees' data changed shape twice.** `chance` is now 0..1, not 0..100, so the
//     conversion to GregTech's base-10000 is *10000 and not *100 - getting that wrong would have
//     made every chanced output a 100x rarer silent nerf. And a centrifuge recipe's fluid output
//     moved OUT of `outputs` into a top-level `fluid` key; the old loop would simply never have
//     seen a fluid. Fluid tags went forge: -> c:, and rather than hand-maintaining ATM9's table of
//     tag -> mod fluid (half of which named Thermal, which ATM10 does not ship) each tag is
//     resolved against the live registry.
//
//  5. GTCEu 8 arities: chancedInput, chancedOutput and chancedFluidOutput all lost their
//     tierBoost argument, notConsumable(item) became notConsumableItem(SizedIngredient), and no
//     GT version binds bare `MV`/`EV`/`UEV` - it is GTValues.VA[GTValues.MV].
//
//  6. ATM9 split any output above 127 into chunks of 127, because a 1.20 ItemStack count was a
//     byte. GregTech 8 takes counts on a SizedIngredient, whose count is a plain int, so the
//     chunking is dropped - which also keeps Apiary II inside its 15 output slots, where 1280
//     items in chunks of 127 would have overflowed for any bee with two outputs.
//
//  7. Every Productive Bees upgrade item moved to the productivelib mod it now bundles, and the
//     comb-block one was renamed on the way: productivebees:upgrade_comb_block is now
//     productivelib:upgrade_block, and upgrade_simulator / upgrade_productivity_4 keep their
//     names under productivelib:.
//
// The six kubejs:bee/<type>/flowers tags ATM9 declared in tags.js were all aliases for
// #minecraft:flowers, so that tag is used directly and no tag file is needed.
//
// Every id out of Productive Bees' data is checked against the registry before it is used, and
// anything missing is counted and named rather than thrown - a mod ATM10 does not ship must not
// abort the whole recipes callback, which is what took the greenhouse to zero recipes once.

const $Partial_ap = Java.loadClass('com.wraithhawit.gttweaks.ingredient.PartialDataIngredient')
const $SizedIngredient_ap = Java.loadClass('net.neoforged.neoforge.common.crafting.SizedIngredient')
const $BuiltInRegistries_ap = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
const $ResourceLocation_ap = Java.loadClass('net.minecraft.resources.ResourceLocation')
const $TagKey_ap = Java.loadClass('net.minecraft.tags.TagKey')
const $Registries_ap = Java.loadClass('net.minecraft.core.registries.Registries')

// GeneValue.getSerializedName() for the three attributes ATM9 demanded: weather tolerance ANY,
// productivity VERY_HIGH, behavior METATURNAL (its 2 / 3 / 2).
const AP_GOOD_GENES =
    '"neoforge:attachments":{"productivebees:attributes_handler":{'
    + 'bee_productivity:"productivity.very_high",'
    + 'bee_behavior:"behavior.metaturnal",'
    + 'bee_weather_tolerance:"weather_tolerance.any"}}'

// 1 perfect bee with 4 productivity omegas yields 40 comb blocks per cycle; the circuit number is
// how many bees the recipe asks for, so output scales with it.
const AP_PER_BEE = 40

// apiary_ii is setMaxIOSize(6, 15, 1, 0); comb_processor is setMaxIOSize(3, 9, 0, 1).
const AP_II_MAX_OUTPUTS = 15
const CP_MAX_OUTPUTS = 9
const CP_BLOCK_MULT = 4
const CP_MAX_MULT = 40960

ServerEvents.recipes(allthemods => {

    // The effective bee definitions, not the jar's: ATM10 overrides some in kubejs/data (the four
    // Ars Nouveau essence bees want essence blocks, not loose essence), and the machines must ask
    // for what the bees themselves do. The jar read is only a fallback.
    const beeOverrides = []
    const beeData = allthemods.resourceManager
        ? global.readJsonFolderFromPacks(allthemods.resourceManager, 'data', 'productivebees',
            'productivebees', beeOverrides)
        : global.readJsonFolderFromMod('data', 'productivebees', 'productivebees')
    if (!allthemods.resourceManager) {
        console.warn('[apiary] no resource manager on the recipes event; reading bee definitions '
            + 'from the Productive Bees jar, so pack overrides are ignored')
    }
    const beeDataKeys = Object.keys(beeData)

    let apiaryI = 0
    let apiaryII = 0
    let combRecipes = 0
    let skippedNoFlower = []
    let skippedMissingItem = []
    let droppedOutputs = 0
    let unknownShapes = []

    function note(list, what) {
        if (list.indexOf(what) < 0) list.push(what)
    }

    function itemExists(id) {
        return $BuiltInRegistries_ap.ITEM.containsKey($ResourceLocation_ap.parse(id))
    }

    function fluidExists(id) {
        return $BuiltInRegistries_ap.FLUID.containsKey($ResourceLocation_ap.parse(id))
    }

    function itemTagHolders(tag) {
        let holders = $BuiltInRegistries_ap.ITEM.getTag(
            $TagKey_ap.create($Registries_ap.ITEM, $ResourceLocation_ap.parse(tag)))
        return holders.isPresent() ? holders.get() : null
    }

    function itemTagSize(tag) {
        let holders = itemTagHolders(tag)
        return holders === null ? 0 : holders.size()
    }

    function itemTagIds(tag) {
        let holders = itemTagHolders(tag)
        let ids = []
        if (holders === null) return ids
        for (let i = 0; i < holders.size(); i++) {
            ids.push($BuiltInRegistries_ap.ITEM.getKey(holders.get(i).value()).toString())
        }
        return ids
    }

    // A fluid tag cannot be an output, so pick the tag's first member. ATM9 hand-mapped nine
    // forge: tags to specific mod fluids; five of those mods are not in ATM10, and the c: tags do
    // the same job without a table to keep current.
    function firstFluidOfTag(tag) {
        let holders = $BuiltInRegistries_ap.FLUID.getTag(
            $TagKey_ap.create($Registries_ap.FLUID, $ResourceLocation_ap.parse(tag)))
        if (!holders.isPresent() || holders.get().size() == 0) return null
        return $BuiltInRegistries_ap.FLUID.getKey(holders.get().get(0).value()).toString()
    }

    function sized(ingredient, count) {
        return new $SizedIngredient_ap(ingredient, count)
    }

    // --- bee cage ingredients -------------------------------------------------------------
    // beeId is the full id from the recipe's `ingredient` field. Configurable bees are all one
    // entity discriminated by `type`; creeper_bee and vanilla bees are their own entities.
    function cageData(beeId, entity, perfect) {
        let body = entity == 'productivebees:configurable_bee'
            ? 'entity:"productivebees:configurable_bee",type:"' + beeId + '"'
            : 'entity:"' + entity + '"'
        return '{' + body + (perfect ? ',' + AP_GOOD_GENES : '') + '}'
    }

    function cage(beeId, entity, perfect, count) {
        return sized($Partial_ap.ofItem('productivebees:bee_cage', cageData(beeId, entity, perfect)), count)
    }

    // A configurable honeycomb / comb block keyed to one bee.
    function comb(beeId, count) {
        return sized(Ingredient.withData(
            'productivebees:configurable_comb', { 'productivebees:bee_type': beeId }), count)
    }

    // The bee's flower, out of its effective bee definition, with ATM9's overrides.
    // Returns { kind: 'item'|'tag'|'fluid', value } or null.
    function findFlower(beeType) {
        // ATM9's six kubejs:bee/<type>/flowers tags were all #minecraft:flowers.
        if (beeType == 'pepto_bismol' || beeType == 'zombie' || beeType == 'plastic'
            || beeType == 'sticky_resin' || beeType == 'menril' || beeType == 'energized_glowstone') {
            return { kind: 'tag', value: 'minecraft:flowers' }
        }
        if (beeType == 'creeper_bee') return { kind: 'tag', value: 'productivebees:flowers/powdery' }
        if (beeType == 'bee') return { kind: 'tag', value: 'minecraft:flowers' }

        let index = beeDataKeys.findIndex(key => key.endsWith('/' + beeType + '.json'))
        if (index < 0) return null
        let data = beeData[beeDataKeys[index]]

        if (data.flowerFluid !== undefined) {
            let fluid = data.flowerFluid
            // ATM9's two overrides. Thermal is not in ATM10, so the oily bee takes whatever the
            // pack's crude oil tag holds.
            if (beeType == 'oily') {
                let tagged = firstFluidOfTag('c:crude_oil')
                if (tagged !== null) fluid = tagged
            } else if (beeType == 'salty') {
                fluid = 'mekanism:brine'      // brine, not water - ATM9's call
            }
            if (!fluidExists(fluid)) { note(skippedMissingItem, fluid); return null }
            return { kind: 'fluid', value: fluid }
        }
        if (data.flowerBlock !== undefined) {
            let block = data.flowerBlock
            if (beeType == 'chocolate') block = 'minecraft:cocoa_beans'   // listed as minecraft:cocoa in PB
            else if (beeType == 'molybdenum') block = 'gtceu:molybdenum_block'
            else if (beeType == 'palladium') block = 'gtceu:palladium_block'
            else if (beeType == 'neodymium') block = 'gtceu:neodymium_block'
            if (!itemExists(block)) { note(skippedMissingItem, block); return null }
            return { kind: 'item', value: block }
        }
        if (data.flowerTag !== undefined) {
            if (itemTagSize(data.flowerTag) == 0) { note(skippedMissingItem, '#' + data.flowerTag); return null }
            return { kind: 'tag', value: data.flowerTag }
        }
        if (data.flowerItem !== undefined) {
            if (!itemExists(data.flowerItem)) { note(skippedMissingItem, data.flowerItem); return null }
            return { kind: 'item', value: data.flowerItem }
        }
        return null
    }

    function applyFlower(builder, flower) {
        if (flower.kind == 'fluid') builder.notConsumableFluid(Fluid.of(flower.value, 1000))
        else if (flower.kind == 'tag') builder.notConsumableItem(sized(Ingredient.of('#' + flower.value), 1))
        else builder.notConsumableItem(sized(Ingredient.of(flower.value), 1))
    }

    // --- machine controllers ---------------------------------------------------------------
    allthemods.shaped('gtceu:apiary_i', ['BAB', 'ACA', 'WSW'], {
        A: '#gtceu:circuits/mv',
        W: 'gtceu:gold_single_cable',
        S: 'gtceu:clean_machine_casing',
        C: 'productivelib:upgrade_simulator',
        // ATM9 asked for productivebees:upgrade_comb_block. Productive Bees 1.21 both renamed it and
        // moved every upgrade item to the productivelib mod it now bundles.
        B: 'productivelib:upgrade_block'
    }).id('gtceu:shaped/apiary_i')

    allthemods.shaped('gtceu:apiary_ii', ['CAC', 'ACA', 'WSW'], {
        A: '#gtceu:circuits/ev',
        W: 'gtceu:black_steel_single_cable',
        S: 'gtceu:stable_machine_casing',
        C: 'productivelib:upgrade_productivity_4'
    }).id('gtceu:shaped/apiary_ii')

    allthemods.shaped('gtceu:comb_processor', ['BAB', 'ACA', 'WSW'], {
        A: '#gtceu:circuits/mv',
        W: 'gtceu:gold_single_cable',
        S: 'gtceu:clean_machine_casing',
        C: 'productivebees:heated_centrifuge',
        B: 'gtceu:stainless_steel_rotor'
    }).id('gtceu:shaped/comb_processor')

    // --- Apiary II: the same bee at 32 circuit settings -------------------------------------
    function makeCircuitRecipes(id, beeId, entity, flower, outputs) {
        let built = 0
        for (let i = 1; i < 33; i++) {
            let r = allthemods.recipes.gtceu.apiary_ii(id + '/circuit_' + i)
                .circuit(i)
                .EUt(GTValues.VA[GTValues.EV])
                .duration(Math.floor(5250 / 8))
                .notConsumableItem(cage(beeId, entity, true, i))
                .notConsumableItem(sized(
                    Ingredient.of('productivelib:upgrade_productivity_4'),
                    (Math.floor((i - 1) / 5) + 1) * 4))

            let emitted = 0
            outputs.forEach(out => {
                if (emitted >= AP_II_MAX_OUTPUTS) { droppedOutputs++; return }
                emitted++
                let stack = out.make(AP_PER_BEE * i)
                if (out.chance >= 10000) r.itemOutputs(stack)
                else r.chancedOutput(stack, out.chance)
            })

            applyFlower(r, flower)
            built++
        }
        return built
    }

    // --- Apiary I / II from every advanced_beehive recipe ------------------------------------
    allthemods.forEachRecipe({ type: 'productivebees:advanced_beehive' }, rawRecipe => {
        const recipe = JSON.parse(rawRecipe.json)
        const beeId = recipe.ingredient
        if (!beeId || beeId.indexOf(':') < 0) {
            note(unknownShapes, 'advanced_beehive ingredient: ' + JSON.stringify(recipe.ingredient))
            return
        }
        const beeType = beeId.split(':')[1]

        // These three read their produce off a live entity, not the recipe, so a static GT recipe
        // cannot reproduce them. Skipped in ATM9 for the same reason.
        if (beeType == 'rancher_bee' || beeType == 'ether_gas' || beeType == 'hematophagous') return

        let entity = 'productivebees:configurable_bee'
        if (beeType == 'creeper_bee') entity = 'productivebees:creeper_bee'
        else if (beeType == 'bee') entity = 'minecraft:bee'

        // --- results ---
        let outputs = []
        let results = recipe.results === undefined ? [] : recipe.results
        results.forEach(result => {
            let item = result.item
            if (!item) { note(unknownShapes, 'result without item: ' + JSON.stringify(result)); return }

            // base-10000, and PB 1.21 states chance as a fraction.
            let chance = result.chance === undefined ? 10000 : Math.round(result.chance * 10000)
            if (chance <= 0) return

            // A component ingredient, i.e. a configurable honeycomb keyed to this bee. The Apiary
            // gives the comb BLOCK, as ATM9 did - it is the 4x form and what the Comb Processor
            // takes.
            if (item.type == 'productivebees:component') {
                outputs.push({ chance: chance, make: n => comb(beeId, n) })
                return
            }
            if (item.tag) {
                // Pollen was skipped in ATM9 too.
                if (item.tag == 'c:pollens' || item.tag == 'forge:pollen') return
                if (itemTagSize(item.tag) == 0) { note(skippedMissingItem, '#' + item.tag); return }
                let tag = item.tag
                outputs.push({ chance: chance, make: n => sized(Ingredient.of('#' + tag), n) })
                return
            }
            if (item.item) {
                let id = item.item
                if (id == 'productivebees:configurable_honeycomb') {
                    outputs.push({ chance: chance, make: n => comb(beeId, n) })
                    return
                }
                if (id == 'productivebees:honeycomb_powdery') id = 'productivebees:comb_powdery'
                else if (id == 'minecraft:honeycomb') id = 'minecraft:honeycomb_block'
                if (!itemExists(id)) { note(skippedMissingItem, id); return }
                let fixed = id
                outputs.push({ chance: chance, make: n => sized(Ingredient.of(fixed), n) })
                return
            }
            note(unknownShapes, 'result item shape: ' + JSON.stringify(item))
        })

        if (outputs.length == 0) {
            note(skippedMissingItem, beeId + ' (nothing left to give)')
            return
        }

        let flower = findFlower(beeType)
        if (flower === null) { note(skippedNoFlower, beeId); return }

        // Apiary I: one bee in, consumed 1% of the time.
        let r = allthemods.recipes.gtceu.apiary_i('kubejs:gtceu/apiary_i/' + beeType)
            .EUt(GTValues.VA[GTValues.MV])
            // time in hive (4800) + pollinating (450), halved by the machine
            .duration(Math.floor(5250 / 2))
            .chancedInput(cage(beeId, entity, false, 1), 100)

        outputs.forEach(out => {
            if (out.chance >= 10000) r.itemOutputs(out.make(1))
            else r.chancedOutput(out.make(1), out.chance)
        })
        applyFlower(r, flower)
        apiaryI++

        apiaryII += makeCircuitRecipes('kubejs:gtceu/apiary_ii/' + beeType, beeId, entity, flower, outputs)
    })

    // --- Lumber and Quarry bees -------------------------------------------------------------
    // These two have no advanced_beehive recipe: they chew whatever block they are standing on and
    // give it back, so ATM9 generated one Apiary II set per member of their flower tags. In a pack
    // this size that is most of #minecraft:logs, #minecraft:leaves, #c:stones, #c:sands and
    // #c:gravels, and it is by far the largest share of this script's recipe count.
    let genBees = 0
    function genericBee(entity, tag, idPrefix) {
        let ids = itemTagIds(tag)
        if (ids.length == 0) {
            note(skippedMissingItem, '#' + tag)
            return
        }
        ids.forEach(block => {
            let parts = block.split(':')
            apiaryII += makeCircuitRecipes(
                idPrefix + '/' + parts[0] + '/' + parts[1],
                null, entity,
                { kind: 'item', value: block },
                [{ chance: 10000, make: n => sized(Ingredient.of(block), n) }])
        })
        genBees += ids.length
    }

    genericBee('productivebees:lumber_bee', 'productivebees:flowers/lumber',
        'kubejs:gtceu/apiary_ii/lumberbee')
    genericBee('productivebees:quarry_bee', 'productivebees:flowers/quarry',
        'kubejs:gtceu/apiary_ii/quarrybee')

    // --- Comb Processor: every centrifuge recipe, three ways --------------------------------
    allthemods.forEachRecipe({ type: 'productivebees:centrifuge' }, rawRecipe => {
        const recipe = JSON.parse(rawRecipe.json)
        const path = rawRecipe.getOrCreateId().getPath().replace('centrifuge/', '')
        const id = 'kubejs:gtceu/comb_processor/' + path
        const ing = recipe.ingredient
        if (!ing) { note(unknownShapes, 'centrifuge without ingredient: ' + path); return }

        // The comb and its block form. "honeycomb" -> "comb" is Productive Bees' own naming:
        // configurable_honeycomb / configurable_comb, honeycomb_powdery / comb_powdery.
        let input, inputBlock
        if (ing.type == 'productivebees:component') {
            let beeId = ing.components === undefined ? null : ing.components['productivebees:bee_type']
            let items = ing.items
            if (!beeId || !items || typeof items != 'string') {
                note(unknownShapes, 'component ingredient: ' + JSON.stringify(ing))
                return
            }
            let blockId = items.replace('honey', '')
            if (!itemExists(items)) { note(skippedMissingItem, items); return }
            input = n => sized(Ingredient.withData(items, { 'productivebees:bee_type': beeId }), n)
            inputBlock = itemExists(blockId)
                ? (n => sized(Ingredient.withData(blockId, { 'productivebees:bee_type': beeId }), n))
                : null
        } else if (ing.item) {
            let plain = ing.item
            if (!itemExists(plain)) { note(skippedMissingItem, plain); return }
            let blockId = plain == 'minecraft:honeycomb' ? 'minecraft:honeycomb_block' : plain.replace('honey', '')
            input = n => sized(Ingredient.of(plain), n)
            inputBlock = itemExists(blockId) ? (n => sized(Ingredient.of(blockId), n)) : null
        } else if (ing.tag) {
            if (itemTagSize(ing.tag) == 0) { note(skippedMissingItem, '#' + ing.tag); return }
            let tag = ing.tag
            input = n => sized(Ingredient.of('#' + tag), n)
            inputBlock = null
        } else {
            note(unknownShapes, 'centrifuge ingredient: ' + JSON.stringify(ing))
            return
        }

        // default centrifuge time is 300 ticks; the heated one is nine times faster
        const duration = Math.floor(300 / 9)

        let plainR = allthemods.recipes.gtceu.comb_processor(id)
            .duration(duration).EUt(GTValues.VA[GTValues.MV]).itemInputs(input(1))
        let blockR = inputBlock === null ? null : allthemods.recipes.gtceu.comb_processor(id + '_block')
            .duration(duration).EUt(GTValues.VA[GTValues.MV]).itemInputs(inputBlock(1)).circuit(1)
        let maxR = inputBlock === null ? null : allthemods.recipes.gtceu.comb_processor(id + '_block_max')
            .duration(20 * 16).EUt(GTValues.VA[GTValues.UEV]).itemInputs(inputBlock(CP_MAX_MULT)).circuit(2)

        let slots = { plain: 0, block: 0, max: 0 }
        function give(builder, key, stack, chance) {
            if (builder === null) return
            if (slots[key] >= CP_MAX_OUTPUTS) { droppedOutputs++; return }
            slots[key]++
            if (chance >= 10000) builder.itemOutputs(stack)
            else builder.chancedOutput(stack, chance)
        }

        let outputs = recipe.outputs === undefined ? [] : recipe.outputs
        outputs.forEach(output => {
            let item = output.item
            if (!item) { note(unknownShapes, 'centrifuge output: ' + JSON.stringify(output)); return }

            let chance = 10000
            if (output.chance !== undefined) {
                let base = Math.round(output.chance * 10000)
                // a small reward for building the Comb Processor: +1.25% or x1.25, whichever helps
                chance = Math.min(Math.max(Math.round(base * 1.25), base + 125), 10000)
            }
            let count = output.max === undefined ? 1 : Math.max(1, Math.round(output.max))

            let make
            if (item.tag) {
                if (itemTagSize(item.tag) == 0) { note(skippedMissingItem, '#' + item.tag); return }
                let tag = item.tag
                make = n => sized(Ingredient.of('#' + tag), n)
            } else if (item.item) {
                if (!itemExists(item.item)) { note(skippedMissingItem, item.item); return }
                let plain = item.item
                make = n => sized(Ingredient.of(plain), n)
            } else if (item.type == 'productivebees:component' && typeof item.items == 'string') {
                let beeId = item.components === undefined ? null : item.components['productivebees:bee_type']
                let items = item.items
                if (!beeId || !itemExists(items)) { note(skippedMissingItem, String(items)); return }
                make = n => sized(Ingredient.withData(items, { 'productivebees:bee_type': beeId }), n)
            } else {
                note(unknownShapes, 'centrifuge output item: ' + JSON.stringify(item))
                return
            }

            // Wax is a by-product of pressing a comb, not of the block, so ATM9 withheld it from
            // the block recipes. c: replaced forge: for that tag.
            let isWax = item.tag == 'c:wax' || item.tag == 'forge:wax'

            give(plainR, 'plain', make(count), chance)
            if (!isWax) {
                give(blockR, 'block', make(count * CP_BLOCK_MULT), chance)
                // The MAX recipe rolls nothing: a chanced output becomes its expected value.
                give(maxR, 'max',
                    make(Math.max(1, Math.floor(count * CP_BLOCK_MULT * CP_MAX_MULT * chance / 10000))),
                    10000)
            }
        })

        // The fluid moved out of `outputs` and into its own top-level key in Productive Bees 1.21.
        if (recipe.fluid) {
            let amount = recipe.fluid.amount === undefined ? 1000 : Math.round(recipe.fluid.amount)
            let fluid = null
            if (recipe.fluid.fluid) {
                fluid = fluidExists(recipe.fluid.fluid) ? recipe.fluid.fluid : null
                if (fluid === null) note(skippedMissingItem, recipe.fluid.fluid)
            } else if (recipe.fluid.tag) {
                fluid = firstFluidOfTag(recipe.fluid.tag)
                if (fluid === null) note(skippedMissingItem, '#' + recipe.fluid.tag)
            }
            if (fluid !== null && amount > 0) {
                plainR.outputFluids(Fluid.of(fluid, amount))
                if (blockR !== null) blockR.outputFluids(Fluid.of(fluid, amount * CP_BLOCK_MULT))
                if (maxR !== null) maxR.outputFluids(Fluid.of(fluid, amount * CP_BLOCK_MULT * CP_MAX_MULT))
            }
        }

        combRecipes += 1 + (blockR === null ? 0 : 1) + (maxR === null ? 0 : 1)
    })

    // --- what actually got built -------------------------------------------------------------
    if (beeDataKeys.length == 0) {
        console.error('[apiary] read no bee definitions - every flower lookup will fail. '
            + 'Check _read_json_from_mod.js.')
    }
    if (beeOverrides.length > 0) {
        console.info('[apiary] ' + beeOverrides.length + ' bee definition(s) taken from a pack '
            + 'override: ' + beeOverrides.join(', '))
    }
    console.info('[apiary] ' + apiaryI + ' Apiary I, ' + apiaryII + ' Apiary II and '
        + combRecipes + ' Comb Processor recipes from ' + beeDataKeys.length + ' bee definitions'
        + ' (' + genBees + ' of the Apiary II sets are lumber/quarry blocks, ' + (genBees * 32)
        + ' recipes)')
    if (skippedNoFlower.length > 0) {
        console.warn('[apiary] ' + skippedNoFlower.length + ' bee(s) skipped for having no flower: '
            + skippedNoFlower.join(', '))
    }
    if (skippedMissingItem.length > 0) {
        console.warn('[apiary] ' + skippedMissingItem.length + ' id(s)/tag(s) from Productive Bees '
            + 'data do not exist here and were left out: ' + skippedMissingItem.join(', '))
    }
    if (droppedOutputs > 0) {
        console.warn('[apiary] ' + droppedOutputs + ' output(s) dropped for exceeding a machine '
            + 'output slot count - those recipes give less than the source recipe did')
    }
    if (unknownShapes.length > 0) {
        console.error('[apiary] ' + unknownShapes.length + ' data shape(s) this script does not '
            + 'understand, so something was skipped:')
        unknownShapes.forEach(s => console.error('[apiary]   ' + s))
    }
})
