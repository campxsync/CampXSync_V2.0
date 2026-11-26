package com.campx.iadm.ops.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side health target inventory (IADM-08-US-021 / decision D5).
 * Plain language: only these loopback health URLs may be called; never a client-supplied address.
 * Labelled minimal in-service list aligned with GatewayConfig.routeTable + DEV start-script checks.
 */
public final class HealthTargetInventory {

    public static final String SOURCE_LABEL =
            "minimal-read-only-in-service-list (aligned with gateway route table + DEV health family)";

    private HealthTargetInventory() {
    }

    public static List<Map<String, String>> all() {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        list.add(target("gateway", "API Gateway", "http://127.0.0.1:8080/actuator/health"));
        list.add(target("iadm-01", "IADM-01 Tenant Lifecycle", "http://127.0.0.1:8099/api/v1/iadm/01/health"));
        list.add(target("iadm-02", "IADM-02 Platform Config", "http://127.0.0.1:8100/api/v1/iadm/02/health"));
        list.add(target("iadm-03", "IADM-03 Identity Access", "http://127.0.0.1:8101/api/v1/iadm/03/health"));
        list.add(target("iadm-04", "IADM-04 Billing Entitlements", "http://127.0.0.1:8102/api/v1/iadm/04/health"));
        list.add(target("iadm-05", "IADM-05 Data Governance", "http://127.0.0.1:8103/api/v1/iadm/05/health"));
        list.add(target("iadm-06", "IADM-06 Platform Analytics", "http://127.0.0.1:8104/api/v1/iadm/06/health"));
        list.add(target("iadm-07", "IADM-07 Security Compliance", "http://127.0.0.1:8105/api/v1/iadm/07/health"));
        list.add(target("iadm-08", "IADM-08 Audit Health Ops", "http://127.0.0.1:8106/api/v1/iadm/08/health"));
        return Collections.unmodifiableList(list);
    }

    public static Map<String, String> find(String id) {
        if (id == null) {
            return null;
        }
        String needle = id.trim();
        for (Map<String, String> t : all()) {
            if (needle.equalsIgnoreCase(t.get("id"))) {
                return t;
            }
        }
        return null;
    }

    private static Map<String, String> target(String id, String name, String url) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("id", id);
        m.put("name", name);
        m.put("url", url);
        m.put("inventorySource", SOURCE_LABEL);
        return m;
    }
}
