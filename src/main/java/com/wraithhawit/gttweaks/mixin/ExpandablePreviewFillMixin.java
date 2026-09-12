package com.wraithhawit.gttweaks.mixin;

import com.google.common.collect.HashBasedTable;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.ExpandablePatternHelper;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.ints.IntIntPair;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops an expandable multiblock's preview from building every optional part to its maximum.
 *
 * <p>Symptom, on the Cleanroom: eight iron doors stacked in a column, ULV machine hulls where
 * Plastcrete belongs, cleanroom glass in the walls. Every number in that list is literally in
 * {@code CleanroomMachine.getPattern()} — {@code doorPredicate().setMaxGlobalLimited(8)},
 * {@code abilities(INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(3)},
 * {@code abilities(PASSTHROUGH_HATCH).setMaxGlobalLimited(30)}.
 *
 * <p><b>Why the optional parts win.</b> {@code MultiPredicate.combine} flattens an operand into the
 * parent's {@code predicates()} list only when the operand's logic type matches; otherwise it is
 * demoted to {@code children()}:
 *
 * <pre>
 *   if (!multiPredicate.isSingle() &amp;&amp; !multiPredicate.isType(type)) children.add(multiPredicate);
 *   else { predicates.addAll(...); children.addAll(...); }
 * </pre>
 *
 * The cleanroom's face is {@code wallPredicate.and(energy).and(doors).and(passthrough)}.
 * {@code wallPredicate} is {@code states(PLASTCRETE, CLEANROOM_GLASS).or(floorTag)} — an OR node
 * with two predicates, so it is neither single nor of type AND, and it becomes a <b>child</b>. The
 * single-predicate abilities all flatten into {@code predicates()}. Then
 * {@code ExpandablePatternHelper.tryMaxCount} walks {@code predicates()} to exhaustion before it
 * ever recurses into {@code children()}:
 *
 * <pre>
 *   if (maxCount != 0 &amp;&amp; (maxCount == -1 || countPopulatedGlobal(map, p) &lt; maxCount)) {
 *       resultStructure.put(pos, p.getCandidates().get(0));
 *       return true;                       // first predicate with headroom wins, every position
 *   }
 * </pre>
 *
 * So the structural wall block is the <i>last</i> thing considered, and only after doors have hit 8,
 * energy hatches 3 and passthrough hatches 30. {@code getCandidates().get(0)} is why they are ULV
 * hulls and lower-half doors specifically: candidate 0 of an ability list is the lowest tier, and
 * {@code doorPredicate()}'s two candidates are the lower half and {@code HALF=UPPER}.
 *
 * <p><b>The fix.</b> GregTech already has the concept this needs — {@code BasePredicate.previewCount},
 * which {@code Predicates.autoAbilities} sets to 1 on every optional hatch. <b>Nothing in the jar
 * reads it</b>; {@code getPreviewCount} has no callers outside {@code BasePredicate} itself. This
 * mixin makes the preview honour it, and gives the many predicates that never set one (the
 * cleanroom's doors among them) a sane default: show only what the structure actually requires.
 *
 * <p>Injecting at {@code AbstractStructureHelper.getMaxCount} rather than at {@code tryMaxCount} is
 * deliberate on two counts. {@code tryMinCount}/{@code tryMaxCount} are private to
 * {@code ExpandablePatternHelper} while the fields they read — {@code blockPreferences},
 * {@code minMaxPreferences} — are declared on the superclass, and <b>{@code @Shadow} does not walk
 * the hierarchy</b>: shadowing an inherited field fails at APPLY time, which with
 * {@code defaultRequire = 1} is a hard crash. Mixing into the class that declares the fields avoids
 * that entirely, and one narrow return value is a far smaller thing to own than a reimplemented loop.
 *
 * <p>{@code AbstractStructureHelper} is the shared base of both helpers, so the
 * {@code instanceof ExpandablePatternHelper} guard keeps this off {@code BlockPatternHelper} — the
 * path every one of the 15 ported multiblocks uses, and one that shows no such symptom.
 */
@Mixin(value = AbstractStructureHelper.class, remap = false)
public abstract class ExpandablePreviewFillMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean gttweaks$loggedOnce = false;

    @Shadow
    @Final
    protected HashBasedTable<MultiPredicate, BasePredicate, IntIntPair> minMaxPreferences;

    @Inject(method = "getMaxCount", at = @At("RETURN"), cancellable = true)
    private void gttweaks$capPreviewFill(MultiPredicate predicate, BasePredicate basePredicate,
                                         CallbackInfoReturnable<Integer> cir) {
        // Fixed-aisle patterns are not affected by this and must not be disturbed.
        if (!(((Object) this) instanceof ExpandablePatternHelper)) {
            return;
        }

        final int max = cir.getReturnValue();

        // -1 is "unlimited" — the structural wall/floor/filter predicate. That is exactly what we
        // want the preview to fall through to, so never touch it.
        if (max == -1) {
            return;
        }

        // The player picked a count for this predicate in the preview's own dropdown. Their choice
        // outranks ours.
        if (this.minMaxPreferences.contains(predicate, basePredicate)) {
            return;
        }

        final int preview = basePredicate.getPreviewCount();
        final int capped = preview >= 0 ? preview : Math.max(basePredicate.getMinCount(), 0);

        if (capped == max) {
            return;
        }

        if (!gttweaks$loggedOnce) {
            gttweaks$loggedOnce = true;
            LOGGER.info("[gttweaks] capping expandable preview fill: max {} -> {} "
                            + "(previewCount {}, minCount {}) for {}",
                    max, capped, preview, basePredicate.getMinCount(), basePredicate);
        }

        cir.setReturnValue(capped);
    }
}
