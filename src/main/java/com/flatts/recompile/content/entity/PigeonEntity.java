package com.flatts.recompile.content.entity;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The Pigeon: the dump's birds (#133).
 *
 * <p><b>It is ambiance, and the whole design follows from that.</b> It is not food, not a resource and
 * not a mechanic - it exists so the landfill has something alive on it. It drops nothing, breeds with
 * nothing, and does nothing to a player who ignores it, which is the point rather than an omission.
 *
 * <p><b>Why a bespoke entity rather than a retextured parrot.</b> Overriding
 * {@code assets/minecraft/textures/entity/parrot/*} would retexture every parrot in the game, so a
 * player could never have a parrot again - and adding a mob must not remove one. That is the same call
 * {@link RoachEntity} already made against the silverfish. There was no middle path: cat, wolf, frog,
 * pig, chicken and cow all have data-driven variant registries in 26.1, so a variant could have been
 * <em>added</em> without touching vanilla's, but parrots have no {@code parrot_variant} registry.
 *
 * <p>Only the entity is bespoke. It wears vanilla's parrot geometry with our own skin, exactly as the
 * Roach wears a silverfish - a pigeon and a parrot are the same shape and the same size, so the art
 * budget for the mod's second mob is one texture.
 *
 * <p><b>A pigeon cannot be tamed</b> (owner, 2026-08-04). That falls out of extending {@link Animal}
 * rather than {@code TamableAnimal} - but it is worth stating, because the bird this borrows its model
 * from is tameable: a vanilla parrot is a {@code ShoulderRidingEntity} and sits on you. A test pins it,
 * since the way this breaks is somebody changing the base class for an unrelated reason.
 *
 * <p><b>{@link net.minecraft.world.entity.MobCategory#AMBIENT}</b>, which cat and wolf could not be:
 * their category is fixed on the vanilla entity type and both are {@code CREATURE}. Ambient is what
 * bats use - a low cap that keeps trickling rather than filling once at chunk generation - which is
 * what "rare, and still there when you come back" needs.
 */
public class PigeonEntity extends Animal {

    public PigeonEntity(EntityType<? extends PigeonEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 6.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected void registerGoals() {
        // Float, flee, wander, look. Nothing else: a bird that reacts to being walked at is alive, and
        // anything past that starts being a feature.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /**
     * Nothing is food, so nothing breeds it.
     *
     * <p>A breedable pigeon is a renewable animal, and a renewable animal in the starting biome is a
     * resource - which is the one thing this mob was decided not to be. Leaving {@link #getBreedOffspring}
     * null is the other half of the same rule.
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }
}
