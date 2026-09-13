package com.wraithhawit.gttweaks;

import brachy.modularui.drawable.schema.Camera;
import brachy.modularui.utils.BlockPosUtil;
import net.minecraft.core.BlockPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Replays ModularUI 3.3.1's preview transform with the staged Camera and BlockPosUtil classes.
 * Matrices are composed as the bytecode does: setupCamera builds the model-view, renderBlocks sets
 * ChunkOffset = -camera.pos(), rendertype_solid.vsh draws ProjMat * ModelViewMat * (Position +
 * ChunkOffset), and rebuildProjection uses ProjMat * ModelViewMat * translate(-camera.pos()).
 */
class SchemaViewTest {
    private static final float EPS = 2e-4F;
    // Asymmetric bounds, no axis touching the origin: 12 x 7 x 4 blocks.
    private static final BlockPos MIN = new BlockPos(-7, 3, 12);
    private static final BlockPos MAX = new BlockPos(4, 9, 15);
    private static final Vector3f CENTRE = new Vector3f(
            SchemaView.geometricCentre(MIN.getX(), MAX.getX()),
            SchemaView.geometricCentre(MIN.getY(), MAX.getY()),
            SchemaView.geometricCentre(MIN.getZ(), MAX.getZ()));
    private static final float[] PITCHES = {-1.2F, -0.3F, 0.3F, (float) (Math.PI / 4), 1.5F};

    private record View(Matrix4f projection, Matrix4f modelView, Vector3f eye) {
        Vector3f ndc(Vector3f vertex) {
            return SchemaView.projectBlockVertex(projection, modelView, new Vector3f(eye).negate(), vertex, new Vector3f());
        }

        Vector3f viewSpace(Vector3f vertex) {
            return modelView.transformPosition(new Vector3f(vertex).sub(eye));
        }

        Vector3f picking(Vector3f vertex) {
            Matrix4f m = new Matrix4f(modelView).translate(-eye.x, -eye.y, -eye.z);
            return new Matrix4f(projection).mul(m).transformProject(vertex, new Vector3f());
        }
    }

    /** SchemaWidget.draw followed by BaseSchemaRenderer.setupCamera for a 200x180 widget. */
    private static View view(boolean fixed, boolean isometric, Vector3f lookAt, float distance, float yaw, float pitch) {
        Camera camera = new Camera().setLookAtAndAngle(lookAt.x, lookAt.y, lookAt.z, distance, yaw, pitch);
        float near = 0.05F, fovY = (float) (Math.PI / 3), aspect = 200F / 180F;
        float top = -near * (float) Math.tan(fovY * 0.5), bottom = -top;
        Matrix4f projection = isometric
                ? new Matrix4f().setOrtho(aspect * bottom, aspect * top, bottom, top, near, 10000F)
                : new Matrix4f().setPerspective(fovY, aspect, near, 10000F);
        Matrix4f modelView = new Matrix4f();
        if (isometric) {
            modelView.translate(0F, 0F, -2000F);
        }
        if (fixed) {
            SchemaView.lookAtRotationOnly(modelView, camera.pos(), camera.lookAt());
        } else {
            modelView.lookAt(camera.pos(), camera.lookAt(), new Vector3f(0F, 1F, 0F)); // MatrixUtils.lookAt
        }
        return new View(projection, modelView, new Vector3f(camera.pos()));
    }

    private static float yaw(int step) {
        return (float) (step * Math.PI * 2 / 24);
    }

    private static void assertNdcCentre(Vector3f ndc, String what) {
        assertEquals(0F, ndc.x, EPS, what + " x");
        assertEquals(0F, ndc.y, EPS, what + " y");
    }

    @Test void stockFocusIsHalfABlockShortAndStockOrbitsTwiceTheTarget() {
        Vector3f focus = BlockPosUtil.getCenterF(MIN, MAX); // MutableSchema.getFocus()
        assertEquals(new Vector3f(-1.5F, 6F, 13.5F), focus);
        assertEquals(new Vector3f(-1F, 6.5F, 14F), CENTRE);

        float distance = SchemaView.fitDistance(12) / SchemaView.FRAMING;
        Vector3f twice = new Vector3f(CENTRE).mul(2F);
        float maxOffCentre = 0F;
        for (int step = 0; step < 24; step++) {
            View stock = view(false, false, CENTRE, distance, yaw(step), (float) (Math.PI / 4));
            assertNdcCentre(stock.ndc(twice), "stock pivot 2*lookAt, yaw step " + step);
            Vector3f centre = stock.ndc(CENTRE);
            maxOffCentre = Math.max(maxOffCentre, Math.max(Math.abs(centre.x), Math.abs(centre.y)));
            // Picking and highlight make the same double subtraction, so they agree with the model.
            assertEquals(stock.ndc(CENTRE).x, stock.picking(CENTRE).x, EPS);
        }
        assertTrue(maxOffCentre > 0.3F, "stock swings the geometric centre across the view: " + maxOffCentre);
    }

    @Test void fixedRotatesAboutTheGeometricCentreAcrossYawPitchZoomPanAndProjection() {
        float fit = SchemaView.fitDistance(12);
        Vector3f[] pans = {new Vector3f(), new Vector3f(2.5F, -1.25F, 0.75F)};
        for (boolean isometric : new boolean[] {false, true}) {
            for (Vector3f pan : pans) {
                Vector3f lookAt = new Vector3f(CENTRE).add(pan);
                for (float distance : new float[] {fit * 0.4F, fit, fit * 3F}) {
                    for (float pitch : PITCHES) {
                        for (int step = 0; step < 24; step++) {
                            View fixed = view(true, isometric, lookAt, distance, yaw(step), pitch);
                            String what = "iso=" + isometric + " pan=" + pan + " d=" + distance + " pitch=" + pitch + " yaw=" + step;
                            assertNdcCentre(fixed.ndc(lookAt), what);
                            assertNdcCentre(fixed.picking(lookAt), what + " picking");
                            if (!isometric) {
                                assertEquals(-distance, fixed.viewSpace(lookAt).z, distance * 1e-4F, what + " depth");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test void fixedKeepsOrientationAndStockOnScreenSize() {
        float stockDistance = SchemaView.fitDistance(12) / SchemaView.FRAMING;
        int[][] corners = new int[8][];
        for (int i = 0; i < 8; i++) {
            corners[i] = new int[] {(i & 1) == 0 ? MIN.getX() : MAX.getX() + 1,
                                    (i & 2) == 0 ? MIN.getY() : MAX.getY() + 1,
                                    (i & 4) == 0 ? MIN.getZ() : MAX.getZ() + 1};
        }
        for (float pitch : PITCHES) {
            for (int step = 0; step < 24; step++) {
                View stock = view(false, false, CENTRE, stockDistance, yaw(step), pitch);
                View fixed = view(true, false, CENTRE, SchemaView.fitDistance(12), yaw(step), pitch);
                // Same rotation: normals, lighting and orientation are untouched.
                assertTrue(stock.modelView.get3x3(new Matrix3f()).equals(fixed.modelView.get3x3(new Matrix3f()), 1e-6F));
                // Fixed geometry is the stock geometry moved by one constant vector: no scale change.
                Vector3f shift = fixed.viewSpace(CENTRE).sub(stock.viewSpace(CENTRE));
                for (int[] c : corners) {
                    Vector3f corner = new Vector3f(c[0], c[1], c[2]);
                    Vector3f delta = fixed.viewSpace(corner).sub(stock.viewSpace(corner));
                    assertTrue(delta.equals(shift, 1e-3F), "corner " + corner + " yaw " + step + ": " + delta + " vs " + shift);
                    assertEquals(fixed.ndc(corner).x, fixed.picking(corner).x, EPS);
                    assertEquals(fixed.ndc(corner).y, fixed.picking(corner).y, EPS);
                }
            }
        }
    }
}
