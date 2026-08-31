package com.flatts.recompile.content.worldgen.tower;

/**
 * Weathering that eats a rim from the top down and <b>cannot leave a block in the air</b>.
 *
 * <p><b>This exists because the obvious version is wrong twice.</b> Rolling each block of the top
 * courses independently is the natural way to write "more of it is gone the higher you go", and it
 * produces two failures at once: the top row survives so rarely that the rim reads as confetti, and
 * blocks survive above holes, so the structure ends up with bricks hovering over it. On a landmark,
 * which is nothing but its silhouette, that is the whole feature broken.
 *
 * <p>The rule is one line: <b>a rim block stands only if the one below it stands.</b> That is how
 * weather actually works on a chimney or a shell, and it makes floating blocks impossible by
 * construction rather than by filtering them out afterwards.
 *
 * <p>Shared by both structures because both got it wrong separately - the cooling tower's was found in
 * review, and the smokestack shipped the same arithmetic to the same effect.
 */
final class RaggedRim {

    private RaggedRim() {
    }

    /**
     * Whether a block in the weathered band survives.
     *
     * <p><b>Each column is eaten to a depth, rather than each block rolled.</b> That is the model, and
     * it is the second attempt: the first made the per-block rolls monotonic by requiring the block
     * below to survive too, which stops things floating but compounds the probabilities - the cooling
     * tower's top course came out at four percent, which is worse confetti than the bug being fixed.
     * A depth per column gives the same guarantee for free, costs one hash instead of a loop, and puts
     * the distribution somewhere it can be reasoned about.
     *
     * <p>The square is what keeps the rim a rim. A uniform depth would leave only one course in
     * {@code raggedRows + 1} intact at the very top; squaring pushes the mass toward shallow erosion,
     * so most columns lose a block or two and a few are bitten deep.
     *
     * <p>No {@code y}: the depth belongs to the column, not to the block, which is the whole point.
     *
     * @param fromTop    0 is the topmost course, counting down
     * @param raggedRows how many courses are weathered at all
     */
    static boolean survives(int x, int z, int fromTop, int raggedRows) {
        double roll = hash(x, 0, z);
        int eaten = (int) (roll * roll * (raggedRows + 1));
        return fromTop >= eaten;
    }

    /** A stable 0..1 from a position. Same block, same answer, on every regeneration. */
    static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7919L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65536.0;
    }
}
