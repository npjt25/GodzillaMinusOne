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

import java.util.function.IntFunction;

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
        int y = this.height / 6 + 16; // (five rows, clear of the Done button even on a small window)
        addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.SHOW_BOSS_BAR.get())
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + ".show_boss_bar.tooltip")))
                .create(x, y, 250, 20, Component.translatable(KEY + ".show_boss_bar"), (button, value) -> {
                    ClientConfig.SHOW_BOSS_BAR.set(value);
                    ClientConfig.SPEC.save();
                }));
        addSlider(new NumberSlider(x, y + 26, Config.SPEC, Config.ATOMIC_BREATH_COOLDOWN, 10, 600, 10, "breath_interval", ModConfigScreen::time));
        addSlider(new NumberSlider(x, y + 52, Config.SPEC, Config.ROAR_INTERVAL, 0, 600, 30, "roar_interval", ModConfigScreen::time));
        addSlider(new NumberSlider(x, y + 78, ClientConfig.SPEC, ClientConfig.NUCLEAR_CLOUD_HEIGHT, 40, 600, 10, "cloud_height",
                blocks -> Component.translatable(KEY + ".blocks", blocks)));
        addSlider(new NumberSlider(x, y + 104, ClientConfig.SPEC, ClientConfig.NUCLEAR_CLOUD_SECONDS, 10, 300, 5, "cloud_seconds",
                ModConfigScreen::time));
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

    private void addSlider(NumberSlider slider) {
        slider.setTooltip(Tooltip.create(Component.translatable(KEY + "." + slider.name + ".tooltip")));
        addRenderableWidget(slider);
    }

    /** A number of seconds as minutes and seconds (0 is off). */
    private static Component time(int seconds) {
        return seconds == 0 ? CommonComponents.OPTION_OFF
                : seconds % 60 == 0 ? Component.translatable(KEY + ".minutes", seconds / 60)
                : seconds < 60 ? Component.translatable(KEY + ".seconds", seconds)
                : Component.translatable(KEY + ".minutes_seconds", seconds / 60, seconds % 60);
    }

    /**
     * A whole-number setting from {@code min} to {@code max} in steps of {@code step}, saved as it's moved: how long
     * Godzilla takes to recharge his atomic breath, how often he roars, how tall the mushroom cloud grows and how
     * long it lasts.
     */
    private static final class NumberSlider extends AbstractSliderButton {
        private final ForgeConfigSpec spec;
        private final ForgeConfigSpec.IntValue setting;
        private final int min;
        private final int max;
        private final int step;
        private final String name;
        private final IntFunction<Component> format;

        NumberSlider(int x, int y, ForgeConfigSpec spec, ForgeConfigSpec.IntValue setting, int min, int max, int step, String name,
                     IntFunction<Component> format) {
            super(x, y, 250, 20, Component.empty(), Mth.clamp((setting.get() - min) / (double) (max - min), 0.0, 1.0));
            this.spec = spec;
            this.setting = setting;
            this.min = min;
            this.max = max;
            this.step = step;
            this.name = name;
            this.format = format;
            updateMessage();
        }

        private int number() {
            int raw = (int) Math.round(this.min + this.value * (this.max - this.min));
            return Math.round(raw / (float) this.step) * this.step;
        }

        @Override
        protected void updateMessage() {
            if (this.format != null) {
                setMessage(Component.translatable(KEY + "." + this.name, this.format.apply(number())));
            }
        }

        @Override
        protected void applyValue() {
            this.setting.set(number());
            this.spec.save();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
