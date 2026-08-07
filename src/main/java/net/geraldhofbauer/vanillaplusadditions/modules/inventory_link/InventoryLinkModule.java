package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.compat.VaultMultiblockResolver;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.config.InventoryLinkConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.InventoryLink;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.InventoryLinkData;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.LinkMode;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.PendingSelection;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.engine.InventoryAccess;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.engine.InventoryLinkEngine;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.item.InventoryLinkerItem;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.LinkDisplay;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.LinkOverlayPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.OpenLinkScreenPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.RequestLinkOverlayPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.RequestOpenLinkScreenPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.UpdateLinkPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Inventory Linker: right-click two inventory blocks to link them, and items move between them on
 * their own — no funnels, chutes or hoppers in between.
 *
 * <p>Built for Create: Aeronautics platforms, where the vanilla logistics rules run out: a Docking
 * Connector never pulls from the vault below it, an Auger Cog exposes no inventory at all, and an
 * Auger never pushes into anything at its axial ends. A link sidesteps all three.</p>
 *
 * <p>Sable physics platforms are plots inside the same level (at ~20.48M block coordinates), so
 * links to contraption blocks need no special handling: the same per-level storage, the same level
 * tick and the same capability lookups apply.</p>
 *
 * <p>No Create/Aeronautics types are referenced from this class; everything typed lives in
 * {@code compat} classes that are only classloaded once {@link #shouldInitialize()} passed.</p>
 */
public class InventoryLinkModule extends AbstractModule<InventoryLinkModule, InventoryLinkConfig> {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, VanillaPlusAdditions.MODID);

    public static final DeferredItem<Item> INVENTORY_LINKER =
            ITEMS.register("inventory_linker", () -> new InventoryLinkerItem(new Item.Properties().stacksTo(1)));

    /** The first block of a link in progress, carried on the tool itself. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PendingSelection>> PENDING_LINK =
            DATA_COMPONENTS.register("inventory_linker_pending",
                    () -> DataComponentType.<PendingSelection>builder()
                            .persistent(PendingSelection.CODEC)
                            .networkSynchronized(PendingSelection.STREAM_CODEC)
                            .build());

    /** Minimum ticks between two overlay lookups per player. */
    private static final long OVERLAY_REQUEST_COOLDOWN_TICKS = 5L;

    private static InventoryLinkModule instance;

    private final InventoryLinkEngine engine = new InventoryLinkEngine(this);
    private final Map<UUID, Long> lastOverlayRequest = new HashMap<>();

    public InventoryLinkModule() {
        super("inventory_link",
                "Inventory Link",
                "Inventory Linker tool: link two inventories and items flow between them automatically.",
                InventoryLinkConfig::new);
        instance = this;
    }

    @Override
    protected boolean shouldInitialize() {
        // "simulated" is Create: Aeronautics' core, shipped jar-in-jar in create-aeronautics-bundled.
        return ModList.get().isLoaded("create") && ModList.get().isLoaded("simulated");
    }

    @Override
    protected void onInitialize() {
        DATA_COMPONENTS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        VanillaPlusCreativeTabs.addToMainTab(INVENTORY_LINKER);

        getModEventBus().addListener(this::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Inventory Link module initialized");
    }

    @Override
    protected void onClientSetup() {
        // Ponder entry (hold W on the item). Fully qualified: keeps the client class off the
        // dedicated server's classloading path.
        net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client
                .InventoryLinkerPonder.register();
    }

    // ---- Interaction ----

    /**
     * The whole link workflow. Runs on both sides; the client side only swallows the click so no
     * block GUI opens on top of the tool. Cancelling client-side does not suppress the interaction
     * packet, so the server half below still runs.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(INVENTORY_LINKER.get())) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        handleLinkClick(level, player, event.getPos().immutable(), event.getItemStack());
    }

    /** Right-clicking air with the tool abandons a selection in progress. */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isModuleEnabled() || !event.getItemStack().is(INVENTORY_LINKER.get())) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getItemStack().get(PENDING_LINK.get()) != null) {
            event.getItemStack().remove(PENDING_LINK.get());
            feedback(player, "cleared");
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS, 0.4f, 1.0f);
        }
    }

    private void handleLinkClick(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack) {
        InventoryLinkData data = InventoryLinkData.get(level);
        PendingSelection pending = stack.get(PENDING_LINK.get());
        String dimension = level.dimension().location().toString();

        if (pending == null) {
            if (!InventoryAccess.hasInventory(level, pos)) {
                reject(player, "no_inventory");
                return;
            }
            stack.set(PENDING_LINK.get(), new PendingSelection(dimension, pos));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.3f);
            spark(level, pos);
            feedback(player, "selected", Component.literal(pos.toShortString()));
            return;
        }

        if (pending.pos().equals(pos)) {
            stack.remove(PENDING_LINK.get());
            feedback(player, "cleared");
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.0f);
            return;
        }

        if (!pending.dimension().equals(dimension)) {
            stack.remove(PENDING_LINK.get());
            reject(player, "wrong_dimension");
            return;
        }

        BlockPos origin = pending.pos();
        int range = getConfig().getMaxLinkRange();
        if (origin.distSqr(pos) > (double) range * range) {
            reject(player, "too_far");
            return;
        }
        if (!InventoryAccess.hasInventory(level, origin)) {
            stack.remove(PENDING_LINK.get());
            reject(player, "no_inventory");
            return;
        }
        if (!InventoryAccess.hasInventory(level, pos)) {
            reject(player, "no_inventory");
            return;
        }
        // Two blocks of the same vault share one inventory — linking them would be a no-op loop.
        if (VaultMultiblockResolver.members(level, origin).contains(pos)) {
            reject(player, "same_inventory");
            return;
        }
        if (data.has(origin, pos)) {
            reject(player, "duplicate");
            return;
        }
        int limit = getConfig().getMaxLinksPerBlock();
        if (data.countAt(origin) >= limit || data.countAt(pos) >= limit) {
            reject(player, "limit_reached");
            return;
        }

        data.add(new InventoryLink(origin, pos, LinkMode.BOTH, LinkMode.BOTH));
        engine.forget(origin, pos);
        stack.remove(PENDING_LINK.get());

        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        spark(level, origin);
        spark(level, pos);
        drawParticleLine(level, origin, pos);
        feedback(player, "linked", Component.literal(origin.toShortString()),
                Component.literal(pos.toShortString()));
    }

    private static void spark(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.35, 0.35, 0.35, 0.02);
    }

    private static void drawParticleLine(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        double distance = start.distanceTo(end);
        int steps = (int) Math.min(128, Math.max(1, distance * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.lerp(end, (double) i / steps);
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private void reject(ServerPlayer player, String key) {
        feedback(player, key);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO,
                SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    private void feedback(Player player, String key, Object... args) {
        player.displayClientMessage(
                Component.translatable("message.vanillaplusadditions.inventory_link." + key, args), true);
    }

    // ---- Transfer ----

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            engine.tick(level);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        lastOverlayRequest.remove(event.getEntity().getUUID());
    }

    // ---- Networking ----

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(RequestLinkOverlayPacket.TYPE, RequestLinkOverlayPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled() || !(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    if (!(player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    // Endpoints may legitimately sit millions of blocks away (physics plots), so a
                    // distance check is meaningless here — a rate limit bounds the work instead.
                    long now = level.getGameTime();
                    Long last = lastOverlayRequest.get(player.getUUID());
                    if (last != null && now - last < OVERLAY_REQUEST_COOLDOWN_TICKS) {
                        return;
                    }
                    lastOverlayRequest.put(player.getUUID(), now);
                    PacketDistributor.sendToPlayer(player,
                            new LinkOverlayPacket(packet.pos(), collectDisplays(level, packet.pos())));
                }));

        registrar.playToServer(RequestOpenLinkScreenPacket.TYPE, RequestOpenLinkScreenPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled() || !(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    if (!(player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    if (!player.getMainHandItem().is(INVENTORY_LINKER.get())) {
                        return;
                    }
                    List<LinkDisplay> displays = collectDisplays(level, packet.pos());
                    if (displays.isEmpty()) {
                        reject(player, "not_linked");
                        return;
                    }
                    PacketDistributor.sendToPlayer(player, new OpenLinkScreenPacket(packet.pos(), displays));
                }));

        registrar.playToServer(UpdateLinkPacket.TYPE, UpdateLinkPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled() || !(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    if (!(player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    // Authority check: the link must exist. Endpoints can legitimately be millions
                    // of blocks away (physics plots), so a distance check would be wrong here.
                    InventoryLinkData data = InventoryLinkData.get(level);
                    if (!data.has(packet.first(), packet.second())) {
                        return;
                    }
                    if (packet.action() == UpdateLinkPacket.ACTION_REMOVE) {
                        data.remove(packet.first(), packet.second());
                        engine.forget(packet.first(), packet.second());
                        feedback(player, "unlinked");
                    } else {
                        data.setModes(packet.first(), packet.second(),
                                LinkMode.fromId(packet.firstMode()), LinkMode.fromId(packet.secondMode()));
                    }
                    PacketDistributor.sendToPlayer(player,
                            new LinkOverlayPacket(packet.anchor(), collectDisplays(level, packet.anchor())));
                }));

        registrar.playToClient(LinkOverlayPacket.TYPE, LinkOverlayPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client
                                .InventoryLinkClientEvents.handleLinkOverlay(packet)));

        registrar.playToClient(OpenLinkScreenPacket.TYPE, OpenLinkScreenPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client
                                .InventoryLinkClientEvents.handleOpenScreen(packet)));
    }

    /**
     * All links touching the structure the given block belongs to, with both endpoint boxes
     * resolved. Looking at any block of a vault finds the links of the whole vault.
     */
    private List<LinkDisplay> collectDisplays(ServerLevel level, BlockPos pos) {
        Set<BlockPos> members = VaultMultiblockResolver.members(level, pos);
        AABB nearBox = VaultMultiblockResolver.bounds(members);

        Set<InventoryLink> found = new LinkedHashSet<>();
        InventoryLinkData data = InventoryLinkData.get(level);
        for (BlockPos member : members) {
            found.addAll(data.linksAt(member));
        }

        List<LinkDisplay> displays = new ArrayList<>(found.size());
        for (InventoryLink link : found) {
            boolean anchorIsFirst = members.contains(link.first());
            BlockPos far = anchorIsFirst ? link.second() : link.first();
            AABB farBox = VaultMultiblockResolver.bounds(level, far);
            displays.add(new LinkDisplay(link.first(), link.second(),
                    link.firstMode().id(), link.secondMode().id(),
                    anchorIsFirst ? nearBox : farBox,
                    anchorIsFirst ? farBox : nearBox,
                    anchorIsFirst));
        }
        return displays;
    }

    // ---- Crafting recipe (in code — JSON datapack recipes don't load reliably here) ----

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        event.addListener(new InventoryLinkerRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    /** Brass Funnel over a Stock Link over a Chute — the three things a link replaces. */
    private void applyInventoryLinkerRecipe(RecipeManager recipeManager) {
        Item funnel = createItem("brass_funnel");
        Item chute = createItem("chute");
        Item link = createItem("stock_link");
        if (link == Items.AIR) {
            link = createItem("redstone_link");
        }
        if (funnel == Items.AIR || chute == Items.AIR || link == Items.AIR) {
            getLogger().warn("Create ingredients missing — skipping Inventory Linker recipe");
            return;
        }

        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('F', Ingredient.of(funnel));
        key.put('L', Ingredient.of(link));
        key.put('C', Ingredient.of(chute));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of("F", "L", "C"));
        ItemStack result = new ItemStack(INVENTORY_LINKER.get(), 1);
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "inventory_linker"), recipe);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> existing : recipeManager.getRecipes()) {
            merged.put(existing.id(), existing);
        }
        merged.put(holder.id(), holder);
        recipeManager.replaceRecipes(merged.values());
    }

    private static Item createItem(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
    }

    private final class InventoryLinkerRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private InventoryLinkerRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return barrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyInventoryLinkerRecipe(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_inventory_linker_recipe";
        }
    }

    // ---- Static accessors for the client ----

    public static float getOutlineRed() {
        return instance != null ? instance.getConfig().getOutlineRed() : 0.3f;
    }

    public static float getOutlineGreen() {
        return instance != null ? instance.getConfig().getOutlineGreen() : 0.9f;
    }

    public static float getOutlineBlue() {
        return instance != null ? instance.getConfig().getOutlineBlue() : 1.0f;
    }

    public static float getOutlineAlpha() {
        return instance != null ? instance.getConfig().getOutlineAlpha() : 0.9f;
    }
}
