package com.visualcrafting.screen;

import com.visualcrafting.block.VisualCraftingBlockEntity;
import com.visualcrafting.block.VisualCraftingBlockEntity.InfusingRecipe;
import com.visualcrafting.block.VisualCraftingBlockEntity.SavedRecipe;
import com.visualcrafting.network.ModMessages;
import com.visualcrafting.network.DimensionBiomesData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VisualCraftingScreen extends AbstractContainerScreen<VisualCraftingMenu> {

    // ================================================================
    // Tab & Mode Constants
    // ================================================================

    private static final int TAB_CRAFT = 0;
    private static final int TAB_INFUSE = 1;
    private static final int TAB_ORE = 2;
    private static final int TAB_FOOD = 3; // sends mode=5 to server

    private static final int[] TAB_MODE_MAP = {0, 1, 2, 5};

    // ================================================================
    // Texture Resources
    // ================================================================

    private static final ResourceLocation TAB_CRAFT_TEX =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "textures/gui/tab_crafting.png");
    private static final ResourceLocation TAB_INFUSE_TEX =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "textures/gui/tab_infusing.png");
    private static final ResourceLocation TAB_ORE_TEX =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "textures/gui/tab_ore.png");
    private static final ResourceLocation TAB_FOOD_TEX =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "textures/gui/tab_food.png");

    // ================================================================
    // Layout
    // ================================================================

    // imageWidth / imageHeight shadow parent's final fields - defined as non-final for custom size
    private int imageWidth = 256;
    private int imageHeight = 222;
    private static final int TAB_W = 38;
    private static final int TAB_H = 22;
    private static final int TAB_GAP = 2;

    // ================================================================
    // State
    // ================================================================

    private BlockPos blockPos;
    private List<SavedRecipe> recipes = new ArrayList<>();
    private List<InfusingRecipe> infusingRecipes = new ArrayList<>();
    private int currentTab = TAB_CRAFT;
    private int tier = 0;
    private int format = 0;
    private boolean isShaped = true;

    // Scrolling
    private int recipeScrollOffset;
    private int infusingScrollOffset;
    private static final int MAX_VISIBLE_RECIPES = 9;
    private static final int MAX_VISIBLE_INFUSING = 9;

    // Craft sub-tab: 0=Add, 1=List, 2=Delete
    private int craftSubTab;

    // Tier dropdown
    private boolean tierDropdownOpen;
    private static final int[] TIER_OPTIONS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    // ================================================================
    // Ore Gen State (Mode 2)
    // ================================================================

    private int mode2Pct = 8;
    private int mode2MinY = -63;
    private int mode2MaxY = 319;
    private int mode2MineralPct = 80;
    private int mode2ByproductPct = 20;
    private int mode2CountMin = 1;
    private int mode2CountMax = 3;
    private int dimSelectedIdx;
    private DimensionBiomesData dimData;
    private List<String> selectedBiomes = new ArrayList<>();
    private DropdownWidget dimDropdown;
    private int biomeScrollOffset;

    // ================================================================
    // Food Editor State (Mode 5 / Tab Food)
    // ================================================================

    // Food Editor State (Tab Food)
    private String foodItemId = "";
    private int foodNutrition;
    private float foodSaturation;
    private int foodEatSeconds = 32;
    private boolean foodAlwaysEdible;

    private final List<FoodEffect> foodEffects = new ArrayList<>();
    private static final int MAX_EFFECTS = 16;

    // Food Editor Widgets
    private EditBox foodNutritionField;
    private EditBox foodSaturationField;
    private EditBox foodEatSecondsField;
    private EditBox mode5DurationEdit;
    private EditBox mode5PotionLevelEdit;
    private Checkbox mode5InfiniteCheckbox;
    private Button mode5BtnSave;
    private Button mode5BtnDelete;
    private Button mode5BtnConfig;
    private Button mode5BtnRefresh;

    private int mode5Duration = 600;
    private boolean mode5DurationInfinite;
    private int foodEffectScrollOffset;
    private FoodTabRenderer foodRenderer = new FoodTabRenderer();

    // Constants matching jar's VisualCraftingMenu layout
    private static final int GRID_START = 0;
    private static final int OUTPUT_SLOT = 81; // VisualCraftingMenu.getCraftSlotCount()

    // ================================================================
    // Management
    // ================================================================

    private GuiAdjustManager adjustManager;

    // ================================================================
    // Constructor
    // ================================================================

    public VisualCraftingScreen(VisualCraftingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.blockPos = menu.blockPos;
    }

    // ================================================================
    // Init
    // ================================================================

    @Override
    protected void init() {
        super.init();
        adjustManager = new GuiAdjustManager(this);

        VisualCraftingBlockEntity be = getMenuBlockEntity();
        if (be != null) {
            this.currentTab = modeToTab(be.getMode());
            this.tier = be.getTier();
            this.format = be.getFormat();
            this.recipes = new ArrayList<>(be.getRecipes());
            this.infusingRecipes = new ArrayList<>(be.getInfusingRecipes());
        } else {
            initWaitingForBe = true;
        }

        // Try load cached dim/biome data
        DimensionBiomesData cached = ModMessages.getCachedDimBiomesData();
        if (cached != null) dimData = cached;
        else {
            // Try load from local cache
            try {
                java.io.File cacheFile = new java.io.File(
                        Minecraft.getInstance().gameDirectory, "visualcrafting/dim_biomes_cache.json");
                if (cacheFile.exists()) {
                    String json = Files.readString(cacheFile.toPath());
                    dimData = DimensionBiomesData.fromJson(json);
                }
            } catch (Exception ignored) {}
        }

        rebuildWidgets();
    }

    // ================================================================
    // Widget Rebuild
    // ================================================================

    protected void rebuildWidgets() {
        this.clearWidgets();
        int gl = getGuiLeft();
        int gt = getGuiTop();
        buildTabButtons(gl, gt);
        buildModeContent(gl, gt);
        // Request dim/biome data for Ore tab
        if (currentTab == TAB_ORE && dimData == null) {
            PacketDistributor.sendToServer(new ModMessages.RequestDimBiomesPacket());
        }
        repositionSlots();
    }

    public int getGuiLeft() { return (this.width - this.imageWidth) / 2; }
    public int getGuiTop() { return (this.height - this.imageHeight) / 2; }

    // ================================================================
    // Tab Buttons (icon-based)
    // ================================================================

    private void buildTabButtons(int gl, int gt) {
        int tabY = gt - TAB_H;
        // Order: Craft, Infuse, Ore, Food
        int[] xs = {gl, gl + TAB_W + TAB_GAP, gl + 2 * (TAB_W + TAB_GAP), gl + 3 * (TAB_W + TAB_GAP)};
        String[] labels = {"Craft", "Infuse", "Ore Gen", "Food"};

        for (int i = 0; i < 4; i++) {
            final int tab = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal(labels[i]),
                    btn -> switchTab(tab)
            ).pos(xs[i], tabY).size(TAB_W, TAB_H).build());
        }
    }

    private void switchTab(int tab) {
        if (tab == currentTab) return;
        currentTab = tab;
        int mode = TAB_MODE_MAP[tab];
        PacketDistributor.sendToServer(new ModMessages.ModeUpdatePacket(blockPos, mode));
        recipeScrollOffset = 0;
        infusingScrollOffset = 0;
        tierDropdownOpen = false;

        // Request dim/biome data when entering Ore tab
        if (tab == TAB_ORE && dimData == null) {
            PacketDistributor.sendToServer(new ModMessages.RequestDimBiomesPacket());
        }
        // Load food from current slot when entering Food tab
        if (tab == TAB_FOOD) loadFoodFromSlot();

        rebuildWidgets();
    }

    private static int modeToTab(int mode) {
        return switch (mode) {
            case 1 -> TAB_INFUSE;
            case 2 -> TAB_ORE;
            case 5 -> TAB_FOOD;
            default -> TAB_CRAFT;
        };
    }

    // ================================================================
    // Mode Content Router
    // ================================================================

    private void buildModeContent(int gl, int gt) {
        switch (currentTab) {
            case TAB_CRAFT -> buildCraft(gl, gt);
            case TAB_INFUSE -> buildInfuse(gl, gt);
            case TAB_ORE -> buildOre(gl, gt);
            case TAB_FOOD -> buildFood(gl, gt);
        }
    }

    // ================================================================
    // Craft Tab (sub-tabs: Add / List / Delete)
    // ================================================================

    private void buildCraft(int gl, int gt) {
        // Sub-tab buttons
        String[] subLabels = {"Add", "List", "Delete"};
        int subY = gt + 2;
        for (int i = 0; i < 3; i++) {
            final int sub = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal(subLabels[i]),
                    btn -> { craftSubTab = sub; recipeScrollOffset = 0; rebuildWidgets(); }
            ).pos(gl + 10 + i * 46, subY).size(42, 16).build());
        }

        if (craftSubTab == 0) buildCraftAdd(gl, gt);
        else if (craftSubTab == 1) buildCraftList(gl, gt);
        else buildCraftDelete(gl, gt);
    }

    private void buildCraftAdd(int gl, int gt) {
        // Shaped / Shapeless
        this.addRenderableWidget(Button.builder(
                Component.literal(isShaped ? "[Shaped]" : "Shaped"),
                btn -> { isShaped = true; rebuildWidgets(); }
        ).pos(gl + 15, gt + 52).size(64, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal(!isShaped ? "[Shapeless]" : "Shapeless"),
                btn -> { isShaped = false; rebuildWidgets(); }
        ).pos(gl + 84, gt + 52).size(74, 20).build());

        // Add Recipe
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Recipe"),
                btn -> addRecipe()
        ).pos(gl + 118, gt + 32).size(54, 20).build());

        // Format toggle
        this.addRenderableWidget(Button.builder(
                Component.literal(format == 0 ? "KubeJS" : "CRT"),
                btn -> toggleFormat()
        ).pos(gl + 200, gt + 2).size(44, 16).build());

        // Tier dropdown
        this.addRenderableWidget(Button.builder(
                Component.literal("T" + tier),
                btn -> { tierDropdownOpen = !tierDropdownOpen; }
        ).pos(gl + 215, gt + 21).size(30, 14).build());
    }

    private void addRecipe() {
        ItemStack output = menu.getSlot(OUTPUT_SLOT).getItem();
        if (output.isEmpty()) return;
        List<ItemStack> ingredients = new ArrayList<>();
        int gridSize = 81; // MAX_GRID
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int idx = col + row * 9;
                ingredients.add(menu.getSlot(idx).getItem().copy());
            }
        }
        PacketDistributor.sendToServer(
                new ModMessages.AddRecipePacket(blockPos, isShaped, output.copy(), ingredients));
    }

    private void buildCraftList(int gl, int gt) {
        // Scroll buttons
        this.addRenderableWidget(Button.builder(
                Component.literal("▲"), btn -> { if (recipeScrollOffset > 0) recipeScrollOffset--; })
                .pos(gl + 235, gt + 22).size(16, 12).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("▼"), btn -> {
                    if (recipeScrollOffset < Math.max(0, recipes.size() - MAX_VISIBLE_RECIPES)) recipeScrollOffset++;
                }).pos(gl + 235, gt + 22 + MAX_VISIBLE_RECIPES * 12).size(16, 12).build());
    }

    private void buildCraftDelete(int gl, int gt) {
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete by Output"),
                btn -> deleteByOutput()
        ).pos(gl + 30, gt + 32).size(120, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete Selected"),
                btn -> deleteSelected()
        ).pos(gl + 30, gt + 56).size(120, 20).build());
    }

    private void deleteByOutput() {
        ItemStack output = menu.getSlot(OUTPUT_SLOT).getItem();
        if (output.isEmpty()) return;
        PacketDistributor.sendToServer(new ModMessages.DeleteByOutputPacket(blockPos, output.copy()));
    }

    private void deleteSelected() {
        if (recipes.isEmpty()) return;
        PacketDistributor.sendToServer(new ModMessages.RemoveRecipePacket(blockPos, 0));
    }

    private void toggleFormat() {
        format = (format == 0) ? 1 : 0;
        PacketDistributor.sendToServer(new ModMessages.FormatUpdatePacket(blockPos, format));
        rebuildWidgets();
    }

    // ================================================================
    // Infuse Tab
    // ================================================================

    private void buildInfuse(int gl, int gt) {
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Infuse"),
                btn -> addInfusingRecipe()
        ).pos(gl + 15, gt + 75).size(80, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete Infuse"),
                btn -> deleteInfusingByOutput()
        ).pos(gl + 100, gt + 75).size(80, 20).build());

        // Infusing list scroll
        this.addRenderableWidget(Button.builder(
                Component.literal("▲"), btn -> { if (infusingScrollOffset > 0) infusingScrollOffset--; })
                .pos(gl + 235, gt + 22).size(16, 12).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("▼"), btn -> {
                    if (infusingScrollOffset < Math.max(0, infusingRecipes.size() - MAX_VISIBLE_INFUSING))
                        infusingScrollOffset++;
                }).pos(gl + 235, gt + 22 + MAX_VISIBLE_INFUSING * 12).size(16, 12).build());

        repositionSlots();
    }

    private void addInfusingRecipe() {
        ItemStack inputA = menu.getSlot(0).getItem();
        ItemStack inputB = menu.getSlot(1).getItem();
        ItemStack output = menu.getSlot(OUTPUT_SLOT).getItem();
        if (output.isEmpty()) return;
        PacketDistributor.sendToServer(new ModMessages.AddInfusingRecipePacket(blockPos,
                inputA.isEmpty() ? ItemStack.EMPTY : inputA.copy(),
                inputB.isEmpty() ? ItemStack.EMPTY : inputB.copy(),
                output.copy(), 1));
    }

    private void deleteInfusingByOutput() {
        ItemStack output = menu.getSlot(OUTPUT_SLOT).getItem();
        if (output.isEmpty()) return;
        PacketDistributor.sendToServer(new ModMessages.DeleteInfusingByOutputPacket(blockPos, output.copy()));
    }

// 在VisualCraftingScreen 类中添加

private void repositionSlots() {
    if (currentTab != TAB_INFUSE) return;
    final int INPUT_A_X = 30, INPUT_A_Y = 30;
    final int INPUT_B_X = 30, INPUT_B_Y = 100;
    final int OUTPUT_X = 200, OUTPUT_Y = 60;
    NonNullList<Slot> slots = menu.slots;
    if (slots == null || slots.isEmpty()) return;
    try {
        java.lang.reflect.Field fx = Slot.class.getDeclaredField("x");
        java.lang.reflect.Field fy = Slot.class.getDeclaredField("y");
        fx.setAccessible(true);
        fy.setAccessible(true);
        if (slots.size() > 0) {
            fx.setInt(slots.get(0), INPUT_A_X);
            fy.setInt(slots.get(0), INPUT_A_Y);
        }
        if (slots.size() > 1) {
            fx.setInt(slots.get(1), INPUT_B_X);
            fy.setInt(slots.get(1), INPUT_B_Y);
        }
        int outputIndex = OUTPUT_SLOT;
        if (slots.size() > outputIndex) {
            fx.setInt(slots.get(outputIndex), OUTPUT_X);
            fy.setInt(slots.get(outputIndex), OUTPUT_Y);
        }
    } catch (Exception ignored) {}
}

    // ================================================================
    // Ore Gen Tab (Mode 2)
    // ================================================================

    private void buildOre(int gl, int gt) {
        // Initialize dimension dropdown
        if (dimDropdown == null && dimData != null && !dimData.dimIds.isEmpty()) {
            dimDropdown = new DropdownWidget(gl + 100, gt + 22, 140, 14,
                    dimData.dimIds, dimSelectedIdx, idx -> dimSelectedIdx = idx);
        }

        // Pct slider - just use buttons
        this.addRenderableWidget(Button.builder(
                Component.literal("-"), btn -> { mode2Pct = Math.max(1, mode2Pct - 1); })
                .pos(gl + 100, gt + 42).size(16, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("+"), btn -> { mode2Pct = Math.min(100, mode2Pct + 1); })
                .pos(gl + 180, gt + 42).size(16, 16).build());

        // MinY / MaxY
        this.addRenderableWidget(Button.builder(
                Component.literal("-8"), btn -> { mode2MinY = Math.max(-64, mode2MinY - 8); })
                .pos(gl + 100, gt + 62).size(24, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("+8"), btn -> { mode2MinY = Math.min(mode2MaxY - 8, mode2MinY + 8); })
                .pos(gl + 154, gt + 62).size(24, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("-8"), btn -> { mode2MaxY = Math.max(mode2MinY + 8, mode2MaxY - 8); })
                .pos(gl + 100, gt + 82).size(24, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("+8"), btn -> { mode2MaxY = Math.min(320, mode2MaxY + 8); })
                .pos(gl + 154, gt + 82).size(24, 16).build());

        // Mineral/Byproduct pct
        this.addRenderableWidget(Button.builder(
                Component.literal("-"), btn -> { mode2MineralPct = Math.max(0, mode2MineralPct - 5); })
                .pos(gl + 100, gt + 102).size(16, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("+"), btn -> { mode2MineralPct = Math.min(100 - mode2ByproductPct, mode2MineralPct + 5); })
                .pos(gl + 180, gt + 102).size(16, 16).build());

        // Count range
        this.addRenderableWidget(Button.builder(
                Component.literal("-"), btn -> { mode2CountMin = Math.max(1, mode2CountMin - 1); })
                .pos(gl + 100, gt + 122).size(16, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("+"), btn -> { mode2CountMin = Math.min(mode2CountMax, mode2CountMin + 1); })
                .pos(gl + 154, gt + 122).size(16, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("-"), btn -> { mode2CountMax = Math.max(mode2CountMin, mode2CountMax - 1); })
                .pos(gl + 100, gt + 142).size(16, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("+"), btn -> { mode2CountMax = Math.min(64, mode2CountMax + 1); })
                .pos(gl + 154, gt + 142).size(16, 16).build());

        // Biome list
        if (dimData != null) {
            String selDim = dimData.dimIds.get(dimSelectedIdx);
            List<String> biomes = dimData.biomesByDim.getOrDefault(selDim, dimData.allBiomes);

            // Biome scroll
            int bListY = gt + 22;
            int bListX = gl + 100;
            for (int i = 0; i < Math.min(biomes.size(), 10); i++) {
                int idx = i + biomeScrollOffset;
                if (idx >= biomes.size()) break;
                String biomeId = biomes.get(idx);
                boolean sel = selectedBiomes.contains(biomeId);
                final int bi = idx;
                this.addRenderableWidget(Button.builder(
                        Component.literal((sel ? "[✓] " : "[  ] ") + shortenId(biomeId)),
                        btn -> {
                            if (selectedBiomes.contains(biomeId))
                                selectedBiomes.remove(biomeId);
                            else
                                selectedBiomes.add(biomeId);
                            rebuildWidgets();
                        }
                ).pos(bListX, bListY + i * 14).size(140, 14).build());
            }
        }
    }

    // ================================================================
    // Food Tab (Mode 5)
    // ================================================================

    private void loadFoodFromSlot() {
        ItemStack stack = ItemStack.EMPTY;
        if (menu != null) stack = menu.getSlot(OUTPUT_SLOT).getItem();
        if (stack.isEmpty() && Minecraft.getInstance().player != null)
            stack = Minecraft.getInstance().player.getMainHandItem();
        if (stack.isEmpty()) return;

        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;

        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        foodItemId = rl.toString();
        foodNutrition = food.nutrition();
        foodSaturation = food.saturation();
        foodAlwaysEdible = food.canAlwaysEat();
        foodEatSeconds = 32;

        foodEffects.clear();
        for (FoodProperties.PossibleEffect pe : food.effects()) {
            String descId = pe.effect().getDescriptionId();
            String effectId = "minecraft:speed";
            if (descId != null && descId.startsWith("effect.")) {
                String rest = descId.substring("effect.".length());
                int ci = rest.indexOf('.');
                String ns = ci > 0 ? rest.substring(0, ci) : "minecraft";
                String p = ci > 0 ? rest.substring(ci + 1) : rest;
                effectId = ns + ":" + p;
            }
            foodEffects.add(new FoodEffect(effectId, 600, 0, pe.probability()));
        }
    }

    // Override to prevent base jar's initMode5Widgets from running
    // Food tab widgets are managed by buildFood + FoodTabRenderer
    protected void initMode5Widgets() {}

    private void buildFood(int gl, int gt) {
        // Hide grid slots 0-79, keep 80 (food) and 81 (return) for food editor
        NonNullList<Slot> slots = menu.slots;
        try {
            java.lang.reflect.Field fx = Slot.class.getDeclaredField("x");
            java.lang.reflect.Field fy = Slot.class.getDeclaredField("y");
            fx.setAccessible(true);
            fy.setAccessible(true);
            for (int i = 0; i < 80 && i < slots.size(); i++) {
                Slot s = slots.get(i);
                fx.setInt(s, -2000);
                fy.setInt(s, -2000);
            }
            // Food slot (slot 80) at (93, 16)
            Slot foodSlot = slots.get(80);
            fx.setInt(foodSlot, 93);
            fy.setInt(foodSlot, 16);
            // Return slot (slot 81) at (93, 39)
            Slot returnSlot = slots.get(81);
            fx.setInt(returnSlot, 93);
            fy.setInt(returnSlot, 39);
            // Arrange remaining slots (player inventory) as 9-wide grid at bottom
            for (int i = 82; i < slots.size(); i++) {
                Slot s = slots.get(i);
                int col = (i - 82) % 9;
                int row = (i - 82) / 9;
                fx.setInt(s, 8 + col * 18);
                fy.setInt(s, imageHeight - 83 + row * 18);
            }
        } catch (Exception ignored) {}

        // Update FoodTabRenderer position (potion data auto-loaded)
        foodRenderer.updatePosition(gl, gt);

        // --- Left column buttons ---
        mode5BtnSave = this.addRenderableWidget(Button.builder(
                Component.literal("保存"),
                btn -> onFoodSave()
        ).pos(gl + 8, gt + 12).size(46, 16).build());

        mode5BtnDelete = this.addRenderableWidget(Button.builder(
                Component.literal("删除"),
                btn -> onFoodDelete()
        ).pos(gl + 8, gt + 31).size(46, 16).build());

        mode5BtnConfig = this.addRenderableWidget(Button.builder(
                Component.literal("配置"),
                btn -> onFoodConfig()
        ).pos(gl + 8, gt + 50).size(46, 16).build());

        mode5BtnRefresh = this.addRenderableWidget(Button.builder(
                Component.literal("刷新"),
                btn -> { loadFoodFromSlot(); rebuildWidgets(); }
        ).pos(gl + 8, gt + 69).size(46, 16).build());

        // --- EditBoxes ---
        // Hunger (饥饿值
        foodNutritionField = new EditBox(font, gl + 144, gt + 18, 24, 16,
                Component.literal("Hunger"));
        foodNutritionField.setValue(String.valueOf(foodNutrition));
        foodNutritionField.setFilter(s -> s.matches("\\d*"));
        foodNutritionField.setResponder(val -> {
            if (!val.isEmpty()) foodNutrition = Integer.parseInt(val);
        });
        addCustomWidget(foodNutritionField);

        // Saturation (饱和度
        foodSaturationField = new EditBox(font, gl + 204, gt + 18, 24, 16,
                Component.literal("Sat"));
        foodSaturationField.setValue(String.format("%.1f", foodSaturation));
        foodSaturationField.setResponder(val -> {
            try { foodSaturation = Float.parseFloat(val); } catch (NumberFormatException ignored) {}
        });
        addCustomWidget(foodSaturationField);

        // Duration (时长)
        mode5DurationEdit = new EditBox(font, gl + 144, gt + 41, 24, 16,
                Component.literal("Duration"));
        mode5DurationEdit.setValue(String.valueOf(mode5Duration));
        mode5DurationEdit.setFilter(s -> s.matches("\\d*"));
        mode5DurationEdit.setResponder(val -> {
            if (!val.isEmpty()) mode5Duration = Integer.parseInt(val);
        });
        addCustomWidget(mode5DurationEdit);

        // Potion Level (等级)
        mode5PotionLevelEdit = new EditBox(font, gl + 204, gt + 64, 24, 16,
                Component.literal("Level"));
        mode5PotionLevelEdit.setValue("1");
        mode5PotionLevelEdit.setFilter(s -> s.matches("\\d*"));
        addCustomWidget(mode5PotionLevelEdit);

        // Eat Seconds (食用秒数)
        foodEatSecondsField = new EditBox(font, gl + 93, gt + 88, 24, 16,
                Component.literal("EatSec"));
        foodEatSecondsField.setValue(String.valueOf(foodEatSeconds));
        foodEatSecondsField.setFilter(s -> s.matches("\\d*"));
        foodEatSecondsField.setResponder(val -> {
            if (!val.isEmpty()) foodEatSeconds = Integer.parseInt(val);
        });
        addCustomWidget(foodEatSecondsField);

        // --- Potion dropdown (药效) —handled by FoodTabRenderer ---

        // --- Infinite checkbox (无限) ---
        mode5InfiniteCheckbox = this.addRenderableWidget(
                Checkbox.builder(Component.literal("无限"), font)
                        .pos(gl + 169, gt + 46)
                        .selected(mode5DurationInfinite)
                        .onValueChange((cb, checked) -> mode5DurationInfinite = checked)
                        .build());

        // Generate Script button
        this.addRenderableWidget(Button.builder(
                Component.literal("Generate Script"),
                btn -> generateFoodScript()
        ).pos(gl + 120, gt + 125).size(100, 20).build());
    }

    private void generateFoodScript() {
        if (foodItemId.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Path kubejsDir = mc.gameDirectory.toPath().resolve("kubejs").resolve("startup_scripts");
        Path outputFile = kubejsDir.resolve("visualcrafting_food.js");

        StringBuilder sb = new StringBuilder();
        sb.append("// VisualCrafting Food Script —auto-generated\n");
        sb.append("// ").append(java.time.LocalDateTime.now()).append("\n\n");
        sb.append("StartupEvents.registry('item', event => {\n");
        sb.append("    event.create('").append(foodItemId).append("')\n");
        sb.append("        .food(food => {\n");
        sb.append("            food.hunger(").append(foodNutrition).append(")\n");
        sb.append("                .saturation(").append(foodSaturation).append(")\n");

        for (FoodEffect fe : foodEffects) {
            sb.append("                .effect('").append(fe.effectId).append("', ")
                    .append(fe.duration).append(", ").append(fe.amplifier).append(", ")
                    .append(fe.probability).append(")\n");
        }
        // Auto-detect return item from slot 81
        Slot returnSlot = menu.getSlot(OUTPUT_SLOT);
        ItemStack returnStack = returnSlot.getItem();
        if (!returnStack.isEmpty()) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(returnStack.getItem());
            sb.append("                .craftingRemainingItem('").append(rl.toString()).append("')\n");
        } else {
            sb.append("                .craftingRemainingItem('minecraft:air')\n");
        }
        sb.append("        })\n");
        sb.append("})\n");

        try {
            Files.createDirectories(kubejsDir);
            Files.writeString(outputFile, sb.toString());
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("Script written to kubejs/startup_scripts/visualcrafting_food.js"), false);
        } catch (IOException e) {
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("Failed to write script: " + e.getMessage()), false);
        }
    }

    private void onFoodSave() {
        if (minecraft == null || minecraft.player == null) return;
        String effectId = foodRenderer.getSelectedPotionId();
        int level = 0;
        try { level = Integer.parseInt(mode5PotionLevelEdit != null ? mode5PotionLevelEdit.getValue() : "0"); }
        catch (NumberFormatException ignored) {}
        foodEffects.clear();
        foodEffects.add(new FoodEffect(effectId, mode5Duration, level, 1.0F));
        generateFoodScript();
    }

    private void onFoodDelete() {
        foodItemId = "";
        foodNutrition = 0;
        foodSaturation = 0;
        foodEatSeconds = 32;
        foodEffects.clear();
        mode5Duration = 600;
        mode5DurationInfinite = false;
        rebuildWidgets();
        if (minecraft != null && minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("Food data cleared"), false);
    }

    private void onFoodConfig() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.displayClientMessage(Component.literal("Config: open food recipe configuration"), false);
    }

    // ================================================================
    // Rendering
    // ================================================================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int gl = getGuiLeft();
        int gt = getGuiTop();

        // Semi-transparent background
        g.fill(gl, gt, gl + imageWidth, gt + imageHeight, 0xC0101010);
        g.fill(gl + 1, gt + 1, gl + imageWidth - 1, gt + imageHeight - 1, 0xC02C2C2C);

        // Tab icons
        drawTabIcons(g, gl, gt);

        // Mode-specific BG
        switch (currentTab) {
            case TAB_CRAFT -> drawCraftBg(g, gl, gt, mouseX, mouseY);
            case TAB_INFUSE -> drawInfuseBg(g, gl, gt);
            case TAB_ORE -> drawOreBg(g, gl, gt);
            case TAB_FOOD -> drawFoodBg(g, gl, gt);
        }

        // Tier dropdown
        if (tierDropdownOpen) drawTierDropdown(g, gl, gt, mouseX, mouseY);
    }

    private void drawTabIcons(GuiGraphics g, int gl, int gt) {
        int tabY = gt - TAB_H;
        // Highlight active tab
        int activeX = gl + currentTab * (TAB_W + TAB_GAP);
        g.fill(activeX, tabY, activeX + TAB_W, tabY + TAB_H, 0x44FFFF00);

        // Draw tab icons (using textures)
        ResourceLocation[] texs = {TAB_CRAFT_TEX, TAB_INFUSE_TEX, TAB_ORE_TEX, TAB_FOOD_TEX};
        for (int i = 0; i < 4; i++) {
            int tx = gl + i * (TAB_W + TAB_GAP) + 9;
            int ty = tabY + 3;
            g.blit(texs[i], tx, ty, 0, 0, 16, 16, 16, 16);
        }
    }

    private void drawCraftBg(GuiGraphics g, int gl, int gt, int mx, int my) {
        if (craftSubTab == 0) {
            g.drawString(font, "Output", gl + 126, gt + 18, 0xFFAA00);
            g.fill(gl + 124, gt + 36, gl + 142, gt + 54, 0x44FFFF00);
            g.drawString(font, isShaped ? "Shaped" : "Shapeless", gl + 30, gt + 62, 0xAAAAAA);
            g.drawString(font, "Tier: " + tier, gl + 200, gt + 40, 0xAAAAAA);
            g.drawString(font, "Format: " + (format == 0 ? "KubeJS" : "CRT"), gl + 200, gt + 52, 0xAAAAAA);
        } else if (craftSubTab == 1 || craftSubTab == 2) {
            int x = gl + 15, y = gt + 22, w = 214;
            g.fill(x, y, x + w, y + MAX_VISIBLE_RECIPES * 12, 0x88000000);
            int ve = Math.min(recipeScrollOffset + MAX_VISIBLE_RECIPES, recipes.size());
            for (int i = recipeScrollOffset; i < ve; i++) {
                int dy = y + (i - recipeScrollOffset) * 12;
                SavedRecipe r = recipes.get(i);
                if (r.banned) g.fill(x, dy, x + w, dy + 11, 0x44FF0000);
                String lbl = (r.shaped ? "[S] " : "[U] ") + r.result.getHoverName().getString();
                g.drawString(font, lbl, x + 4, dy + 1, r.banned ? 0xFF6666 : 0xFFFFFF);
            }
        }
    }

    private void drawInfuseBg(GuiGraphics g, int gl, int gt) {
        g.drawString(font, "Input A", gl + 28, gt + 8, 0xAAAAAA);
        g.drawString(font, "Input B", gl + 28, gt + 40, 0xAAAAAA);
        g.drawString(font, "Output", gl + 122, gt + 24, 0xAAAAAA);

        int x = gl + 160, y = gt + 22, w = 85;
        g.fill(x, y, x + w, y + MAX_VISIBLE_INFUSING * 12, 0x88000000);
        int ve = Math.min(infusingScrollOffset + MAX_VISIBLE_INFUSING, infusingRecipes.size());
        for (int i = infusingScrollOffset; i < ve; i++) {
            int dy = y + (i - infusingScrollOffset) * 12;
            InfusingRecipe r = infusingRecipes.get(i);
            g.drawString(font, r.output.getHoverName().getString(), x + 2, dy + 1,
                    r.banned ? 0xFF6666 : 0xFFFFFF);
        }
    }

    private void drawOreBg(GuiGraphics g, int gl, int gt) {
        g.drawString(font, "Ore Generation", gl + 20, gt + 8, 0xFFD700);
        g.drawString(font, "Dimension:", gl + 20, gt + 24, 0xAAAAAA);

        // Render dropdown
        if (dimDropdown != null) dimDropdown.render(g, -1, -1, 0);

        g.drawString(font, "Spawn Chance: " + mode2Pct + "%", gl + 20, gt + 44, 0xCCCCCC);
        g.drawString(font, "Min Y: " + mode2MinY, gl + 20, gt + 64, 0xCCCCCC);
        g.drawString(font, "Max Y: " + mode2MaxY, gl + 20, gt + 84, 0xCCCCCC);
        g.drawString(font, "Mineral: " + mode2MineralPct + "%", gl + 20, gt + 104, 0xCCCCCC);
        g.drawString(font, "Byproduct: " + mode2ByproductPct + "%", gl + 20, gt + 124, 0xCCCCCC);
        g.drawString(font, "Count: " + mode2CountMin + "-" + mode2CountMax, gl + 20, gt + 144, 0xCCCCCC);
        g.drawString(font, "Biomes (" + selectedBiomes.size() + "):", gl + 20, gt + 164, 0xFFAA00);
    }

    private void drawFoodBg(GuiGraphics g, int gl, int gt) {
        foodRenderer.updatePosition(gl, gt);
        foodRenderer.drawBg(g);
    }

    private void drawTierDropdown(GuiGraphics g, int gl, int gt, int mx, int my) {
        int dx = gl + 215, dy = gt + 37;
        g.fill(dx, dy, dx + 35, dy + TIER_OPTIONS.length * 14 + 4, 0xCC333333);
        for (int i = 0; i < TIER_OPTIONS.length; i++) {
            int oy = dy + 2 + i * 14;
            boolean hover = mx >= dx && mx <= dx + 35 && my >= oy && my <= oy + 14;
            g.fill(dx, oy, dx + 35, oy + 14, hover ? 0xFF555555 : 0xFF333333);
            g.drawString(font, String.valueOf(TIER_OPTIONS[i]), dx + 4, oy + 3,
                    TIER_OPTIONS[i] == tier ? 0xFFFF55 : 0xCCCCCC);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    // Override to prevent base jar's renderMode5Extras from running
    // Food tab labels are drawn by drawFoodBg + FoodTabRenderer
    protected void renderMode5Extras(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);

        int gl = getGuiLeft(), gt = getGuiTop();

        // Recipe list tooltips
        if ((currentTab == TAB_CRAFT && (craftSubTab == 1 || craftSubTab == 2)) || currentTab == TAB_INFUSE) {
            int x = currentTab == TAB_INFUSE ? gl + 160 : gl + 15;
            int y = gt + 22;
            int w = currentTab == TAB_INFUSE ? 85 : 214;
            List<?> list = currentTab == TAB_INFUSE ? infusingRecipes : recipes;
            int vo = currentTab == TAB_INFUSE ? infusingScrollOffset : recipeScrollOffset;
            int maxV = currentTab == TAB_INFUSE ? MAX_VISIBLE_INFUSING : MAX_VISIBLE_RECIPES;

            int ve = Math.min(vo + maxV, list.size());
            for (int i = vo; i < ve; i++) {
                int dy = y + (i - vo) * 12;
                if (mx >= x && mx <= x + w && my >= dy && my <= dy + 11) {
                    if (currentTab == TAB_INFUSE) {
                        InfusingRecipe r = infusingRecipes.get(i);
                        List<Component> tt = new ArrayList<>();
                        tt.add(r.output.getHoverName());
                        if (r.banned) tt.add(Component.literal("Banned"));
                        g.renderComponentTooltip(font, tt, mx, my);
                    } else {
                        SavedRecipe r = recipes.get(i);
                        List<Component> tt = new ArrayList<>();
                        tt.add(Component.literal(r.shaped ? "Shaped" : "Shapeless"));
                        tt.add(r.result.getHoverName());
                        if (r.banned) tt.add(Component.literal("Banned"));
                        g.renderComponentTooltip(font, tt, mx, my);
                    }
                }
            }
        }
    }

    // ================================================================
    // Mouse Input
    // ================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gl = getGuiLeft(), gt = getGuiTop();

        // Dropdown clicks
        if (dimDropdown != null && currentTab == TAB_ORE) {
            if (dimDropdown.mouseClicked(mx, my, button)) return true;
        }
        if (foodRenderer.getPotionDropdown() != null && currentTab == TAB_FOOD) {
            if (foodRenderer.getPotionDropdown().mouseClicked(mx, my, button)) return true;
        }

        // Tier dropdown
        if (tierDropdownOpen) {
            int dx = gl + 215, dy = gt + 37;
            for (int i = 0; i < TIER_OPTIONS.length; i++) {
                int oy = dy + 2 + i * 14;
                if (mx >= dx && mx <= dx + 35 && my >= oy && my <= oy + 14) {
                    tier = TIER_OPTIONS[i];
                    PacketDistributor.sendToServer(new ModMessages.TierUpdatePacket(blockPos, tier));
                    tierDropdownOpen = false;
                    rebuildWidgets();
                    return true;
                }
            }
            tierDropdownOpen = false;
            return true;
        }

        // Right-click to remove recipe in list
        if (button == 1 && currentTab == TAB_CRAFT && (craftSubTab == 1 || craftSubTab == 2)) {
            int x = gl + 15, y = gt + 22;
            int ve = Math.min(recipeScrollOffset + MAX_VISIBLE_RECIPES, recipes.size());
            for (int i = recipeScrollOffset; i < ve; i++) {
                int dy = y + (i - recipeScrollOffset) * 12;
                if (mx >= x && mx <= x + 214 && my >= dy && my <= dy + 11) {
                    PacketDistributor.sendToServer(new ModMessages.RemoveRecipePacket(blockPos, i));
                    return true;
                }
            }
        }

        // Ghost slot handling
        Slot slot = this.hoveredSlot;
        if (slot != null && currentTab != TAB_ORE
                && slot.index >= GRID_START
                && slot.index <= OUTPUT_SLOT) {
            if (button == 0 && hasShiftDown()) {
                slot.set(ItemStack.EMPTY);
                return true;
            }
            ItemStack carried = this.menu.getCarried();
            if (!carried.isEmpty()) {
                slot.set(carried.copyWithCount(1));
                return true;
            }
            if (carried.isEmpty() && slot.hasItem()) {
                slot.set(ItemStack.EMPTY);
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int gl = getGuiLeft(), gt = getGuiTop();

        // Dim dropdown scroll
        if (dimDropdown != null && currentTab == TAB_ORE && dimDropdown.isOpen()) {
            if (dimDropdown.mouseScrolled(mx, my, sx, sy)) return true;
        }

        // Food potion dropdown scroll
        if (foodRenderer.getPotionDropdown() != null && currentTab == TAB_FOOD && foodRenderer.getPotionDropdown().isOpen()) {
            if (foodRenderer.getPotionDropdown().mouseScrolled(mx, my, sx, sy)) return true;
        }

        // Recipe list scroll
        if (currentTab == TAB_CRAFT && (craftSubTab == 1 || craftSubTab == 2)) {
            int x = gl + 15, y = gt + 22;
            if (mx >= x && mx <= x + 214 && my >= y && my <= y + MAX_VISIBLE_RECIPES * 12) {
                recipeScrollOffset = (int) Math.max(0, Math.min(
                        recipeScrollOffset - sy, Math.max(0, recipes.size() - MAX_VISIBLE_RECIPES)));
                return true;
            }
        }

        // Infusing list scroll
        if (currentTab == TAB_INFUSE) {
            int x = gl + 160, y = gt + 22;
            if (mx >= x && mx <= x + 85 && my >= y && my <= y + MAX_VISIBLE_INFUSING * 12) {
                infusingScrollOffset = (int) Math.max(0, Math.min(
                        infusingScrollOffset - sy, Math.max(0, infusingRecipes.size() - MAX_VISIBLE_INFUSING)));
                return true;
            }
        }

        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ================================================================
    // Key
    // ================================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && tierDropdownOpen) {
            tierDropdownOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ================================================================
    // Tick
    // ================================================================

    private boolean initWaitingForBe;

    @Override
    protected void containerTick() {
        super.containerTick();
        VisualCraftingBlockEntity be = getMenuBlockEntity();
        if (be == null) return;

        if (initWaitingForBe) {
            initWaitingForBe = false;
            this.currentTab = modeToTab(be.getMode());
            this.tier = be.getTier();
            this.format = be.getFormat();
            this.recipes = new ArrayList<>(be.getRecipes());
            this.infusingRecipes = new ArrayList<>(be.getInfusingRecipes());
            rebuildWidgets();
            return;
        }

        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.tickCount % 20 == 0) {
            int beMode = be.getMode();
            int beTab = modeToTab(beMode);
            if (beTab != currentTab) {
                currentTab = beTab;
                rebuildWidgets();
            }
            if (be.getTier() != tier) tier = be.getTier();
            if (be.getFormat() != format) format = be.getFormat();
        }
    }

    // ================================================================
    // Network Callbacks
    // ================================================================

    public void updateRecipes(List<SavedRecipe> newRecipes) {
        this.recipes = new ArrayList<>(newRecipes);
    }

    public void updateInfusingRecipes(List<InfusingRecipe> newRecipes) {
        this.infusingRecipes = new ArrayList<>(newRecipes);
    }

    public void applyDimBiomesData(DimensionBiomesData data) {
        this.dimData = data;
    }

    // ================================================================
    // Ghost Items (JEI / Infuse chem slot)
    // ================================================================

    private final Map<Integer, ItemStack> ghostItems = new HashMap<>();
    private ItemStack selectedChemical = ItemStack.EMPTY;

    public void setGhostItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < menu.slots.size()) {
            ghostItems.put(slot, stack.copyWithCount(1));
        }
    }

    private VisualCraftingBlockEntity getMenuBlockEntity() {
        if (this.minecraft == null || this.minecraft.player == null) return null;
        var level = this.minecraft.player.level();
        var be = level.getBlockEntity(((VisualCraftingMenu)menu).blockPos);
        return be instanceof VisualCraftingBlockEntity vcbe ? vcbe : null;
    }

    public ItemStack getGhostItem(int slot) {
        return ghostItems.getOrDefault(slot, ItemStack.EMPTY);
    }

    public int getMode() {
        VisualCraftingBlockEntity be = getMenuBlockEntity();
        return be != null ? be.getMode() : 0;
    }

    public int getChemSlotX() {
        // Infuse chem slot X = guiLeft + slot0 X offset (52) + 18
        return getGuiLeft() + 70;
    }

    public int getChemSlotY() {
        // Infuse chem slot Y —try guiTop + 50
        return getGuiTop() + 50;
    }

    public void setSelectedChemical(ItemStack stack) {
        this.selectedChemical = stack;
    }

    public ItemStack getSelectedChemical() {
        return selectedChemical;
    }

    // ================================================================
    // Public Accessors
    // ================================================================

    public BlockPos getBlockPos() { return blockPos; }
    public VisualCraftingMenu getVCraftMenu() { return menu; }

    @SuppressWarnings("unchecked")
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addCustomWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static String shortenId(String fullId) {
        int colon = fullId.indexOf(':');
        return colon >= 0 ? fullId.substring(colon + 1) : fullId;
    }

    // ================================================================
    // FoodEffect inner class
    // ================================================================

    public static class FoodEffect {
        public String effectId;
        public int duration;
        public int amplifier;
        public float probability;

        public FoodEffect() {}
        public FoodEffect(String effectId, int duration, int amplifier, float probability) {
            this.effectId = effectId;
            this.duration = duration;
            this.amplifier = amplifier;
            this.probability = probability;
        }
    }

    // ================================================================
    // Cleanup
    // ================================================================

    @Override
    public void removed() {
        super.removed();
        adjustManager.saveOffsetsToJar();
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
