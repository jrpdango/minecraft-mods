package com.example.spawncontrol;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.Weight;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Runs once per world/server start-up: biomes (and their MobSpawnSettings) are not affected
// by /reload, so re-applying here on every ServerAboutToStartEvent is sufficient.
@Mod.EventBusSubscriber(modid = SpawnControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnWeightOverride
{
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event)
    {
        Map<EntityType<?>, Integer> overrides = new HashMap<>();

        for (Config.WeightPattern pattern : Config.weightPatternOverrides)
        {
            boolean matchedAny = false;
            for (Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> entry : ForgeRegistries.ENTITY_TYPES.getEntries())
            {
                if (pattern.globPattern().matcher(entry.getKey().location().toString()).matches())
                {
                    overrides.put(entry.getValue(), pattern.weight());
                    matchedAny = true;
                }
            }
            if (!matchedAny)
            {
                SpawnControlMod.LOGGER.warn(
                        "SpawnControl: weightOverrides glob {} matched no registered entity types; override had no effect.",
                        pattern.glob());
            }
        }

        overrides.putAll(Config.weightOverrides);

        if (overrides.isEmpty()) return;

        Registry<Biome> biomes = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
        Set<EntityType<?>> matched = new HashSet<>();

        for (Biome biome : biomes)
        {
            MobSpawnSettings settings = biome.getMobSettings();
            for (MobCategory category : MobCategory.values())
            {
                for (MobSpawnSettings.SpawnerData data : settings.getMobs(category).unwrap())
                {
                    Integer override = overrides.get(data.type);
                    if (override != null)
                    {
                        data.weight = Weight.of(override);
                        matched.add(data.type);
                    }
                }
            }
        }

        for (EntityType<?> type : overrides.keySet())
        {
            if (!matched.contains(type))
            {
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
                SpawnControlMod.LOGGER.warn(
                        "SpawnControl: weightOverrides configured {} but it has no natural spawn entry in any loaded biome; override had no effect.",
                        key);
            }
        }
    }
}