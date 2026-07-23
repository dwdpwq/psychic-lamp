package com.visualcrafting.jei;

import com.visualcrafting.screen.ChemSlotData;
import com.visualcrafting.screen.MekanismIntegration;
import com.visualcrafting.screen.VisualCraftingMenu;
import com.visualcrafting.screen.VisualCraftingScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * JEI Ghost Ingredient handler (mode-aware).
 * Dispatches targets based on VisualCraftingScreen.getMode():
 *   0 = CRAFT: 9 grid slots + output slot
 *   1 = INFUSE: chem slot + quantity slot + output slot
 *   4 = mode-4: 3 input slots
 *   5 = FOOD:   2 input slots
 */
public class VisualCraftingGhostHandler implements IGhostIngredientHandler<VisualCraftingScreen> {

    private static final Field SLOT_X_FIELD;
    private static final Field SLOT_Y_FIELD;
    private static final Field LEFT_POS_FIELD;
    private static final Field TOP_POS_FIELD;

    static {
        Field sx = null, sy = null, lp = null, tp = null;
        try {
            sx = Slot.class.getDeclaredField("x");
            sx.setAccessible(true);
            sy = Slot.class.getDeclaredField("y");
            sy.setAccessible(true);
            lp = AbstractContainerScreen.class.getDeclaredField("leftPos");
            lp.setAccessible(true);
            tp = AbstractContainerScreen.class.getDeclaredField("topPos");
            tp.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SLOT_X_FIELD = sx;
        SLOT_Y_FIELD = sy;
        LEFT_POS_FIELD = lp;
        TOP_POS_FIELD = tp;
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(VisualCraftingScreen screen,
                                                ITypedIngredient<I> ingredient,
                                                boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        int mode = screen.getMode();

        switch (mode) {
            case 0 -> { // CRAFT
                for (int i = 0; i < 9; i++) {
                    targets.add(new GridSlotTarget<>(screen, i));
                }
                targets.add(new CraftOutputTarget<>(screen));
            }
            case 1 -> { // INFUSE
                targets.add(new ChemTarget<>(screen));
                targets.add(new InfuseQuantityTarget<>(screen));
                targets.add(new InfuseOutputTarget<>(screen));
            }
            case 4 -> {
                targets.add(new Mode4Slot0Target<>(screen));
                targets.add(new Mode4Slot1Target<>(screen));
                targets.add(new Mode4Slot2Target<>(screen));
            }
            case 5 -> { // FOOD
                targets.add(new Mode5Slot0Target<>(screen));
                targets.add(new Mode5Slot1Target<>(screen));
            }
        }

        return targets;
    }

    @Override
    public void onComplete() {
        // no-op
    }

    // ---- reflection helpers ----

    private static int getGuiLeft(VisualCraftingScreen screen) {
        try {
            if (LEFT_POS_FIELD != null) return LEFT_POS_FIELD.getInt(screen);
        } catch (Exception ignored) {}
        return 0;
    }

    private static int getGuiTop(VisualCraftingScreen screen) {
        try {
            if (TOP_POS_FIELD != null) return TOP_POS_FIELD.getInt(screen);
        } catch (Exception ignored) {}
        return 0;
    }

    static int getSlotScreenX(VisualCraftingScreen screen, int slotIndex) {
        int guiLeft = getGuiLeft(screen);
        Slot slot = ((VisualCraftingMenu) screen.getMenu()).getSlot(slotIndex);
        if (slot == null) return 0;
        try {
            return guiLeft + SLOT_X_FIELD.getInt(slot);
        } catch (Exception ignored) {
            return 0;
        }
    }

    static int getSlotScreenY(VisualCraftingScreen screen, int slotIndex) {
        int guiTop = getGuiTop(screen);
        Slot slot = ((VisualCraftingMenu) screen.getMenu()).getSlot(slotIndex);
        if (slot == null) return 0;
        try {
            return guiTop + SLOT_Y_FIELD.getInt(slot);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Handles accept for CRAFT mode grid slots.
     * Chemical ingredients → chem ghost; ItemStack → standard ghost item.
     */
    static <I> void handleMode0Accept(VisualCraftingScreen screen, int slotIndex, I ingredient) {
        if (MekanismIntegration.isChemicalIngredient(ingredient)) {
            CompoundTag tag = MekanismIntegration.convertChemicalToTag(ingredient);
            if (tag != null) {
                VisualCraftingMenu menu = (VisualCraftingMenu) screen.getMenu();
                menu.setChemGhost(slotIndex, tag);
                screen.setGhostItem(slotIndex, MekanismIntegration.createChemicalTagItem(tag));
            }
        } else if (ingredient instanceof ItemStack item) {
            screen.setGhostItem(slotIndex, item.copyWithCount(1));
        }
    }

    // ================================================================
    //  Inner target classes
    // ================================================================

    /** CRAFT grid slot target (slots 0-8), 16×16. */
    private static class GridSlotTarget<I> implements Target<I> {
        private final VisualCraftingScreen screen;
        private final int slotIndex;

        GridSlotTarget(VisualCraftingScreen screen, int slotIndex) {
            this.screen = screen;
            this.slotIndex = slotIndex;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(
                getSlotScreenX(screen, slotIndex),
                getSlotScreenY(screen, slotIndex),
                16, 16
            );
        }

        @Override
        public void accept(I ingredient) {
            handleMode0Accept(screen, slotIndex, ingredient);
        }
    }

    /** CRAFT output slot (slot 81), 16×16. */
    private static class CraftOutputTarget<I> implements Target<I> {
        private final VisualCraftingScreen screen;

        CraftOutputTarget(VisualCraftingScreen screen) {
            this.screen = screen;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(
                getSlotScreenX(screen, 81),
                getSlotScreenY(screen, 81),
                16, 16
            );
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(81).set(item.copy());
            }
        }
    }

    /** INFUSE chemical slot, 22×22 matching the rendered click area. */
    private static class ChemTarget<I> implements Target<I> {
        private final VisualCraftingScreen screen;

        ChemTarget(VisualCraftingScreen screen) {
            this.screen = screen;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(getSlotScreenX(screen, 0), screen.getChemSlotY(), 18, 18);
        }

        @Override
        public void accept(I ingredient) {
            if (MekanismIntegration.isChemicalIngredient(ingredient)) {
                ChemSlotData data = MekanismIntegration.buildChemSlotData(ingredient);
                if (data != null) {
                    VisualCraftingMenu menu = (VisualCraftingMenu) screen.getMenu();
                    menu.chemSlotData = data;
                    CompoundTag tag = MekanismIntegration.convertChemicalToTag(ingredient);
                    screen.setGhostItem(0, MekanismIntegration.createChemicalTagItem(tag));
                }
            }
        }
    }

    /** INFUSE quantity slot (slot 0), 16×16. */
    private static class InfuseQuantityTarget<I> implements Target<I> {
        private final VisualCraftingScreen screen;

        InfuseQuantityTarget(VisualCraftingScreen screen) {
            this.screen = screen;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(
                getSlotScreenX(screen, 0),
                getSlotScreenY(screen, 0),
                16, 16
            );
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).craftSlots.setItem(0, item.copy());
            }
        }
    }

    /** INFUSE output slot (slot 81), 16×16. */
    private static class InfuseOutputTarget<I> implements Target<I> {
        private final VisualCraftingScreen screen;

        InfuseOutputTarget(VisualCraftingScreen screen) {
            this.screen = screen;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(
                getSlotScreenX(screen, 81),
                getSlotScreenY(screen, 81),
                16, 16
            );
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(81).set(item.copy());
            }
        }
    }

    // ---- mode-4 targets (slots 0, 1, 2) ----

    private static class Mode4Slot0Target<I> implements Target<I> {
        private final VisualCraftingScreen screen;
        Mode4Slot0Target(VisualCraftingScreen screen) { this.screen = screen; }
        @Override
        public Rect2i getArea() {
            return new Rect2i(getSlotScreenX(screen, 0), getSlotScreenY(screen, 0), 16, 16);
        }
        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(0).set(item.copy());
            }
        }
    }

    private static class Mode4Slot1Target<I> implements Target<I> {
        private final VisualCraftingScreen screen;
        Mode4Slot1Target(VisualCraftingScreen screen) { this.screen = screen; }
        @Override
        public Rect2i getArea() {
            return new Rect2i(getSlotScreenX(screen, 1), getSlotScreenY(screen, 1), 16, 16);
        }
        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(1).set(item.copy());
            }
        }
    }

    private static class Mode4Slot2Target<I> implements Target<I> {
        private final VisualCraftingScreen screen;
        Mode4Slot2Target(VisualCraftingScreen screen) { this.screen = screen; }
        @Override
        public Rect2i getArea() {
            return new Rect2i(getSlotScreenX(screen, 2), getSlotScreenY(screen, 2), 16, 16);
        }
        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(2).set(item.copy());
            }
        }
    }

    // ---- mode-5 FOOD targets (slots 0, 1) ----

    private static class Mode5Slot0Target<I> implements Target<I> {
        private final VisualCraftingScreen screen;
        Mode5Slot0Target(VisualCraftingScreen screen) { this.screen = screen; }
        @Override
        public Rect2i getArea() {
            return new Rect2i(getSlotScreenX(screen, 0), getSlotScreenY(screen, 0), 16, 16);
        }
        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(0).set(item.copy());
            }
        }
    }

    private static class Mode5Slot1Target<I> implements Target<I> {
        private final VisualCraftingScreen screen;
        Mode5Slot1Target(VisualCraftingScreen screen) { this.screen = screen; }
        @Override
        public Rect2i getArea() {
            return new Rect2i(getSlotScreenX(screen, 1), getSlotScreenY(screen, 1), 16, 16);
        }
        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack item) {
                ((VisualCraftingMenu) screen.getMenu()).getSlot(1).set(item.copy());
            }
        }
    }
}
