package com.campx.iadm.identity;

import com.campx.iadm.identity.server.Iadm03Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Iadm03PortTest {
    @Test public void defaultPortIs8101() { assertEquals(8101, Iadm03Server.DEFAULT_PORT); }
}
