package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.menu.ShinseiMaruMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The Shinsei Maru's mine hold, drawn like a one-row chest. */
public class ShinseiMaruScreen extends AbstractContainerScreen<ShinseiMaruMenu> {
    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");

    public ShinseiMaruScreen(ShinseiMaruMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 114 + 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(CHEST, x, y, 0, 0, this.imageWidth, 18 + 17);
        graphics.blit(CHEST, x, y + 18 + 17, 0, 126, this.imageWidth, 96);
    }
}
