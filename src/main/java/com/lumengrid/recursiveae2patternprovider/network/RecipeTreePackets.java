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
    public record Expand(UUID session, int parent, int offset) implements CustomPacketPayload {
        public static final Type<Expand> TYPE = RecipeTreePackets.type("recipe_tree_expand");
        public static final StreamCodec<RegistryFriendlyByteBuf, Expand> CODEC = new StreamCodec<>() {
            public Expand decode(RegistryFriendlyByteBuf buf) { return new Expand(buf.readUUID(), bounded(buf.readVarInt(), Integer.MAX_VALUE), bounded(buf.readVarInt(), Integer.MAX_VALUE)); }
            public void encode(RegistryFriendlyByteBuf buf, Expand packet) { buf.writeUUID(packet.session()); buf.writeVarInt(packet.parent()); buf.writeVarInt(packet.offset()); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Expand packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer player) RecipeTreeSessions.expand(player, packet); });
        }
    }
    public record Branch(UUID session, int parent, int start, RecipeTree.Page page) implements CustomPacketPayload {
        public static final Type<Branch> TYPE = RecipeTreePackets.type("recipe_tree_branch");
        public static final StreamCodec<RegistryFriendlyByteBuf, Branch> CODEC = new StreamCodec<>() {
            public Branch decode(RegistryFriendlyByteBuf buf) {
                UUID session = buf.readUUID();
                int parent = bounded(buf.readVarInt(), Integer.MAX_VALUE);
                int start = bounded(buf.readVarInt(), Integer.MAX_VALUE);
                int offset = bounded(buf.readVarInt(), Integer.MAX_VALUE);
                boolean done = buf.readBoolean(); int note = bounded(buf.readVarInt(), 2);
                int count = bounded(buf.readVarInt(), RecipeTree.MAX_NODES);
                var nodes = new ArrayList<RecipeTree.Node>();
                if (parent < 1 || start <= parent || start > Integer.MAX_VALUE - count) throw new IllegalArgumentException("Invalid branch range");
                for (int i = 0; i < count; i++) {
                    int nodeParent = buf.readVarInt();
                    if (nodeParent != parent && (nodeParent < start || nodeParent >= start + i)) throw new IllegalArgumentException("Invalid branch parent");
                    boolean recipe = buf.readBoolean(); var item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    String id = buf.readUtf(PatternRecipeRules.MAX_ID_LENGTH);
                    if (recipe && !PatternRecipeRules.valid(id)) throw new IllegalArgumentException("Invalid recipe ID");
                    int nodeNote = bounded(buf.readVarInt(), 2);
                    var grid = new ArrayList<ItemStack>();
                    if (recipe) for (int slot = 0; slot < 9; slot++) grid.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    nodes.add(new RecipeTree.Node(nodeParent, recipe, item, id, grid, nodeNote));
                }
                return new Branch(session, parent, start, new RecipeTree.Page(List.copyOf(nodes), offset, done, note));
            }
            public void encode(RegistryFriendlyByteBuf buf, Branch packet) {
                buf.writeUUID(packet.session()); buf.writeVarInt(packet.parent()); buf.writeVarInt(packet.start());
                buf.writeVarInt(packet.page().nextOffset()); buf.writeBoolean(packet.page().done()); buf.writeVarInt(packet.page().note());
                buf.writeVarInt(bounded(packet.page().nodes().size(), RecipeTree.MAX_NODES));
                for (var node : packet.page().nodes()) {
                    buf.writeVarInt(node.parent()); buf.writeBoolean(node.recipe());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, node.item()); buf.writeUtf(node.recipeId(), PatternRecipeRules.MAX_ID_LENGTH); buf.writeVarInt(node.note());
                    if (node.recipe()) {
                        if (node.grid().size() != 9) throw new IllegalArgumentException("Invalid recipe grid");
                        for (var item : node.grid()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
                    }
                }
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Branch packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.lumengrid.recursiveae2patternprovider.client.RecipeTreeScreen.branch(packet));
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
