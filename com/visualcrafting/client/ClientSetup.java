package com.visualcrafting.client;

import appeng.client.gui.implementations.InterfaceScreen;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.AEBaseMenu;
import com.visualcrafting.VisualCraftingTable;
import com.visualcrafting.screen.VisualCraftingMenu;
import com.visualcrafting.screen.VisualCraftingScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

@EventBusSubscriber(modid = "visualcrafting", value = {Dist.CLIENT})
public class ClientSetup {
    private static Method ADD_TO_LEFT_TOOLBAR_METHOD = null;

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        try {
            Class<?> screenConstructorClass = Class.forName("net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor");
            MenuType<VisualCraftingMenu> menuType = VisualCraftingTable.VISUAL_CRAFTING_MENU.get();
            Method registerMethod = RegisterMenuScreensEvent.class.getMethod("register", MenuType.class, screenConstructorClass);

            Object proxy = Proxy.newProxyInstance(screenConstructorClass.getClassLoader(),
                    new Class[]{screenConstructorClass},
                    (proxyObj, method, args) -> {
                        if ("create".equals(method.getName())) {
                            return new VisualCraftingScreen(
                                    (VisualCraftingMenu) args[0],
                                    (Inventory) args[1],
                                    (Component) args[2]);
                        }
                        if (method.isDefault()) {
                            return MethodHandles.lookup()
                                    .unreflectSpecial(method, method.getDeclaringClass())
                                    .bindTo(proxyObj)
                                    .invokeWithArguments(args);
                        }
                        return null;
                    });

            registerMethod.invoke(event, menuType, proxy);
        } catch (Exception e) {
            throw new RuntimeException("[VC] Failed to register screen", e);
        }
    }

    private static boolean isAe2InterfaceScreen(Screen screen) {
        return screen instanceof InterfaceScreen;
    }

    private static BlockPos getInterfaceBlockPos(AbstractContainerScreen<?> screen) {
        try {
            Method method = AEBaseMenu.class.getMethod("getBlockEntity");
            BlockEntity blockEntity = (BlockEntity) method.invoke(screen.getMenu());
            return blockEntity.getBlockPos();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void callAddToLeftToolbar(Screen screen, TabButton tabButton) {
        if (ADD_TO_LEFT_TOOLBAR_METHOD == null) {
            for (Class<?> clazz = screen.getClass();
                 clazz != null && clazz != Object.class;
                 clazz = clazz.getSuperclass()) {
                try {
                    ADD_TO_LEFT_TOOLBAR_METHOD = clazz.getDeclaredMethod("addToLeftToolbar", Button.class);
                    ADD_TO_LEFT_TOOLBAR_METHOD.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        if (ADD_TO_LEFT_TOOLBAR_METHOD != null) {
            try {
                ADD_TO_LEFT_TOOLBAR_METHOD.invoke(screen, tabButton);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void addRenderableWidget(Screen screen, TabButton tabButton) {
        try {
            Method method = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
            method.setAccessible(true);
            method.invoke(screen, tabButton);
        } catch (Exception e1) {
            try {
                Field field = Screen.class.getDeclaredField("children");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<GuiEventListener> list = (List<GuiEventListener>) field.get(screen);
                list.add(tabButton);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }
}
