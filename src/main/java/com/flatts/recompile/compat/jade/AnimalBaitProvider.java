package com.flatts.recompile.compat.jade;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.AnimalBaitBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (client): every gate on an animal bait is an invisible failure mode ("I placed it and nothing
 * happened"), so name the exact blocker on hover - no grass, a player too near (settling is held), too
 * close to another bait, or the settle countdown - plus what the surrounding land is drawing (the
 * Expecting line, #436). All of it is read client-side from the blockstate, the world, the synced diet
 * tags and the synced {@code bait_weight} data map, so no server data provider is needed.
 */
public enum AnimalBaitProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "animal_bait");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof AnimalBaitBlock)) {
            return;
        }
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();

        // List EVERY reason the bait is not working, not just the first - each blocker is a separate
        // fix. Blockers are coloured as problems (red = will never work here; gold = held, will resume);
        // only the settling / ready state, shown when nothing blocks it, reads green.
        boolean blocked = false;
        if (!RCConfig.ANIMAL_BAIT_ENABLED.get()) {
            tooltip.add(Component.translatable("jade.recompile.bait_disabled").withStyle(ChatFormatting.RED));
            blocked = true;
        }
        if (!AnimalBaitBlock.onGrass(level, pos)) {
            tooltip.add(Component.translatable("jade.recompile.bait_no_grass").withStyle(ChatFormatting.RED));
            blocked = true;
        }
        if (AnimalBaitBlock.playerNear(level, pos)) {
            tooltip.add(Component.translatable("jade.recompile.bait_waiting").withStyle(ChatFormatting.GOLD));
            blocked = true;
        }
        if (AnimalBaitBlock.baitNear(level, pos)) {
            tooltip.add(Component.translatable("jade.recompile.bait_crowded").withStyle(ChatFormatting.GOLD));
            blocked = true;
        }

        if (!blocked) {
            int settle = AnimalBaitBlock.settle(state);
            if (settle >= AnimalBaitBlock.SETTLE_MAX) {
                tooltip.add(Component.translatable("jade.recompile.bait_ready").withStyle(ChatFormatting.GREEN));
            } else {
                int seconds = (AnimalBaitBlock.SETTLE_MAX - settle)
                    * RCConfig.ANIMAL_BAIT_SETTLE_INTERVAL_TICKS.get() / 20;
                tooltip.add(Component.translatable("jade.recompile.bait_settling", seconds)
                    .withStyle(ChatFormatting.GREEN));
            }
        }

        // What the land is drawing, even while something else holds the bait: the terrain decides the
        // animal, and that is the one thing about a bait a player cannot see until it fires (#436).
        // Skipped off grass, where the bait never fires at all and a shortlist would be a promise.
        if (AnimalBaitBlock.onGrass(level, pos)) {
            Component expected = expecting(level, pos, state.getValue(AnimalBaitBlock.DIET));
            if (expected != null) {
                tooltip.add(Component.translatable("jade.recompile.bait_expecting", expected)
                    .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** How many names the Expecting line carries: enough to show the lean, few enough to read at a glance. */
    private static final int SHORTLIST = 3;

    /**
     * The likeliest few animals, heaviest first, from the same scoring the draw uses. Null when the diet
     * tag is empty. Ties keep tag order, because the sort is stable.
     */
    static @org.jspecify.annotations.Nullable Component expecting(Level level, BlockPos pos,
            AnimalBaitBlock.Diet diet) {
        java.util.List<AnimalBaitBlock.Candidate> ranked =
            new java.util.ArrayList<>(AnimalBaitBlock.candidates(level, pos, diet));
        if (ranked.isEmpty()) {
            return null;
        }
        ranked.sort(java.util.Comparator.comparingInt(AnimalBaitBlock.Candidate::weight).reversed());
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        for (int i = 0; i < Math.min(SHORTLIST, ranked.size()); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(ranked.get(i).type().getDescription());
        }
        return line;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
