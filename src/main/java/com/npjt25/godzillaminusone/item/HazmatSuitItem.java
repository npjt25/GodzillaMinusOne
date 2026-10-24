package com.npjt25.godzillaminusone.item;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.client.render.HazmatSuitRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * A piece of the hazmat suit (hood, suit, trousers or boots): a blue chemical-protection suit with a full-face mask,
 * a harness with a gas detector and a gauge, and two air cylinders on the back, drawn by GeckoLib
 * (client.render.HazmatSuitRenderer). Armour about as good as leather; wearing all four keeps radiation off you
 * (entity.Radiation).
 */
public class HazmatSuitItem extends ArmorItem implements GeoItem {
    /** By slot index (boots, trousers, suit, hood): durability (x 15, as iron's) and armour points (as leather's). */
    private static final int[] DURABILITY = {13, 15, 16, 11};
    private static final int[] DEFENSE = {1, 2, 3, 1};
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return DURABILITY[type.getSlot().getIndex()] * 15;
        }

        @Override
        public int getDefenseForType(Type type) {
            return DEFENSE[type.getSlot().getIndex()];
        }

        @Override
        public int getEnchantmentValue() {
            return 12;
        }

        @Override
        public @NotNull SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public @NotNull Ingredient getRepairIngredient() {
            return Ingredient.of(Items.BLUE_WOOL);
        }

        @Override
        public @NotNull String getName() {
            return GodzillaMinusOne.MODID + ":hazmat";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };
    private static final String TEXTURE = GodzillaMinusOne.MODID + ":textures/armor/hazmat_suit.png";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HazmatSuitItem(Type type, Properties properties) {
        super(MATERIAL, type, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private HazmatSuitRenderer renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = new HazmatSuitRenderer();
                }
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
    }

    /** (GeckoLib draws it with its own texture; this is what the armour layer is handed meanwhile.) */
    @Override
    public @Nullable String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item." + GodzillaMinusOne.MODID + ".hazmat.desc"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
