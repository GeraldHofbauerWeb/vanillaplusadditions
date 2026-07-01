package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import net.minecraft.core.BlockPos;

/**
 * One curated map marker. Two sources flow into the same MarkerSet:
 * <ul>
 *     <li>{@link Source#SIGN} — auto-derived from a {@code [bm]} sign; managed only via the sign.</li>
 *     <li>{@link Source#COMMAND} — created/edited via {@code /bmsigns add|edit}.</li>
 * </ul>
 *
 * @param id      stable marker id ({@code s/<packedPos>} for signs, {@code c<n>} for command markers)
 * @param source  where the marker came from
 * @param pos     block position the marker sits at
 * @param label   title shown on the map
 * @param iconKey icon key (see {@link IconKey}); empty/unknown falls back to the default pin
 * @param detail  optional popup description
 */
public record MapSignMarker(String id, Source source, BlockPos pos, String label, String iconKey,
                            String detail) {

    public enum Source {
        SIGN,
        COMMAND
    }

    public boolean isSign() {
        return source == Source.SIGN;
    }
}
