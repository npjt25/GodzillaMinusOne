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
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's settings screen (Mods, Godzilla Minus One, Config). Changes are saved right away. The atomic breath's
 * and the roar's intervals are gameplay settings (config/godzillaminusone-common.toml): they apply to worlds played
 * on this computer; a dedicated server keeps its own.
 */
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
        SecondsSlider breath = new SecondsSlider(x, y + 26, 250, 20, Config.ATOMIC_BREATH_COOLDOWN, 10, 600, 10, "breath_interval");
        breath.setTooltip(Tooltip.create(Component.translatable(KEY + ".breath_interval.tooltip")));
        addRenderableWidget(breath);
        SecondsSlider roar = new SecondsSlider(x, y + 52, 250, 20, Config.ROAR_INTERVAL, 0, 600, 30, "roar_interval");
        roar.setTooltip(Tooltip.create(Component.translatable(KEY + ".roar_interval.tooltip")));
        addRenderableWidget(roar);
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

    /**
     * A time setting in seconds, from {@code min} to {@code max} in steps of {@code step}: how long Godzilla takes to
     * recharge his atomic breath, how often he roars (0 is off).
     */
    private static final class SecondsSlider extends AbstractSliderButton {
        private final ForgeConfigSpec.IntValue setting;
        private final int min;
        private final int max;
        private final int step;
        private final String name;

        SecondsSlider(int x, int y, int width, int height, ForgeConfigSpec.IntValue setting, int min, int max, int step, String name) {
            super(x, y, width, height, Component.empty(), Mth.clamp((setting.get() - min) / (double) (max - min), 0.0, 1.0));
            this.setting = setting;
            this.min = min;
            this.max = max;
            this.step = step;
            this.name = name;
            updateMessage();
        }

        private int seconds() {
            int raw = (int) Math.round(this.min + this.value * (this.max - this.min));
            return Math.round(raw / (float) this.step) * this.step;
        }

        @Override
        protected void updateMessage() {
            int seconds = seconds();
            Component time = seconds == 0 ? CommonComponents.OPTION_OFF
                    : seconds % 60 == 0 ? Component.translatable(KEY + ".minutes", seconds / 60)
                    : seconds < 60 ? Component.translatable(KEY + ".seconds", seconds)
                    : Component.translatable(KEY + ".minutes_seconds", seconds / 60, seconds % 60);
            setMessage(Component.translatable(KEY + "." + this.name, time));
        }

        @Override
        protected void applyValue() {
            this.setting.set(seconds());
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
