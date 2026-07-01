package net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.BluemapSignsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class BluemapSignsConfig
        extends AbstractModuleConfig<BluemapSignsModule, BluemapSignsConfig> {

    private ModConfigSpec.ConfigValue<String> prefix;
    private ModConfigSpec.ConfigValue<String> markerSetName;
    private ModConfigSpec.BooleanValue toggleable;
    private ModConfigSpec.BooleanValue defaultHidden;
    private ModConfigSpec.IntValue maxDistance;

    public BluemapSignsConfig(BluemapSignsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        prefix = builder
                .comment("Trigger on line 1 of a sign that turns it into a BlueMap marker.",
                        "Case-insensitive, trimmed. Default \"[bm]\".")
                .define("prefix", "[bm]");

        markerSetName = builder
                .comment("Display name of the marker layer in the BlueMap web UI.")
                .define("marker_set_name", "Map Signs");

        toggleable = builder
                .comment("Whether players can toggle the marker layer on/off in BlueMap.")
                .define("toggleable", true);

        defaultHidden = builder
                .comment("Whether the marker layer starts hidden (players must enable it).")
                .define("default_hidden", false);

        maxDistance = builder
                .comment("Max camera distance (blocks) at which markers stay visible. Large = always.")
                .defineInRange("max_distance", 10000, 1, 10_000_000);
    }

    public String getPrefix() {
        return prefix != null ? prefix.get() : "[bm]";
    }

    public String getMarkerSetName() {
        return markerSetName != null ? markerSetName.get() : "Map Signs";
    }

    public boolean isToggleable() {
        return toggleable == null || toggleable.get();
    }

    public boolean isDefaultHidden() {
        return defaultHidden != null && defaultHidden.get();
    }

    public int getMaxDistance() {
        return maxDistance != null ? maxDistance.get() : 10000;
    }
}
