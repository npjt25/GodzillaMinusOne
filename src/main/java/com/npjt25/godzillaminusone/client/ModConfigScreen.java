package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The mod's settings screen (Mods, Godzilla Minus One, Config). Changes are saved right away. */
public class ModConfigScreen extends Screen {
    private static final String KEY = "config." + GodzillaMinusOne.MODID;

    @Nullable
    private final Screen parent;

    public ModConfigScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + ".title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 125;
        int y = this.height / 4 + 24;
        addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.SHOW_BOSS_BAR.get())
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + ".show_boss_bar.tooltip")))
                .create(x, y, 250, 20, Component.translatable(KEY + ".show_boss_bar"), (button, value) -> {
                    ClientConfig.SHOW_BOSS_BAR.set(value);
                    ClientConfig.SPEC.save();
                }));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
