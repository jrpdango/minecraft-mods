package com.amphitherefix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

@Mod(AmphithereFixMod.MODID)
public class AmphithereFixMod {

    public static final String MODID = "amphitherefix";

    private static final Logger LOGGER = LogUtils.getLogger();

    public AmphithereFixMod() {
        Mixins.addConfiguration(MODID + ".mixins.json");
        LOGGER.info("Amphithere Dismount Fix loaded");
    }
}