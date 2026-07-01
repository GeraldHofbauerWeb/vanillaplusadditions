package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.Locale;
import java.util.Optional;

/**
 * Reads a {@code [bm]} marker off a sign. Both faces are checked (the first matching side wins).
 * The first non-styled line must equal the configured prefix; lines 2-4 become label / icon-key /
 * detail. All comparisons strip formatting via {@link Component#getString()} so coloured signs work.
 */
public final class SignReader {

    private SignReader() {
    }

    public static Optional<MapSignMarker> readMarker(SignBlockEntity sign, String prefix) {
        Optional<MapSignMarker> front = fromSide(sign.getFrontText(), sign.getBlockPos(), prefix);
        if (front.isPresent()) {
            return front;
        }
        return fromSide(sign.getBackText(), sign.getBlockPos(), prefix);
    }

    private static Optional<MapSignMarker> fromSide(SignText text, BlockPos pos, String prefix) {
        String line1 = plain(text.getMessage(0, false)).trim();
        if (!line1.equalsIgnoreCase(prefix.trim())) {
            return Optional.empty();
        }
        String label = plain(text.getMessage(1, false)).trim();
        String iconKey = plain(text.getMessage(2, false)).trim().toLowerCase(Locale.ROOT);
        String detail = plain(text.getMessage(3, false)).trim();
        return Optional.of(new MapSignMarker(
                MapSignManager.signId(pos), MapSignMarker.Source.SIGN, pos.immutable(),
                label, iconKey, detail));
    }

    private static String plain(Component component) {
        return component.getString();
    }
}
