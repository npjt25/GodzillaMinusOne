package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.item.NuclearBombItem;
import com.npjt25.godzillaminusone.item.RemoteDetonatorItem;
import com.npjt25.godzillaminusone.item.SearchlightItem;
import com.npjt25.godzillaminusone.item.ShinseiMaruItem;
import com.npjt25.godzillaminusone.item.TorridItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GodzillaMinusOne.MODID);

    public static final RegistryObject<Item> TORRID = ITEMS.register("torrid",
            () -> new TorridItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SEARCHLIGHT = ITEMS.register("searchlight",
            () -> new SearchlightItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NUCLEAR_BOMB = ITEMS.register("nuclear_bomb",
            () -> new NuclearBombItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> REMOTE_DETONATOR = ITEMS.register("remote_detonator",
            () -> new RemoteDetonatorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SHINSEI_MARU = ITEMS.register("shinsei_maru",
            () -> new ShinseiMaruItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // Godzilla's, the Shinden's and the naval mine's eggs use their own textures from the add-on, so both tint colours
    // are white (no tint); the Odo Island Monster's is a vanilla-style egg (item/template_spawn_egg) in his colours.
    public static final RegistryObject<Item> GODZILLA_SPAWN_EGG = ITEMS.register("godzilla_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GODZILLA, 0xFFFFFF, 0xFFFFFF, new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SHINDEN_SPAWN_EGG = ITEMS.register("shinden_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SHINDEN, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));
    public static final RegistryObject<Item> NAVAL_MINE_SPAWN_EGG = ITEMS.register("naval_mine_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NAVAL_MINE, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));
    public static final RegistryObject<Item> ODO_ISLAND_MONSTER_SPAWN_EGG = ITEMS.register("odo_island_monster_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ODO_ISLAND_MONSTER, 0x6B6258, 0x3A2F28, new Item.Properties().rarity(Rarity.RARE)));

    private ModItems() {
    }
}
