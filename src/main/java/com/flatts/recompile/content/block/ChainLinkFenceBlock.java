package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Chain-link fence (#310): the mod's first boundary, and its first climbable block.
 *
 * <p><b>What it says that a wall does not.</b> This world has no edges - regions are a distance from
 * origin and blend by noise, so you cross from the sprawl into the demolition yard with nothing to
 * mark it. Corrugated Metal already comes in a wall form, but a solid wall says <i>someone built a
 * building</i>. Chain-link says <b>someone fenced this off</b>, which is a different sentence and one
 * the mod had never said.
 *
 * <p><b>See-through AND climbable, which vanilla owns no block for.</b> Iron bars are see-through and
 * unclimbable; a ladder is climbable and solid-backed; a fence is neither. Chain-link is climbable in
 * life, so it is here: {@link #isLadder} returns true and you scramble over it. That is the whole
 * mechanical reason the block exists rather than being a Corrugated Metal Wall with a new texture.
 *
 * <p><b>One block with a {@code barbed} boolean, not two blocks.</b> The two behaviours are the same
 * block answering {@code isLadder} differently, the models share everything below the top edge, and
 * one block is one recipe and one loot table. A barbed run is built as an ordinary run with its top
 * course barbed, so a climb carries you up the fence and stops at the wire - which is what barbed wire
 * is for and needs no explaining to anybody who has seen one.
 *
 * <p><b>The barbed variant hurts, and that is a recorded reversal</b> (owner, 2026-08-30). This mod
 * has deliberately avoided contact damage: the leachate ruling is that standing in it makes you ill
 * and <i>deliberately nothing worse</i>, on the reasoning that the real cost of leachate is being
 * water you cannot use. Barbed wire is the first thing here that hurts you for touching it. It is
 * defensible on its own terms - a fence that refuses a climb is a nuisance and one that draws blood is
 * a barrier, which is the difference the variant exists to express - and it means a mob path can now
 * kill something on your fence.
 *
 * <p><b>It hurts you for MOVING against it, not for standing near it</b>, which is the sweet berry
 * bush's rule rather than the cactus's. That distinction is load-bearing here in a way it is not for a
 * cactus: {@code entityInside} fires whenever an entity's box overlaps the block's cell, and a fence
 * panel is two pixels thick in the middle of a full cube, so a player walking peacefully <i>alongside</i>
 * a fence is inside it by that test. Cactus can afford to hurt on contact because its collision box
 * fills almost the whole cell and you have to press into it. Copying the cactus here would have made a
 * barbed fence a wall of damage a block wide.
 */
public class ChainLinkFenceBlock extends IronBarsBlock {

    /** Whether this course carries wire on top. Set on the top block of a run, not the whole run. */
    public static final BooleanProperty BARBED = BooleanProperty.create("barbed");

    public static final MapCodec<ChainLinkFenceBlock> CODEC = simpleCodec(ChainLinkFenceBlock::new);

    public ChainLinkFenceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(BARBED, false));
    }

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BARBED);
    }

    /**
     * Climbable unless it is wired.
     *
     * <p>{@code entity} is documented nullable and is not read, so nothing here needs a guard; the
     * answer is a property of the block rather than of who is asking.
     */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos,
            @Nullable LivingEntity entity) {
        return !state.getValue(BARBED);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!state.getValue(BARBED) || !(entity instanceof LivingEntity)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // MOVING AGAINST IT, not merely inside its cell - see the class note. Vanilla's own threshold
        // and its own client-authoritative check, so a player pressed into the wire by someone else's
        // knockback is judged the same way a berry bush judges it.
        Vec3 movement = entity.isClientAuthoritative()
            ? entity.getKnownMovement()
            : entity.oldPosition().subtract(entity.position());
        if (movement.horizontalDistanceSqr() <= 0.0) {
            return;
        }
        if (Math.abs(movement.x()) >= 0.003 || Math.abs(movement.z()) >= 0.003) {
            entity.hurtServer(serverLevel, level.damageSources().cactus(), 1.0F);
        }
    }
}
