package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.ResearchData;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeTypeUILayout;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeViewerWidget;
import com.gregtechceu.gtceu.api.recipe.gui.RecipeViewerCapabilityLayoutBuilder;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.ResearchManager;
import com.wraithhawit.gttweaks.mixin.RecipeTypeLayoutAccessor;

import brachy.modularui.drawable.progress.ProgressDrawable;
import brachy.modularui.integration.recipeviewer.RecipeSlotRole;
import brachy.modularui.integration.recipeviewer.RecipeViewerSlotWidget;
import brachy.modularui.integration.recipeviewer.entry.fluid.FluidStackList;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.value.DoubleValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ProgressWidget;
import brachy.modularui.widgets.layout.Flow;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Restores the 1.20 Assembly Line recipe page: a 4x4 item grid feeding a column of four fluids
 * through the connector-line bar, with the output and the research data slot on the right.
 *
 * <p>GregTech 1.20 drew that page from {@code assets/gtceu/ui/recipe_type/assembly_line.rtui}. The
 * 8.0 snapshot still ships the file, byte-identical, but {@code loadRecipeTypeUIFromFile} now throws
 * {@code NotImplementedException}, so every recipe type falls back to the generic column layout.
 * The positions below are copied from that file.
 *
 * <p>Item and fluid slots are not filled here. {@code GTRecipeViewerWidget} finds each slot by
 * {@link GTRecipeViewerWidget#capabilityWidgetName} after the layout is built and fills it with
 * GregTech's own content builders, so chances, tooltips and slot roles behave as they do on every
 * other recipe page.
 *
 * <p>A custom layout replaces GregTech's {@code buildDefaultLayout} entirely, and that method is
 * also what creates the EU "Total / Usage" text block. {@code RecipeViewerTextCapabilitiesMixin}
 * calls {@link #addTextCapabilities} to create it again, in the same place and order.
 *
 * <p>The research slot is filled here, the way 1.20's {@code ItemRecipeCapability} did it: one copy
 * of each research entry's data item with that entry's research written onto it, cycling, as a
 * catalyst. The snapshot shows the same items in a separate {@code research_info} row below the
 * recipe; {@code ResearchInfoRowMixin} drops that row for recipes drawn by this layout, so the data
 * item appears once.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID, value = Dist.CLIENT)
public final class AssemblyLineRecipeLayout {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int ITEM_INPUTS = 16;
    private static final int FLUID_INPUTS = 4;

    /** Held once so {@link #drawsLayout} can recognise the builder by identity. */
    private static final Function<GTRecipe, Flow> BUILDER = AssemblyLineRecipeLayout::build;

    private AssemblyLineRecipeLayout() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AssemblyLineRecipeLayout::install);
    }

    private static void install() {
        GTRecipeTypeUILayout layout = GTRecipeTypes.ASSEMBLY_LINE_RECIPES.getUiLayout();
        if (layout == null) {
            LOGGER.warn("[gttweaks] assembly line has no recipe UI layout; leaving its recipe page alone");
            return;
        }
        if (layout.getCustomUIBuilder() != null) {
            LOGGER.info("[gttweaks] assembly line already has a custom recipe page; leaving it alone");
            return;
        }
        ((RecipeTypeLayoutAccessor) layout).gttweaks$setCustomUIBuilder(BUILDER);
        LOGGER.info("[gttweaks] assembly line recipe page set to the 1.20 layout");
    }

    /** True when pages using {@code layout} are drawn by this class. */
    public static boolean drawsLayout(GTRecipeTypeUILayout layout) {
        return layout != null && layout.getCustomUIBuilder() == BUILDER;
    }

    /** True when {@code recipe}'s page is drawn by this layout, which shows research in its own slot. */
    public static boolean drawsResearchSlot(GTRecipe recipe) {
        return drawsLayout(recipe.getType().getUiLayout());
    }

    /**
     * Runs GregTech's recipe-viewer layout builders for every capability this layout does not place
     * itself — EU, and computation on types that use it — exactly as {@code buildDefaultLayout}
     * would. They add named text blocks to {@code widget.textComponents}, which GregTech then fills.
     */
    public static void addTextCapabilities(GTRecipeTypeUILayout layout, GTRecipeViewerWidget widget) {
        addTextCapabilities(layout, widget, layout.getRecipeType().maxInputs, IO.IN);
        addTextCapabilities(layout, widget, layout.getRecipeType().maxOutputs, IO.OUT);
    }

    private static void addTextCapabilities(GTRecipeTypeUILayout layout, GTRecipeViewerWidget widget,
                                            Object2IntMap<RecipeCapability<?>> maxSlots, IO io) {
        for (Object2IntMap.Entry<RecipeCapability<?>> entry : maxSlots.object2IntEntrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            if (cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP || entry.getIntValue() == 0) {
                continue;
            }
            GTRecipeTypeUILayout.CapabilityUIInfo info = layout.capabilityInfo(cap);
            RecipeViewerCapabilityLayoutBuilder builder = info == null ? null : info.recipeViewerLayoutBuilder;
            if (builder != null) {
                builder.createCapabilityUILayout(layout, widget, io);
            }
        }
    }

    private static Flow build(GTRecipe recipe) {
        GTRecipeTypeUILayout layout = recipe.getType().getUiLayout();
        ParentWidget<?> panel = new ParentWidget<>();
        panel.size(154, 80);

        for (int i = 0; i < ITEM_INPUTS; i++) {
            panel.child(itemSlot(layout, IO.IN, i).pos(4 + 18 * (i % 4), 4 + 18 * (i / 4)));
        }
        panel.child(new ProgressWidget()
                .value(DoubleValue.simulateProgress(2000))
                .texture(GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE, ProgressDrawable.Direction.RIGHT)
                .size(53, 69)
                .pos(76, 5));
        for (int i = 0; i < FLUID_INPUTS; i++) {
            panel.child(fluidSlot(layout, i).pos(93, 4 + 18 * i));
        }
        panel.child(itemSlot(layout, IO.OUT, 0).pos(130, 4));
        panel.child(new ProgressWidget()
                .value(DoubleValue.simulateProgress(2000))
                .texture(GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE_ARROW, ProgressDrawable.Direction.UP)
                .size(10, 18)
                .pos(134, 22));
        panel.child(researchSlot(recipe).pos(130, 41));

        return Flow.row().coverChildren().horizontalCenter().child(panel);
    }

    private static RecipeViewerSlotWidget<?> itemSlot(GTRecipeTypeUILayout layout, IO io, int index) {
        RecipeViewerSlotWidget<?> slot = RecipeViewerSlotWidget.create();
        slot.value(ItemStackList.of(ItemStack.EMPTY));
        slot.background(GTGuiTextures.SLOT, layout.capabilityInfo(ItemRecipeCapability.CAP).getOverlay(io, index));
        slot.name(GTRecipeViewerWidget.capabilityWidgetName(ItemRecipeCapability.CAP, io, index));
        return slot;
    }

    private static RecipeViewerSlotWidget<?> fluidSlot(GTRecipeTypeUILayout layout, int index) {
        RecipeViewerSlotWidget<?> slot = RecipeViewerSlotWidget.create();
        slot.value(FluidStackList.of(FluidStack.EMPTY));
        slot.background(GTGuiTextures.FLUID_SLOT,
                layout.capabilityInfo(FluidRecipeCapability.CAP).getOverlay(IO.IN, index));
        slot.name(GTRecipeViewerWidget.capabilityWidgetName(FluidRecipeCapability.CAP, IO.IN, index));
        return slot;
    }

    private static RecipeViewerSlotWidget<?> researchSlot(GTRecipe recipe) {
        RecipeViewerSlotWidget<?> slot = RecipeViewerSlotWidget.create();
        List<ItemStack> dataItems = researchDataItems(recipe);
        if (dataItems.isEmpty()) {
            slot.value(ItemStackList.of(ItemStack.EMPTY));
            slot.recipeSlotRole(RecipeSlotRole.RENDER_ONLY);
        } else {
            slot.value(ItemStackList.of(dataItems));
            slot.recipeSlotRole(RecipeSlotRole.CATALYST);
        }
        // Must come after value(): EmiRecipeViewerSlot.value() resets the background to ModularUI's
        // plain SLOT_ITEM. As a background the orb sits under the item and EMI's hover highlight,
        // which EMI draws during the slot's own draw; an overlay would be painted over both.
        slot.background(GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY);
        return slot;
    }

    private static List<ItemStack> researchDataItems(GTRecipe recipe) {
        List<ItemStack> items = new ArrayList<>();
        if (!ConfigHolder.INSTANCE.machines.enableResearch) {
            return items;
        }
        for (RecipeCondition<?> condition : recipe.conditions) {
            if (!(condition instanceof ResearchCondition research)) continue;
            for (ResearchData.ResearchEntry entry : research.getData()) {
                ItemStack stack = entry.dataItem().copy();
                stack.set(GTDataComponents.RESEARCH_ITEM.get(),
                        new ResearchManager.ResearchItem(entry.researchId(), recipe.getType()));
                items.add(stack);
            }
        }
        return items;
    }
}
