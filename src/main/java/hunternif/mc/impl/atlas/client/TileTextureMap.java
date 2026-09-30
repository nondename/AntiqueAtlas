package hunternif.mc.impl.atlas.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import hunternif.mc.impl.atlas.AntiqueAtlas;
import hunternif.mc.impl.atlas.client.texture.ITexture;
import hunternif.mc.impl.atlas.core.scaning.TileHeightType;
import hunternif.mc.impl.atlas.util.Log;
//import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.Tags;

/**
 * Maps biome IDs (or pseudo IDs) to textures. <i>Not thread-safe!</i>
 * <p>If several textures are set for one ID, one will be chosen at random when
 * putting tile into Atlas.</p>
 *
 * @author Hunternif
 */
public class TileTextureMap {
    private static final TileTextureMap INSTANCE = new TileTextureMap();

    public static final ResourceLocation DEFAULT_TEXTURE = AntiqueAtlas.id("test");

    public static TileTextureMap instance() {
        return INSTANCE;
    }

    /**
     * This map stores the pseudo biome texture mappings, any biome with ID <0 is assumed to be a pseudo biome
     */
    private final Map<ResourceLocation, TextureSet> textureMap = new HashMap<>();

    /**
     * Assign texture set to pseudo biome
     */
    public void setTexture(ResourceLocation tileId, TextureSet textureSet) {
        if (tileId == null) return;

        if (textureSet == null) {
            if (textureMap.remove(tileId) != null) {
                Log.warn("Removing old texture for %d", tileId);
            }
            return;
        }

        textureMap.put(tileId, textureSet);
    }

    /**
     * Assign the same texture set to all height variations of the tileId
     */
    public void setAllTextures(ResourceLocation tileId, TextureSet textureSet) {
        setTexture(tileId, textureSet);

        for (TileHeightType layer : TileHeightType.values()) {
            setTexture(ResourceLocation.tryParse(tileId + "_" + layer), textureSet);
        }
    }

    public TextureSet getDefaultTexture() {
        return TextureSetMap.instance().getByName(DEFAULT_TEXTURE);
    }

    /**
     * Find the most appropriate standard texture set depending on
     * BiomeDictionary types.
     */
    public void autoRegister(ResourceLocation id, ResourceKey<Biome> biome) {
        if (biome == null || id == null) {
            Log.error("Given biome is null. Cannot autodetect a suitable texture set for that.");
            return;
        }

        Optional<ResourceLocation> textureSet = guessFittingTextureSet(biome);

        // 1.20.1 lost the old BiomeCategory fallback, and many modded biomes do
        // not participate in the vanilla/Forge tags checked above. In upstream
        // 8.0.1 this makes them fall back to the built-in `test` texture set,
        // which is intentionally a loud debug/checkerboard texture. Try a
        // conservative name-based fallback before giving up.
        if (textureSet.isEmpty()) {
            textureSet = guessFittingTextureSetById(id);
        }

        // Never render an otherwise valid biome as the debug tile. An imperfect
        // plains fallback is much more useful on a map than a diagnostic texture.
        ResourceLocation selected = textureSet.orElse(AntiqueAtlas.id("plains"));
        TextureSet set = TextureSetMap.instance().getByName(selected);

        if (set == null) {
            Log.warn("Texture set %s selected for biome %s is missing; falling back to plains.", selected, id);
            selected = AntiqueAtlas.id("plains");
            set = TextureSetMap.instance().getByName(selected);
        }

        if (set == null) {
            // Keep the original debug fallback only for a genuinely broken
            // resource pack where even the built-in plains set is unavailable.
            Log.error("Failed to find a usable texture set for biome '%s'. Falling back to the debug texture.", id);
            set = getDefaultTexture();
        }

        setAllTextures(id, set);
        Log.info("Auto-registered standard texture set for biome %s: %s", id, selected);
    }

    /**
     * Best-effort fallback for biomes that expose no useful vanilla/Forge tags.
     * This is intentionally based only on the registry id so it also works with
     * third-party biome mods without adding hard dependencies on them.
     */
    static Optional<ResourceLocation> guessFittingTextureSetById(ResourceLocation biomeId) {
        String path = biomeId.getPath().toLowerCase(Locale.ROOT);

        boolean cold = containsAny(path, "snow", "frozen", "ice", "icy", "glacier", "tundra", "frost");

        if (containsAny(path, "ocean", "river", "lake", "water", "bay", "reef", "lagoon")) {
            return Optional.of(AntiqueAtlas.id(cold ? "ice" : "water"));
        }
        if (containsAny(path, "beach", "shore", "coast")) {
            return Optional.of(AntiqueAtlas.id("shore"));
        }
        if (containsAny(path, "swamp", "marsh", "bog", "mangrove", "fen")) {
            return Optional.of(AntiqueAtlas.id("swamp"));
        }
        if (containsAny(path, "jungle", "rainforest", "tropical")) {
            return Optional.of(AntiqueAtlas.id("jungle"));
        }
        if (containsAny(path, "savanna", "savannah")) {
            return Optional.of(AntiqueAtlas.id("savana"));
        }
        if (containsAny(path, "badlands", "mesa", "canyon")) {
            return Optional.of(AntiqueAtlas.id("plateau_mesa"));
        }
        if (containsAny(path, "taiga", "pine", "conifer", "spruce")) {
            return Optional.of(AntiqueAtlas.id(cold ? "snow_pines" : "pines"));
        }
        if (containsAny(path, "forest", "wood", "woods", "woodland", "grove")) {
            return Optional.of(AntiqueAtlas.id(cold ? "snow_pines" : "forest"));
        }
        if (containsAny(path, "desert", "dune", "arid")) {
            return Optional.of(AntiqueAtlas.id("desert"));
        }
        if (containsAny(path, "mountain", "peak", "alps", "highland")) {
            return Optional.of(AntiqueAtlas.id(cold ? "mountains_snow_caps" : "mountains"));
        }
        if (containsAny(path, "hill")) {
            return Optional.of(AntiqueAtlas.id(cold ? "snow_hills" : "hills"));
        }
        if (containsAny(path, "mushroom", "mycel")) {
            return Optional.of(AntiqueAtlas.id("mushroom"));
        }
        if (containsAny(path, "plains", "meadow", "field", "prairie", "steppe")) {
            return Optional.of(AntiqueAtlas.id(cold ? "snow" : "plains"));
        }
        if (cold) {
            return Optional.of(AntiqueAtlas.id("snow"));
        }

        return Optional.empty();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static public Optional<ResourceLocation> guessFittingTextureSet(ResourceKey<Biome> biome) {
        if (Minecraft.getInstance().level == null)
            return Optional.empty();

        Holder<Biome> biomeTag = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);

        if (biomeTag.is(Tags.Biomes.IS_SWAMP)) {
            if (biomeTag.is(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("swamp_hills"));
            } else {
                return Optional.of(AntiqueAtlas.id("swamp"));
            }
        }

        if (biomeTag.is(BiomeTags.IS_OCEAN)
                || biomeTag.is(BiomeTags.IS_DEEP_OCEAN)
                || biomeTag.is(BiomeTags.IS_RIVER)
                /*|| biomeTag.is(Tags.Biomes.IS_AQUATIC)*/) {
            if (biomeTag.is(Tags.Biomes.IS_COLD))
                return Optional.of(AntiqueAtlas.id("ice"));

            return Optional.of(AntiqueAtlas.id("water"));
        }

        if (biomeTag.is(BiomeTags.IS_BEACH)/* || biomeTag.is(Tags.Biomes.IS_BEACH)*/) {
            return Optional.of(AntiqueAtlas.id("shore"));
        }

        if (biomeTag.is(BiomeTags.IS_JUNGLE)) {
            if (biomeTag.is(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("jungle_hills"));
            } else {
                return Optional.of(AntiqueAtlas.id("jungle"));
            }
        }

        if (biomeTag.is(BiomeTags.IS_SAVANNA)) {
            return Optional.of(AntiqueAtlas.id("savana"));
        }

        if (biomeTag.is((BiomeTags.IS_BADLANDS))) {
            return Optional.of(AntiqueAtlas.id("plateau_mesa"));
        }

        if (biomeTag.is(BiomeTags.IS_FOREST) /*|| biomeTag.is(Tags.Biomes.IS_DENSE_VEGETATION) || biomeTag.is(Tags.Biomes.IS_DECIDUOUS_TREE) || biomeTag.is(Tags.Biomes.IS_CONIFEROUS_TREE)*/) {
            if (biomeTag.is(Tags.Biomes.IS_COLD) || biomeTag.is(Tags.Biomes.IS_SNOWY)) {
                if (biomeTag.is(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("snow_pines_hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("snow_pines"));
                }
            } else {
                if (biomeTag.is(Tags.Biomes.IS_CONIFEROUS/*_TREE*/)) {
                    if (biomeTag.is(BiomeTags.IS_HILL)) {
                        return Optional.of(AntiqueAtlas.id("pines_hills"));
                    } else {
                        return Optional.of(AntiqueAtlas.id("pines"));
                    }
                }
                else {
                    if (biomeTag.is(BiomeTags.IS_HILL)) {
                        return Optional.of(AntiqueAtlas.id("forest_hills"));
                    } else {
                        return Optional.of(AntiqueAtlas.id("forest"));
                    }
                }
            }
        }

        if (biomeTag.is(Tags.Biomes.IS_PLAINS)/* || biomeTag.is(Tags.Biomes.IS_SNOWY_PLAINS) || biomeTag.is(Tags.Biomes.IS_SPARSE_VEGETATION)*/) {
            if (/*biomeTag.is(Tags.Biomes.IS_ICY)
                    || */biomeTag.is(Tags.Biomes.IS_SNOWY)
            ) {
                if (biomeTag.is(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("snow_hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("snow"));
                }
            } else {
                if (biomeTag.is(BiomeTags.IS_HILL)) {
                    return Optional.of(AntiqueAtlas.id("hills"));
                } else {
                    return Optional.of(AntiqueAtlas.id("plains"));
                }
            }
        }

        if (biomeTag.is(Tags.Biomes.IS_COLD)) {
            if (biomeTag.is(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("mountains_snow_caps"));
            } else {
                return Optional.of(AntiqueAtlas.id("ice_spikes"));
            }
        }

        if (biomeTag.is(Tags.Biomes.IS_DESERT)) {
            if (biomeTag.is(BiomeTags.IS_HILL)) {
                return Optional.of(AntiqueAtlas.id("desert_hills"));
            } else {
                return Optional.of(AntiqueAtlas.id("desert"));
            }
        }

        if (biomeTag.is(BiomeTags.IS_TAIGA)) {
            return Optional.of(AntiqueAtlas.id("snow"));
        }

//        if (biomeTag.is(Tags.Biomes.ex)) {
//            return Optional.of(AntiqueAtlas.id("hills"));
//        }

        if (biomeTag.is(Tags.Biomes.IS_MOUNTAIN)/* || biomeTag.is(Tags.Biomes.IS_MOUNTAIN_SLOPE)*/) {
            return Optional.of(AntiqueAtlas.id("mountains"));
        }

//        if (biomeTag.is(Tags.Biomes.IS_MOUNTAIN_PEAK)) {
//            return Optional.of(AntiqueAtlas.id("mountains_snow_caps"));
//        }

        if (biomeTag.is(BiomeTags.IS_END)/* || biomeTag.is(Tags.Biomes.IS_OUTER_END_ISLAND)*/) {
            if (biomeTag.is(Tags.Biomes.IS_DENSE_END) || biomeTag.is(Tags.Biomes.IS_SPARSE_END)) {
                return Optional.of(AntiqueAtlas.id("end_island_plants"));
            } else {
                return Optional.of(AntiqueAtlas.id("end_island"));
            }
        }

        if (biomeTag.is(Tags.Biomes.IS_MUSHROOM)) {
            return Optional.of(AntiqueAtlas.id("mushroom"));
        }

        if (/*biomeTag.is(Tags.Biomes.IS_NETHER) || */biomeTag.is(BiomeTags.IS_NETHER)) {
            return Optional.of(AntiqueAtlas.id("soul_sand_valley"));
        }

        if (biomeTag.is(Tags.Biomes.IS_VOID)) {
            return Optional.of(AntiqueAtlas.id("end_void"));
        }

        if (biomeTag.is(Tags.Biomes.IS_UNDERGROUND)) {
            AntiqueAtlas.LOG.warn("Underground biomes aren't supported yet.");
        }

        if (biomeTag.is(BiomeTags.IS_BADLANDS)) {
            return Optional.of(AntiqueAtlas.id("mesa"));
        }

        return TileTextureMap.guessFittingTextureSetFallback(biomeTag.value());
    }

    static public Optional<ResourceLocation> guessFittingTextureSetFallback(Biome biome) {
//        ResourceLocation texture_set = switch (biome.getBiomeCategory()) {
//            case SWAMP -> AntiqueAtlasMod.id("swamp");
//            case OCEAN, RIVER ->
//                    biome.getPrecipitation() == Biome.Precipitation.SNOW ? AntiqueAtlasMod.id("ice") : AntiqueAtlasMod.id("water");
//            case BEACH -> AntiqueAtlasMod.id("shore");
//            case JUNGLE -> AntiqueAtlasMod.id("jungle");
//            case SAVANNA -> AntiqueAtlasMod.id("savanna");
//            case MESA -> AntiqueAtlasMod.id("plateau_mesa");
//            case FOREST ->
//                    biome.getPrecipitation() == Biome.Precipitation.SNOW ? AntiqueAtlasMod.id("snow_pines") : AntiqueAtlasMod.id("forest");
//            case PLAINS ->
//                    biome.getPrecipitation() == Biome.Precipitation.SNOW ? AntiqueAtlasMod.id("snow") : AntiqueAtlasMod.id("plains");
//            case ICY -> AntiqueAtlasMod.id("ice_spikes");
//            case DESERT -> AntiqueAtlasMod.id("desert");
//            case TAIGA -> AntiqueAtlasMod.id("snow");
//            case EXTREME_HILLS -> AntiqueAtlasMod.id("hills");
//            case MOUNTAIN -> AntiqueAtlasMod.id("mountains");
//            case THEEND -> {
//                List<HolderSet<PlacedFeature>> features = biome.getGenerationSettings().features();
//                PlacedFeature chorus_plant_feature = BuiltinRegistries.PLACED_FEATURE.get(VersionHelper.toLoc("chorus_plant"));
//                assert chorus_plant_feature != null;
//                boolean has_chorus_plant = features.stream().anyMatch(entries -> entries.stream().anyMatch(feature -> feature.value() == chorus_plant_feature));
//                if (has_chorus_plant) {
//                    yield AntiqueAtlasMod.id("end_island_plants");
//                } else {
//                    yield AntiqueAtlasMod.id("end_island");
//                }
//            }
//            case MUSHROOM -> AntiqueAtlasMod.id("mushroom");
//            case NETHER -> AntiqueAtlasMod.id("soul_sand_valley");
//            case NONE -> AntiqueAtlasMod.id("end_void");
//            case UNDERGROUND -> {
//                Log.warn("Underground biomes aren't supported yet.");
//                yield null;
//            }
//        };

        return Optional.ofNullable(/* texture_set */null);
    }

    public boolean isRegistered(ResourceLocation id) {
        return textureMap.containsKey(id);
    }

    /**
     * If unknown biome, auto-registers a texture set. If null, returns default set.
     */
    public TextureSet getTextureSet(ResourceLocation tile) {
        if (tile == null) {
            return getDefaultTexture();
        }

        return textureMap.getOrDefault(tile, getDefaultTexture());
    }

    public ITexture getTexture(SubTile subTile) {
        return getTextureSet(subTile.tile).getTexture(subTile.variationNumber);
    }

    public List<ResourceLocation> getAllTextures() {
        List<ResourceLocation> list = new ArrayList<>();

        for (Entry<ResourceLocation, TextureSet> entry : textureMap.entrySet()) {
            Arrays.stream(entry.getValue().textures).forEach(iTexture -> list.add(iTexture.getTexture()));
        }

        return list;
    }
}
