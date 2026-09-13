// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
// Port note: every name inside .components() is namespaced, which ATM9's are not. GTCEu 8 runs
// component names through GTMaterials.get(), which now parses them as ResourceLocations - a bare
// 'calcium' resolves to minecraft:calcium, misses, logs one WARN line and yields Material.NULL.
// So trinaquadalloy, fluorite, dolomite and nitinol all registered with null components, losing
// their generated decomposition recipes and, for nitinol, the .colorAverage() it computes from
// them. Nothing throws; the only symptom was eleven "X is not a known Material" lines at startup.
// Same break as the fluorite vein in server_scripts/mods/gtceu/mining_dim_ores.js.


const $FluidProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty')
const $OreProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.OreProperty')
const $FluidBuilder = Java.loadClass('com.gregtechceu.gtceu.api.fluids.FluidBuilder')
const $FluidStorageKeys = Java.loadClass('com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys')
const $GasTier = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.BlastProperty$GasTier')

GTCEuStartupEvents.registry('gtceu:material', allthemods => {
    let prop = new $FluidProperty()
    prop.getStorage().enqueueRegistration($FluidStorageKeys.LIQUID, new $FluidBuilder())
    GTMaterials.NetherStar.setProperty(PropertyKey.FLUID, prop)
    GTMaterials.Trinium.setProperty(PropertyKey.ORE, new $OreProperty(1, 1, true))

    allthemods.create('inert_nether_essence')
        .fluid()
        .color(0x500bbf)
    
    allthemods.create('molten_piglich_heart')
        .fluid()
        .color(0xe6af15)

    allthemods.create("trinaquadalloy")
        .ingot().fluid()
        .color(0x281832).iconSet(GTMaterialIconSet.BRIGHT)
        .flags(GTMaterialFlags.GENERATE_PLATE)
        .components('6x gtceu:trinium', '2x gtceu:naquadah', '1x gtceu:carbon')
        ["blast(java.util.function.UnaryOperator)"](b => b.temp(8747, $GasTier.HIGHER).blastStats(GTValues.VA[GTValues.ZPM], 1200))

    allthemods.create('fluorite')
        .gem().ore()
        .color(0x0c9949).iconSet('diamond')
        .components('1x gtceu:calcium', '2x gtceu:fluorine')

    allthemods.create('dolomite')
        .dust().ore()
        .color(0xf2e4bd).iconSet('rough')
        .components('1x gtceu:calcium', '1x gtceu:magnesium', '1x gtceu:carbon','3x gtceu:oxygen')
    
    allthemods.create('star_matter')
        ['fluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey,com.gregtechceu.gtceu.api.fluids.FluidBuilder)'](GTFluidStorageKeys.PLASMA, new GTFluidBuilder().state(GTFluidState.PLASMA).customStill())
        .color(0xb219d1)

    allthemods.create('nitinol')
        .ingot().fluid()
        .iconSet(GTMaterialIconSet.METALLIC)
        .colorAverage()
        .flags(GTMaterialFlags.GENERATE_PLATE)
        .components('4x gtceu:nickel', '6x gtceu:titanium')
        ["blast(java.util.function.UnaryOperator)"](b => b.temp(1583, $GasTier.HIGH).blastStats(GTValues.VA[GTValues.IV], 1200))
    
    allthemods.create('alltheneutronium')
        .ingot().fluid()
        .iconSet(GTMaterialIconSet.METALLIC)
        .color(0xfcfc3d).secondaryColor(0x000000)
        .flags(GTMaterialFlags.GENERATE_BOLT_SCREW, GTMaterialFlags.GENERATE_GEAR, GTMaterialFlags.GENERATE_SMALL_GEAR, GTMaterialFlags.GENERATE_PLATE)
        ["blast(java.util.function.UnaryOperator)"](b => b.temp(9000, $GasTier.HIGHEST).blastStats(GTValues.VA[GTValues.ZPM], 1200))
        .rotorStats(1200, 300, 13.0, 655360)

        allthemods.create('vibtronium')
        .ingot().fluid()
        .iconSet(GTMaterialIconSet.METALLIC)
        .color(0x72fcb7).secondaryColor(0x000000)
        .flags(GTMaterialFlags.GENERATE_BOLT_SCREW, GTMaterialFlags.GENERATE_GEAR, GTMaterialFlags.GENERATE_SMALL_GEAR, GTMaterialFlags.GENERATE_PLATE)
        ["blast(java.util.function.UnaryOperator)"](b => b.temp(9000, $GasTier.HIGHEST).blastStats(GTValues.VA[GTValues.UV], 1200))
        .rotorStats(1000, 400, 14.0, 655360 * 2)

        allthemods.create('unobtronium')
        .ingot().fluid()
        .iconSet(GTMaterialIconSet.METALLIC)
        .color(0xe782f2).secondaryColor(0x000000)
        .flags(GTMaterialFlags.GENERATE_BOLT_SCREW, GTMaterialFlags.GENERATE_GEAR, GTMaterialFlags.GENERATE_SMALL_GEAR, GTMaterialFlags.GENERATE_PLATE)
        ["blast(java.util.function.UnaryOperator)"](b => b.temp(10100, $GasTier.HIGHEST).blastStats(GTValues.VA[GTValues.UHV], 1200))
        .rotorStats(1600, 200, 16.0, 655360 * 4)
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
