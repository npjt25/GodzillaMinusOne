package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GodzillaMinusOne.MODID);

    public static final RegistryObject<EntityType<GodzillaEntity>> GODZILLA = ENTITIES.register("godzilla",
            () -> EntityType.Builder.of(GodzillaEntity::new, MobCategory.MONSTER)
                    .sized(8.0F, 30.0F)
                    .fireImmune()
                    .clientTrackingRange(20)
                    .build(GodzillaMinusOne.id("godzilla").toString()));

    public static final RegistryObject<EntityType<ShindenEntity>> SHINDEN = ENTITIES.register("shinden",
            () -> EntityType.Builder.of(ShindenEntity::new, MobCategory.MISC)
                    .sized(5.0F, 3.0F)
                    .clientTrackingRange(16)
                    .build(GodzillaMinusOne.id("shinden").toString()));

    public static final RegistryObject<EntityType<NavalMineEntity>> NAVAL_MINE = ENTITIES.register("naval_mine",
            () -> EntityType.Builder.of(NavalMineEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(GodzillaMinusOne.id("naval_mine").toString()));

    private ModEntities() {
    }
}
