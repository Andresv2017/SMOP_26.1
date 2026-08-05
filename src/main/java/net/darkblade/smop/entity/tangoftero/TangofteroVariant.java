package net.darkblade.smop.entity.tangoftero;

/** Coat colours a Tangoftero can be born with. The id is what gets stored and synced. */
public enum TangofteroVariant {

    BLACK,
    BLUE,
    BROWN,
    CREAM,
    LAVANDER,
    RED,
    SILVER,
    WHITE,
    YELLOW;

    private static final TangofteroVariant[] BY_ID = values();

    public int getId() {
        return this.ordinal();
    }

    public static TangofteroVariant byId(int id) {
        return BY_ID[Math.floorMod(id, BY_ID.length)];
    }

    public static int count() {
        return BY_ID.length;
    }
}
