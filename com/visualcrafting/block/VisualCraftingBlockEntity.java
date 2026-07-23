package com.visualcrafting.block;

import com.visualcrafting.Config;
import com.visualcrafting.VisualCraftingTable;
import com.visualcrafting.recipe.RecipeRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class VisualCraftingBlockEntity extends BlockEntity {
    private static final int MAX_RECIPES = 100;

    private final List<SavedRecipe> recipes = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int tier = 0;
    private int format = 0;
    private int mode = 0;
    private final List<InfusingRecipe> infusingRecipes = new ArrayList<>();

    private void trimHistory() {
        while (history.size() > 100) {
            history.remove(history.size() - 1);
        }
    }

    public VisualCraftingBlockEntity(BlockPos pos, BlockState state) {
        super(VisualCraftingTable.VISUAL_CRAFTING_BE.get(), pos, state);
        this.format = Config.getFormatValue();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide()) {
            RecipeRegistrar.updateTableRecipes(this.worldPosition, this.recipes, this.format);
            RecipeRegistrar.updateInfusingTableRecipes(this.worldPosition, this.infusingRecipes, this.format);
        }
    }

    public List<SavedRecipe> getRecipes() {
        return recipes;
    }

    public List<String> getHistory() {
        return history;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getFormat() {
        return format;
    }

    public void setFormat(int format) {
        this.format = format;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public List<InfusingRecipe> getInfusingRecipes() {
        return infusingRecipes;
    }

    public void addRecipe(SavedRecipe recipe) {
        if (recipes.size() >= MAX_RECIPES) {
            System.err.println("[VisualCrafting] WARN: Recipe limit reached (100), cannot add more recipes");
            return;
        }
        recipes.add(0, recipe);
        history.add("+ " + recipe.result.getHoverName().getString());
        trimHistory();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void removeRecipe(int index) {
        if (index >= 0 && index < recipes.size()) {
            history.add("- " + recipes.get(index).result.getHoverName().getString());
            trimHistory();
            recipes.remove(index);
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void addInfusingRecipe(InfusingRecipe recipe) {
        if (infusingRecipes.size() >= MAX_RECIPES) {
            System.err.println("[VisualCrafting] WARN: Infusing recipe limit reached (100), cannot add more recipes");
            return;
        }
        infusingRecipes.add(0, recipe);
        history.add("+ [Infuse] " + recipe.output.getHoverName().getString());
        trimHistory();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void removeInfusingRecipe(int index) {
        if (index >= 0 && index < infusingRecipes.size()) {
            history.add("- [Infuse] " + infusingRecipes.get(index).output.getHoverName().getString());
            trimHistory();
            infusingRecipes.remove(index);
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Tier", tier);
        tag.putInt("Format", format);
        tag.putInt("Mode", mode);

        ListTag recipeList = new ListTag();
        for (SavedRecipe recipe : recipes) {
            recipeList.add(recipe.toTag(provider));
        }
        tag.put("Recipes", recipeList);

        ListTag infusingList = new ListTag();
        for (InfusingRecipe recipe : infusingRecipes) {
            infusingList.add(recipe.toTag(provider));
        }
        tag.put("InfusingRecipes", infusingList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        tier = tag.getInt("Tier");
        format = tag.getInt("Format");
        mode = tag.getInt("Mode");

        recipes.clear();
        ListTag recipeList = tag.getList("Recipes", 10);
        for (int i = 0; i < recipeList.size(); i++) {
            recipes.add(SavedRecipe.fromTag(recipeList.getCompound(i), provider));
        }

        infusingRecipes.clear();
        ListTag infusingList = tag.getList("InfusingRecipes", 10);
        for (int i = 0; i < infusingList.size(); i++) {
            infusingRecipes.add(InfusingRecipe.fromTag(infusingList.getCompound(i), provider));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static class SavedRecipe {
        public boolean shaped;
        public boolean banned;
        public ItemStack result;
        public List<ItemStack> ingredients;

        public SavedRecipe(boolean shaped, ItemStack result, List<ItemStack> ingredients) {
            this.shaped = shaped;
            this.banned = false;
            this.result = result;
            this.ingredients = ingredients;
        }

        public SavedRecipe(boolean shaped, boolean banned, ItemStack result, List<ItemStack> ingredients) {
            this.shaped = shaped;
            this.banned = banned;
            this.result = result;
            this.ingredients = ingredients;
        }

        public SavedRecipe(ItemStack result) {
            this.shaped = false;
            this.banned = true;
            this.result = result;
            this.ingredients = new ArrayList<>();
        }

        public CompoundTag toTag(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("shaped", shaped);
            tag.putBoolean("banned", banned);
            tag.put("result", result.save(provider));

            ListTag ingredientsTag = new ListTag();
            for (ItemStack stack : ingredients) {
                if (stack.isEmpty()) {
                    ingredientsTag.add(new CompoundTag());
                } else {
                    ingredientsTag.add(stack.save(provider));
                }
            }
            tag.put("ingredients", ingredientsTag);
            return tag;
        }

        public static SavedRecipe fromTag(CompoundTag tag, HolderLookup.Provider provider) {
            boolean shaped = tag.getBoolean("shaped");
            boolean banned = tag.getBoolean("banned");
            ItemStack result = ItemStack.parse(provider, tag.getCompound("result")).orElse(ItemStack.EMPTY);

            List<ItemStack> ingredients = new ArrayList<>();
            ListTag ingredientsTag = tag.getList("ingredients", 10);
            for (int i = 0; i < ingredientsTag.size(); i++) {
                ingredients.add(ItemStack.parse(provider, ingredientsTag.getCompound(i)).orElse(ItemStack.EMPTY));
            }
            return new SavedRecipe(shaped, banned, result, ingredients);
        }
    }

    public static class InfusingRecipe {
        public ItemStack inputA;
        public ItemStack inputB;
        public ItemStack output;
        public int infusionAmount;
        public boolean banned;

        public InfusingRecipe(ItemStack inputA, ItemStack inputB, ItemStack output, int infusionAmount) {
            this.inputA = inputA.copy();
            this.inputB = inputB.copy();
            this.output = output.copy();
            this.infusionAmount = infusionAmount;
            this.banned = false;
        }

        public InfusingRecipe(ItemStack output) {
            this.inputA = ItemStack.EMPTY;
            this.inputB = ItemStack.EMPTY;
            this.output = output.copy();
            this.infusionAmount = 0;
            this.banned = true;
        }

        public CompoundTag toTag(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("banned", banned);
            tag.putInt("infusionAmount", infusionAmount);
            tag.put("inputA", inputA.isEmpty() ? new CompoundTag() : inputA.save(provider));
            tag.put("inputB", inputB.isEmpty() ? new CompoundTag() : inputB.save(provider));
            tag.put("output", output.isEmpty() ? new CompoundTag() : output.save(provider));
            return tag;
        }

        public static InfusingRecipe fromTag(CompoundTag tag, HolderLookup.Provider provider) {
            boolean banned = tag.getBoolean("banned");
            int infusionAmount = tag.getInt("infusionAmount");
            ItemStack inputA = tag.contains("inputA")
                    ? ItemStack.parse(provider, tag.getCompound("inputA")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            ItemStack inputB = tag.contains("inputB")
                    ? ItemStack.parse(provider, tag.getCompound("inputB")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            ItemStack output = tag.contains("output")
                    ? ItemStack.parse(provider, tag.getCompound("output")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;

            if (banned) {
                InfusingRecipe recipe = new InfusingRecipe(output);
                recipe.inputA = inputA;
                recipe.inputB = inputB;
                recipe.infusionAmount = infusionAmount;
                return recipe;
            }
            return new InfusingRecipe(inputA, inputB, output, infusionAmount);
        }
    }
}
