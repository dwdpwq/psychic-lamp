package com.visualcrafting;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_FORMAT;
    public static final ModConfigSpec.ConfigValue<Integer> FURNACE_CARD_ENABLED;

    public static int getFormatValue() {
        return "CRT".equalsIgnoreCase(DEFAULT_FORMAT.get()) ? 1 : 0;
    }

    public static void setFormatValue(int n) {
        DEFAULT_FORMAT.set(n == 1 ? "CRT" : "KUBEJS");
        DEFAULT_FORMAT.save();
    }

    public static boolean isFurnaceCardEnabled() {
        return FURNACE_CARD_ENABLED.get() != 0;
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEFAULT_FORMAT = builder
                .comment("Default recipe output format for newly placed Visual Crafting Tables.",
                         "Options: KUBEJS, CRT")
                .define("default_format", "KUBEJS");
        FURNACE_CARD_ENABLED = builder
                .comment("Enable Furnace Card synthesis and AE2 integration.",
                         "1 = enabled, 0 = disabled")
                .define("furnace_card_enabled", 1);
        SPEC = builder.build();
    }
}
