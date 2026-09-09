package com.example.spawncontrol;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SpawnControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnRejectListener
{
    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event)
    {
        if (!Config.spawnRejectEnabled) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;

        EntityType<?> type = event.getEntity().getType();
        Double override = Config.spawnRejectOverrides.get(type);
        double chance;
        if (override != null)
        {
            chance = override;
        }
        else
        {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (!Config.isDefaultRejectApplicable(key.toString())) return;
            chance = Config.defaultSpawnRejectChance;
        }
        if (chance <= 0.0) return;

        double roll = event.getLevel().getRandom().nextDouble() * 100.0;
        if (roll < chance)
        {
            event.setResult(Event.Result.DENY);
        }
    }
}