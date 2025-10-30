package me.shiny.matesignal;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraft.world.phys.AABB;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Random;

@Mod("matesignal")
public class MateSignal {
    private static final InetSocketAddress TARGET = new InetSocketAddress("127.0.0.1", 32145);

    private static final Set<UUID> inside = new HashSet<>();
    private static final Set<UUID> scratch = new HashSet<>();

    private Object lastDim = null;
    private boolean wasLowHp = false;
    private boolean wasLowHunger = false;
    private long lastTick = -1L;

    private boolean wasRaining = false;
    private boolean wasDead = false;
    private boolean wasHalfDrowning = false;
    private boolean wasSleeping = false;

    private boolean craftingObserved = false;
    private int lastMenuStateId = -1;
    private boolean lastCarriedNonEmpty = false;
    private long lastCarriedBeganAt = 0L;
    private long lastCraftMsgAt = 0L;
    private final Random rng = new Random();

    public MateSignal() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MateSignalConfigScreen(parent)));
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::onClientTick);
    }

    private void onClientTick(TickEvent.ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) {
            inside.clear();
            lastTick = -1L;
            wasRaining = false;
            wasDead = false;
            wasHalfDrowning = false;
            wasSleeping = false;
            craftingObserved = false;
            return;
        }

        if (lastDim == null || lastDim != level.dimension()) {
            inside.clear();
            lastTick = -1L;
            lastDim = level.dimension();
        }

        long t = level.getDayTime() % 24000L;
        if (lastTick >= 0) {
            if (Config.DAY_MESSAGE.get() && crossed(lastTick, t, 23500L)) send("{\"type\":\"time_day\"}");
            if (Config.NIGHT_MESSAGE.get() && crossed(lastTick, t, 12750L)) send("{\"type\":\"time_night\"}");
        }
        lastTick = t;

        float hp = p.getHealth();
        boolean lowHp = hp <= 6.0f;
        if (Config.LOW_HEALTH_MESSAGE.get()) {
            if (lowHp && !wasLowHp) send("{\"type\":\"low_health\",\"hp\":" + String.format(Locale.ROOT,"%.1f", hp) + "}");
        }
        wasLowHp = lowHp;

        FoodData food = p.getFoodData();
        int hunger = food.getFoodLevel();
        boolean lowHung = hunger < 10;
        if (Config.LOW_HUNGER_MESSAGE.get()) {
            if (lowHung && !wasLowHunger) send("{\"type\":\"low_hunger\",\"hunger\":" + hunger + "}");
        }
        wasLowHunger = lowHung;

        boolean rainingNow = level.isRainingAt(p.blockPosition());
        if (Config.RAIN_START_MESSAGE.get()) {
            if (rainingNow && !wasRaining) send("{\"type\":\"rain_start\"}");
        }
        wasRaining = rainingNow;

        boolean deadNow = p.isDeadOrDying() || hp <= 0.0f;
        if (Config.DEATH_MESSAGE.get()) {
            if (deadNow && !wasDead) send("{\"type\":\"death\"}");
        }
        wasDead = deadNow && hp <= 0.0f;

        int maxAir = p.getMaxAirSupply();
        int air = p.getAirSupply();
        boolean halfDrownNow = air <= (maxAir / 2) && air < maxAir && p.isUnderWater();
        if (Config.DROWNING_HALF_MESSAGE.get()) {
            if (halfDrownNow && !wasHalfDrowning) send("{\"type\":\"drowning_half\",\"air\":" + air + ",\"max\":" + maxAir + "}");
        }
        if (air > (int)(maxAir * 0.8f)) wasHalfDrowning = false; else wasHalfDrowning = halfDrownNow;

        boolean sleepingNow = p.isSleeping();
        if (Config.SLEEP_MESSAGE.get()) {
            if (sleepingNow && !wasSleeping) send("{\"type\":\"sleep_start\"}");
        }
        wasSleeping = sleepingNow;

        observeCrafting(mc, p);

        int r = Math.max(3, Math.min(64, Config.RADIUS.get()));
        double r2 = r * r;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        AABB box = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);

        List<Entity> ents = level.getEntities(null, box);
        if (ents.isEmpty()) {
            inside.clear();
            return;
        }

        Set<String> allow = new HashSet<>();
        var cfg = Config.MOBS.get();
        if (cfg != null) for (String s : cfg) if (s != null && !s.isBlank()) allow.add(s.toLowerCase(Locale.ROOT));

        scratch.clear();
        for (Entity en : ents) {
            EntityType<?> type = en.getType();
            if (type.getCategory() != MobCategory.MONSTER) continue;
            if (!en.isAlive()) continue;

            double d2 = en.distanceToSqr(p);
            if (d2 > r2) continue;

            ResourceLocation rid = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (rid == null) continue;
            String typeId = rid.toString();
            String name = rid.getPath();

            if (!allow.isEmpty()) {
                String ln = name.toLowerCase(Locale.ROOT);
                String lid = typeId.toLowerCase(Locale.ROOT);
                if (!allow.contains(ln) && !allow.contains(lid)) continue;
            }

            UUID id = en.getUUID();
            scratch.add(id);

            if (!inside.contains(id)) {
                inside.add(id);
                int dist = (int)Math.floor(Math.sqrt(d2));
                long ts = System.currentTimeMillis();
                String json = "{\"type\":\"mob_proximity\",\"phase\":\"enter\",\"uuid\":\"" + id + "\",\"id\":\"" + typeId + "\",\"name\":\"" + name + "\",\"distance\":" + dist + ",\"ts\":" + ts + "}";
                send(json);
            }
        }

        if (!inside.isEmpty()) inside.retainAll(scratch);
    }

    private void observeCrafting(Minecraft mc, Player p) {
        Screen scr = mc.screen;
        boolean inCraftingUi = scr instanceof CraftingScreen || scr instanceof InventoryScreen;
        if (!Config.CRAFTING_MESSAGE.get() || !inCraftingUi) {
            craftingObserved = false;
            lastMenuStateId = -1;
            lastCarriedNonEmpty = false;
            return;
        }

        AbstractContainerMenu menu = p.containerMenu;
        if (menu == null) return;

        int sid = menu.getStateId();
        boolean carriedNow = !menu.getCarried().isEmpty();

        long now = System.currentTimeMillis();
        if (carriedNow && !lastCarriedNonEmpty) lastCarriedBeganAt = now;

        boolean carriedReleasedQuick = !carriedNow && lastCarriedNonEmpty && (now - lastCarriedBeganAt) <= 1500L;
        boolean stateChanged = lastMenuStateId != -1 && sid != lastMenuStateId;

        if (!craftingObserved && carriedReleasedQuick && stateChanged) {
            if (now - lastCraftMsgAt > 1000L && rng.nextFloat() < 0.30f) {
                send("{\"type\":\"crafted\"}");
                lastCraftMsgAt = now;
                craftingObserved = true;
            }
        }

        if (sid != lastMenuStateId) craftingObserved = false;

        lastCarriedNonEmpty = carriedNow;
        lastMenuStateId = sid;
    }

    private static boolean crossed(long last, long now, long threshold) {
        if (last == now) return false;
        if (last < now) return last < threshold && now >= threshold;
        return last < threshold || now >= threshold;
    }

    private static void send(String json) {
        try (DatagramSocket s = new DatagramSocket()) {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            DatagramPacket pkt = new DatagramPacket(b, b.length, TARGET);
            s.send(pkt);
        } catch (Exception ignored) {}
    }
}
