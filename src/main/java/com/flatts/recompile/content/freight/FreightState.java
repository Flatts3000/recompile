package com.flatts.recompile.content.freight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * How far up the freight ladder this WORLD has climbed (#387, spec
 * {@code docs/freight_conversion_spec.md} section 1.1).
 *
 * <p><b>Per-world, and that is a deliberate difference from the scrip balance.</b> {@code Market}
 * keeps a balance per player as a data attachment, and {@code market_spec.md} calls that the strong
 * part of the currency. Tier is the opposite: a shared factory that advanced one player's tier and
 * not their partner's is a bug nobody would call a feature, and the phases are meant to be fed by a
 * base rather than by a person. Satisfactory, which the ladder is modelled on, tracks its phases per
 * save for the same reason.
 *
 * <p><b>It is the first {@code SavedData} in this mod</b>, so the shape is worth stating: 26.1 is
 * codec-based rather than NBT-based, a {@link SavedDataType} pairs an id with a constructor and a
 * codec, and {@code level.getDataStorage().computeIfAbsent(TYPE)} both loads and creates. Nothing
 * overrides a save method; the codec is the save.
 *
 * <p><b>Always read it off the OVERWORLD.</b> Data storage is per-dimension, so a terminal in the
 * Nether reading its own dimension's storage would keep a second, private ladder. {@link #of} takes
 * whatever level it is handed and resolves to the overworld itself, so no caller has to remember.
 *
 * <p><b>Progress is keyed by item and only counts what the current phase wants.</b> The terminal
 * refuses anything else at the slot, so an entry for an unwanted item should never appear; storing
 * it by item id rather than by requirement index means a pack reordering a phase's {@code requires}
 * list does not silently reassign a player's progress to a different line.
 */
public class FreightState extends SavedData {

    public static final Codec<FreightState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("tier", 0).forGetter(state -> state.tier),
        Codec.unboundedMap(Identifier.CODEC, Codec.INT)
            .optionalFieldOf("progress", Map.of())
            .forGetter(FreightState::progressByIdentifier)
    ).apply(instance, FreightState::new));

    public static final SavedDataType<FreightState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("recompile", "freight"), FreightState::new, CODEC);

    /** Completed phases. Tier 0 means nothing has shipped yet and phase one is in progress. */
    private int tier;

    private final Map<Item, Integer> progress = new HashMap<>();

    public FreightState() {
    }

    private FreightState(int tier, Map<Identifier, Integer> progress) {
        this.tier = Math.max(0, tier);
        progress.forEach((id, count) -> {
            // An id that no longer resolves comes back as AIR rather than null, which is how a
            // removed mod shows up here.
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != Items.AIR && count > 0) {
                this.progress.put(item, count);
            }
        });
    }

    /** The world's freight state, always the overworld's, whatever level you are standing in. */
    public static FreightState of(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<Identifier, Integer> progressByIdentifier() {
        Map<Identifier, Integer> out = new HashMap<>();
        progress.forEach((item, count) -> out.put(BuiltInRegistries.ITEM.getKey(item), count));
        return out;
    }

    /** Completed phases. The phase in progress is the one at this index in the sorted ladder. */
    public int tier() {
        return tier;
    }

    /** How much of {@code item} has been delivered toward the current phase. */
    public int delivered(Item item) {
        return progress.getOrDefault(item, 0);
    }

    /** Count {@code amount} more of {@code item} toward the current phase. */
    public void deliver(Item item, int amount) {
        if (amount <= 0) {
            return;
        }
        progress.merge(item, amount, Integer::sum);
        setDirty();
    }

    /**
     * Advance one rung and clear progress.
     *
     * <p>Returns false when the tier has already moved past {@code from}, which is the guard against
     * two deliveries completing the same phase on one tick and advancing it twice. The caller
     * re-reads the tier rather than trusting the one it captured.
     */
    public boolean completePhase(int from) {
        if (tier != from) {
            return false;
        }
        tier = from + 1;
        progress.clear();
        setDirty();
        return true;
    }

    /** For tests and for {@code /reload} recovery when a pack shortens the ladder under a save. */
    public void setTier(int tier) {
        this.tier = Math.max(0, tier);
        progress.clear();
        setDirty();
    }
}
