// gttweaks: ATM9's GregStar quest rewards a "GregStar Champion" gtceu:doge_coin, but no GTCEu 1.20
// build registered that item (only its texture and a stale uk_ua lang key were left in 7.2), and
// GTCEu 8 has neither. This registers the coin as a decorative item under kubejs: so a later GTCEu
// cannot collide with it. Texture: assets/kubejs/textures/item/doge_coin.png, copied from GTCEu 7.2.

StartupEvents.registry('item', event => {
    event.create('doge_coin')
        .displayName('Doge Coin')
        .tooltip('§7wow much coin how money so crypto plz mine v rich very currency wow')
})
