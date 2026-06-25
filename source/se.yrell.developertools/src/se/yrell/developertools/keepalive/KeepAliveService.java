
package se.yrell.developertools.keepalive;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ARServerUser;
import com.bmc.arsys.studio.model.ModelException;
import com.bmc.arsys.studio.model.store.ARServerStore;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.store.StoreManager;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Lightweight keepalive service for connected BMC Developer Studio AR sessions.
 *
 * It only calls ARServerUser.verifyUser() on already connected ARServerStore
 * instances. It does not browse forms, workflow, object lists or metadata.
 */
public final class KeepAliveService {
    private static final KeepAliveService INSTANCE = new KeepAliveService();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean tickRunning = new AtomicBoolean(false);
    private final Map<String, AtomicBoolean> serverRunning = new ConcurrentHashMap<String, AtomicBoolean>();
    private final AtomicInteger tickNumber = new AtomicInteger(0);

    private volatile ScheduledExecutorService scheduler;
    private volatile PropertyChangeListener storeListener;
    private volatile int activeIntervalSeconds;

    private KeepAliveService() {
        // singleton
    }

    public static KeepAliveService getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (!ToolsPreferences.isKeepAliveEnabled()) {
            Log.info("Keepalive disabled by preferences");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            int configured = ToolsPreferences.getKeepAliveIntervalSeconds();
            if (configured != activeIntervalSeconds) {
                reconfigure();
            }
            return;
        }

        installStoreListener();
        activeIntervalSeconds = ToolsPreferences.getKeepAliveIntervalSeconds();
        scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Yrell Developer Tools AR Keepalive");
                t.setDaemon(true);
                return t;
            }
        });
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                safeTick("schedule");
            }
        }, Math.min(15L, activeIntervalSeconds), activeIntervalSeconds, TimeUnit.SECONDS);

        Log.info("Keepalive started; intervalSeconds=" + activeIntervalSeconds);
    }

    public synchronized void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        ScheduledExecutorService s = scheduler;
        scheduler = null;
        if (s != null) {
            s.shutdownNow();
        }
        uninstallStoreListener();
        tickRunning.set(false);
        serverRunning.clear();
        Log.info("Keepalive stopped");
    }

    public synchronized void reconfigure() {
        boolean enabled = ToolsPreferences.isKeepAliveEnabled();
        int interval = ToolsPreferences.getKeepAliveIntervalSeconds();
        if (!enabled) {
            stop();
            return;
        }
        if (!started.get()) {
            start();
            return;
        }
        if (interval != activeIntervalSeconds) {
            stop();
            start();
        }
    }

    private void safeTick(String reason) {
        if (!started.get() || !ToolsPreferences.isKeepAliveEnabled()) {
            return;
        }
        if (!tickRunning.compareAndSet(false, true)) {
            Log.info("Keepalive tick skipped; previous tick is still running");
            return;
        }
        try {
            tick(reason);
        } catch (Throwable t) {
            Log.error("Unexpected keepalive tick error", t);
        } finally {
            tickRunning.set(false);
        }
    }

    private void tick(String reason) {
        int currentTick = tickNumber.incrementAndGet();
        Collection<IStore> stores;
        try {
            stores = StoreManager.getInstance().getStores();
        } catch (Throwable t) {
            Log.warn("Keepalive tick #" + currentTick + " could not read StoreManager stores: "
                    + t.getClass().getName() + ": " + safeMessage(t));
            return;
        }

        if (stores == null || stores.isEmpty()) {
            Log.info("Keepalive tick #" + currentTick + " no stores available yet");
            return;
        }

        List<IStore> snapshot = new ArrayList<IStore>(stores);
        int connectedArStores = 0;
        int arStores = 0;
        for (IStore store : snapshot) {
            if (!(store instanceof ARServerStore)) {
                continue;
            }
            arStores++;
            if (!safeIsConnected(store)) {
                continue;
            }
            connectedArStores++;
            keepAlive((ARServerStore) store);
        }

        if (connectedArStores == 0) {
            Log.info("Keepalive tick #" + currentTick + " no connected ARServerStore found; arStores=" + arStores);
        } else {
            Log.info("Keepalive tick #" + currentTick + " completed; connectedArStores=" + connectedArStores
                    + "; reason=" + reason);
        }
    }

    private boolean safeIsConnected(IStore store) {
        try {
            return store != null && store.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    private void keepAlive(ARServerStore store) {
        String key = safeStoreName(store);
        AtomicBoolean running = serverRunning.get(key);
        if (running == null) {
            AtomicBoolean created = new AtomicBoolean(false);
            AtomicBoolean existing = serverRunning.putIfAbsent(key, created);
            running = existing == null ? created : existing;
        }
        if (!running.compareAndSet(false, true)) {
            Log.info("Previous keepalive is still running for " + key + "; skipping");
            return;
        }

        long startedAt = System.currentTimeMillis();
        try {
            ARServerUser ctx = store.getContext();
            if (ctx == null) {
                Log.info(key + " has no AR context; skipping keepalive");
                return;
            }
            ctx.verifyUser();
            long elapsed = System.currentTimeMillis() - startedAt;
            Log.info(key + " keepalive verifyUser OK " + elapsed + " ms");
        } catch (ModelException e) {
            long elapsed = System.currentTimeMillis() - startedAt;
            Log.warn(key + " keepalive store/context error after " + elapsed + " ms: " + safeMessage(e));
        } catch (ARException e) {
            long elapsed = System.currentTimeMillis() - startedAt;
            Log.warn(key + " keepalive verifyUser failed after " + elapsed + " ms: " + safeMessage(e));
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - startedAt;
            Log.error(key + " keepalive failed after " + elapsed + " ms", t);
        } finally {
            running.set(false);
        }
    }

    private void installStoreListener() {
        try {
            if (storeListener != null) {
                return;
            }
            storeListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent event) {
                    ScheduledExecutorService s = scheduler;
                    if (s != null && !s.isShutdown()) {
                        s.schedule(new Runnable() {
                            @Override
                            public void run() {
                                safeTick("store-event");
                            }
                        }, 5L, TimeUnit.SECONDS);
                    }
                }
            };
            StoreManager.getInstance().addPropertyChangeListener(storeListener);
            Log.info("Keepalive StoreManager listener installed");
        } catch (Throwable t) {
            Log.warn("Could not install keepalive StoreManager listener: " + t.getClass().getName() + ": " + safeMessage(t));
        }
    }

    private void uninstallStoreListener() {
        PropertyChangeListener listener = storeListener;
        if (listener == null) {
            return;
        }
        try {
            StoreManager.getInstance().removePropertyChangeListener(listener);
        } catch (Throwable ignored) {
            // shutdown path
        } finally {
            storeListener = null;
        }
    }

    private static String safeStoreName(IStore store) {
        if (store == null) {
            return "<null>";
        }
        try {
            String name = store.getName();
            if (name != null && name.trim().length() > 0) {
                return name;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        if (store instanceof ARServerStore) {
            try {
                ARServerUser ctx = ((ARServerStore) store).getContext();
                if (ctx != null && ctx.getServer() != null && ctx.getServer().trim().length() > 0) {
                    return ctx.getServer();
                }
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return store.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(store));
    }

    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "";
        }
        String message = t.getMessage();
        return message == null ? t.getClass().getName() : message;
    }
}
