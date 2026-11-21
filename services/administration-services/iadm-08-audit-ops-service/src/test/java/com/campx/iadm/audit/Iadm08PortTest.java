package com.campx.iadm.audit;

import com.campx.iadm.audit.server.Iadm08Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Iadm08PortTest {
    @Test public void defaultPortIs8106() { assertEquals(8106, Iadm08Server.DEFAULT_PORT); }
}
