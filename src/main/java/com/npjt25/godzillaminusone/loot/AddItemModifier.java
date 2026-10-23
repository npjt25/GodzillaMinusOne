package com.npjt25.godzillaminusone.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * A global loot modifier that adds an item to a loot table's roll (on top of what it already gives, nothing replaced):
 * with probability {@code chance}, a stack of {@code min} to {@code max} of {@code item}. Which tables it applies to
 * is up to its conditions (forge:loot_table_id). Used for the naval mines found in end city, nether fortress and ancient
 * city chests (data/godzillaminusone/loot_modifiers, listed in data/forge/loot_modifiers/global_loot_modifiers.json).
 */
public class AddItemModifier extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, GodzillaMinusOne.MODID);

    public static final Supplier<Codec<AddItemModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst -> codecStart(inst)
            .and(ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item))
            .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(m -> m.chance))
            .and(Codec.intRange(1, 64).fieldOf("min").forGetter(m -> m.min))
            .and(Codec.intRange(1, 64).fieldOf("max").forGetter(m -> m.max))
            .apply(inst, AddItemModifier::new)));

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM = SERIALIZERS.register("add_item", CODEC);

    private final Item item;
    private final float chance;
    private final int min;
    private final int max;

    public AddItemModifier(LootItemCondition[] conditions, Item item, float chance, int min, int max) {
        super(conditions);
        this.item = item;
        this.chance = chance;
        this.min = min;
        this.max = Math.max(min, max);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() < this.chance) {
            loot.add(new ItemStack(this.item, Mth.randomBetweenInclusive(context.getRandom(), this.min, this.max)));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
