package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config.MysticalCatConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetch Riddle: the cat poses a cryptic riddle for a simple, obtainable item; bring the item and
 * hand it over (right-click the cat) to win. Purely interaction-driven — no blocks or entities.
 */
public class FetchRiddleGame implements MiniGame {

    private record Riddle(Item item, String riddleKey) {
    }

    private record State(Item target, String riddleKey) {
    }

    @Override
    public String id() {
        return "fetch_riddle";
    }

    @Override
    public boolean canRunAt(ServerLevel level, MysticalCatEntity cat) {
        return !resolveRiddles().isEmpty();
    }

    @Override
    public void start(GameSession ctx) {
        List<Riddle> pool = resolveRiddles();
        if (pool.isEmpty()) {
            ctx.lose();
            return;
        }
        Riddle picked = pool.get(ctx.random().nextInt(pool.size()));
        ctx.setState(new State(picked.item(), picked.riddleKey()));
        ctx.gameMessage("start", Component.translatable(GameSession.lang(picked.riddleKey())));
    }

    @Override
    public void onCatInteract(GameSession ctx, ServerPlayer player, InteractionHand hand) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(state.target())) {
            held.shrink(1);
            ctx.win();
        } else {
            // Re-whisper the riddle as a hint on the action bar.
            ctx.gameActionBar("start", Component.translatable(GameSession.lang(state.riddleKey())));
        }
    }

    /** Parses {@code itemId=riddleKey} config entries into installed-item riddles. */
    private static List<Riddle> resolveRiddles() {
        MysticalCatConfig cfg = MysticalCatModule.config();
        List<Riddle> pool = new ArrayList<>();
        if (cfg == null) {
            return pool;
        }
        for (String entry : cfg.getFetchRiddleItems()) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq).trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            pool.add(new Riddle(BuiltInRegistries.ITEM.get(id), entry.substring(eq + 1).trim()));
        }
        return pool;
    }
}
