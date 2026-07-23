package com.visualcrafting.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.client.gui.widgets.TabButton;
import com.visualcrafting.network.ExtractFurnaceExpPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenMixin {

    @Unique
    private TabButton visualcrafting$furnaceExpButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!((Object) this instanceof InterfaceScreen)) {
            return;
        }

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        int leftPos = getLeftPos(self);
        int topPos = getTopPos(self);
        BlockPos pos = getInterfaceBlockPos(self);

        if (pos == null) {
            System.err.println("[VC] Mixin init: could not get Interface block pos");
            return;
        }

        try {
            BlockPos interfacePos = pos;
            visualcrafting$furnaceExpButton = new TabButton(Icon.CLEAR, Component.empty(),
                    button -> sendExtractPacket(interfacePos));
            visualcrafting$furnaceExpButton.visible = false;
            setWidgetPosition(visualcrafting$furnaceExpButton, leftPos + 130, topPos - 5);

            addRenderableWidget(visualcrafting$furnaceExpButton);

            System.err.println("[VC] Mixin: button created, pos=(" + (leftPos + 130) + "," + (topPos - 5) + ")");
        } catch (Exception e) {
            System.err.println("[VC] Mixin onInit error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (visualcrafting$furnaceExpButton == null) {
            return;
        }

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        boolean hasCard = false;
        int storedMilli = 0;

        try {
            for (Slot slot : self.getMenu().slots) {
                if (slot.container instanceof Inventory) continue;
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                if (!isFurnaceCardItem(stack)) continue;
                hasCard = true;
                storedMilli = getStoredExpFromCard(stack);
                break;
            }
        } catch (Exception ignored) {
        }

        if (!hasCard) {
            visualcrafting$furnaceExpButton.visible = false;
            return;
        }

        visualcrafting$furnaceExpButton.visible = true;
        int levels = milliToLevel(storedMilli);
        visualcrafting$furnaceExpButton.setTooltip(
                Tooltip.create(Component.literal("\u53d6\u51fa\u7ecf\u9a8c:" + levels + "\u7ea7")));
        visualcrafting$furnaceExpButton.setFocused(
                visualcrafting$furnaceExpButton.isMouseOver(mouseX, mouseY));
    }

    @Unique
    private static boolean isFurnaceCardItem(ItemStack stack) {
        try {
            Class<?> clazz = Class.forName("com.visualcrafting.item.FurnaceCardItem");
            return clazz.isInstance(stack.getItem());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Unique
    private static int getStoredExpFromCard(ItemStack stack) {
        try {
            Class<?> clazz = Class.forName("com.visualcrafting.item.FurnaceCardItem");
            Method method = clazz.getMethod("getStoredExpMilli", ItemStack.class);
            return (int) method.invoke(null, stack);
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private static void sendExtractPacket(BlockPos pos) {
        try {
            PacketDistributor.sendToServer(new ExtractFurnaceExpPacket(pos));
        } catch (Exception e) {
            System.err.println("[VC] Mixin sendExtractPacket error: " + e.getMessage());
        }
    }

    @Unique
    private static int getLeftPos(AbstractContainerScreen<?> screen) {
        try {
            return screen.getGuiLeft();
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private static int getTopPos(AbstractContainerScreen<?> screen) {
        try {
            return screen.getGuiTop();
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private BlockPos getInterfaceBlockPos(AbstractContainerScreen<?> screen) {
        try {
            Object menu = screen.getMenu();
            Method getHost = menu.getClass().getMethod("getHost");
            Object host = getHost.invoke(menu);
            Method getBlockEntity = host.getClass().getMethod("getBlockEntity");
            Object be = getBlockEntity.invoke(host);
            Method getBlockPos = be.getClass().getMethod("getBlockPos");
            return (BlockPos) getBlockPos.invoke(be);
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private static void setWidgetPosition(Object widget, int x, int y) {
        for (Class<?> clazz = widget.getClass(); clazz != null && clazz != Object.class;
             clazz = clazz.getSuperclass()) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("x");
                field.setAccessible(true);
                field.setInt(widget, x);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("y");
                field.setAccessible(true);
                field.setInt(widget, y);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        }
    }

    @Unique
    private static int milliToLevel(int milliExp) {
        int exp = milliExp / 1000;
        int level = 0;
        while (exp > 0) {
            int needed;
            if (level >= 30) {
                needed = 112 + (level - 30) * 9;
            } else if (level >= 15) {
                needed = 37 + (level - 15) * 5;
            } else {
                needed = 7 + level * 2;
            }
            if (exp < needed) break;
            exp -= needed;
            level++;
        }
        return level;
    }
}
