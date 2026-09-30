package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaStatueEntity;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import com.npjt25.godzillaminusone.entity.NuclearBombEntity;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
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
                    .sized(10.0F, 40.0F)
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

    public static final RegistryObject<EntityType<OdoIslandMonsterEntity>> ODO_ISLAND_MONSTER = ENTITIES.register("odo_island_monster",
            () -> EntityType.Builder.of(OdoIslandMonsterEntity::new, MobCategory.MONSTER)
                    .sized(9.0F, 14.0F)
                    .clientTrackingRange(16)
                    .build(GodzillaMinusOne.id("odo_island_monster").toString()));

    public static final RegistryObject<EntityType<SearchlightEntity>> SEARCHLIGHT = ENTITIES.register("searchlight",
            () -> EntityType.Builder.<SearchlightEntity>of(SearchlightEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.4F)
                    .clientTrackingRange(10)
                    .build(GodzillaMinusOne.id("searchlight").toString()));

    public static final RegistryObject<EntityType<NuclearBombEntity>> NUCLEAR_BOMB = ENTITIES.register("nuclear_bomb",
            () -> EntityType.Builder.<NuclearBombEntity>of(NuclearBombEntity::new, MobCategory.MISC)
                    .sized(1.0F, 3.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(GodzillaMinusOne.id("nuclear_bomb").toString()));

    public static final RegistryObject<EntityType<ShinseiMaruEntity>> SHINSEI_MARU = ENTITIES.register("shinsei_maru",
            () -> EntityType.Builder.<ShinseiMaruEntity>of(ShinseiMaruEntity::new, MobCategory.MISC)
                    .sized(6.5F, 3.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(GodzillaMinusOne.id("shinsei_maru").toString()));

    // Posed Godzilla statues for thumbnails: /summon only (no egg, no natural spawns).
    public static final RegistryObject<EntityType<GodzillaStatueEntity>> GODZILLA_POSE_CHARGING_ROAR =
            statue("godzilla_pose_charging_roar", GodzillaStatueEntity.Stance.CHARGING_ROAR);
    public static final RegistryObject<EntityType<GodzillaStatueEntity>> GODZILLA_POSE_TAIL_WHIP =
            statue("godzilla_pose_tail_whip", GodzillaStatueEntity.Stance.TAIL_WHIP);
    public static final RegistryObject<EntityType<GodzillaStatueEntity>> GODZILLA_POSE_SKY_ROAR =
            statue("godzilla_pose_sky_roar", GodzillaStatueEntity.Stance.SKY_ROAR);

    private static RegistryObject<EntityType<GodzillaStatueEntity>> statue(String name, GodzillaStatueEntity.Stance stance) {
        return ENTITIES.register(name, () -> EntityType.Builder.<GodzillaStatueEntity>of((type, level) -> new GodzillaStatueEntity(type, level, stance), MobCategory.MISC)
                .sized(10.0F, 40.0F)
                .fireImmune()
                .clientTrackingRange(20)
                .updateInterval(20)
                .build(GodzillaMinusOne.id(name).toString()));
    }

    private ModEntities() {
    }
}
