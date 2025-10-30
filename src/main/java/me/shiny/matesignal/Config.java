package me.shiny.matesignal;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOBS;
    public static final ForgeConfigSpec.BooleanValue DAY_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue NIGHT_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue LOW_HEALTH_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue LOW_HUNGER_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue DEATH_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue RAIN_START_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue DROWNING_HALF_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue SLEEP_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue CRAFTING_MESSAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        RADIUS = b.defineInRange("radius", 10, 3, 64);
        MOBS = b.defineListAllowEmpty(
                List.of("mobs"),
                () -> List.of("minecraft:creeper","minecraft:zombie"),
                o -> o instanceof String s && !s.isBlank()
        );
        DAY_MESSAGE = b.define("dayMessage", true);
        NIGHT_MESSAGE = b.define("nightMessage", true);
        LOW_HEALTH_MESSAGE = b.define("lowHealthMessage", true);
        LOW_HUNGER_MESSAGE = b.define("lowHungerMessage", true);
        DEATH_MESSAGE = b.define("deathMessage", true);
        RAIN_START_MESSAGE = b.define("rainStartMessage", true);
        DROWNING_HALF_MESSAGE = b.define("drowningHalfMessage", true);
        SLEEP_MESSAGE = b.define("sleepMessage", true);
        CRAFTING_MESSAGE = b.define("craftingMessage", true);
        SPEC = b.build();
    }

    private Config() {}
}
