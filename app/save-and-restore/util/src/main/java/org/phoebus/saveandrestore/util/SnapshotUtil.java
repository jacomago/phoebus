package org.phoebus.saveandrestore.util;

import org.epics.vtype.VType;
import org.phoebus.applications.saveandrestore.model.ConfigPv;
import org.phoebus.applications.saveandrestore.model.Configuration;
import org.phoebus.applications.saveandrestore.model.ConfigurationData;
import org.phoebus.applications.saveandrestore.model.RestoreResult;
import org.phoebus.applications.saveandrestore.model.SnapshotItem;
import org.phoebus.core.vtypes.VTypeHelper;
import org.phoebus.framework.preferences.Preference;
import org.phoebus.framework.preferences.PropertyPreferenceLoader;
import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides some utility methods to read and write PVs in an asynchronous manner.
 */
public class SnapshotUtil {

    private final Logger LOG = Logger.getLogger(SnapshotUtil.class.getName());

    private int connectionTimeout = Preferences.connectionTimeout;

    private int writeTimeout = Preferences.writeTimeout;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    public SnapshotUtil() {
        final File site_settings = new File("settings.ini");
        if (site_settings.canRead()) {
            LOG.config("Loading settings from " + site_settings);
            try {
                PropertyPreferenceLoader.load(new FileInputStream(site_settings));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to read settings.ini, falling back to default values.");
            }
        }
    }

    /**
     * Restore PV values from a list of snapshot items
     *
     * <p>
     * Writes concurrently the pv value to the non-null set PVs in
     * the snapshot items.
     * Uses synchronized to ensure only one frontend can write at a time.
     * Returns a list of the snapshot items you have set, with an error message if
     * an error occurred.
     *
     * @param snapshotItems {@link SnapshotItem}
     */
    public synchronized List<RestoreResult> restore(List<SnapshotItem> snapshotItems) {
        // First clean the list of SnapshotItems from read-only elements.
        List<SnapshotItem> cleanedSnapshotItems = cleanSnapshotItems(snapshotItems);
        List<RestoreResult> restoreResultList = new ArrayList<>();

        List<RestoreCallable> callables = new ArrayList<>();
        for (SnapshotItem si : cleanedSnapshotItems) {
            RestoreCallable restoreCallable = new RestoreCallable(si, restoreResultList);
            callables.add(restoreCallable);
        }

        try {
            executorService.invokeAll(callables);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Got exception waiting for all tasks to finish", e);
            // Return empty list here?
            return Collections.emptyList();
        } finally {
            callables.forEach(RestoreCallable::release);
        }

        return restoreResultList;
    }

    /**
     * Reads all PVs and read-back PVs as defined in the {@link ConfigurationData} argument. For each
     * {@link ConfigPv} item in {@link ConfigurationData} a {@link SnapshotItem} is created holding the
     * values read.
     * Read operations are concurrent using a thread pool. Failed connections/reads will cause a wait of at most
     * {@link #connectionTimeout} ms on each thread.
     *
     * @param configurationData Identifies which {@link Configuration} user selected to create a snapshot.
     * @return A list of {@link SnapshotItem}s holding the values read from IOCs.
     */
    public List<SnapshotItem> takeSnapshot(ConfigurationData configurationData) {
        List<SnapshotItem> snapshotItems = new ArrayList<>();
        List<Callable<Void>> callables = new ArrayList<>();
        Map<String, VType> pvValues = new HashMap<>();
        Map<String, VType> readbackPvValues = new HashMap<>();
        for (ConfigPv configPv : configurationData.getPvList()) {
            Callable<Void> pvValueCallable = () -> {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                PV pv = null;
                try {
                    pv = PVPool.getPV(configPv.getPvName());
                    pv.onValueEvent().subscribe(value -> {
                        if (!VTypeHelper.isDisconnected(value)) {
                            countDownLatch.countDown();
                        }
                    });
                    if (!countDownLatch.await(connectionTimeout, TimeUnit.MILLISECONDS)) {
                        LOG.log(Level.WARNING, "Connection to PV '" + configPv.getPvName() +
                                "' timed out after " + connectionTimeout + " ms.");
                        pvValues.put(configPv.getPvName(), null);
                    }
                    else{
                        pvValues.put(configPv.getPvName(), pv.read());
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to read PV '" + configPv.getPvName() + "'", e);
                    pvValues.put(configPv.getPvName(), null);
                    countDownLatch.countDown();
                } finally {
                    if (pv != null) {
                        PVPool.releasePV(pv);
                    }
                }
                return null;
            };
            callables.add(pvValueCallable);
        }

        for (ConfigPv configPv : configurationData.getPvList()) {
            if (configPv.getReadbackPvName() == null) {
                continue;
            }
            Callable<Void> readbackPvValueCallable = () -> {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                PV pv = null;
                try {
                    pv = PVPool.getPV(configPv.getReadbackPvName());
                    pv.onValueEvent().subscribe(value -> {
                        if (!VTypeHelper.isDisconnected(value)) {
                            countDownLatch.countDown();
                        }
                    });
                    if (!countDownLatch.await(connectionTimeout, TimeUnit.MILLISECONDS)) {
                        LOG.log(Level.WARNING, "Connection to read-back PV '" + configPv.getReadbackPvName() +
                                "' timed out after " + connectionTimeout + " ms.");
                        readbackPvValues.put(configPv.getPvName(), null);
                    }
                    else{
                        readbackPvValues.put(configPv.getPvName(), pv.read());
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to read read-back PV '" + configPv.getReadbackPvName() + "'", e);
                    readbackPvValues.put(configPv.getPvName(), null);
                    countDownLatch.countDown();
                } finally {
                    if (pv != null) {
                        PVPool.releasePV(pv);
                    }
                }
                return null;
            };
            callables.add(readbackPvValueCallable);
        }

        try {
            executorService.invokeAll(callables);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Got exception waiting for all read tasks to finish", e);
            // Return empty list here?
            return Collections.emptyList();
        }

        // Merge data into SnapshotItems
        for (String pvName : pvValues.keySet()) {
            SnapshotItem snapshotItem = new SnapshotItem();
            for (ConfigPv configPv : configurationData.getPvList()) {
                if (configPv.getPvName().equals(pvName)) {
                    snapshotItem.setConfigPv(configPv);
                    break;
                }
            }
            VType value = pvValues.get(pvName);
            snapshotItem.setValue(value);
            VType readbackValue = readbackPvValues.get(pvName);
            if (readbackValue != null) {
                snapshotItem.setReadbackValue(readbackValue);
            }
            snapshotItems.add(snapshotItem);
        }

        return snapshotItems;
    }

    private List<SnapshotItem> cleanSnapshotItems(List<SnapshotItem> snapshotItems) {
        return snapshotItems.stream().filter(si -> !si.getConfigPv().isReadOnly()).toList();
    }

    /**
     * Wraps PV functionality such that client code may release PV back to the pool once
     * write/restore operation has succeeded (or failed).
     */
    private class RestoreCallable implements Callable<Void> {

        private final List<RestoreResult> restoreResultList;
        private PV pv;
        private final SnapshotItem snapshotItem;

        public RestoreCallable(SnapshotItem snapshotItem, List<RestoreResult> restoreResultList) {
            this.snapshotItem = snapshotItem;
            this.restoreResultList = restoreResultList;
        }

        @Override
        public Void call() {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            try {
                pv = PVPool.getPV(snapshotItem.getConfigPv().getPvName());
                pv.onValueEvent().subscribe(value -> {
                    if (!PV.isDisconnected(value)) {
                        countDownLatch.countDown();
                    }
                });
                if (!countDownLatch.await(connectionTimeout, TimeUnit.MILLISECONDS)) {
                    LOG.log(Level.WARNING, "Connection to PV '" + snapshotItem.getConfigPv().getPvName() +
                            "' timed out after " + connectionTimeout + "ms.");
                    RestoreResult restoreResult = new RestoreResult();
                    restoreResult.setSnapshotItem(snapshotItem);
                    restoreResult.setErrorMsg("No monitor event from PV '" + snapshotItem.getConfigPv().getPvName() + "'");
                    restoreResultList.add(restoreResult);
                } else {
                    pv.write(VTypeHelper.toObject(snapshotItem.getValue()));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to write to PV '" + snapshotItem.getConfigPv().getPvName() + "'", e);
                RestoreResult restoreResult = new RestoreResult();
                restoreResult.setSnapshotItem(snapshotItem);
                restoreResult.setErrorMsg("Failed to write to PV '" + snapshotItem.getConfigPv().getPvName() + "', cause: " + e.getMessage());
                restoreResultList.add(restoreResult);
                countDownLatch.countDown();
            }
            return null;
        }

        public void release() {
            if (pv != null) {
                PVPool.releasePV(pv);
            }
        }
    }
}
