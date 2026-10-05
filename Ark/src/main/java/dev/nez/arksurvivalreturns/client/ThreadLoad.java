package dev.nez.arksurvivalreturns.client;

import java.lang.management.ManagementFactory;
import net.minecraft.client.Minecraft;

/**
 * Share of one processor core used by the render thread and by the integrated server's thread, for the debug
 * screen. The figures are slow to read (tens of milliseconds), so another thread reads them once a second, and
 * only while they are being asked for.
 */
public final class ThreadLoad {
    private static final long IDLE_NANOS = 3_000_000_000L;
    private static volatile double render = -1, server = -1;
    private static volatile long asked;
    private static Thread watcher;

    /** The latest figures in words, empty until the first second has passed. Call from the render thread. */
    public static String summary() {
        asked = System.nanoTime();
        if (watcher == null || !watcher.isAlive()) {
            long renderThread = Thread.currentThread().threadId();
            watcher = new Thread(() -> watch(renderThread), "Ark thread load");
            watcher.setDaemon(true);
            watcher.start();
        }
        double renderNow = render, serverNow = server;
        if (renderNow < 0) return "";
        return "render " + Math.round(renderNow * 100) + " %" + (serverNow < 0 ? "" : ", server " + Math.round(serverNow * 100) + " %");
    }

    private static void watch(long renderThread) {
        var threads = ManagementFactory.getThreadMXBean();
        long at = System.nanoTime(), renderTime = threads.getThreadCpuTime(renderThread), serverThread = -1, serverTime = -1;
        while (System.nanoTime() - asked < IDLE_NANOS) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
            long now = System.nanoTime(), renderNow = threads.getThreadCpuTime(renderThread);
            var integrated = Minecraft.getInstance().getSingleplayerServer();
            long thread = integrated == null ? -1 : integrated.getRunningThread().threadId();
            long serverNow = thread < 0 ? -1 : threads.getThreadCpuTime(thread);
            render = renderNow < 0 || renderTime < 0 ? -1 : (renderNow - renderTime) / (double) (now - at);
            // Another world runs on another thread: its first reading has nothing to be compared with.
            server = serverNow < 0 || serverTime < 0 || thread != serverThread ? -1 : (serverNow - serverTime) / (double) (now - at);
            renderTime = renderNow;
            serverThread = thread;
            serverTime = serverNow;
            at = now;
        }
        render = server = -1;
    }

    private ThreadLoad() {}
}
