package com.dismountrebind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DismountRebind.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings
{
    public static final String KEY_DISMOUNT = "key.dismount_rebind.dismount";
    public static final String CATEGORY_MOVEMENT = "key.categories.movement";

    public static final KeyMapping DISMOUNT = new KeyMapping(
            KEY_DISMOUNT,
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY_MOVEMENT
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event)
    {
        event.register(DISMOUNT);
    }
}
