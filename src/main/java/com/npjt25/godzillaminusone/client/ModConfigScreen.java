package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's settings screen (Mods, Godzilla Minus One, Config). Changes are saved right away. The atomic breath's
 * interval is a gameplay setting (config/godzillaminusone-common.toml): it applies to worlds played on this
 * computer; a dedicated server keeps its own.
 */
public class ModConfigScreen extends Screen {
    private static final String KEY = "config." + GodzillaMinusOne.MODID;
    /** The breath-interval slider's range and steps, in seconds. */
    private static final int BREATH_MIN = 10;
    private static final int BREATH_MAX = 600;
    private static final int BREATH_STEP = 10;

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
        BreathSlider breath = new BreathSlider(x, y + 26, 250, 20);
        breath.setTooltip(Tooltip.create(Component.translatable(KEY + ".breath_interval.tooltip")));
        addRenderableWidget(breath);
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

    /** How long Godzilla takes to recharge his atomic breath after using it. */
    private static final class BreathSlider extends AbstractSliderButton {
        BreathSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), toSlider(Config.ATOMIC_BREATH_COOLDOWN.get()));
            updateMessage();
        }

        private static double toSlider(int seconds) {
            return Mth.clamp((seconds - BREATH_MIN) / (double) (BREATH_MAX - BREATH_MIN), 0.0, 1.0);
        }

        private int seconds() {
            int raw = (int) Math.round(BREATH_MIN + this.value * (BREATH_MAX - BREATH_MIN));
            return Math.round(raw / (float) BREATH_STEP) * BREATH_STEP;
        }

        @Override
        protected void updateMessage() {
            int seconds = seconds();
            Component time = seconds % 60 == 0
                    ? Component.translatable(KEY + ".minutes", seconds / 60)
                    : seconds < 60 ? Component.translatable(KEY + ".seconds", seconds)
                    : Component.translatable(KEY + ".minutes_seconds", seconds / 60, seconds % 60);
            setMessage(Component.translatable(KEY + ".breath_interval", time));
        }

        @Override
        protected void applyValue() {
            Config.ATOMIC_BREATH_COOLDOWN.set(seconds());
            Config.SPEC.save();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
