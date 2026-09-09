package com.example.spawncontrol;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = SpawnControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final Pattern ENTRY_PATTERN = Pattern.compile("^([a-z0-9_.\\-*]+:[a-z0-9_./\\-*]+)=(-?\\d+)$");
    private static final Pattern CHANCE_ENTRY_PATTERN = Pattern.compile("^([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)=(\\d+(?:\\.\\d+)?)$");
    private static final Pattern GLOB_PATTERN = Pattern.compile("^([a-z0-9_.\\-*]+:[a-z0-9_./\\-*]+)$");

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEIGHT_OVERRIDES = BUILDER
            .comment(
                    "Overrides the natural spawn weight of an entity, applied everywhere that entity already",
                    "has spawn data (does not add new biome/mob spawn entries).",
                    "Each entry: \"modid:entity_id=weight\", e.g. \"minecraft:zombie=40\".",
                    "Glob patterns over the full registry key are supported, e.g. \"minecraft:*=40\" to",
                    "override every vanilla entity that has spawn data. Exact entries take precedence over",
                    "glob entries; when multiple globs match the same entity, the last one listed wins.")
            .defineListAllowEmpty("weightOverrides", List.of(), obj -> validateEntry(obj, true));

    private static final ForgeConfigSpec.BooleanValue SPAWN_REJECT_ENABLED = BUILDER
            .comment("Whether to reject natural spawns according to the configured rejection chances.")
            .define("spawnRejectEnabled", true);

    private static final ForgeConfigSpec.DoubleValue DEFAULT_SPAWN_REJECT_CHANCE = BUILDER
            .comment(
                    "Percentage chance (0-100) that a natural spawn attempt is cancelled.",
                    "Applied unless the entity has an entry in spawnRejectOverrides, and only to entities",
                    "matching defaultSpawnRejectPatterns.")
            .defineInRange("defaultSpawnRejectChance", 0.0, 0.0, 100.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEFAULT_SPAWN_REJECT_PATTERNS = BUILDER
            .comment(
                    "Glob patterns over the full entity registry key (\"namespace:path\") that decide which",
                    "entities defaultSpawnRejectChance applies to. \"*\" matches any sequence.",
                    "Each entry: \"namespace:path\", e.g. \"minecraft:*\" or \"*:zombie\".",
                    "A pattern without \"*\" is treated as a namespace and gets \":*\" appended (e.g. \"minecraft\").",
                    "When the list is empty, defaultSpawnRejectChance applies to ALL entities.",
                    "Per-entity spawnRejectOverrides always take precedence over this default.",
                    "Defaults to vanilla-only.")
            .defineListAllowEmpty("defaultSpawnRejectPatterns", List.of("minecraft:*"), obj -> validateGlob(obj));

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWN_REJECT_OVERRIDES = BUILDER
            .comment(
                    "Per-entity rejection chance override, as a percentage (0-100).",
                    "Each entry: \"modid:entity_id=chance\", e.g. \"minecraft:zombie=25.5\".")
            .defineListAllowEmpty("spawnRejectOverrides", List.of(), obj -> validateChanceEntry(obj));

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static Map<EntityType<?>, Integer> weightOverrides = Map.of();
    public static List<WeightPattern> weightPatternOverrides = List.of();
    public static boolean spawnRejectEnabled;
    public static double defaultSpawnRejectChance;
    public static List<Pattern> defaultRejectPatterns = List.of();
    public static Map<EntityType<?>, Double> spawnRejectOverrides = Map.of();

    public record WeightPattern(String glob, int weight, Pattern globPattern)
    {
    }

    private static boolean validateEntry(final Object obj, boolean requirePositiveWeight)
    {
        if (!(obj instanceof final String entry)) return false;
        var matcher = ENTRY_PATTERN.matcher(entry);
        if (!matcher.matches()) return false;
        String key = matcher.group(1);
        if (!key.contains("*") && !ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(key))) return false;
        int value = Integer.parseInt(matcher.group(2));
        return requirePositiveWeight ? value >= 1 : value >= 0;
    }

    private static boolean validateGlob(final Object obj)
    {
        return obj instanceof final String entry && GLOB_PATTERN.matcher(entry).matches();
    }

    private static boolean validateChanceEntry(final Object obj)
    {
        if (!(obj instanceof final String entry)) return false;
        var matcher = CHANCE_ENTRY_PATTERN.matcher(entry);
        if (!matcher.matches()) return false;
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(matcher.group(1)))) return false;
        double value = Double.parseDouble(matcher.group(2));
        return value >= 0.0 && value <= 100.0;
    }

    private static Map<EntityType<?>, Integer> parseEntries(List<? extends String> entries)
    {
        Map<EntityType<?>, Integer> result = new HashMap<>();
        for (String entry : entries)
        {
            var matcher = ENTRY_PATTERN.matcher(entry);
            if (!matcher.matches()) continue;
            String key = matcher.group(1);
            if (key.contains("*")) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(key));
            if (type == null) continue;
            result.put(type, Integer.parseInt(matcher.group(2)));
        }
        return result;
    }

    private static List<WeightPattern> parseWeightPatterns(List<? extends String> entries)
    {
        List<WeightPattern> result = new ArrayList<>();
        for (String entry : entries)
        {
            var matcher = ENTRY_PATTERN.matcher(entry);
            if (!matcher.matches()) continue;
            String key = matcher.group(1);
            if (!key.contains("*")) continue;
            result.add(new WeightPattern(key, Integer.parseInt(matcher.group(2)), compileGlob(key)));
        }
        return result;
    }

    private static Map<EntityType<?>, Double> parseChanceEntries(List<? extends String> entries)
    {
        Map<EntityType<?>, Double> result = new HashMap<>();
        for (String entry : entries)
        {
            var matcher = CHANCE_ENTRY_PATTERN.matcher(entry);
            if (!matcher.matches()) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(matcher.group(1)));
            if (type == null) continue;
            result.put(type, Double.parseDouble(matcher.group(2)));
        }
        return result;
    }

    private static List<Pattern> parseGlobPatterns(List<? extends String> patterns)
    {
        List<Pattern> result = new ArrayList<>();
        for (String pattern : patterns)
        {
            var matcher = GLOB_PATTERN.matcher(pattern);
            if (!matcher.matches()) continue;
            result.add(compileGlob(pattern));
        }
        return result;
    }

    private static Pattern compileGlob(String glob)
    {
        String normalized = glob.contains("*") ? glob : glob + ":*";
        StringBuilder regex = new StringBuilder("^");
        int segmentStart = 0;
        for (int i = 0; i < normalized.length(); i++)
        {
            if (normalized.charAt(i) == '*')
            {
                regex.append(Pattern.quote(normalized.substring(segmentStart, i)));
                regex.append(".*");
                segmentStart = i + 1;
            }
        }
        regex.append(Pattern.quote(normalized.substring(segmentStart)));
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    public static boolean isDefaultRejectApplicable(String registryKey)
    {
        if (defaultRejectPatterns.isEmpty()) return true;
        for (Pattern pattern : defaultRejectPatterns)
        {
            if (pattern.matcher(registryKey).matches()) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        weightOverrides = parseEntries(WEIGHT_OVERRIDES.get());
        weightPatternOverrides = parseWeightPatterns(WEIGHT_OVERRIDES.get());
        spawnRejectEnabled = SPAWN_REJECT_ENABLED.get();
        defaultSpawnRejectChance = DEFAULT_SPAWN_REJECT_CHANCE.get();
        defaultRejectPatterns = parseGlobPatterns(DEFAULT_SPAWN_REJECT_PATTERNS.get());
        spawnRejectOverrides = parseChanceEntries(SPAWN_REJECT_OVERRIDES.get());
    }
}