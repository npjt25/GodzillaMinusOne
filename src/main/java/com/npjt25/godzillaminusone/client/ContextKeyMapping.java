package com.npjt25.godzillaminusone.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;

/**
 * A key that only counts in its own situation (one of {@link ModKeyContexts}), held for as long as its key is.
 * Forge's own {@link KeyMapping#isDown()} drops a key whose context isn't "in game" while Ctrl, Shift or Alt is held
 * (KeyModifier.NONE), so a key bound to Left Control itself, the default for these, would never count; this one keeps
 * track of its key itself, and only asks for a modifier when it's bound with one (Ctrl + F, say).
 */
final class ContextKeyMapping extends KeyMapping {
    private boolean held;

    ContextKeyMapping(String name, IKeyConflictContext context, int key, String category) {
        super(name, context, InputConstants.Type.KEYSYM, key, category);
    }

    @Override
    public void setDown(boolean down) {
        super.setDown(down);
        this.held = down;
    }

    @Override
    public boolean isDown() {
        KeyModifier modifier = getKeyModifier();
        return this.held && getKeyConflictContext().isActive()
                && (modifier == KeyModifier.NONE || modifier.isActive(getKeyConflictContext()));
    }
}
