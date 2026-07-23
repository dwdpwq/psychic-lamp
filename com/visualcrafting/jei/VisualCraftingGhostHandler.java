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

public class VisualCraftingGhostHandler implements IGhostIngredientHandler<VisualCraftingScreen> {

    private static final Field SLOT_X_FIELD;
    private static final Field SLOT_Y_FIELD;
    private static final Field LEFT_POS_FIELD;
    private static final Field TOP_POS_FIELD;

    static {
        Field xField = null;
        Field yField = null;
        Field leftField = null;
        Field topField = null;
        try {
            xField = Slot.class.getDeclaredField("x");
            xField.setAccessible(true);
            yField = Slot.class.getDeclaredField("y");
            yField.setAccessible(true);
            leftField = AbstractContainerScreen.class.getDeclaredField("leftPos");
            leftField.setAccessible(true);
            topField = AbstractContainerScreen.class.getDeclaredField("topPos");
            topField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SLOT_X_FIELD = xField;
        SLOT_Y_FIELD = yField;
        LEFT_POS_FIELD = leftField;
        TOP_POS_FIELD = topField;
    }

    private static int getSlotScreenX(VisualCraftingScreen screen, int slotIndex) {
        try {
            VisualCraftingMenu menu = screen.getMenu();
            Slot slot = menu.slots.get(slotIndex);
            int leftPos = LEFT_POS_FIELD.getInt(screen);
            return leftPos + SLOT_X_FIELD.getInt(slot);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getSlotScreenY(VisualCraftingScreen screen, int slotIndex) {
        try {
            VisualCraftingMenu menu = screen.getMenu();
            Slot slot = menu.slots.get(slotIndex);
            int topPos = TOP_POS_FIELD.getInt(screen);
            return topPos + SLOT_Y_FIELD.getInt(slot);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(VisualCraftingScreen screen, ITypedIngredient<I> ingredient,
                                               boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        int mode = screen.getMode();

        if (mode == 0) {
            // Mode 0 (Craft): 9 crafting grid slots + output
            for (int i = 0; i < 9; i++) {
                final int slotIdx = i;
                targets.add(new Target<>() {
                    public Rect2i getArea() {
                        return new Rect2i(getSlotScreenX(screen, slotIdx),
                                getSlotScreenY(screen, slotIdx), 16, 16);
                    }

                    public void accept(I ingredient) {
                        handleMode0Accept(screen, slotIdx, ingredient);
                    }
                });
            }
            targets.add(new Target<>() {
                public Rect2i getArea() {
                    return new Rect2i(getSlotScreenX(screen, 81),
                            getSlotScreenY(screen, 81), 16, 16);
                }

                public void accept(I ingredient) {
                    if (ingredient instanceof ItemStack stack) {
                        screen.getMenu().getSlot(81).set(stack.copy());
                    }
                }
            });
        } else if (mode == 1) {
            // Mode 1 (Infuse): chem ghost + input A + output
            targets.add(new Target<>() {
                public Rect2i getArea() {
                    return new Rect2i(screen.getChemSlotX(), screen.getChemSlotY(), 18, 18);
                }

                public void accept(I ingredient) {
                    if (MekanismIntegration.isChemicalIngredient(ingredient)) {
                        ChemSlotData data = MekanismIntegration.buildChemSlotData(ingredient);
                        if (data != null) {
                            screen.getMenu().chemSlotData = data;
                            CompoundTag tag = MekanismIntegration.convertChemicalToTag(ingredient);
                            ItemStack stack = MekanismIntegration.createChemicalTagItem(tag);
                            screen.setGhostItem(0, stack);
                            screen.setSelectedChemical(stack);
                        }
                    }
                }
            });
            targets.add(new Target<>() {
                public Rect2i getArea() {
                    return new Rect2i(getSlotScreenX(screen, 0),
                            getSlotScreenY(screen, 0), 16, 16);
                }

                public void accept(I ingredient) {
                    if (ingredient instanceof ItemStack stack) {
                        screen.getMenu().craftSlots.setItem(0, stack.copy());
                    }
                }
            });
            targets.add(new Target<>() {
                public Rect2i getArea() {
                    return new Rect2i(getSlotScreenX(screen, 81),
                            getSlotScreenY(screen, 81), 16, 16);
                }

                public void accept(I ingredient) {
                    if (ingredient instanceof ItemStack stack) {
                        screen.getMenu().getSlot(81).set(stack.copy());
                    }
                }
            });
        } else if (mode == 3) {
            // Mode 3: 3 input slots
            for (int i = 0; i < 3; i++) {
                final int slotIdx = i;
                targets.add(new Target<>() {
                    public Rect2i getArea() {
                        return new Rect2i(getSlotScreenX(screen, slotIdx),
                                getSlotScreenY(screen, slotIdx), 16, 16);
                    }

                    public void accept(I ingredient) {
                        if (ingredient instanceof ItemStack stack) {
                            screen.getMenu().getSlot(slotIdx).set(stack.copy());
                        }
                    }
                });
            }
        } else if (mode == 4) {
            // Mode 4: 2 input slots
            for (int i = 0; i < 2; i++) {
                final int slotIdx = i;
                targets.add(new Target<>() {
                    public Rect2i getArea() {
                        return new Rect2i(getSlotScreenX(screen, slotIdx),
                                getSlotScreenY(screen, slotIdx), 16, 16);
                    }

                    public void accept(I ingredient) {
                        if (ingredient instanceof ItemStack stack) {
                            screen.getMenu().getSlot(slotIdx).set(stack.copy());
                        }
                    }
                });
            }
        }

        return targets;
    }

    private static <I> void handleMode0Accept(VisualCraftingScreen screen, int slotIndex, I ingredient) {
        if (MekanismIntegration.isChemicalIngredient(ingredient)) {
            CompoundTag tag = MekanismIntegration.convertChemicalToTag(ingredient);
            if (tag != null) {
                screen.getMenu().setChemGhost(slotIndex, tag);
                screen.setGhostItem(slotIndex, MekanismIntegration.createChemicalTagItem(tag));
            }
        } else if (ingredient instanceof ItemStack stack) {
            screen.setGhostItem(slotIndex, stack.copyWithCount(1));
        }
    }

    @Override
    public void onComplete() {
    }
}
