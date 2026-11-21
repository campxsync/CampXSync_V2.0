package com.campx.iadm.analytics;

import com.campx.iadm.analytics.server.Iadm06Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Iadm06PortTest {
    @Test public void defaultPortIs8104() { assertEquals(8104, Iadm06Server.DEFAULT_PORT); }
}
