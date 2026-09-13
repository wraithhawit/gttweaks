// Restores what AlmostUnified used to do for GregTech, and no longer can.
//
// ATM9 (AU 0.10.1, GTCEu 7.2) rewrote GregTech recipe OUTPUTS, so a bender there hands you
// alltheores:iron_plate. ATM10 (AU 1.4.2, GTCEu 8) cannot: GT8 registers its own recipe type with a
// custom codec, and AU never loads a single gtceu recipe - its log mentions gtceu only in
// AutoHiding lines. That left the pack in the worst of both worlds once gtceu was added to
// mod_priorities: AU HID 363 GregTech items while GregTech machines carried on producing them, so
// the item you actually received had no EMI page.
//
// KubeJS can still see them - 496 gtceu:bender recipes - so the rewrite happens here instead.
//
// The preferred item comes from AlmostUnified itself via AlmostKube rather than from a second copy
// of the priority list; a duplicate list would drift from config/almostunified the first time
// anyone edited it. Only recipes in the gtceu namespace are touched - AU already handles the rest,
// and re-doing its work here would be a second source of truth for those too.
//
// Safe because GregTech's own inputs are tag-based: a recipe asks for #c:plates/iron, not for
// gtceu:iron_plate, so an AllTheOres plate feeds straight back into every GT chain.

const $AlmostKube = Java.loadClass('com.almostreliable.unified.compat.kube.AlmostKube')
const $BuiltInRegistries_u = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
const $ResourceLocation_u = Java.loadClass('net.minecraft.resources.ResourceLocation')

ServerEvents.recipes(event => {
    let replacements = 0
    let tagsSeen = 0

    $AlmostKube.getTags().forEach(tagStr => {
        let rl = $ResourceLocation_u.parse(tagStr)
        let target = $AlmostKube.getTagTargetItem(rl)
        if (target === null || target.isEmpty()) return

        let targetId = $BuiltInRegistries_u.ITEM.getKey(target.getItem()).toString()
        if (targetId.startsWith('gtceu:')) return   // GregTech already wins this tag

        tagsSeen++
        $AlmostKube.getTagEntries(rl).forEach(entry => {
            if (!entry.startsWith('gtceu:')) return
            event.replaceOutput({ mod: 'gtceu' }, entry, targetId)
            replacements++
        })
    })

    console.info('[gt-unify] rewrote GregTech outputs for ' + replacements
        + ' item(s) across ' + tagsSeen + ' unified tag(s)')
})
