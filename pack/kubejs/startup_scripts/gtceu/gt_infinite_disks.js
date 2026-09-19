// Refined Storage equivalents of gt_infinity_cells.js: one infinite disk per fluid the Star Forge's
// infinity cell recipes make, registered with the 'infinite_disk' item type from the Refined Storage -
// Additional Disks mod. Each disk supplies its fluid endlessly from a Refined Storage disk drive.
//
// Item icons follow the cells: Refined Storage's 64B fluid disk with the teal fluid pixels recoloured
// to the same fluid colours gt_infinity_cells.js uses (lava uses ATM10's lava cell colour), and the
// creative disk's tier pixel. In a disk drive they use Refined Storage's own fluid disk model.

if (Platform.isLoaded('rsadditionaldisks')) {
    StartupEvents.registry('item', event => {
        const disks = {
            chlorine: 'gtceu:chlorine',
            sulfuric_acid: 'gtceu:sulfuric_acid',
            hydrogen: 'gtceu:hydrogen',
            oxygen: 'gtceu:oxygen',
            styrene_butadiene_rubber: 'gtceu:styrene_butadiene_rubber',
            polytetrafluoroethylene: 'gtceu:polytetrafluoroethylene',
            polybenzimidazole: 'gtceu:polybenzimidazole',
            ethylene: 'gtceu:ethylene',
            lubricant: 'gtceu:lubricant',
            air: 'gtceu:air',
            nether_air: 'gtceu:nether_air',
            ender_air: 'gtceu:ender_air',
            fluorine: 'gtceu:fluorine',
            soldering_alloy: 'gtceu:soldering_alloy'
        }

        Object.keys(disks).forEach(name => {
            event.create(`${name}_disk`, 'infinite_disk')
                .fluidType(disks[name])
                .texture(`kubejs:item/${name}_disk`)
        })
    })
}
