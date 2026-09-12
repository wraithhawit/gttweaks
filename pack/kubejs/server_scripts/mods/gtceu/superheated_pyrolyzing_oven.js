// Ported from All the Mods 9. Original by AllTheMods Staff / community contributor.
//
// The Superheated Pyrolyzing Oven has no recipes of its own: it clones every Pyrolyse Oven recipe
// twice - once unchanged, once "superheated" with amounts, duration and voltage scaled up and the
// programmed circuit shifted by one so the two variants can never be selected by the same setting.
//
// FOUR things changed between GTCEu 7 (ATM9) and GTCEu 8, all silent:
//
//  1. Capability keys are namespaced: recipe.inputs.item -> inputs['gtceu:item'], tickInputs.eu ->
//     tickInputs['gtceu:eu']. (Same change that breaks 26 Productive Bees recipes in this pack.)
//
//  2. The stack shape moved. GTCEu 7 wrapped stacks in a typed content object
//     (content.type == 'gtceu:sized', content.count). GTCEu 8 writes
//     content: { ingredient: {...}, count: N } for items, { ingredient: {...}, amount: N } for
//     fluids, and a circuit's setting at content.ingredient.configuration with
//     ingredient.type == 'gtceu:circuit'.
//
//  3. category must name a registered gtceu:recipe_category. ATM9 hardcoded "gtceu:gregstar",
//     which does not exist in GTCEu 8. Not carried over - the builder assigns the recipe type's own.
//
//  4. **Raw JSON recipes cannot be used at all.** ATM9's idiom was to clone the source recipe's
//     JSON and hand it to event.custom(). On GTCEu 8 that loads and then kills the server during
//     resource reload: GTRecipe.id is a plain field that the codec never writes, only
//     GTRecipeBuilder sets it, and GT's recipe-lookup DB is a hash set keyed on it -
//     "Cannot invoke ResourceLocation.hashCode() because this.id is null".
//     Setting it from KubeJS does not help: custom(json).id('x') throws TypeError, because
//     KubeRecipe has both an id FIELD and an id() METHOD and Rhino resolves the field first.
//     So the JSON is read for its CONTENTS only, and every recipe is rebuilt through the builder.
//
// VERIFIED 2026-08-28: the recipe manager holds all 52 - 26 plain, 26 superheated, every pair
// complete. EMI displays 48, which is a display collapse (its entries for a circuit-less recipe's
// plain and superheated variants differ only in counts), not missing content. GregTech's lookup DB
// separately rejects spo/pyrolyse_oven/bio_chaff_to_fermented_biomass as equal to an existing
// recipe; that removes it from the runtime matcher, not from the manager.
//
// Copying ATM9's file unchanged would not have thrown - the scaling branches would have read
// undefined and skipped, emitting two identical copies of each stock recipe. The counters at the
// bottom exist so that failure is loud rather than a full-looking recipe list.

const $SizedFluidIngredient_spo = Java.loadClass('net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient')
const $TagKey_spo = Java.loadClass('net.minecraft.tags.TagKey')
const $Registries_spo = Java.loadClass('net.minecraft.core.registries.Registries')
const $ResourceLocation_spo = Java.loadClass('net.minecraft.resources.ResourceLocation')

const SPO_ITEM = 'gtceu:item'
const SPO_FLUID = 'gtceu:fluid'
const SPO_EU = 'gtceu:eu'

const SPO_DURATION_MULT = 20
const SPO_VOLTAGE_MULT = 50
const SPO_AMOUNT_MULT = 1000

ServerEvents.recipes(allthemods => {
    let cloned = 0
    let superheated = 0
    let unreadable = []
    let signatures = {}
    let collisions = []

    // Ingredient JSON -> KubeJS spec string. Returns null for a shape this does not know, and the
    // first few unknowns are logged in full: "15 were unreadable" is useless without the shape.
    let shapesLogged = 0
    function spec(ingredient, count) {
        if (!ingredient) return null
        if (ingredient.item) return count + 'x ' + ingredient.item
        if (ingredient.tag) return count + 'x #' + ingredient.tag
        if (shapesLogged < 3) {
            shapesLogged++
            console.warn('[spo] unhandled ingredient shape: ' + JSON.stringify(ingredient))
        }
        return null
    }

    function isCircuit(entry) {
        return entry.content && entry.content.ingredient
            && entry.content.ingredient.type == 'gtceu:circuit'
    }

    // Everything is collected and validated BEFORE the builder is touched. The first version
    // created the recipe and then discovered an unreadable ingredient, which registered 15 recipes
    // missing inputs - worse than not registering them, because they look real in EMI.
    function build(id, recipe, mult, durationMult, voltageMult, circuitShift) {
        let itemIn = [], itemOut = [], fluidIn = [], fluidOut = []
        let circuit = null
        let ok = true

        function readItems(section, sink) {
            if (!section || !section[SPO_ITEM]) return
            section[SPO_ITEM].forEach(entry => {
                if (isCircuit(entry)) {
                    let cfg = parseInt(entry.content.ingredient.configuration)
                    circuit = (cfg + circuitShift) % 33
                    return
                }
                let count = entry.content.count === undefined ? 1 : parseInt(entry.content.count)
                let s = spec(entry.content.ingredient, count * mult)
                if (s === null) { ok = false; return }
                sink.push(s)
            })
        }
        // Fluid ingredients come in two shapes and the tag one is why 15 recipes were rejected:
        // sugar_to_charcoal_nitrogen asks for {"tag":"c:nitrogen"}, not {"fluid":"..."}. Items had
        // a tag branch from the start; fluids did not, and rejected silently.
        function readFluids(section, sink) {
            if (!section || !section[SPO_FLUID]) return
            section[SPO_FLUID].forEach(entry => {
                let ing = entry.content.ingredient
                let amount = parseInt(entry.content.amount) * mult
                if (ing && ing.fluid) {
                    sink.push(Fluid.of(ing.fluid, amount))
                } else if (ing && ing.tag) {
                    let key = $TagKey_spo.create($Registries_spo.FLUID, $ResourceLocation_spo.parse(ing.tag))
                    sink.push($SizedFluidIngredient_spo.of(key, amount))
                } else {
                    ok = false
                    if (shapesLogged < 3) {
                        shapesLogged++
                        console.warn('[spo] unhandled FLUID shape: ' + JSON.stringify(ing))
                    }
                }
            })
        }

        readItems(recipe.inputs, itemIn)
        readItems(recipe.outputs, itemOut)
        readFluids(recipe.inputs, fluidIn)
        readFluids(recipe.outputs, fluidOut)
        if (!ok) return false

        // EMI collapses recipes whose display is identical, so a lower count in game than the number
        // built is not necessarily missing content. Signatures are recorded to tell those apart.
        let sig = JSON.stringify([itemIn, itemOut, fluidIn.map(f => String(f)), fluidOut.map(f => String(f)), circuit])
        if (signatures[sig] === undefined) { signatures[sig] = id } else { collisions.push(id + ' == ' + signatures[sig]) }

        let r = allthemods.recipes.gtceu.superheated_pyrolyzing_oven(id)
        if (itemIn.length > 0) r.itemInputs(itemIn)
        if (itemOut.length > 0) r.itemOutputs(itemOut)
        if (fluidIn.length > 0) r.inputFluids(fluidIn)
        if (fluidOut.length > 0) r.outputFluids(fluidOut)
        if (circuit !== null) r.circuit(circuit)

        r.duration(parseInt(recipe.duration) * durationMult)

        let eu = 30
        if (recipe.tickInputs && recipe.tickInputs[SPO_EU] && recipe.tickInputs[SPO_EU][0]) {
            eu = parseInt(recipe.tickInputs[SPO_EU][0].content)
        }
        r.EUt(eu * voltageMult)
        return true
    }

    allthemods.forEachRecipe({ type: 'gtceu:pyrolyse_oven' }, rawRecipe => {
        const recipe = JSON.parse(rawRecipe.json)
        const path = rawRecipe.getOrCreateId().getPath()

        // The 1:1 copy, so the multiblock still runs ordinary pyrolyse recipes.
        if (build('kubejs:spo/' + path, recipe, 1, 1, 1, 0)) {
            cloned++
        } else {
            unreadable.push(path)
            // One full dump beats a name: the shape is what is needed, and the fluid branch can
            // reject without ever reaching the ingredient logger.
            if (unreadable.length == 1) {
                console.warn('[spo] first unreadable recipe in full: ' + JSON.stringify(recipe).substring(0, 700))
            }
        }

        // The superheated variant: same recipe, bigger everything, circuit shifted by one.
        if (build('kubejs:spo/' + path + '_superheated', recipe,
                SPO_AMOUNT_MULT, SPO_DURATION_MULT, SPO_VOLTAGE_MULT, 1)) {
            superheated++
        }
    })

    if (cloned == 0) {
        console.error('[spo] no gtceu:pyrolyse_oven recipes matched - the oven will be empty')
    } else {
        console.info('[spo] built ' + cloned + ' plain and ' + superheated + ' superheated recipes')
    }
    if (collisions.length > 0) {
        console.warn('[spo] ' + collisions.length + ' recipe(s) are identical to another and will show once in EMI: ' + collisions.join('; '))
    }
    if (unreadable.length > 0) {
        console.error('[spo] ' + unreadable.length + ' recipe(s) had an ingredient shape this script '
            + 'does not understand, so they are incomplete: ' + unreadable.join(', '))
    }
})
