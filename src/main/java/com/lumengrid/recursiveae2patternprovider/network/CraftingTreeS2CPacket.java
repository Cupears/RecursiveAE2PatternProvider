package com.lumengrid.recursiveae2patternprovider.network;

import com.lumengrid.recursiveae2patternprovider.CraftingTreeData;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.client.CraftingTreeClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: the crafting tree to display (or refresh) in the inspector overlay. */
public record CraftingTreeS2CPacket(int menuId, CraftingTreeData tree) implements CustomPacketPayload {

    private static final int MAX_NODES = 4096;

    public static final Type<CraftingTreeS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RecursiveAE2PatternProvider.MODID, "crafting_tree"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingTreeS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CraftingTreeS2CPacket decode(RegistryFriendlyByteBuf buf) {
            int menuId = buf.readVarInt();
            int itemCount = bounded(buf.readVarInt());
            List<CraftingTreeData.ItemNode> items = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                List<Integer> recipeIndices = readInts(buf);
                int depth = buf.readVarInt();
                items.add(new CraftingTreeData.ItemNode(icon, recipeIndices, depth));
            }
            int recipeCount = bounded(buf.readVarInt());
            List<CraftingTreeData.RecipeNode> recipes = new ArrayList<>(recipeCount);
            for (int i = 0; i < recipeCount; i++) {
                String recipeId = buf.readUtf();
                String outputItemId = buf.readUtf();
                int producingItemIndex = buf.readVarInt();
                List<ItemStack> grid = new ArrayList<>(9);
                for (int s = 0; s < 9; s++) {
                    grid.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                ItemStack output = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                boolean banned = buf.readBoolean();
                boolean bannedEverywhere = buf.readBoolean();
                boolean active = buf.readBoolean();
                boolean root = buf.readBoolean();
                List<Integer> ingredients = readInts(buf);
                recipes.add(new CraftingTreeData.RecipeNode(recipeId, outputItemId, producingItemIndex, grid, output,
                        banned, bannedEverywhere, active, root, ingredients));
            }
            return new CraftingTreeS2CPacket(menuId, new CraftingTreeData(items, recipes));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CraftingTreeS2CPacket packet) {
            buf.writeVarInt(packet.menuId());
            CraftingTreeData tree = packet.tree();
            buf.writeVarInt(tree.items().size());
            for (var item : tree.items()) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item.icon());
                writeInts(buf, item.recipeIndices());
                buf.writeVarInt(item.depth());
            }
            buf.writeVarInt(tree.recipes().size());
            for (var recipe : tree.recipes()) {
                buf.writeUtf(recipe.recipeId());
                buf.writeUtf(recipe.outputItemId());
                buf.writeVarInt(recipe.producingItemIndex());
                for (int s = 0; s < 9; s++) {
                    ItemStack stack = s < recipe.grid().size() ? recipe.grid().get(s) : ItemStack.EMPTY;
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                }
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, recipe.output());
                buf.writeBoolean(recipe.banned());
                buf.writeBoolean(recipe.bannedEverywhere());
                buf.writeBoolean(recipe.active());
                buf.writeBoolean(recipe.root());
                writeInts(buf, recipe.ingredientItemIndices());
            }
        }
    };

    private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> values) {
        buf.writeVarInt(values.size());
        for (int value : values) {
            buf.writeVarInt(value);
        }
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buf) {
        int count = bounded(buf.readVarInt());
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(buf.readVarInt());
        }
        return values;
    }

    private static int bounded(int count) {
        if (count < 0 || count > MAX_NODES) {
            throw new IllegalArgumentException("Invalid crafting tree size: " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftingTreeS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CraftingTreeClient.acceptTree(packet.menuId(), packet.tree()));
    }
}
