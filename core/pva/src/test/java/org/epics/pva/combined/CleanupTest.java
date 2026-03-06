/*******************************************************************************
 * Copyright (c) 2019-2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.epics.pva.combined;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.epics.pva.PVASettings;
import org.epics.pva.client.PVAChannel;
import org.epics.pva.client.PVAClient;
import org.epics.pva.data.PVADouble;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.server.PVAServer;
import org.epics.pva.server.ServerPV;
import org.junit.jupiter.api.Test;

/** Tests that normal server/client teardown does not produce spurious WARNING log entries.
 *
 *  <p>Reproduces warnings previously seen in projects using core-pva
 *  (e.g. micrometer-registry-pva, EPICS Archiver Appliance):
 *  <ul>
 *  <li>TCPHandler.sender() WARNING "exits because of error / Socket is closed"
 *  <li>PVAClient.close() WARNING "closed with remaining channels"
 *  </ul>
 */
@SuppressWarnings("nls")
public class CleanupTest
{
    /** Captures WARNING (and above) log records from org.epics.pva.* loggers */
    private static List<LogRecord> captureWarnings(final Handler[] holder)
    {
        final List<LogRecord> warnings = new ArrayList<>();
        final Handler capture = new Handler()
        {
            @Override
            public void publish(final LogRecord r)
            {
                if (r.getLevel().intValue() >= Level.WARNING.intValue() &&
                    r.getLoggerName() != null &&
                    r.getLoggerName().startsWith("org.epics.pva"))
                    warnings.add(r);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        holder[0] = capture;
        Logger.getLogger("").addHandler(capture);
        return warnings;
    }

    private static PVAStructure demoData()
    {
        return new PVAStructure("demo", "demo_t", new PVADouble("value", 0.0));
    }

    /** Saving/restoring these settings lets the tests run in CI without UDP broadcast. */
    private static String savedAddrList;
    private static boolean savedAutoAddrList;

    private static void setupLocalhost()
    {
        savedAddrList = PVASettings.EPICS_PVA_ADDR_LIST;
        savedAutoAddrList = PVASettings.EPICS_PVA_AUTO_ADDR_LIST;
        if (!PVASettings.EPICS_PVA_ENABLE_IPV6)
        {
            PVASettings.EPICS_PVA_ADDR_LIST = "127.0.0.1";
            PVASettings.EPICS_PVA_AUTO_ADDR_LIST = false;
        }
    }

    private static void restoreSettings()
    {
        PVASettings.EPICS_PVA_ADDR_LIST = savedAddrList;
        PVASettings.EPICS_PVA_AUTO_ADDR_LIST = savedAutoAddrList;
    }

    /** Closing the server while a client is still connected used to produce:
     *  "TCP sender ... exits because of error / java.net.SocketException: Socket is closed"
     *  at WARNING level.  After the fix, this is logged at FINE.
     */
    @Test
    public void senderDoesNotWarnWhenSocketClosedDuringShutdown() throws Exception
    {
        setupLocalhost();
        final Handler[] holder = new Handler[1];
        final List<LogRecord> warnings = captureWarnings(holder);
        try
        {
            final PVAServer server = new PVAServer();
            server.createPV("cleanup.test.sender", demoData());
            final PVAClient client = new PVAClient();
            final PVAChannel channel = client.getChannel("cleanup.test.sender");
            channel.connect().get(5, TimeUnit.SECONDS);

            // Close server while client is still connected.
            // Previously caused WARNING from TCPHandler.sender().
            server.close();
            TimeUnit.MILLISECONDS.sleep(300);
            client.close();
        }
        finally
        {
            Logger.getLogger("").removeHandler(holder[0]);
            restoreSettings();
        }
        assertTrue(warnings.isEmpty(),
                   "Unexpected WARNING(s) during teardown: " + warningMessages(warnings));
    }

    /** Closing PVAClient with channels still in SEARCHING state used to produce:
     *  "PVA Client closed with remaining channels: [... SEARCHING ...]" at WARNING level.
     *  After the fix, remaining channels are closed automatically and the log is at FINE.
     */
    @Test
    public void clientCloseDoesNotWarnForSearchingChannels() throws Exception
    {
        setupLocalhost();
        final Handler[] holder = new Handler[1];
        final List<LogRecord> warnings = captureWarnings(holder);
        try
        {
            final PVAServer server = new PVAServer();
            final ServerPV serverPV = server.createPV("cleanup.test.searching", demoData());
            final PVAClient client = new PVAClient();
            final PVAChannel channel = client.getChannel("cleanup.test.searching");
            channel.connect().get(5, TimeUnit.SECONDS);

            // Remove server PV so the channel starts searching again
            serverPV.close();
            TimeUnit.MILLISECONDS.sleep(200);

            // Close client without explicitly closing the channel.
            // Previously caused WARNING about remaining SEARCHING channels.
            client.close();
            server.close();
        }
        finally
        {
            Logger.getLogger("").removeHandler(holder[0]);
            restoreSettings();
        }
        assertTrue(warnings.isEmpty(),
                   "Unexpected WARNING(s) during teardown: " + warningMessages(warnings));
    }

    /** Closing PVAClient with channels still in CONNECTED state used to produce:
     *  "PVA Client closed with remaining channels: [... CONNECTED ...]" at WARNING level.
     *  After the fix, remaining channels are closed automatically and the log is at FINE.
     */
    @Test
    public void clientCloseDoesNotWarnForConnectedChannels() throws Exception
    {
        setupLocalhost();
        final Handler[] holder = new Handler[1];
        final List<LogRecord> warnings = captureWarnings(holder);
        try
        {
            final PVAServer server = new PVAServer();
            server.createPV("cleanup.test.connected", demoData());
            final PVAClient client = new PVAClient();
            final PVAChannel channel = client.getChannel("cleanup.test.connected");
            channel.connect().get(5, TimeUnit.SECONDS);

            // Close client without explicitly closing the channel.
            // Previously caused WARNING about remaining CONNECTED channels.
            client.close();
            server.close();
        }
        finally
        {
            Logger.getLogger("").removeHandler(holder[0]);
            restoreSettings();
        }
        assertTrue(warnings.isEmpty(),
                   "Unexpected WARNING(s) during teardown: " + warningMessages(warnings));
    }

    private static String warningMessages(final List<LogRecord> records)
    {
        final StringBuilder sb = new StringBuilder();
        for (LogRecord r : records)
            sb.append("\n  [").append(r.getLoggerName()).append("] ").append(r.getMessage());
        return sb.toString();
    }
}
