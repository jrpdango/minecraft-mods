package com.example.spawncontrol;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SpawnControlMod.MODID)
public class SpawnControlMod
{
    public static final String MODID = "spawncontrol";
    static final Logger LOGGER = LogUtils.getLogger();

    public SpawnControlMod(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
