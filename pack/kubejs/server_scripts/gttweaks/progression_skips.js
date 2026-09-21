// gttweaks: progression skips - recipes that let a player reach a GregTech-tier material early.
// Tracked in https://github.com/wraithhawit/gttweaks/issues/1. Remove a shortcut here only when the
// mod keeps its own intended route to the same item; gate it instead when removal would break that mod.

ServerEvents.recipes(event => {
    // Modern Industrialization titanium dust -> hot ingot. MI's intended chain is ore -> hot ingot
    // (materials/blast_furnace/titanium_from_raw) -> ingot -> dust; this recipe only turns MI's own
    // dust back into ingots. Industrial Foregoing generates ore -> dust for every material with raw
    // and dust tags, so with it installed this became ore -> dust -> ingot, long before GregTech's
    // HV electric blast furnace (480 EU/t, 1941 K) can smelt titanium. Anyone holding titanium dust
    // that late can smelt it in GregTech; MI keeps its raw-ore route.
    event.remove({ id: 'modern_industrialization:materials/titanium/blast_furnace/dust' })

    // Tungsten. GregTech asks for an EV electric blast furnace (1920 EU/t, 3600 K, Nichrome coil);
    // these six recipes hand the same ingot over far earlier, and all of them read generic tags, so
    // they accept GregTech's own gtceu:tungsten_dust straight out of a macerator.
    //
    // Neo Vitae generates a plain furnace and blast-furnace recipe over c:dusts/tungsten, which is
    // the cheapest bypass in the pack - a campfire-tier machine for an EV metal.
    event.remove({ id: 'neovitae:materials/smelting_tungsten_dust' })
    event.remove({ id: 'neovitae:materials/blasting_tungsten_dust' })

    // Immersive Engineering's arc furnace converts every form of tungsten to the ingot. Its own gate
    // is steel, which this pack unifies across IE, MI, AllTheOres and Mekanism and is common enough
    // not to gate anything. Left in, these would reopen the skip the moment Neo Vitae's are gone.
    event.remove({ id: 'immersiveengineering:arcfurnace/dust_tungsten' })
    event.remove({ id: 'immersiveengineering:arcfurnace/raw_ore_tungsten' })
    event.remove({ id: 'immersiveengineering:arcfurnace/ore_tungsten' })
    event.remove({ id: 'immersiveengineering:arcfurnace/raw_block_tungsten' })

    // What is left: GregTech's EBF, and MI's implosion compressor (materials/implosion_compressor/
    // tungsten), which needs a stainless steel nugget - stainless is a hot ingot needing MI's vacuum
    // freezer, which is behind the electronic circuit this pack gates on gtceu:circuits/hv. Osmium
    // and iridium are the same shape but are NOT removed: the pack unifies both and Mekanism needs
    // osmium from its first machine onward. See issue #1 for that reasoning.
})
