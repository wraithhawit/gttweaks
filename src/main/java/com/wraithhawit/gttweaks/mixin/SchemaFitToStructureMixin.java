package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.drawable.schema.BaseSchemaRenderer;
import brachy.modularui.drawable.schema.ISchema;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widgets.SchemaWidget;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Pulls the preview camera back far enough to see the whole multiblock.
 *
 * <p>{@code SchemaWidget}'s constructor hard-codes {@code scale = 10.0f}, and that value is the
 * camera <i>distance</i>: {@code draw} passes it as the {@code dist} argument of
 * {@code Camera.setLookAtAndAngle(focus, dist, yaw, pitch)}. GregTech 8 never calls
 * {@code SchemaWidget.scale(...)}, so every multiblock is viewed from ten blocks away — fine for a
 * 3x4x3 machine, and hopeless for the Micro Universe Orb at roughly fifty blocks across, where the
 * camera sits <i>inside</i> the structure. That is why the big previews render enormous and clipped,
 * and why rotating them sweeps geometry across the view like an off-centre pivot: the orbit centre
 * is correct, it is just far outside the visible frustum.
 *
 * <p>GregTech 7 did not have this problem because LDLib's {@code SceneWidget.setRenderedCore} walks
 * the rendered positions for a bounding box and derives both the orthographic size and the camera
 * distance from its {@code range}. This restores that step for the ModularUI stack.
 *
 * <p>{@code max(10, 2.5 * largestDimension)} is chosen so that a small machine is framed exactly as
 * it is today — a 3x4x3 has a largest dimension of 4, giving 10, the current constant — while a
 * fifty-block structure gets the distance it needs. The fit is applied <b>once</b>, so scrolling to
 * zoom still works afterwards, and it is skipped entirely while the schema is empty, since the
 * blocks are not always present on the first frame.
 */
@Mixin(SchemaWidget.class)
public abstract class SchemaFitToStructureMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 45 degrees in radians; MUI's yaw and pitch are both radians. */
    private static final float QUARTER_TURN = (float) (Math.PI / 4.0);
    private static boolean gttweaks$reported;

    @Shadow
    public abstract BaseSchemaRenderer getSchemaRenderer();

    @Unique
    private boolean gttweaks$fitted;

    @Inject(method = "draw", at = @At("HEAD"))
    private void gttweaks$fitCameraToStructure(ModularGuiContext context, WidgetThemeEntry<?> theme,
                                               CallbackInfo ci) {
        if (gttweaks$fitted) {
            return;
        }
        final BaseSchemaRenderer renderer = getSchemaRenderer();
        if (renderer == null || renderer.schema() == null) {
            return;
        }

        final ISchema schema = renderer.schema();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int blocks = 0;
        for (Map.Entry<BlockPos, BlockState> entry : schema) {
            final BlockPos pos = entry.getKey();
            blocks++;
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }
        if (blocks == 0) {
            return; // Not built yet; try again next frame rather than fitting to nothing.
        }

        final int largest = Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));
        // Sub-linear on purpose. A distance proportional to size renders every structure at the
        // same apparent size, which is why the Coke Oven looked right while the Micro Universe Orb
        // - thirteen times larger - looked no bigger. Apparent size goes as L/d, so with
        // d = L^0.875 it goes as L^0.125: a 53-block structure gets about 1.4x the screen of a
        // 4-block one, while the small machines keep the framing that already looked correct.
        // The floor keeps a one-block schema from putting the camera inside itself.
        final float distance = (float) Math.max(3.3D, Math.pow(largest, 0.875D));

        // The pivot half. The camera aims at getFocus() PLUS this widget's offset, so where the
        // focus is wrong the offset is the public way to correct it. Centre is (min + max) / 2 to
        // match BlockPosUtil.getCenterF, which returned (1.0, 1.5, 1.0) for a box of 0,0,0..2,3,2.
        final Vector3fc focus = schema.getFocus();
        final float centreX = (minX + maxX) / 2.0F;
        final float centreY = (minY + maxY) / 2.0F;
        final float centreZ = (minZ + maxZ) / 2.0F;
        final float offsetX = centreX - focus.x();
        final float offsetY = centreY - focus.y();
        final float offsetZ = centreZ - focus.z();

        gttweaks$fitted = true;
        ((SchemaWidget) (Object) this).scale(distance);
        // Face the structure from a corner, the way ATM9 did. ModularUI defaults yaw to 0,
        // which is dead-on to one face and reads flat; LDLib's SceneWidget defaulted to a
        // three-quarter view. Its elevation already matches - MUI's default pitch is 45 deg
        // and LDLib's -135 deg gives the same tan() - so yaw is the only thing to change.
        ((SchemaWidget) (Object) this).yaw(QUARTER_TURN);
        ((SchemaWidget) (Object) this).offset(offsetX, offsetY, offsetZ);

        if (!gttweaks$reported) {
            gttweaks$reported = true;
            LOGGER.info("[gttweaks] fitted preview: {} blocks, box {},{},{}..{},{},{}, largest {}, "
                    + "distance {} | focus ({},{},{}) vs centre ({},{},{}) -> offset ({},{},{})",
                    blocks, minX, minY, minZ, maxX, maxY, maxZ, largest, distance,
                    focus.x(), focus.y(), focus.z(), centreX, centreY, centreZ,
                    offsetX, offsetY, offsetZ);
        }
    }
}
