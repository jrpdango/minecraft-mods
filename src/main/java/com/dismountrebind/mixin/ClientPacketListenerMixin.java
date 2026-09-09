package com.dismountrebind.mixin;

import com.dismountrebind.KeyBindings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin
{
    @Redirect(method = "handleSetEntityPassengersPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void dismountHintOverlay(Gui gui, Component component, boolean animate)
    {
        Component hint = dismountHint();
        if (hint != null)
        {
            gui.setOverlayMessage(hint, animate);
        }
    }

    @Redirect(method = "handleSetEntityPassengersPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;sayNow(Lnet/minecraft/network/chat/Component;)V"))
    private void dismountHintNarrator(GameNarrator narrator, Component component)
    {
        Component hint = dismountHint();
        if (hint != null)
        {
            narrator.sayNow(hint);
        }
    }

    private static Component dismountHint()
    {
        KeyMapping dismount = KeyBindings.DISMOUNT;
        if (dismount.getKey().getValue() == InputConstants.UNKNOWN.getValue())
        {
            return null;
        }
        return Component.translatable("mount.onboard", dismount.getTranslatedKeyMessage());
    }
}