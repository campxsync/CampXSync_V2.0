package com.campx.gateway.config;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies that the API Gateway registers STM-06 Student Documents routes
 * and maps them to the Document Service downstream endpoint on port 8098.
 */
public class DocumentGatewayRouteTest {

    @Test
    public void testDocumentServiceRoutesRegistered() {
        GatewayConfig config = new GatewayConfig();
        Map<String, String> routeTable = config.getRouteTable();

        assertTrue("Gateway must map /api/v1/documents", routeTable.containsKey("/api/v1/documents"));
        String route = routeTable.get("/api/v1/documents");
        assertTrue("Must route to port 8098 documents", route.equals("http://localhost:8098/api/v1/documents") || route.equals("http://127.0.0.1:8098/api/v1/documents"));
    }
}

