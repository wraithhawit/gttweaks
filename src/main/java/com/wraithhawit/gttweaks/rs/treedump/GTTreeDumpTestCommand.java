package com.wraithhawit.gttweaks.rs.treedump;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.rsapg.tree.PatternTargets;
import com.wraithhawit.rsapg.tree.PlanLine;
import com.wraithhawit.rsapg.tree.RecipeRequest;
import com.wraithhawit.rsapg.tree.TreePlanner;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /gttweaks rstreedump test <grid>}: plans real GregTech recipes against the tree-dump rig
 * and checks GTTreeDump's choices (lowest sufficient tier, multiblock via its input bus or RS
 * Pattern Buffer, too-high tier refused). Plans only; nothing is encoded.
 */
public final class GTTreeDumpTestCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("gttweaks/rs-treedump-test");
    private static final String[] TIERS = { "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv" };

    private GTTreeDumpTestCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gttweaks")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("rstreedump")
                        .then(Commands.literal("test")
                                .then(Commands.argument("grid", BlockPosArgument.blockPos())
                                        .executes(ctx -> test(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "grid")))))
                        .then(Commands.literal("encodebuffer")
                                .then(Commands.argument("grid", BlockPosArgument.blockPos())
                                        .then(Commands.argument("buffer", BlockPosArgument.blockPos())
                                                .executes(ctx -> encodeBuffer(ctx.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "grid"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "buffer"))))))));
    }

    private static int test(CommandSourceStack source, BlockPos gridPos) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(gridPos) instanceof PatternGridBlockEntity grid)) {
            source.sendFailure(Component.literal("no pattern grid at " + gridPos.toShortString()));
            return 0;
        }
        GTRecipe lvWire = find(level, GTRecipeTypes.WIREMILL_RECIPES, 1, 1);
        GTRecipe highWire = find(level, GTRecipeTypes.WIREMILL_RECIPES, 2, 8);
        GTRecipe lvBlast = find(level, GTRecipeTypes.BLAST_RECIPES, 0, 2);
        GTRecipe highBlast = find(level, GTRecipeTypes.BLAST_RECIPES, 4, 8);
        List<ResourceLocation> wiremills = new ArrayList<>();
        for (String tier : TIERS) {
            wiremills.add(ResourceLocation.fromNamespaceAndPath("gtceu", tier + "_wiremill"));
        }
        List<ResourceLocation> ebf = List.of(ResourceLocation.fromNamespaceAndPath("gtceu", "electric_blast_furnace"));
        // Distinct dummy outputs so the "already on the network" check never hides a line.
        List<RecipeRequest> requests = List.of(
                request("Wiremill LV", lvWire, wiremills, "minecraft:dead_bush"),
                request("Wiremill high", highWire, wiremills, "minecraft:cobweb"),
                request("EBF MV", lvBlast, ebf, "minecraft:fern"),
                request("EBF high", highBlast, ebf, "minecraft:vine"));
        List<TreePlanner.Assignment> plan = TreePlanner.plan(level, grid, requests);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < plan.size(); i++) {
            TreePlanner.Assignment a = plan.get(i);
            GTRecipe recipe = i == 0 ? lvWire : i == 1 ? highWire : i == 2 ? lvBlast : highBlast;
            LOGGER.info("rstreedump test: {} recipe={} tier={} -> {} {} [{}]", requests.get(i).label(),
                    recipe == null ? "none" : recipe.getId(), recipe == null ? -1 : RecipeHelper.getRecipeEUtTier(recipe),
                    a.line().status(), a.line().target(), a.target());
        }
        check(out, "LV wire recipe found", lvWire != null);
        check(out, "LV wire -> LV wiremill", lvWire != null && machine(plan.get(0), "gtceu:lv_wiremill"));
        check(out, "high wire recipe found", highWire != null);
        check(out, "high wire -> UV wiremill (LV skipped)", highWire != null && machine(plan.get(1), "gtceu:uv_wiremill"));
        if (lvBlast != null) {
            check(out, "MV-or-lower blast -> the EBF's RS Pattern Buffer (wins the tie)",
                    machine(plan.get(2), "gtceu:electric_blast_furnace") && plan.get(2).target().holder != null);
        } else {
            out.append("SKIP no EBF recipe at MV or below; ");
        }
        check(out, "HV+ blast refused (EBF is MV)", highBlast != null
                && plan.get(3).line().status() == PlanLine.Status.NO_TARGET);
        String result = out.toString();
        LOGGER.info("rstreedump test: {}", result);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    /**
     * Encodes one dummy EBF-tier processing recipe through rsapg's real encoder and checks the
     * pattern lands in the RS Pattern Buffer at {@code bufferPos}; removes it again afterwards.
     */
    private static int encodeBuffer(CommandSourceStack source, BlockPos gridPos, BlockPos bufferPos) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(gridPos) instanceof PatternGridBlockEntity grid)
                || !(level.getBlockEntity(bufferPos) instanceof com.wraithhawit.gttweaks.rs.machine.RSPatternBufferPartMachine buffer)) {
            source.sendFailure(Component.literal("need a pattern grid and an RS Pattern Buffer"));
            return 0;
        }
        GTRecipe blast = find(level, GTRecipeTypes.BLAST_RECIPES, 0, 2);
        var network = grid.getNetworkForItem();
        if (blast == null || network == null) {
            source.sendFailure(Component.literal("no MV blast recipe or no network"));
            return 0;
        }
        network.getComponent(com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent.class).insert(
                new ItemResource(com.refinedmods.refinedstorage.common.content.Items.INSTANCE.getPattern()), 1,
                com.refinedmods.refinedstorage.api.core.Action.EXECUTE, () -> "gttweaks rstreedump test");
        ResourceAmount in = new ResourceAmount(new ItemResource(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:cobblestone"))), 1);
        ResourceAmount out = new ResourceAmount(new ItemResource(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:fern"))), 1);
        RecipeRequest request = new RecipeRequest(RecipeRequest.Kind.PROCESSING, "Electric Blast Furnace",
                List.of(new ArrayList<>(List.of(in))), List.of(new ArrayList<>(List.of(out))),
                List.of(ResourceLocation.fromNamespaceAndPath("gtceu", "electric_blast_furnace")), blast.getId());
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        List<PlanLine> lines = com.wraithhawit.rsapg.tree.TreeEncoder.encode(player, grid, List.of(request));
        PlanLine line = lines.get(0);
        var patterns = buffer.getPatternInventory();
        int found = -1;
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            var stack = patterns.getStackInSlot(slot);
            var made = stack.isEmpty() ? java.util.Optional.<net.minecraft.world.item.ItemStack>empty()
                    : com.refinedmods.refinedstorage.common.content.Items.INSTANCE.getPattern().getOutput(stack, level);
            if (made.isPresent() && made.get().is(net.minecraft.world.item.Items.FERN)) {
                found = slot;
                break;
            }
        }
        StringBuilder result = new StringBuilder();
        check(result, "encoded and PLACED (" + line.status() + " " + line.target() + ")", line.status() == PlanLine.Status.PLACED);
        check(result, "pattern is in the RS Pattern Buffer (slot " + found + ")", found >= 0);
        if (found >= 0) {
            patterns.setStackInSlot(found, net.minecraft.world.item.ItemStack.EMPTY);
            result.append("(test pattern removed); ");
        }
        LOGGER.info("rstreedump encodebuffer: {}", result);
        source.sendSuccess(() -> Component.literal(result.toString()), false);
        return 1;
    }

    private static boolean machine(TreePlanner.Assignment a, String id) {
        PatternTargets.Target t = a.target();
        return t != null && t.machines.contains(ResourceLocation.parse(id));
    }

    @Nullable
    private static GTRecipe find(ServerLevel level, GTRecipeType type, int minTier, int maxTier) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof GTRecipe recipe && recipe.recipeType == type) {
                int tier = RecipeHelper.getRecipeEUtTier(recipe);
                if (tier >= minTier && tier <= maxTier) {
                    return recipe;
                }
            }
        }
        return null;
    }

    private static RecipeRequest request(String label, @Nullable GTRecipe recipe, List<ResourceLocation> workstations,
                                         String output) {
        ResourceAmount out = new ResourceAmount(new ItemResource(BuiltInRegistries.ITEM.get(ResourceLocation.parse(output))), 1);
        return new RecipeRequest(RecipeRequest.Kind.PROCESSING, label, List.of(), List.of(List.of(out)), workstations,
                recipe == null ? null : recipe.getId());
    }

    private static void check(StringBuilder out, String what, boolean ok) {
        out.append(ok ? "PASS " : "FAIL ").append(what).append("; ");
    }
}
