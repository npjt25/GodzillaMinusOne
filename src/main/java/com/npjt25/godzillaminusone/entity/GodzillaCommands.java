package com.npjt25.godzillaminusone.entity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Playing as Godzilla (Walkers / ReMorphed: entity.PlayerGodzilla), his moves as commands, for whoever runs them (any
 * player: they're the same moves as his keys, with the same waits and recharge):
 *
 * <ul>
 *   <li>{@code /godzilla tailwhip}, {@code scratch}, {@code bite}, {@code roar}, {@code breath} (his atomic breath; as
 *       Godzilla Minus Zero, his with its second blast);</li>
 *   <li>{@code /godzilla tailstrike}: Minus Zero's tail strike (only as him).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GodzillaCommands {
    private static final String KEY = "commands." + GodzillaMinusOne.MODID + ".godzilla.";

    /** Each command's word, and the move it makes. */
    private static final Object[][] MOVES = {
            {"tailwhip", PlayerGodzilla.Move.TAIL_WHIP}, {"scratch", PlayerGodzilla.Move.SCRATCH}, {"bite", PlayerGodzilla.Move.BITE},
            {"roar", PlayerGodzilla.Move.ROAR}, {"breath", PlayerGodzilla.Move.ATOMIC_BREATH}, {"tailstrike", PlayerGodzilla.Move.TAIL_STRIKE}};

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("godzilla");
        for (Object[] entry : MOVES) {
            PlayerGodzilla.Move move = (PlayerGodzilla.Move) entry[1];
            root.then(Commands.literal((String) entry[0]).executes(context -> use(context.getSource(), move)));
        }
        event.getDispatcher().register(root);
    }

    private static int use(CommandSourceStack source, PlayerGodzilla.Move move) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PlayerGodzilla.isGodzilla(player)) {
            source.sendFailure(Component.translatable(KEY + "not_godzilla"));
            return 0;
        }
        if (move == PlayerGodzilla.Move.TAIL_STRIKE && !PlayerGodzilla.canTailStrike(player)) {
            source.sendFailure(Component.translatable(KEY + "minus_zero_only"));
            return 0;
        }
        if (!PlayerGodzilla.use(player, move)) {
            if (move != PlayerGodzilla.Move.ATOMIC_BREATH || PlayerGodzilla.breathCooldown(player) <= 0) {
                source.sendFailure(Component.translatable(KEY + "busy")); // (the breath's recharge time it shows itself)
            }
            return 0;
        }
        return 1;
    }

    private GodzillaCommands() {
    }
}
