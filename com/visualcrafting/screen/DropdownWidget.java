package com.visualcrafting.screen;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class DropdownWidget {
    private int x;
    private int y;
    private final int width;
    private final int height;
    private final List<String> options;
    private int selectedIdx;
    private boolean open;
    private final Consumer<Integer> onSelect;
    private int visibleRows = 8;
    private int scrollOffset;

    public DropdownWidget(int x, int y, int width, int height, List<String> options, int selectedIdx, Consumer<Integer> onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.options = options;
        this.selectedIdx = Math.max(0, Math.min(selectedIdx, options.size() - 1));
        this.onSelect = onSelect;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setOptions(List<String> options, int selectedIdx) {
        this.options.clear();
        this.options.addAll(options);
        this.selectedIdx = Math.max(0, Math.min(selectedIdx, this.options.size() - 1));
        this.scrollOffset = 0;
    }

    public int getSelectedIdx() {
        return this.selectedIdx;
    }

    public String getSelectedValue() {
        return this.selectedIdx >= 0 && this.selectedIdx < this.options.size()
                ? this.options.get(this.selectedIdx)
                : "";
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void toggle() {
        this.open = !this.open;
        this.scrollOffset = 0;
    }

    public void close() {
        this.open = false;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Font font = Minecraft.getInstance().font;
        if (!this.open) {
            guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, -12303292);
            guiGraphics.fill(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, -14540254);
            String displayText = this.getSelectedValue();
            if (displayText.length() > 25) {
                displayText = displayText.substring(0, 22) + "...";
            }
            guiGraphics.drawString(font, displayText, this.x + 4, this.y + (this.height - 8) / 2, 0xFFFFFF);
            guiGraphics.drawString(font, "\u25bc", this.x + this.width - 14, this.y + (this.height - 8) / 2, 0xAAAAAA);
            return;
        }
        int dropdownHeight = Math.min(this.options.size(), this.visibleRows) * 14 + 4;
        guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + dropdownHeight, -870178270);
        int maxRow = Math.min(this.scrollOffset + this.visibleRows, this.options.size());
        for (int i = this.scrollOffset; i < maxRow; ++i) {
            int rowY = this.y + 2 + (i - this.scrollOffset) * 14;
            boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width
                    && mouseY >= rowY && mouseY <= rowY + 14;
            int color = hovered ? -11184811 : (i == this.selectedIdx ? -12303360 : -13421773);
            guiGraphics.fill(this.x, rowY, this.x + this.width, rowY + 14, color);
            String optionText = this.options.get(i);
            if (optionText.length() > 25) {
                optionText = optionText.substring(0, 22) + "...";
            }
            guiGraphics.drawString(font, optionText, this.x + 4, rowY + 3, 0xFFFFFF);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open) {
            if (this.isMouseOver(mouseX, mouseY)) {
                this.toggle();
                return true;
            }
            return false;
        }
        int maxRow = Math.min(this.scrollOffset + this.visibleRows, this.options.size());
        for (int i = this.scrollOffset; i < maxRow; ++i) {
            int rowY = this.y + 2 + (i - this.scrollOffset) * 14;
            if (!(mouseX >= (double) this.x) || !(mouseX <= (double) (this.x + this.width))
                    || !(mouseY >= (double) rowY) || !(mouseY <= (double) (rowY + 14))) {
                continue;
            }
            this.selectedIdx = i;
            this.onSelect.accept(i);
            this.open = false;
            return true;
        }
        this.open = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.open) {
            return false;
        }
        if (mouseX >= (double) this.x && mouseX <= (double) (this.x + this.width)) {
            this.scrollOffset = (int) Math.max(0.0,
                    Math.min((double) this.scrollOffset - scrollY,
                            (double) Math.max(0, this.options.size() - this.visibleRows)));
            return true;
        }
        return false;
    }

    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= (double) this.x && mouseX <= (double) (this.x + this.width)
                && mouseY >= (double) this.y && mouseY <= (double) (this.y + this.height);
    }
}
