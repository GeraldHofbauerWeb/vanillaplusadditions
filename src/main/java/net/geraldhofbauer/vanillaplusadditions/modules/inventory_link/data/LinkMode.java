package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data;

/**
 * Per-endpoint transfer mode of an inventory link. An endpoint only ever gives items away when it
 * may output, and only ever receives items when it may input. When both endpoints allow both
 * directions, the click order of link creation decides (first clicked → second clicked).
 */
public enum LinkMode {
    INPUT((byte) 0),
    OUTPUT((byte) 1),
    BOTH((byte) 2);

    private final byte id;

    LinkMode(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    public static LinkMode fromId(byte id) {
        for (LinkMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return BOTH;
    }

    public boolean canOutput() {
        return this == OUTPUT || this == BOTH;
    }

    public boolean canInput() {
        return this == INPUT || this == BOTH;
    }

    public LinkMode next() {
        return switch (this) {
            case INPUT -> OUTPUT;
            case OUTPUT -> BOTH;
            case BOTH -> INPUT;
        };
    }
}
