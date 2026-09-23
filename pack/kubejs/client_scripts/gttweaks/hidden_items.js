// gttweaks: items hidden from EMI because this instance removes every way to make them. The recipes
// go in server_scripts/gttweaks/; the reason for each lives beside its removal there.
//
// Kept out of the pack's own client_scripts/RecipeViewer.js, which an ATM10 update overwrites.
// Client scripts do not reload with /reload; use /kubejs reload client_scripts.

RecipeViewerEvents.removeEntriesCompletely('item', event => {
    // progression_skips.js
    event.remove('mekanism:oredictionificator')
})
