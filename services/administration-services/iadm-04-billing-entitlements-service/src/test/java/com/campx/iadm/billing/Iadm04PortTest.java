package com.campx.iadm.billing;

import com.campx.iadm.billing.server.Iadm04Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Iadm04PortTest {
    @Test public void defaultPortIs8102() { assertEquals(8102, Iadm04Server.DEFAULT_PORT); }
}
