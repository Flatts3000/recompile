package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.aquarium.AquariumPieces;
import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure;
import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
import com.flatts.recompile.content.worldgen.tower.CoolingTowerPiece;
import com.flatts.recompile.content.worldgen.tower.CoolingTowerStructure;
import com.flatts.recompile.content.worldgen.tower.SmokestackPiece;
import com.flatts.recompile.content.worldgen.tower.SmokestackStructure;
import com.flatts.recompile.content.worldgen.sewer.SewerStructure;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The structure registries: one {@link StructureType} and the {@link StructurePieceType}s it builds
 * from (#90).
 *
 * <p><b>Both halves are required and they fail differently.</b> The structure type is what a datapack
 * names, so a missing one is loud - the JSON refuses to parse and worldgen dies on load. A missing
 * <em>piece</em> type is quiet: generation works fine until the chunk is saved and reloaded, at which
 * point the piece cannot be deserialised and the sewer comes back with holes in it. Registering both
 * together is the cheapest way not to find that out later.
 *
 * <p>Piece types are {@code ContextlessType}, the plain {@code CompoundTag} form, because a sewer piece
 * saves nothing beyond the box and depth that {@code StructurePiece} writes itself.
 */
public final class RCStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_TYPE, Recompile.MOD_ID);

    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_PIECE, Recompile.MOD_ID);

    /**
     * The cooling tower (#307). Named by {@code data/recompile/worldgen/structure/cooling_tower.json}.
     *
     * <p>A surface landmark rather than a dungeon: one piece, no loot, and its whole job is being
     * visible from the next region.
     */
    public static final Supplier<StructureType<CoolingTowerStructure>> COOLING_TOWER =
        STRUCTURE_TYPES.register("cooling_tower", () -> () -> CoolingTowerStructure.CODEC);

    public static final Supplier<StructurePieceType> COOLING_TOWER_SHELL =
        PIECE_TYPES.register("cooling_tower_shell",
            () -> (StructurePieceType.ContextlessType) CoolingTowerPiece::new);

    /**
     * Brick smokestacks (#308). Named by {@code data/recompile/worldgen/structure/smokestack.json}.
     *
     * <p>The demolition yard's skyline, and the counterpart to the cooling tower: shorter, in brick,
     * and several to a region rather than one every few thousand blocks.
     */
    public static final Supplier<StructureType<SmokestackStructure>> SMOKESTACK =
        STRUCTURE_TYPES.register("smokestack", () -> () -> SmokestackStructure.CODEC);

    public static final Supplier<StructurePieceType> SMOKESTACK_PIECE =
        PIECE_TYPES.register("smokestack",
            () -> (StructurePieceType.ContextlessType) SmokestackPiece::new);

    /** The sewer itself. Named by {@code data/recompile/worldgen/structure/sewer.json}. */
    public static final Supplier<StructureType<SewerStructure>> SEWER =
        STRUCTURE_TYPES.register("sewer", () -> () -> SewerStructure.CODEC);

    public static final Supplier<StructurePieceType> SEWER_CORRIDOR =
        PIECE_TYPES.register("sewer_corridor",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerCorridor::new);

    public static final Supplier<StructurePieceType> SEWER_CROSSING =
        PIECE_TYPES.register("sewer_crossing",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerCrossing::new);

    public static final Supplier<StructurePieceType> SEWER_STAIRS =
        PIECE_TYPES.register("sewer_stairs",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerStairs::new);

    public static final Supplier<StructurePieceType> SEWER_ENTRANCE =
        PIECE_TYPES.register("sewer_entrance",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerEntrance::new);

    public static final Supplier<StructurePieceType> SEWER_TURTLE_DEN =
        PIECE_TYPES.register("sewer_turtle_den",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerTurtleDen::new);

    public static final Supplier<StructurePieceType> SEWER_FROG_DEN =
        PIECE_TYPES.register("sewer_frog_den",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerFrogDen::new);

    public static final Supplier<StructurePieceType> SEWER_ACCESS_CHAMBER =
        PIECE_TYPES.register("sewer_access_chamber",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerAccessChamber::new);

    public static final Supplier<StructurePieceType> SEWER_SUMP =
        PIECE_TYPES.register("sewer_sump",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerSump::new);

    public static final Supplier<StructurePieceType> SEWER_ROOM =
        PIECE_TYPES.register("sewer_room",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerRoom::new);

    private RCStructures() {
    }

    /**
     * The Municipal Aquarium (docs/municipal_aquarium_spec.md): the sewer's shape sited like the
     * tower. One piece type per room, because a piece serialises only its box and each room derives
     * everything else from which room it is - which the type carries and a tag would have to.
     */
    public static final Supplier<StructureType<AquariumStructure>> MUNICIPAL_AQUARIUM =
        STRUCTURE_TYPES.register("municipal_aquarium", () -> () -> AquariumStructure.CODEC);

    public static final Supplier<StructurePieceType> AQUARIUM_FORECOURT =
        PIECE_TYPES.register("aquarium_forecourt",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.Forecourt::new);

    public static final Supplier<StructurePieceType> AQUARIUM_LOBBY =
        PIECE_TYPES.register("aquarium_lobby",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.Lobby::new);

    public static final Supplier<StructurePieceType> AQUARIUM_GALLERY =
        PIECE_TYPES.register("aquarium_gallery",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.Gallery::new);

    public static final Supplier<StructurePieceType> AQUARIUM_BIG_TANK =
        PIECE_TYPES.register("aquarium_big_tank",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.BigTank::new);

    public static final Supplier<StructurePieceType> AQUARIUM_GUARDIAN_TANK =
        PIECE_TYPES.register("aquarium_guardian_tank",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.GuardianTank::new);

    public static final Supplier<StructurePieceType> AQUARIUM_FILTRATION_HALL =
        PIECE_TYPES.register("aquarium_filtration_hall",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.FiltrationHall::new);

    public static final Supplier<StructurePieceType> AQUARIUM_BACK_OF_HOUSE =
        PIECE_TYPES.register("aquarium_back_of_house",
            () -> (StructurePieceType.ContextlessType) AquariumPieces.BackOfHouse::new);

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECE_TYPES.register(modEventBus);
    }
}
