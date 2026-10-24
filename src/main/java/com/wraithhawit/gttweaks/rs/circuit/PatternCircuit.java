package com.wraithhawit.gttweaks.rs.circuit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.mojang.serialization.Codec;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.GTTweaks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GregTech programmed circuits on Refined Storage processing patterns, the RS counterpart of the
 * Programmed Circuit Card for AE2.
 *
 * <p><b>Encoding</b> (any RS pattern grid): a programmed circuit among a processing pattern's inputs
 * is taken out of the inputs and stored on the pattern as {@link #CIRCUIT}. RS then never plans,
 * extracts or pushes a circuit, and needs none in storage.
 *
 * <p><b>Pushing</b>: when a pattern provider (RS's autocrafter or Cable Tiers' tiered ones, which
 * share RS's provider node and sink factory) pushes such a pattern, every GregTech machine it pushed
 * into gets its programmed circuit set to the stored number. Only after the whole push was accepted,
 * so a rejected push never leaves a machine on another pattern's circuit.
 *
 * <p>How the push finds the circuit: RS's pattern objects carry only an id, so the id-to-circuit
 * map is filled whenever RS resolves a pattern item ({@link #remember}). The provider node's
 * pattern-aware {@code accept} opens a {@link Push}; the {@link #SINK_FACTORY sink} RS creates for
 * each target records the GregTech machines in it; the node's return applies the circuit.
 */
public final class PatternCircuit {
    private static final Logger LOGGER = LoggerFactory.getLogger("gttweaks/rs-circuit");

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, GTTweaks.MOD_ID);

    /** The programmed circuit number of an encoded processing pattern. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CIRCUIT =
            REGISTRY.register("rs_pattern_circuit", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, IntCircuitBehaviour.CIRCUIT_MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Pattern id to circuit, for every resolved pattern that has one. */
    private static final Map<UUID, Integer> BY_PATTERN = new ConcurrentHashMap<>();

    private static final ThreadLocal<Push> CURRENT = new ThreadLocal<>();

    private PatternCircuit() {
    }

    // ------------------------------------------------------------------ items

    public static boolean isCircuit(ItemResource resource) {
        return IntCircuitBehaviour.isIntegratedCircuit(resource.toItemStack());
    }

    public static int configuration(ItemResource resource) {
        return IntCircuitBehaviour.getCircuitConfiguration(resource.toItemStack());
    }

    public static ItemStack circuitStack(int number) {
        return IntCircuitBehaviour.stack(number);
    }

    @Nullable
    public static Integer get(ItemStack pattern) {
        return pattern.get(CIRCUIT.get());
    }

    /** Called whenever RS resolves a pattern item; keeps the id-to-circuit map current. */
    public static void remember(Pattern pattern, ItemStack stack) {
        Integer circuit = get(stack);
        if (circuit != null) {
            BY_PATTERN.put(pattern.id(), circuit);
        } else {
            BY_PATTERN.remove(pattern.id());
        }
    }

    public static void onTooltip(ItemTooltipEvent event) {
        Integer circuit = get(event.getItemStack());
        if (circuit != null) {
            event.getToolTip().add(Component.translatable("gttweaks.rs.pattern_circuit", circuit)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ pushing

    /** One provider push of one pattern. */
    private static final class Push {
        final int circuit;
        final List<Target> targets = new ArrayList<>(1);

        Push(int circuit) {
            this.circuit = circuit;
        }
    }

    private record Target(ServerLevel level, BlockPos pos) {
    }

    /** Provider node's {@code accept(pattern, ...)} starts. */
    public static void begin(Pattern pattern, Action action) {
        Integer circuit = action == Action.EXECUTE ? BY_PATTERN.get(pattern.id()) : null;
        CURRENT.set(circuit != null ? new Push(circuit) : null);
    }

    /** Provider node's {@code accept(pattern, ...)} returned {@code result}. */
    public static void end(ExternalPatternSink.Result result) {
        Push push = CURRENT.get();
        CURRENT.remove();
        if (push == null || result != ExternalPatternSink.Result.ACCEPTED) {
            return;
        }
        for (Target target : push.targets) {
            MetaMachine machine = MetaMachine.getMachine(target.level(), target.pos());
            if (machine == null) {
                continue;
            }
            machine.getTraitOptional(ProgrammableCircuitSlotTrait.class).ifPresent(slot -> {
                if (slot.getCurrentCircuit() != push.circuit) {
                    slot.setCurrentCircuit(push.circuit);
                    LOGGER.debug("set circuit {} on {} at {}", push.circuit,
                            machine.getDefinition().getId(), target.pos().toShortString());
                }
            });
        }
    }

    /**
     * Added to RS's sink factory: one sink per provider target. It never inserts anything (always
     * SKIPPED, so RS's item and fluid sinks decide the result); it only notes GregTech machines with
     * a circuit slot while a circuit push is open.
     */
    public static final com.refinedmods.refinedstorage.common.api.autocrafting.PatternProviderExternalPatternSinkFactory SINK_FACTORY =
            (level, pos, direction) -> new PlatformPatternProviderExternalPatternSink() {
                @Override
                public ExternalPatternSink.Result accept(Collection<ResourceAmount> resources, Action action) {
                    Push push = CURRENT.get();
                    if (push != null && action == Action.EXECUTE) {
                        MetaMachine machine = MetaMachine.getMachine(level, pos);
                        if (machine != null && machine.getTraitOptional(ProgrammableCircuitSlotTrait.class).isPresent()) {
                            push.targets.add(new Target(level, pos.immutable()));
                        }
                    }
                    return ExternalPatternSink.Result.SKIPPED;
                }

                @Override
                public boolean isEmpty() {
                    return true;
                }
            };

    public static void registerSinkFactory() {
        RefinedStorageApi.INSTANCE.addPatternProviderExternalPatternSinkFactory(SINK_FACTORY);
        LOGGER.info("[gttweaks] RS pattern circuits: sink factory registered");
    }
}
