package rtx.kimiko.api.modules.impl.Utils;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.Kimiko;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.EventBus;
import rtx.kimiko.api.events.impl.game.TickEvent;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.ModuleManager;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.utils.optimization.OcclusionCuller;

public final class Optimization extends Module {

    private static final int[] RENDER_DISTANCE_CAP    = { 0, 14, 10, 6 };
    private static final double[] VIEW_SCALE           = { 1.0, 0.85, 0.65, 0.45 };
    private static final double[] PARTICLE_DIST_SQ     = { 2304.0, 1024.0, 484.0, 196.0 };
    private static final double[] BLOCK_ENTITY_DIST_SQ = { 2304.0, 1296.0, 576.0, 256.0 };

    private static int cpuCores = 0;
    private static long totalMemMB = 0;
    private static String cpuName = "";
    private static String gpuInfo = "";
    private static boolean hwDetected = false;

    private static final int FPS_SAMPLES = 20;
    private final double[] fpsBuffer = new double[FPS_SAMPLES];
    private int fpsIndex = 0;
    private double avgFps = 0;

    private static Optimization instance;

    private final ModeSetting mode = this.register(new ModeSetting(
        "\u0420\u0435\u0436\u0438\u043c \u043e\u043f\u0442\u0438\u043c\u0438\u0437\u0430\u0446\u0438\u0438",
        "\u0421\u0440\u0435\u0434\u043d\u0438\u0439",
        "\u041d\u0438\u0437\u043a\u0438\u0439",
        "\u0421\u0440\u0435\u0434\u043d\u0438\u0439",
        "\u0412\u044b\u0441\u043e\u043a\u0438\u0439",
        "\u0423\u043b\u044c\u0442\u0440\u0430"
    ));
    private final BooleanSetting entityCulling = this.register(new BooleanSetting(
        "\u041a\u0443\u043b\u043b\u0438\u043d\u0433 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439",
        "\u0421\u043a\u0440\u044b\u0432\u0430\u0435\u0442 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0438 \u0437\u0430 \u0431\u043b\u043e\u043a\u0430\u043c\u0438.", true));
    private final BooleanSetting limitRenderDistance = this.register(new BooleanSetting(
        "\u041e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u0438\u0435 \u043f\u0440\u043e\u0440\u0438\u0441\u043e\u0432\u043a\u0438",
        "\u041e\u0433\u0440\u0430\u043d\u0438\u0447\u0438\u0432\u0430\u0435\u0442 \u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c \u043f\u0440\u043e\u0440\u0438\u0441\u043e\u0432\u043a\u0438.", true)
        .visibleWhen(() -> this.tier() >= 1));
    private final BooleanSetting reduceParticles = this.register(new BooleanSetting(
        "\u0423\u043c\u0435\u043d\u044c\u0448\u0435\u043d\u0438\u0435 \u0447\u0430\u0441\u0442\u0438\u0446",
        "\u041e\u0433\u0440\u0430\u043d\u0438\u0447\u0438\u0432\u0430\u0435\u0442 \u0434\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044e \u0447\u0430\u0441\u0442\u0438\u0446.", true)
        .visibleWhen(() -> this.tier() >= 1));
    private final BooleanSetting disableBlockEntities = this.register(new BooleanSetting(
        "\u041e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u0438\u0435 \u0431\u043b\u043e\u043a \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u044f",
        "\u0421\u043e\u043a\u0440\u0430\u0449\u0430\u0435\u0442 \u0434\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044e \u0431\u043b\u043e\u043a\u043e\u0432 \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u044f.", true)
        .visibleWhen(() -> this.tier() >= 1));
    private final BooleanSetting aggressiveChunkLoading = this.register(new BooleanSetting(
        "\u0410\u0433\u0440\u0435\u0441\u0441\u0438\u0432\u043d\u0430\u044f \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0430",
        "\u041f\u0440\u0438\u043e\u0440\u0438\u0442\u0435\u0442\u043d\u043e \u0437\u0430\u0433\u0440\u0443\u0436\u0430\u0435\u0442 \u0431\u043b\u0438\u0436\u043d\u0438\u0435 \u0447\u0430\u043d\u043a\u0438.", true)
        .visibleWhen(() -> this.tier() >= 2));
    private final BooleanSetting hideAnimations = this.register(new BooleanSetting(
        "\u0421\u043a\u0440\u044b\u0442\u044c \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0438",
        "\u041e\u0442\u043a\u043b\u044e\u0447\u0430\u0435\u0442 \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0438.", false)
        .visibleWhen(() -> this.tier() >= 2));

    public Optimization() {
        super("Optimization",
              "\u041f\u043e\u0434\u043d\u0438\u043c\u0430\u0435\u0442 FPS: \u043a\u0443\u043b\u043b\u0438\u043d\u0433, \u0447\u0430\u0441\u0442\u0438\u0446\u044b, \u0431\u043b\u043e\u043a\u0438, \u043e\u0431\u043b\u0430\u043a\u0430.",
              Category.UTILS);
        instance = this;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        detectHardware();
        EventBus.get().subscribe(this);
        Kimiko.LOGGER.info("[Optimization] tier={} | CPU={} ({} cores) | RAM={}MB | GPU={}",
                mode.getValue(), cpuName, cpuCores, totalMemMB, gpuInfo);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        EventBus.get().unsubscribe(this);
    }

    @EventHandler
    private void onTick(TickEvent tickEvent) {
        if (tickEvent.isPost()) updateFps();
    }

    private static void detectHardware() {
        if (hwDetected) return;
        hwDetected = true;
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            cpuCores = Runtime.getRuntime().availableProcessors();
            totalMemMB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
            cpuName = os.getName() + " " + os.getArch();
            RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
            if (rt != null && rt.getVmName() != null) cpuName += " [" + rt.getVmName() + "]";
        } catch (Exception e) {
            cpuCores = Runtime.getRuntime().availableProcessors();
            totalMemMB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
            cpuName = "Unknown CPU";
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getWindow() != null && mc.getWindow().getFramebufferWidth() > 0) {
                gpuInfo = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR)
                        + " " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
            } else {
                gpuInfo = "detect on next frame";
            }
        } catch (Exception e) {
            gpuInfo = "Unknown GPU";
        }
    }

    private void updateFps() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int currentFps = mc.getCurrentFps();
        fpsBuffer[fpsIndex % FPS_SAMPLES] = currentFps;
        fpsIndex++;
        if (fpsIndex >= FPS_SAMPLES) {
            double sum = 0;
            for (double v : fpsBuffer) sum += v;
            avgFps = sum / FPS_SAMPLES;
        }
    }

    /* ─── Public API for mixins ─── */
    public static int capEffectiveRenderDistance(int n) {
        if (!active() || !instance.limitRenderDistance.getValue()) return n;
        int cap = RENDER_DISTANCE_CAP[instance.tier()];
        return cap > 0 ? Math.min(n, cap) : n;
    }

    public static boolean allowBlockEntity(double distSq) {
        if (!active() || !instance.disableBlockEntities.getValue()) return true;
        return distSq <= BLOCK_ENTITY_DIST_SQ[instance.tier()];
    }

    public static boolean allowParticle(double x, double y, double z) {
        if (!active() || !instance.reduceParticles.getValue()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null) return true;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return true;
        Vec3d camPos = camera.getCameraPos();
        double dx = x - camPos.x;
        double dy = y - camPos.y;
        double dz = z - camPos.z;
        return (dx * dx + dy * dy + dz * dz) <= PARTICLE_DIST_SQ[instance.tier()];
    }

    public static double scaleEntityViewScale(double original) {
        return active() ? original * VIEW_SCALE[instance.tier()] : original;
    }

    public static boolean hideEntityShadows() {
        return active() && instance.tier() >= 1;
    }

    public static boolean shouldRenderEntity(boolean original, Entity entity, double dx, double dy, double dz) {
        if (!(original && active() && instance.entityCulling.getValue())) return original;
        return OcclusionCuller.isVisible(entity, dx, dy, dz, instance.tier());
    }

    public static boolean shaderTransparency(boolean original) {
        return active() && instance.tier() >= 1 ? false : original;
    }

    public static boolean cullClouds() {
        return active() && instance.tier() >= 3;
    }

    public static boolean isUltra() {
        return active() && instance.tier() >= 3;
    }

    public static boolean hideGuiAnimations() {
        return active() && instance.tier() >= 2 && instance.hideAnimations.getValue();
    }

    public static Optimization getInstance() {
        return ModuleManager.get().get(Optimization.class);
    }

    private static boolean active() {
        return instance != null && instance.isEnabled();
    }

    private int tier() {
        String v = mode.getValue();
        if ("\u041d\u0438\u0437\u043a\u0438\u0439".equals(v)) return 0;
        if ("\u0421\u0440\u0435\u0434\u043d\u0438\u0439".equals(v)) return 1;
        if ("\u0412\u044b\u0441\u043e\u043a\u0438\u0439".equals(v)) return 2;
        if ("\u0423\u043b\u044c\u0442\u0440\u0430".equals(v)) return 3;
        return 1;
    }

    public double getAvgFps() { return avgFps; }
    public int getCpuCores() { return cpuCores; }
    public long getTotalMemMB() { return totalMemMB; }
    public String getCpuName() { return cpuName; }
    public String getGpuInfo() { return gpuInfo; }
}
