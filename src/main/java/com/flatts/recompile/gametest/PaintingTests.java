package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Recovered paintings (#99).
 *
 * <p>Two of these guard failures that are invisible until somebody looks at a wall: a {@code placeable}
 * tag that ADDED our six to vanilla's fifty-one instead of replacing them, and a broken painting that
 * comes back as a blank canvas.
 */
final class PaintingTests {

    private static final String[] WORKS =
        {"great_wave", "starry_night", "the_scream", "mona_lisa", "pearl_earring", "the_kiss"};

    private PaintingTests() {
    }

    private static Holder<PaintingVariant> variant(GameTestHelper helper, String id) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT)
            .getOrThrow(ResourceKey.create(Registries.PAINTING_VARIANT,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, id)));
    }

    static void register() {
        // Every one of the six loads at the size the spec declares. A wrong width or height silently
        // reshapes the artwork, and a squashed Mona Lisa still looks like a Mona Lisa - which is exactly
        // why nothing else would catch it.
        RCGameTests.test("every_painting_variant_loads_at_its_declared_size", 20, helper -> {
            record Expect(String id, int w, int h) { }
            List<String> wrong = new ArrayList<>();
            for (Expect e : new Expect[] {
                    new Expect("great_wave", 3, 2), new Expect("starry_night", 4, 3),
                    new Expect("the_scream", 3, 4), new Expect("mona_lisa", 2, 3),
                    new Expect("pearl_earring", 3, 4), new Expect("the_kiss", 4, 4)}) {
                PaintingVariant v = variant(helper, e.id()).value();
                if (v.width() != e.w() || v.height() != e.h()) {
                    wrong.add(e.id() + " is " + v.width() + "x" + v.height()
                        + ", expected " + e.w() + "x" + e.h());
                }
                if (v.title().isEmpty() || v.author().isEmpty()) {
                    wrong.add(e.id() + " is missing a title or author");
                }
            }
            helper.assertTrue(wrong.isEmpty(), "painting variants are wrong: " + wrong);
            helper.succeed();
        });

        // THE TAG TEST. A placeable tag that adds instead of replaces looks identical to one that worked,
        // right up until a player hangs a painting and gets a Kebab. Vanilla ships 51 variants in that
        // tag, so if any survive, the exclusivity this whole feature rests on is gone.
        RCGameTests.test("only_recovered_paintings_are_placeable", 20, helper -> {
            List<String> foreign = new ArrayList<>();
            int ours = 0;
            for (Holder<PaintingVariant> h : helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.PAINTING_VARIANT)
                    .getOrThrow(PaintingVariantTags.PLACEABLE)) {
                Identifier id = h.unwrapKey().orElseThrow().identifier();
                if (Recompile.MOD_ID.equals(id.getNamespace())) {
                    ours++;
                } else {
                    foreign.add(id.toString());
                }
            }
            helper.assertTrue(ours == WORKS.length,
                "expected " + WORKS.length + " recovered paintings to be placeable, found " + ours);
            helper.assertTrue(foreign.isEmpty(),
                "vanilla paintings are still placeable, so the tag ADDED instead of replacing - "
                    + foreign.size() + " survivors, e.g. "
                    + foreign.subList(0, Math.min(3, foreign.size())));
            helper.succeed();
        });

        // THE ROUND TRIP, which is acceptance criterion 4. Break it and it is still the Mona Lisa.
        RCGameTests.test("a_broken_painting_is_still_the_same_painting", 40, helper -> {
            BlockPos rel = new BlockPos(2, 2, 2);
            helper.setBlock(rel.north(), Blocks.STONE);
            // The 4-arg constructor is the only public one that takes a variant; the others are private
            // or leave the variant unset, which would make this test assert against a random work.
            Painting painting = new Painting(helper.getLevel(), helper.absolutePos(rel),
                Direction.SOUTH, variant(helper, "mona_lisa"));
            helper.getLevel().addFreshEntity(painting);

            painting.hurtServer(helper.getLevel(),
                helper.getLevel().damageSources().generic(), 4.0F);

            ItemStack dropped = findDroppedPainting(helper, rel);
            helper.assertTrue(dropped != null, "breaking a painting must drop an item");
            Holder<PaintingVariant> got = dropped.get(DataComponents.PAINTING_VARIANT);
            helper.assertTrue(got != null,
                "the dropped painting lost its variant - re-hanging it would roll a random work");
            helper.assertTrue(got.is(variant(helper, "mona_lisa")),
                "the dropped painting is the wrong work: " + got.unwrapKey().orElseThrow().identifier());
            helper.assertTrue(dropped.has(DataComponents.ITEM_NAME),
                "the dropped painting must keep its name, or the item reads Painting while its tooltip "
                    + "says Mona Lisa");
            helper.succeed();
        });

        // Negative control. Without it, the round trip above would also pass for a handler that stamps a
        // variant onto every painting item it ever sees, including one a player dropped on the floor.
        RCGameTests.test("a_loose_painting_item_is_not_rewritten", 20, helper -> {
            BlockPos abs = helper.absolutePos(new BlockPos(6, 2, 2));
            ItemEntity loose = new ItemEntity(helper.getLevel(), abs.getX() + 0.5, abs.getY(),
                abs.getZ() + 0.5, new ItemStack(Items.PAINTING));
            helper.getLevel().addFreshEntity(loose);
            helper.assertFalse(loose.getItem().has(DataComponents.PAINTING_VARIANT),
                "a painting item dropped with no painting nearby must be left alone");
            helper.succeed();
        });
    }

    private static @Nullable ItemStack findDroppedPainting(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(abs).inflate(5))) {
            if (e.getItem().is(Items.PAINTING)) {
                return e.getItem();
            }
        }
        return null;
    }
}
