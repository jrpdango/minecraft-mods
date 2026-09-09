package com.dismountrebind.client;

import com.dismountrebind.DismountRebind;
import com.dismountrebind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DismountRebind.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RidingInputHandler
{
    // The fake sneak is held for a few consecutive movement ticks so it reliably overlaps a
    // server tick. A single forced sneak tick can land on a tick where the ridden entity's
    // dismount check already ran, so it would be missed and the rider would stay mounted.
    private static final int DISMOUNT_HOLD_TICKS = 3;

    private static int pendingDismountTicks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
        {
            while (KeyBindings.DISMOUNT.consumeClick())
            {
            }
            pendingDismountTicks = 0;
            return;
        }

        boolean riding = mc.player.isPassenger();
        while (KeyBindings.DISMOUNT.consumeClick())
        {
            if (riding)
            {
                pendingDismountTicks = DISMOUNT_HOLD_TICKS;
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event)
    {
        if (!(event.getEntity() instanceof LocalPlayer player))
        {
            return;
        }

        if (!player.isPassenger())
        {
            pendingDismountTicks = 0;
            return;
        }

        Input input = event.getInput();

        // Sneak no longer dismounts while riding, so suppress the sneak bit
        // that would otherwise reach the server through the riding input packet.
        input.shiftKeyDown = false;

        // The dedicated Dismount key triggers a short burst of vanilla sneak-ticks,
        // which makes the server perform a regular dismount. Held for a few ticks so
        // it always overlaps the server tick where the ridden entity checks for it.
        if (pendingDismountTicks > 0)
        {
            input.shiftKeyDown = true;
            pendingDismountTicks--;
        }
    }
}
