package com.flatts.recompile.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Roach (#78, spec {@code docs/roach_spec.md}): the dump's one native creature, and the mod's first
 * entity.
 *
 * <p>It comes out of a garbage block you are picking through, which is why it does not break the
 * starting biome's design: {@code household_sprawl} has every spawner list empty on purpose (P1.9), and
 * a roach released by disturbing a block is not a spawn - it is a consequence of an action the player
 * chose. The biome stays creature-free and the dump still bites.
 *
 * <p><b>It extends {@link Monster}, deliberately NOT {@code Silverfish}.</b> Inheriting Silverfish would
 * inherit its summoning behaviour - hurt one and it calls more out of every infested block nearby - and
 * that is the single thing this design leaves out. In a dump that cascade would land in the starting
 * biome, where the player has trash tools and no armour. Owning the class is what makes "one roach per
 * disturbance" a property of the entity rather than a fight with vanilla.
 *
 * <p>The model and animation are still vanilla's ({@code SilverfishModel}); only the skin is ours. The
 * entity being bespoke costs a class, not an art budget.
 */
public class RoachEntity extends Monster {

    public RoachEntity(EntityType<? extends RoachEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Small, fast, and barely dangerous.
     *
     * <p>Tuned as a nuisance rather than a threat: it appears when a player is mid-pull with a shovel in
     * hand, so it has to be survivable at that moment or picking through garbage stops being worth it.
     * First-pass; balance is #36.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 6.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    /**
     * Plain melee AI. No {@code SilverfishWakeUpFriendsGoal} equivalent and no block-hiding, which are
     * the two behaviours that make vanilla silverfish an infestation rather than a pest.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
}
