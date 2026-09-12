// priority: 100
//
// Ported from All the Mods 9's kubejs/startup_scripts/read_json_from_mod.js
// (original by Uncandango, via AllTheMods Staff).
//
// Reads a mod's own data files straight out of its jar. apiary_recipes.js needs it: Productive
// Bees keeps each bee's flower in data/productivebees/productivebees/<group>/<bee>.json, and there
// is no runtime API to ask for it during a recipe reload.
//
// It has to be a STARTUP script: KubeJS 2101 refuses `global.x = ...` from a client or server
// script ("'global' cannot be assigned to in client or server scripts"), which is where ATM9 kept
// it too. It sits under startup_scripts/gtceu/ rather than the folder root so the gttweaks
// headless harness, which stages only that folder, gets it along with the recipes it serves.
// Load order does not matter - the globals are defined while scripts load and read much later,
// when the ServerEvents.recipes callbacks run, which is also when the mod pack list this reads
// through has been populated.
//
// Four things changed from ATM9's 1.20 Forge version:
//
//  1. ResourcePackLoader moved to net.neoforged.neoforge.resource, and getPackFor now returns a
//     Pack.ResourcesSupplier rather than the PackResources itself. Opening it needs a
//     PackLocationInfo, which is only used for display, so a synthetic one is fine.
//
//  2. **FallbackResourceManager is not usable from Rhino here.** Its listResources returns a
//     TreeMap keyed by ResourceLocation, and Rhino wraps a Java Map so that ANY property access -
//     including `.forEach` - is looked up as a map key first. Looking up the String "forEach" in a
//     TreeMap<ResourceLocation, _> throws ClassCastException inside the comparator before Rhino
//     ever falls back to the method. So this uses PackResources.listResources instead, which hands
//     each entry to a callback and never builds a Map. The manager is gone entirely.
//
//  3. new ResourceLocation(s) is private; use ResourceLocation.parse(s).
//
//  4. JsonIO is gone from KubeJS 2101. Rhino's own JSON.parse gives plain JS objects, which is
//     what the callers want anyway.
//
// The "minecraft" pseudo-mod branch is deliberately dropped rather than ported. It needed
// ServerPacksSource, whose constructor now takes a DirectoryValidator, and nothing here reads
// vanilla data. Asking for it throws instead of quietly returning nothing.

const $ResourcePackLoader_rj = Java.loadClass('net.neoforged.neoforge.resource.ResourcePackLoader')
const $PackType_rj = Java.loadClass('net.minecraft.server.packs.PackType')
const $PackLocationInfo_rj = Java.loadClass('net.minecraft.server.packs.PackLocationInfo')
const $PackSource_rj = Java.loadClass('net.minecraft.server.packs.repository.PackSource')
const $Component_rj = Java.loadClass('net.minecraft.network.chat.Component')
const $Optional_rj = Java.loadClass('java.util.Optional')
const $ResourceLocation_rj = Java.loadClass('net.minecraft.resources.ResourceLocation')
const $ScriptIO_rj = Java.loadClass("com.wraithhawit.gttweaks.ScriptIO")

function packTypeOf(type) {
    if (type == 'assets') return $PackType_rj.CLIENT_RESOURCES
    if (type == 'data') return $PackType_rj.SERVER_DATA
    throw new Error('readJson*FromMod: type must be "assets" or "data", got "' + type + '"')
}

// Returns the mod's PackResources, or null when the mod is absent. The caller closes it.
function openModPack(modId) {
    if (modId == 'minecraft') {
        throw new Error('readJson*FromMod: the vanilla pack branch was not ported; nothing needs it')
    }
    let supplier = $ResourcePackLoader_rj.getPackFor(modId).orElse(null)
    if (supplier === null) {
        console.warn('[readJson] mod "' + modId + '" is not loaded, or ships no resources')
        return null
    }
    // Display-only, but PackLocationInfo has no no-arg form.
    return supplier.openPrimary(new $PackLocationInfo_rj(
        'mod/' + modId, $Component_rj.literal(modId), $PackSource_rj.DEFAULT, $Optional_rj.empty()))
}

// Bytes to text is the one step Rhino cannot take here: KubeJS's class filter refuses
// java.nio.charset.StandardCharsets, and new java.lang.String(bytes, "UTF-8") resolves to a
// constructor wanting a byte ("Cannot convert NativeJavaArray to byte"). gttweaks does it.
function readStream(stream) {
    return $ScriptIO_rj.readUtf8(stream)
}

/**
 * One file. `resourceLocation` is relative to the pack type's root, with or without the .json
 * suffix. Returns null if the mod or the file is missing.
 */
global.readJsonFileFromMod = (type, modId, resourceLocation) => {
    let packType = packTypeOf(type)
    let pack = openModPack(modId)
    if (pack === null) return null
    try {
        let path = resourceLocation.endsWith('.json') ? resourceLocation : resourceLocation + '.json'
        let supplier = pack.getResource(packType, $ResourceLocation_rj.parse(path))
        if (supplier === null) {
            console.warn('[readJson] "' + path + '" not found in ' + modId)
            return null
        }
        return JSON.parse(readStream(supplier.get()))
    } finally {
        pack.close()
    }
}

/**
 * Every .json under `folder` in the mod's OWN namespace, as a plain object keyed by the full
 * resource location string ("productivebees:productivebees/alloys/enderium.json"). Returns {} if
 * the mod is missing, so a caller can tell "no such mod" from a throw.
 */
global.readJsonFolderFromMod = (type, modId, folder) => {
    let out = {}
    let packType = packTypeOf(type)
    let pack = openModPack(modId)
    if (pack === null) return out
    try {
        pack.listResources(packType, modId, folder, (location, supplier) => {
            let key = location.toString()
            if (!key.endsWith('.json')) return
            try {
                out[key] = JSON.parse(readStream(supplier.get()))
            } catch (e) {
                console.warn('[readJson] could not read ' + key + ': ' + e)
            }
        })
    } finally {
        pack.close()
    }
    return out
}
