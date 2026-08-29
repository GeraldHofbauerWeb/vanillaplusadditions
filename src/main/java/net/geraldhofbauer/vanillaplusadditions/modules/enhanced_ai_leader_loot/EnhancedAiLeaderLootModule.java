package net.geraldhofbauer.vanillaplusadditions.modules.enhanced_ai_leader_loot;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.enhanced_ai_leader_loot.config.EnhancedAiLeaderLootConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Adds bonus loot (golden carrots / golden apples / a rare enchanted golden apple) to Enhanced
 * AI's "leader" mobs (banner-carrying, promoted Zombie/Skeleton-family mobs) on death, on top of
 * their normal mob loot. Enhanced AI itself rolls a separate loot table, {@code
 * enhancedai:leader_mob}, for leaders alongside their regular death loot — but ships it empty
 * ({@code {"type": "minecraft:empty"}}) as an intentional extension point. We replace that table
 * at load time via {@link LootTableLoadEvent}; no Enhanced AI class is referenced directly, so
 * this module needs no compile-time dependency, only the runtime {@link #shouldInitialize()}
 * gate. See {@code docs/enhanced_ai_leader_loot.md} for how this was reverse-engineered.
 */
public class EnhancedAiLeaderLootModule
        extends AbstractModule<EnhancedAiLeaderLootModule, EnhancedAiLeaderLootConfig> {

    private static final ResourceLocation LEADER_MOB_LOOT_TABLE =
            ResourceLocation.fromNamespaceAndPath("enhancedai", "leader_mob");

    public EnhancedAiLeaderLootModule() {
        super("enhanced_ai_leader_loot",
                "Enhanced AI Leader Loot",
                "Adds bonus golden food loot to Enhanced AI's banner-carrying leader mobs.",
                EnhancedAiLeaderLootConfig::new
        );
    }

    @Override
    protected boolean shouldInitialize() {
        return ModList.get().isLoaded("enhancedai");
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!isModuleEnabled() || !event.getName().equals(LEADER_MOB_LOOT_TABLE)) {
            return;
        }
        event.setTable(buildLeaderLootTable());
    }

    private LootTable buildLeaderLootTable() {
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));

        for (String entry : getConfig().getLeaderBonusLoot()) {
            String[] parts = entry.split(";");
            if (parts.length != 4) {
                continue;
            }

            try {
                ResourceLocation itemId = ResourceLocation.parse(parts[0]);
                int weight = Integer.parseInt(parts[1]);
                int minCount = Integer.parseInt(parts[2]);
                int maxCount = Integer.parseInt(parts[3]);

                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == Items.AIR) {
                    if (getConfig().shouldDebugLog()) {
                        getLogger().warn("Leader bonus loot: item not found: {}", parts[0]);
                    }
                    continue;
                }

                LootPoolSingletonContainer.Builder<?> lootEntry = LootItem.lootTableItem(item)
                        .setWeight(weight)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minCount, maxCount)));
                pool.add(lootEntry);
            } catch (Exception e) {
                getLogger().error("Failed to parse leader bonus loot entry: {}", entry, e);
            }
        }

        return LootTable.lootTable().setParamSet(LootContextParamSets.ENTITY).withPool(pool).build();
    }
}
