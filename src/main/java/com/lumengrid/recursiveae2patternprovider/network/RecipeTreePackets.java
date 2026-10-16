package com.lumengrid.recursiveae2patternprovider.network;

import com.lumengrid.recursiveae2patternprovider.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

public final class RecipeTreePackets {
    private RecipeTreePackets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(RecursiveAE2PatternProvider.MODID, name));
    }
    private static int bounded(int n, int max) {
        if (n < 0 || n > max) throw new IllegalArgumentException("Invalid recipe tree size");
        return n;
    }
    private static Set<String> readIds(RegistryFriendlyByteBuf buf) {
        int count = bounded(buf.readVarInt(), PatternRecipeRules.MAX_EXCLUSIONS);
        Set<String> ids = new TreeSet<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(PatternRecipeRules.MAX_ID_LENGTH);
            if (!PatternRecipeRules.valid(id) || !ids.add(id)) throw new IllegalArgumentException("Invalid recipe ID");
        }
        return Set.copyOf(ids);
    }
    private static void writeIds(RegistryFriendlyByteBuf buf, Set<String> ids) {
        buf.writeVarInt(bounded(ids.size(), PatternRecipeRules.MAX_EXCLUSIONS));
        for (String id : new TreeSet<>(ids)) {
            if (!PatternRecipeRules.valid(id)) throw new IllegalArgumentException("Invalid recipe ID");
            buf.writeUtf(id, PatternRecipeRules.MAX_ID_LENGTH);
        }
    }
    public record Open(UUID session, RecipeTree tree, Set<String> excluded) implements CustomPacketPayload {
        public static final Type<Open> TYPE = RecipeTreePackets.type("recipe_tree_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = new StreamCodec<>() {
            public Open decode(RegistryFriendlyByteBuf buf) {
                var id = buf.readUUID();
                boolean truncated = buf.readBoolean();
                int count = bounded(buf.readVarInt(), RecipeTree.MAX_NODES);
                List<RecipeTree.Node> nodes = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int parent = buf.readVarInt();
                    if ((i == 0 && parent != -1) || (i > 0 && (parent < 0 || parent >= i)))
                        throw new IllegalArgumentException("Invalid tree parent");
                    boolean recipe = buf.readBoolean();
                    var item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    String recipeId = buf.readUtf(PatternRecipeRules.MAX_ID_LENGTH);
                    if (recipe && !PatternRecipeRules.valid(recipeId)) throw new IllegalArgumentException("Invalid recipe ID");
                    int note = bounded(buf.readVarInt(), 4);
                    List<ItemStack> grid = new ArrayList<>();
                    if (recipe) for (int j = 0; j < 9; j++) grid.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    nodes.add(new RecipeTree.Node(parent, recipe, item, recipeId, grid, note));
                }
                return new Open(id, new RecipeTree(List.copyOf(nodes), truncated), readIds(buf));
            }
            public void encode(RegistryFriendlyByteBuf buf, Open packet) {
                buf.writeUUID(packet.session());
                buf.writeBoolean(packet.tree().truncated());
                buf.writeVarInt(bounded(packet.tree().nodes().size(), RecipeTree.MAX_NODES));
                for (var node : packet.tree().nodes()) {
                    buf.writeVarInt(node.parent()); buf.writeBoolean(node.recipe());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, node.item());
                    buf.writeUtf(node.recipeId(), PatternRecipeRules.MAX_ID_LENGTH);
                    buf.writeVarInt(bounded(node.note(), 4));
                    if (node.recipe()) {
                        if (node.grid().size() != 9) throw new IllegalArgumentException("Invalid recipe grid");
                        for (var stack : node.grid()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                    }
                }
                writeIds(buf, packet.excluded());
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Open packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.lumengrid.recursiveae2patternprovider.client.RecipeTreeScreen.open(packet));
        }
    }
    public record Save(UUID session, boolean apply, Set<String> excluded) implements CustomPacketPayload {
        public static final Type<Save> TYPE = RecipeTreePackets.type("recipe_tree_save");
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC = new StreamCodec<>() {
            public Save decode(RegistryFriendlyByteBuf buf) { return new Save(buf.readUUID(), buf.readBoolean(), readIds(buf)); }
            public void encode(RegistryFriendlyByteBuf buf, Save packet) {
                buf.writeUUID(packet.session()); buf.writeBoolean(packet.apply()); writeIds(buf, packet.excluded());
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Save packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer player) RecipeTreeSessions.save(player, packet); });
        }
    }
    public record Result(UUID session, boolean saved, String message) implements CustomPacketPayload {
        public static final Type<Result> TYPE = RecipeTreePackets.type("recipe_tree_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = new StreamCodec<>() {
            public Result decode(RegistryFriendlyByteBuf buf) { return new Result(buf.readUUID(), buf.readBoolean(), buf.readUtf(16)); }
            public void encode(RegistryFriendlyByteBuf buf, Result packet) {
                buf.writeUUID(packet.session()); buf.writeBoolean(packet.saved()); buf.writeUtf(packet.message(), 16);
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Result packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.lumengrid.recursiveae2patternprovider.client.RecipeTreeScreen.result(packet));
        }
    }
}
