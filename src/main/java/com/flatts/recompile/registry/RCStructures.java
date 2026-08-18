package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
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

    public static final Supplier<StructurePieceType> SEWER_ROOM =
        PIECE_TYPES.register("sewer_room",
            () -> (StructurePieceType.ContextlessType) SewerPieces.SewerRoom::new);

    private RCStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECE_TYPES.register(modEventBus);
    }
}
