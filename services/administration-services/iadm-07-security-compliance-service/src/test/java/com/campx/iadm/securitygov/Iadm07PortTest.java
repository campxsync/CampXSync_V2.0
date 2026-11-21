package com.campx.iadm.securitygov;

import com.campx.iadm.securitygov.server.Iadm07Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Iadm07PortTest {
    @Test public void defaultPortIs8105() { assertEquals(8105, Iadm07Server.DEFAULT_PORT); }
}
