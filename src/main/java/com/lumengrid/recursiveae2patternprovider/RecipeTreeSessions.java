package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.PatternDetailsHelper;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid = RecursiveAE2PatternProvider.MODID)
public final class RecipeTreeSessions {
    private record Session(UUID id, InteractionHand hand, int slot, ItemStack original,
                           Set<String> shown, Set<String> excluded, long opened, List<RecipeTree.Node> nodes,
                           Map<Integer, Integer> offsets, Set<Integer> complete) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_OPEN = new WeakHashMap<>();
    private RecipeTreeSessions() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!Config.ENABLE.get() || !PatternUtil.isRecursive(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        Long previous = LAST_OPEN.get(player);
        if (previous != null && now >= previous && now - previous < 20) return;
        LAST_OPEN.put(player, now);
        var stack = player.getItemInHand(event.getHand());
        try {
            var details = PatternDetailsHelper.decodePattern(stack, player.level());
            if (details == null) return;
            var tree = RecipeTree.build(details, player.level());
            var exclusions = PatternRecipeRules.read(stack);
            var session = new Session(UUID.randomUUID(), event.getHand(), player.getInventory().getSelectedSlot(),
                    stack.copy(), new HashSet<>(), exclusions, now, new ArrayList<>(tree.nodes()), new HashMap<>(), new HashSet<>());
            SESSIONS.put(player, session);
            PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Open(session.id(), tree, exclusions));
        } catch (RuntimeException e) {
            RecursiveAE2PatternProvider.LOGGER.warn("Could not open recipe tree: {}", e.getMessage());
            player.sendOverlayMessage(Component.translatable("gui.recursiveae2patternprovider.tree.error"));
        }
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
        boolean sameSlot = session.hand() == InteractionHand.OFF_HAND || player.getInventory().getSelectedSlot() == session.slot();
        if (!matchesPattern(player.getItemInHand(session.hand()), session.original(), sameSlot,
                player.level().getGameTime() - session.opened())) {
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
        boolean sameSlot = session.hand() == InteractionHand.OFF_HAND || player.getInventory().getSelectedSlot() == session.slot();
        var held = player.getItemInHand(session.hand());
        String message;
        boolean saved = false;
        if (!matchesPattern(held, session.original(), sameSlot, player.level().getGameTime() - session.opened())) {
            message = "stale";
        } else if (!PatternRecipeRules.maySave(request.excluded(), session.excluded(), session.shown())) {
            message = "invalid";
        } else {
            // Replace the stack; never mutate an AE2 key's shared component identity in place.
            var updated = held.copy();
            PatternRecipeRules.write(updated, request.excluded());
            player.setItemInHand(session.hand(), updated);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            message = "saved";
            saved = true;
        }
        PacketDistributor.sendToPlayer(player, new RecipeTreePackets.Result(session.id(), saved, message));
    }
}
