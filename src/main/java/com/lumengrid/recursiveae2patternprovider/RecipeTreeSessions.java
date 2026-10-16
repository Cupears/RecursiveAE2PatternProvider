package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.PatternDetailsHelper;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = RecursiveAE2PatternProvider.MODID)
public final class RecipeTreeSessions {
    private record Session(UUID id, net.minecraft.world.inventory.AbstractContainerMenu menu, net.minecraft.world.inventory.Slot slot, ItemStack original,
                           Set<String> shown, Set<String> excluded, long opened, List<RecipeTree.Node> nodes,
                           Map<Integer, Integer> offsets, Set<Integer> complete) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_OPEN = new WeakHashMap<>();
    private RecipeTreeSessions() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity()); LAST_OPEN.remove(event.getEntity());
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var session = SESSIONS.get(player);
            if (session != null && (player.containerMenu != session.menu()
                    || player.level().getGameTime() - session.opened() > 20 * 60 * 15)) SESSIONS.remove(player);
        }
    }

    public static void open(ServerPlayer player, int containerId) {
        var menu = player.containerMenu;
        if (!Config.ENABLE.get() || menu.containerId != containerId || !menu.stillValid(player)
                || !(menu instanceof com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu terminal)) return;
        var slot = terminal.recursiveAE2$treeSlot();
        if (slot == null || !PatternUtil.isRecursive(slot.getItem())) return;
        long now = player.level().getGameTime();
        Long previous = LAST_OPEN.get(player);
        if (previous != null && now >= previous && now - previous < 5) return;
        LAST_OPEN.put(player, now);
        var stack = slot.getItem();
        try {
            var details = PatternDetailsHelper.decodePattern(stack, player.level());
            if (details == null) return;
            var tree = RecipeTree.build(details, player.level());
            var exclusions = PatternRecipeRules.read(stack);
            var session = new Session(UUID.randomUUID(), menu, slot,
                    stack.copy(), new HashSet<>(), exclusions, now, new ArrayList<>(tree.nodes()), new HashMap<>(), new HashSet<>());
            SESSIONS.put(player, session);
            PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Open(session.id(), tree, exclusions));
        } catch (RuntimeException e) {
            RecursiveAE2PatternProvider.LOGGER.warn("Could not open recipe tree", e);
            player.sendOverlayMessage(Component.translatable("gui.recursiveae2patternprovider.tree.error"));
        }
    }

    private static boolean validSession(ServerPlayer player, Session session) {
        return player.containerMenu == session.menu() && session.menu().stillValid(player)
                && matchesPattern(session.slot().getItem(), session.original(), true,
                player.level().getGameTime() - session.opened());
    }

    static boolean matchesPattern(ItemStack held, ItemStack original, boolean sameSlot, long age) {
        return sameSlot && age >= 0 && age <= 20 * 60 * 15 && PatternUtil.isRecursive(held)
                && ItemStack.matches(held, original);
    }

    public static void expand(ServerPlayer player, RecipeTreePackets.Expand request) {
        var session = SESSIONS.get(player);
        if (session == null || !session.id().equals(request.session())) {
            PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Result(request.session(), false, "stale"));
            return;
        }
        if (!validSession(player, session)) {
            PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Result(request.session(), false, "stale"));
            return;
        }
        int parent = request.parent();
        if (parent <= 0 || parent >= session.nodes().size() || session.nodes().get(parent).recipe()
                || session.complete().contains(parent) || request.offset() != session.offsets().getOrDefault(parent, 0)) return;
        try {
            int start = session.nodes().size();
            var page = RecipeTree.expand(session.nodes(), parent, request.offset(), player.level());
            session.nodes().addAll(page.nodes());
            for (var node : page.nodes()) if (node.recipe()) session.shown().add(node.recipeId());
            session.offsets().put(parent, page.nextOffset());
            if (page.done()) session.complete().add(parent);
            PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Branch(session.id(), parent, start, page));
        } catch (RuntimeException e) {
            RecursiveAE2PatternProvider.LOGGER.warn("Could not expand recipe branch", e);
            PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Result(session.id(), false, "error"));
        }
    }

    public static void save(ServerPlayer player, RecipeTreePackets.Save request) {
        var session = SESSIONS.get(player);
        if (session == null || !session.id().equals(request.session())) {
            if (request.apply()) PacketDistributor.sendToPlayer(player,
                    new RecipeTreePackets.Result(request.session(), false, "stale"));
            return;
        }
        SESSIONS.remove(player);
        if (!request.apply()) return;
        var held = session.slot().getItem();
        String message;
        boolean saved = false;
        if (!validSession(player, session)) {
            message = "stale";
        } else if (!PatternRecipeRules.maySave(request.excluded(), session.excluded(), session.shown())) {
            message = "invalid";
        } else {
            // Replace the stack; never mutate an AE2 key's shared component identity in place.
            var updated = held.copy();
            PatternRecipeRules.write(updated, request.excluded());
            session.slot().set(updated);
            session.slot().setChanged();
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            message = "saved";
            saved = true;
        }
        PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Result(session.id(), saved, message));
    }
}
