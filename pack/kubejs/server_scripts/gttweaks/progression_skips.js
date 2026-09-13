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
})
