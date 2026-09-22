package com.wraithhawit.gttweaks.rs.gui;

import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.ae2.gui.AEGuiHelper;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceRendering;

import com.wraithhawit.gttweaks.rs.slot.RSConfigurableSlotList;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.integration.recipeviewer.handlers.GhostIngredientSlot;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.SecondaryPanel;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.value.StringValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The configuration grid for a Refined Storage input bus, input hatch or stocking part.
 *
 * <p>One cell per slot, in pairs: the standing order on top and what the part is actually holding
 * underneath. The player sets the top one and the machine fills the bottom one. This is GregTech's
 * {@code AEConfigWidget} re-expressed over Refined Storage's {@link ResourceAmount}, and it is
 * deliberately the same shape with the same controls, because a player who has used an ME bus
 * should not have to learn a second set.
 *
 * <p>Everything a click does happens on the server. The widget only ever sends a synced action and
 * waits for {@link RSConfigSyncHandler} to send the result back; it never edits a slot it is looking
 * at. That is what stops a desynced client from inventing a configuration, and it is why the handler
 * is the widget's only source of what to draw.
 *
 * <p>Two things GregTech's widget has are missing here, both deliberately. There is no auto-pull
 * mode, because these parts have none. And a stocking part accepts no amount at all — its slots
 * mirror the network, so the scroll wheel and the amount editor are refused, here and again on the
 * server.
 *
 * <p>The amount formatting, parsing and the selection overlay come from GregTech's own
 * {@link AEGuiHelper}, which is where the ME parts get them. Nothing in the methods used touches
 * AE2 — they take longs and coordinates — and sharing them is what keeps a configured 64,000 reading
 * the same on an RS part as on an ME one. {@code parseAmount} also evaluates expressions, so the
 * editor accepts {@code 64*9} exactly as GregTech's does.
 */
public class RSConfigWidget extends Widget<RSConfigWidget>
        implements Interactable, GhostIngredientSlot<ItemStack> {

    /** Set the configured resource of a slot from whatever the player is holding on the cursor. */
    public static final String ACTION_CONFIG_SET = "rs_config_set";
    /** Clear a slot's configuration. */
    public static final String ACTION_CONFIG_CLEAR = "rs_config_clear";
    /** Set a slot's configured amount, keeping its resource. */
    public static final String ACTION_CONFIG_AMOUNT = "rs_config_amount";
    /** Take the stock a slot is holding onto the player's cursor. */
    public static final String ACTION_STOCK_PICKUP = "rs_stock_pickup";
    /** Set a slot's configured resource from a recipe viewer's dragged ingredient. */
    public static final String ACTION_CONFIG_SET_GHOST = "rs_config_set_ghost";

    private static final String SYNC_KEY = "rs_config_display";

    private static final int CELL_SIZE = 18;
    /** Config cell, stock cell, and two pixels before the next row of pairs. */
    private static final int PAIR_HEIGHT = 38;
    private static final int COLUMNS = 8;

    /**
     * One past the largest amount a slot may be configured for.
     *
     * <p>GregTech's ceiling, kept deliberately: a slot's amount is a {@code long} and Refined
     * Storage would carry a larger one, but every handler that hands the contents to a recipe takes
     * an {@code int}, so an amount above this could be configured and never fully drawn.
     */
    private static final long MAX_AMOUNT = 2147483648L;

    private final RSConfigurableSlotList slotList;
    private final int slotCount;
    private final boolean isFluid;

    @Nullable
    private PanelSyncManager syncManager;
    @Nullable
    private RSConfigSyncHandler configSyncHandler;

    private float lastMouseX;
    private float lastMouseY;

    private int editingSlotIndex = -1;
    private String pendingAmount = "0";
    @Nullable
    private SecondaryPanel amountEditorPanel;
    @Nullable
    private TextFieldWidget amountField;

    public RSConfigWidget(RSConfigurableSlotList slotList, int slotCount, boolean isFluid) {
        this.slotList = slotList;
        this.slotCount = slotCount;
        this.isFluid = isFluid;
    }

    /** The width this widget needs for {@code slotCount} slots, for the machine to size it with. */
    public static int widthFor(int slotCount) {
        return Math.min(slotCount, COLUMNS) * CELL_SIZE;
    }

    /** The height this widget needs for {@code slotCount} slots. */
    public static int heightFor(int slotCount) {
        return ((slotCount + COLUMNS - 1) / COLUMNS) * PAIR_HEIGHT;
    }

    public RSConfigWidget syncManager(PanelSyncManager syncManager) {
        this.syncManager = syncManager;
        this.configSyncHandler = new RSConfigSyncHandler(slotList, slotCount);
        syncManager.syncValue(SYNC_KEY, configSyncHandler);
        return this;
    }

    @Override
    public void onInit() {
        super.onInit();
        getContext().getRecipeViewerSettings().addGhostIngredientSlot(this);
        this.editingSlotIndex = -1;
        this.pendingAmount = "0";
        this.amountEditorPanel = new SecondaryPanel(getPanel(), this::buildAmountEditor, true);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        GuiGraphics graphics = context.getGraphics();
        this.lastMouseX = context.getMouseX();
        this.lastMouseY = context.getMouseY();

        for (int i = 0; i < slotCount; i++) {
            int x = slotX(i);
            int y = slotY(i);
            drawSlotBackground(context, x, y);

            ResourceAmount config = clientConfig(i);
            if (config != null) {
                drawResource(graphics, config.resource(), x + 1, y + 1);
                AEGuiHelper.drawAmountOverlay(graphics, config.amount(), x + 1, y + 1);
            }
            ResourceAmount stock = clientStock(i);
            if (stock != null) {
                drawResource(graphics, stock.resource(), x + 1, y + CELL_SIZE + 1);
                AEGuiHelper.drawAmountOverlay(graphics, stock.amount(), x + 1, y + CELL_SIZE + 1);
            }
        }

        int hovered = getSlotAtLocal(context.getMouseX(), context.getMouseY());
        if (hovered >= 0) {
            int y = slotY(hovered);
            boolean onConfigRow = context.getMouseY() < y + CELL_SIZE;
            AEGuiHelper.drawSelectionOverlay(graphics, slotX(hovered) + 1,
                    (onConfigRow ? y : y + CELL_SIZE) + 1, 16, 16);
        }
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        int hovered = getSlotAtLocal(context.getMouseX(), context.getMouseY());
        if (hovered < 0) {
            return;
        }
        boolean onConfigRow = context.getMouseY() < slotY(hovered) + CELL_SIZE;
        ResourceAmount amount = onConfigRow ? clientConfig(hovered) : clientStock(hovered);
        if (amount == null) {
            return;
        }
        ResourceKey resource = amount.resource();
        List<Component> tooltip = new ArrayList<>(rendering(resource).getTooltip(resource));
        tooltip.add(Component.literal(AEGuiHelper.formatAmountFull(amount.amount())));
        context.getGraphics().renderComponentTooltip(Minecraft.getInstance().font, tooltip,
                context.getAbsMouseX(), context.getAbsMouseY());
    }

    /**
     * Draws the pair of cells for one slot: a live config cell on top, a read-only stock cell below.
     *
     * <p>GregTech's widget draws a darker, arrowed config cell when a bus is auto-pulling, because
     * the configuration is then the network's and not the player's. Nothing here auto-pulls, so the
     * config cell is always the live one.
     */
    private void drawSlotBackground(ModularGuiContext context, int x, int y) {
        (isFluid ? GTGuiTextures.FLUID_SLOT : GTGuiTextures.SLOT)
                .draw(context, x, y, CELL_SIZE, CELL_SIZE);
        GTGuiTextures.CONFIG_ARROW_DARK.draw(context, x, y, CELL_SIZE, CELL_SIZE);
        GTGuiTextures.SLOT_DARK.draw(context, x, y + CELL_SIZE, CELL_SIZE, CELL_SIZE);
    }

    /**
     * Draws one resource, whatever kind it is.
     *
     * <p>Refined Storage keeps a {@link ResourceRendering} per resource class and hands it out on
     * the client, so an item and a fluid are drawn through the same call, and a resource type
     * another mod registers would be too. This is the one place the widget is simpler than
     * GregTech's, which branches on {@code isFluid} and unwraps an {@code AEItemKey} by hand.
     */
    private void drawResource(GuiGraphics graphics, ResourceKey resource, int x, int y) {
        rendering(resource).render(resource, graphics, x, y);
    }

    private static ResourceRendering rendering(ResourceKey resource) {
        return RefinedStorageClientApi.INSTANCE.getResourceRendering(resource.getClass());
    }

    // ------------------------------------------------------------------ input

    @Override
    public Result onMousePressed(int mouseButton) {
        int index = getSlotAtLocal(getContext().getMouseX(), getContext().getMouseY());
        if (index < 0 || syncManager == null) {
            return Result.IGNORE;
        }
        boolean onConfigRow = getContext().getMouseY() < slotY(index) + CELL_SIZE;

        if (onConfigRow) {
            if (mouseButton == 1) {
                syncManager.callSyncedAction(ACTION_CONFIG_CLEAR, buffer -> buffer.writeVarInt(index));
                return Result.SUCCESS;
            }
            ItemStack carried = Minecraft.getInstance().player.containerMenu.getCarried();
            if (carried.isEmpty()) {
                // An empty hand on a configured slot means "change how much", not "set to nothing";
                // right-click is how a slot is cleared.
                if (clientConfig(index) != null && !slotList.isStocking()) {
                    openAmountEditor(index);
                    return Result.SUCCESS;
                }
                return Result.IGNORE;
            }
            syncManager.callSyncedAction(ACTION_CONFIG_SET, buffer -> buffer.writeVarInt(index));
            return Result.SUCCESS;
        }

        // The stock row. A mirror holds nothing that could be picked up, and a fluid cannot go onto
        // the cursor at all.
        if (slotList.isStocking() || isFluid) {
            return Result.IGNORE;
        }
        syncManager.callSyncedAction(ACTION_STOCK_PICKUP, buffer -> buffer.writeVarInt(index));
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseScrolled(double scrollDirection, double amountScrolled) {
        if (slotList.isStocking() || syncManager == null) {
            return false;
        }
        // Unlike onMousePressed, a scroll arrives in the screen's coordinates rather than this
        // widget's, so it has to be brought back into local space by hand. GregTech's widget makes
        // the same distinction between the two handlers.
        double localX = getContext().getMouseX() - getArea().x;
        double localY = getContext().getMouseY() - getArea().y;
        int index = getSlotAtLocal(localX, localY);
        if (index < 0 || localY >= slotY(index) + CELL_SIZE) {
            return false;
        }
        ResourceAmount config = clientConfig(index);
        if (config == null || scrollDirection == 0.0D) {
            return false;
        }
        long current = config.amount();
        long next;
        if (Interactable.hasControlDown()) {
            next = scrollDirection > 0.0D ? current * 2L : current / 2L;
        } else {
            next = scrollDirection > 0.0D ? current + 1L : current - 1L;
        }
        if (next <= 0L || next >= MAX_AMOUNT) {
            return false;
        }
        long target = next;
        syncManager.callSyncedAction(ACTION_CONFIG_AMOUNT, buffer -> {
            buffer.writeVarInt(index);
            buffer.writeVarLong(target);
        });
        return true;
    }

    // ------------------------------------------------------------------ amount editor

    private ModularPanel<?> buildAmountEditor(ModularPanel<?> parent, Player player) {
        this.amountField = new TextFieldWidget() {

            @Override
            public Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
                // 257 is enter and 335 the numpad one. Confirming from the keyboard is the whole
                // reason this is a subclass rather than a plain field.
                if (keyCode == 257 || keyCode == 335) {
                    confirmAmountEdit();
                    return Result.SUCCESS;
                }
                return super.onKeyPressed(keyCode, scanCode, modifiers);
            }
        };
        this.amountField.value(new StringValue.Dynamic(() -> pendingAmount, value -> pendingAmount = value));

        return new ModularPanel<>("rs_amount_editor")
                .size(120, 50)
                .posRel(0.5f, 0.35f)
                .background(GTGuiTextures.BACKGROUND)
                .child(ButtonWidget.panelCloseButton())
                .child(Text.dynamic(() -> Component.translatable("gttweaks.rs.config.amount"))
                        .asWidget().pos(6, 6))
                .child(Flow.row()
                        .left(6).right(6).bottom(6).height(16)
                        .child(amountField.expanded().heightRel(1.0f))
                        .child(new ButtonWidget<>()
                                .size(16, 16)
                                .overlay(Text.str("="))
                                .onMousePressed((context, button) -> {
                                    confirmAmountEdit();
                                    return true;
                                })));
    }

    private void openAmountEditor(int index) {
        ResourceAmount config = clientConfig(index);
        if (config == null || amountEditorPanel == null) {
            return;
        }
        this.editingSlotIndex = index;
        this.pendingAmount = String.valueOf(config.amount());
        if (amountEditorPanel.isPanelOpen()) {
            if (amountField != null) {
                amountField.setText(pendingAmount);
            }
            return;
        }
        // Without this the panel comes back holding the previous slot's text.
        amountEditorPanel.deleteCachedPanel();
        amountEditorPanel.openPanel();
    }

    private void confirmAmountEdit() {
        if (editingSlotIndex < 0 || syncManager == null) {
            return;
        }
        if (amountField != null) {
            this.pendingAmount = String.valueOf(AEGuiHelper.parseAmount(amountField.getText()));
            amountField.setText(pendingAmount);
        }
        long amount;
        try {
            amount = Long.parseLong(pendingAmount);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (amount <= 0L || amount >= MAX_AMOUNT) {
            return;
        }
        int index = editingSlotIndex;
        syncManager.callSyncedAction(ACTION_CONFIG_AMOUNT, buffer -> {
            buffer.writeVarInt(index);
            buffer.writeVarLong(amount);
        });
        this.editingSlotIndex = -1;
        if (amountEditorPanel != null) {
            amountEditorPanel.closePanel();
        }
    }

    // ------------------------------------------------------------------ recipe viewer drags

    @Override
    public Class<ItemStack> ingredientClass() {
        return ItemStack.class;
    }

    @Override
    @Nullable
    public ItemStack castGhostIngredientIfValid(Object ingredient) {
        if (!areAncestorsEnabled()) {
            return null;
        }
        return ingredient instanceof ItemStack stack ? stack : null;
    }

    @Override
    public void setGhostIngredient(ItemStack ingredient) {
        if (syncManager == null) {
            return;
        }
        int index = findTargetSlot();
        if (index < 0) {
            return;
        }
        if (isFluid) {
            // A fluid hatch takes the fluid a dragged bucket or tank holds, not the item itself.
            FluidUtil.getFluidContained(ingredient).ifPresent(fluid ->
                    syncManager.callSyncedAction(ACTION_CONFIG_SET_GHOST, buffer -> {
                        buffer.writeVarInt(index);
                        FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, fluid);
                    }));
            return;
        }
        syncManager.callSyncedAction(ACTION_CONFIG_SET_GHOST, buffer -> {
            buffer.writeVarInt(index);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, ingredient);
        });
    }

    /**
     * Where a dragged ingredient lands: the slot under the cursor, or else the first empty one.
     */
    private int findTargetSlot() {
        int hovered = getSlotAtLocal(lastMouseX, lastMouseY);
        if (hovered >= 0) {
            return hovered;
        }
        for (int i = 0; i < slotCount; i++) {
            if (clientConfig(i) == null) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ geometry and state

    private int slotX(int index) {
        return (index % COLUMNS) * CELL_SIZE;
    }

    private int slotY(int index) {
        return (index / COLUMNS) * PAIR_HEIGHT;
    }

    private int getSlotAtLocal(double x, double y) {
        for (int i = 0; i < slotCount; i++) {
            int cellX = slotX(i);
            int cellY = slotY(i);
            if (x >= cellX && x < cellX + CELL_SIZE && y >= cellY && y < cellY + CELL_SIZE * 2) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private ResourceAmount clientConfig(int index) {
        return configSyncHandler == null ? null : configSyncHandler.getClientConfig(index);
    }

    @Nullable
    private ResourceAmount clientStock(int index) {
        return configSyncHandler == null ? null : configSyncHandler.getClientStock(index);
    }
}
