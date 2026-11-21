package com.campx.iadm.privacy;

import com.campx.iadm.privacy.server.Iadm05Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Iadm05PortTest {
    @Test public void defaultPortIs8103() { assertEquals(8103, Iadm05Server.DEFAULT_PORT); }
}
