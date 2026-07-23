package com.visualcrafting.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

public class FoodTabRenderer {
    private final List<String> potionIds = new ArrayList<>();
    private final List<String> potionNames = new ArrayList<>();
    private final DropdownWidget potionDropdown = new DropdownWidget(0, 0, 64, 16, this.potionNames, 0, n -> {});
    private EditBox nutritionField;
    private EditBox saturationField;
    private EditBox eatSecondsField;
    private EditBox durationField;
    private EditBox levelField;
    private Checkbox infiniteCheckbox;
    private int guiLeft;
    private int guiTop;

    public FoodTabRenderer() {
        this.loadPotionData();
    }

    private void loadPotionData() {
        this.potionIds.clear();
        this.potionNames.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        Registry<MobEffect> registry = minecraft.level.registryAccess().registryOrThrow(Registries.MOB_EFFECT);
        for (MobEffect mobEffect : registry) {
            ResourceLocation resourceLocation = BuiltInRegistries.MOB_EFFECT.getKey(mobEffect);
            if (resourceLocation == null) {
                continue;
            }
            this.potionIds.add(resourceLocation.toString());
            String translationKey = "effect." + resourceLocation.getNamespace() + "." + resourceLocation.getPath();
            String displayName = Language.getInstance().getOrDefault(translationKey);
            this.potionNames.add(displayName.equals(translationKey) ? resourceLocation.getPath() : displayName);
        }
    }

    public void updatePosition(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.potionDropdown.setPosition(guiLeft + 93, guiTop + 64);
    }

    public void setWidgets(EditBox nutritionField, EditBox saturationField, EditBox eatSecondsField,
                           EditBox durationField, EditBox levelField, Checkbox infiniteCheckbox) {
        this.nutritionField = nutritionField;
        this.saturationField = saturationField;
        this.eatSecondsField = eatSecondsField;
        this.durationField = durationField;
        this.levelField = levelField;
        this.infiniteCheckbox = infiniteCheckbox;
    }

    public DropdownWidget getPotionDropdown() {
        return this.potionDropdown;
    }

    public String getSelectedPotionId() {
        int idx = this.potionDropdown.getSelectedIdx();
        return idx >= 0 && idx < this.potionIds.size() ? this.potionIds.get(idx) : "minecraft:speed";
    }

    public void drawBg(GuiGraphics guiGraphics) {
        int x = this.guiLeft;
        int y = this.guiTop;
        Font font = Minecraft.getInstance().font;
        int textColor = -2039584;
        guiGraphics.drawString(font, "Food Editor", x + 20, y + 4, 16766720);
        guiGraphics.renderOutline(x + 93, y + 16, 18, 18, -1);
        guiGraphics.renderOutline(x + 93, y + 39, 18, 18, -1);
        guiGraphics.drawString(font, "\u98df\u7269", x + 96 - font.width("\u98df\u7269"), y + 20, textColor);
        guiGraphics.drawString(font, "\u9965\u997f\u503c", x + 140 - font.width("\u9965\u997f\u503c"), y + 19, textColor);
        guiGraphics.drawString(font, "\u9971\u548c\u5ea6", x + 199 - font.width("\u9971\u548c\u5ea6"), y + 19, textColor);
        guiGraphics.drawString(font, "\u8fd4\u8fd8\u7269", x + 84 - font.width("\u8fd4\u8fd8\u7269"), y + 45, textColor);
        guiGraphics.drawString(font, "\u65f6\u957f", x + 133 - font.width("\u65f6\u957f"), y + 44, textColor);
        guiGraphics.drawString(font, "\u836f\u6548", x + 73 - font.width("\u836f\u6548"), y + 68, textColor);
        guiGraphics.drawString(font, "\u7b49\u7ea7", x + 197 - font.width("\u7b49\u7ea7"), y + 68, textColor);
        guiGraphics.drawString(font, "\u98df\u7528\u79d2\u6570", x + 88 - font.width("\u98df\u7528\u79d2\u6570"), y + 91, textColor);
        this.potionDropdown.render(guiGraphics, x + 93, y + 64, 0.0f);
    }
}
