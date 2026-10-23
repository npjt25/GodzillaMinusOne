package com.npjt25.godzillaminusone.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * A global loot modifier that adds an item to a loot table's roll (on top of what it already gives, nothing replaced):
 * with probability {@code chance}, a stack of {@code min} to {@code max} of {@code item}, each count as likely as its
 * entry in {@code weights} (e.g. [6, 3, 1] for 1, 2, 3: one 60% of the time, two 30%, three 10%; left out, all counts
 * are equally likely). Which tables it applies to
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
            .and(Codec.intRange(0, 1000).listOf().optionalFieldOf("weights", List.of()).forGetter(m -> m.weights))
            .apply(inst, AddItemModifier::new)));

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM = SERIALIZERS.register("add_item", CODEC);

    private final Item item;
    private final float chance;
    private final int min;
    private final int max;
    private final List<Integer> weights;

    public AddItemModifier(LootItemCondition[] conditions, Item item, float chance, int min, int max, List<Integer> weights) {
        super(conditions);
        this.item = item;
        this.chance = chance;
        this.min = min;
        this.max = Math.max(min, max);
        this.weights = List.copyOf(weights);
    }

    /** A stack size from min to max, by the weights. */
    private int count(RandomSource random) {
        int n = this.max - this.min + 1;
        if (this.weights.size() != n) {
            return Mth.randomBetweenInclusive(random, this.min, this.max);
        }
        int total = 0;
        for (int w : this.weights) {
            total += w;
        }
        int pick = random.nextInt(Math.max(1, total));
        for (int i = 0; i < n; i++) {
            pick -= this.weights.get(i);
            if (pick < 0) {
                return this.min + i;
            }
        }
        return this.max;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() < this.chance) {
            loot.add(new ItemStack(this.item, count(context.getRandom())));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
