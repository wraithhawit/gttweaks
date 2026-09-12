// ATM10 ships this file removing every recipe that produces a Greg Star, because the pack has no
// GregTech: AllTheTweaks provides greg_star_block (9 stars -> block) and the reverse, but nothing
// anywhere makes the STAR. Both recipes are therefore dead ends and the pack strips them.
//
// This instance HAS GregTech, and starforge_recipes.js (ported from ATM9) makes greg stars in the
// Star Forge. The removals are commented out so the compression recipes work again rather than
// leaving a craftable star with no block and a block with no recipe.
//
// The matching half is client-side: kubejs/client_scripts/RecipeViewer.js hides these same items
// from EMI, and those lines have to stay commented out too. Client scripts do NOT reload with
// /reload - use /kubejs reload client_scripts, or restart the client.

ServerEvents.recipes(allthemods =>{
  // allthemods.remove({output: 'allthetweaks:greg_star'})
  // allthemods.remove({output: 'allthetweaks:greg_star_block'})
  // for(let i=1; i < 10; i++){
  //   allthemods.remove({output: `allthecompressed:greg_star_block_${i}x`})}
})
