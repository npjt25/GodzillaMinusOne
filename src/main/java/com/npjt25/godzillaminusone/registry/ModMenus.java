package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.menu.ShinseiMaruMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, GodzillaMinusOne.MODID);

    /** The Shinsei Maru's mine hold. */
    public static final RegistryObject<MenuType<ShinseiMaruMenu>> SHINSEI_MARU = MENUS.register("shinsei_maru",
            () -> IForgeMenuType.create((id, inventory, data) -> new ShinseiMaruMenu(id, inventory)));

    private ModMenus() {
    }
}
