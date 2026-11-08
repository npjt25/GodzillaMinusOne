package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GodzillaMinusOne.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + GodzillaMinusOne.MODID))
            .icon(() -> new ItemStack(ModItems.GODZILLA_SPAWN_EGG.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.GODZILLA_SPAWN_EGG.get());
                output.accept(ModItems.GODZILLA_MINUS_ZERO_SPAWN_EGG.get());
                output.accept(ModItems.ODO_ISLAND_MONSTER_SPAWN_EGG.get());
                output.accept(ModItems.SHINDEN_SPAWN_EGG.get());
                output.accept(ModItems.NAVAL_MINE_SPAWN_EGG.get());
                output.accept(ModItems.SHINSEI_MARU.get());
                output.accept(ModItems.REMOTE_DETONATOR.get());
                output.accept(ModItems.SEARCHLIGHT.get());
                output.accept(ModItems.NUCLEAR_BOMB.get());
                output.accept(ModItems.HAZMAT_HOOD.get());
                output.accept(ModItems.HAZMAT_SUIT.get());
                output.accept(ModItems.HAZMAT_TROUSERS.get());
                output.accept(ModItems.HAZMAT_BOOTS.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
