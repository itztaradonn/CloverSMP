package net.cloversmp.cloverblessing.model;

/**
 * The four leaf positions in a clover cross layout, plus the curse slot (leaf 5).
 *
 * <pre>
 *       LEAF_2
 *         |
 * LEAF_1--+--LEAF_3        CURSE (outside the cross)
 *         |
 *       LEAF_4
 * </pre>
 *
 * Opposite pairs: LEAF_1 ↔ LEAF_3, LEAF_2 ↔ LEAF_4. The curse slot has no opposite.
 */
public enum LeafSlot {
    LEAF_1(0),
    LEAF_2(1),
    LEAF_3(2),
    LEAF_4(3),
    CURSE(4);

    private final int index;

    LeafSlot(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public boolean isCurse() {
        return this == CURSE;
    }

    /**
     * Returns the opposite leaf in the cross layout, or null for the curse slot.
     */
    public LeafSlot getOpposite() {
        return switch (this) {
            case LEAF_1 -> LEAF_3;
            case LEAF_3 -> LEAF_1;
            case LEAF_2 -> LEAF_4;
            case LEAF_4 -> LEAF_2;
            case CURSE -> null;
        };
    }

    /**
     * Returns the number of active leaves required for this slot. The curse slot opens with leaf 4.
     */
    public int getRequiredLeafCount() {
        return isCurse() ? 4 : index + 1;
    }

    /**
     * Human-readable label ("Leaf 1" … "Leaf 5").
     */
    public String label() {
        return "Leaf " + (index + 1);
    }
}
