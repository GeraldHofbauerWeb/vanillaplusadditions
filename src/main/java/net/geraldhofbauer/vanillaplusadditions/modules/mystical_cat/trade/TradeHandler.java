package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.trade;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config.MysticalCatConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Exchanges Mystical Paws for rewards. Paw thresholds ({@code trade_tiers}, default 5/15/30) unlock
 * successively better reward pools; sneak-right-clicking a cat spends paws on the highest tier the
 * player can currently afford. Rewards are weighted entries of the form {@code item*count@weight}.
 */
public final class TradeHandler {

    private TradeHandler() {
    }

    private record Reward(Item item, int count, int weight) {
    }

    public static void trade(MysticalCatEntity cat, ServerPlayer player) {
        MysticalCatConfig cfg = MysticalCatModule.config();
        if (cfg == null) {
            return;
        }
        List<Integer> tiers = new ArrayList<>();
        for (Integer t : cfg.getTradeTiers()) {
            tiers.add(t);
        }
        tiers.sort(Integer::compareTo);
        if (tiers.isEmpty()) {
            return;
        }

        int paws = countPaws(player);
        player.sendSystemMessage(Component.translatable(GameSession.lang("trade.balance"), paws));

        int tierIndex = -1;
        for (int i = tiers.size() - 1; i >= 0; i--) {
            if (paws >= tiers.get(i)) {
                tierIndex = i;
                break;
            }
        }
        if (tierIndex < 0) {
            int needed = tiers.get(0) - paws;
            player.sendSystemMessage(Component.translatable(GameSession.lang("trade.info"), needed));
            return;
        }

        List<Reward> rewards = parseRewards(rewardListForTier(cfg, tierIndex));
        if (rewards.isEmpty()) {
            player.sendSystemMessage(Component.translatable(GameSession.lang("trade.none"), tiers.get(0)));
            return;
        }

        Reward chosen = pickWeighted(rewards, player);
        consumePaws(player, tiers.get(tierIndex));

        ItemStack reward = new ItemStack(chosen.item(), chosen.count());
        Component label = Component.literal(chosen.count() + "x ").append(new ItemStack(chosen.item()).getHoverName());
        if (!player.getInventory().add(reward) || !reward.isEmpty()) {
            player.drop(reward, false);
        }
        player.sendSystemMessage(Component.translatable(GameSession.lang("trade.success"), label));
        cat.level().playSound(null, cat.getX(), cat.getY(), cat.getZ(),
                SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 1.0f, 1.3f);
    }

    private static List<? extends String> rewardListForTier(MysticalCatConfig cfg, int tierIndex) {
        return switch (Math.min(tierIndex, 2)) {
            case 0 -> cfg.getTradeRewardsTier1();
            case 1 -> cfg.getTradeRewardsTier2();
            default -> cfg.getTradeRewardsTier3();
        };
    }

    private static int countPaws(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(MysticalCatModule.MYSTICAL_PAW.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumePaws(ServerPlayer player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(MysticalCatModule.MYSTICAL_PAW.get())) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static Reward pickWeighted(List<Reward> rewards, ServerPlayer player) {
        int total = 0;
        for (Reward r : rewards) {
            total += r.weight();
        }
        if (total <= 0) {
            return rewards.get(0);
        }
        int roll = player.getRandom().nextInt(total);
        for (Reward r : rewards) {
            roll -= r.weight();
            if (roll < 0) {
                return r;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    /** Parses {@code item*count@weight} entries, skipping any whose item is not installed. */
    private static List<Reward> parseRewards(List<? extends String> entries) {
        List<Reward> parsed = new ArrayList<>();
        for (String entry : entries) {
            int star = entry.indexOf('*');
            int at = entry.indexOf('@');
            if (star <= 0 || at <= star) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, star).trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            try {
                int count = Integer.parseInt(entry.substring(star + 1, at).trim());
                int weight = Integer.parseInt(entry.substring(at + 1).trim());
                if (count > 0 && weight > 0) {
                    parsed.add(new Reward(BuiltInRegistries.ITEM.get(id), count, weight));
                }
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return parsed;
    }
}
