package com.campx.iadm.analytics.client;

import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.service.AnalyticsOutageFlags;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronous read clients for IADM-01 and IADM-04 aggregate contracts (US-022).
 * Plain language: never read another service's tables; timeouts become "source unavailable", never zero.
 */
public class SourceAggregateClient {

    private final HttpJsonClient iadm01;
    private final HttpJsonClient iadm04;

    public SourceAggregateClient(String iadm01Base, String iadm04Base) {
        this.iadm01 = new HttpJsonClient(iadm01Base, 3000, 10000);
        this.iadm04 = new HttpJsonClient(iadm04Base, 3000, 10000);
    }

    public String fetchIadm01Aggregates(String window, String tenantId, Map<String, String> headers) {
        if (AnalyticsOutageFlags.isIadm01Disabled()) {
            throw new InstituteAdminException(503, "IADM06_SOURCE_UNAVAILABLE",
                    "Analytics source is temporarily unavailable");
        }
        StringBuilder q = new StringBuilder("/analytics-aggregates?");
        if (window != null && !window.isEmpty()) {
            q.append("window=").append(enc(window)).append("&");
        }
        if (tenantId != null && !tenantId.isEmpty()) {
            q.append("tenantId=").append(enc(tenantId)).append("&");
        }
        return iadm01.get(q.toString(), headers);
    }

    public String fetchIadm04Aggregates(String window, String tenantId, Map<String, String> headers) {
        if (AnalyticsOutageFlags.isIadm04Disabled()) {
            throw new InstituteAdminException(503, "IADM06_SOURCE_UNAVAILABLE",
                    "Analytics source is temporarily unavailable");
        }
        StringBuilder q = new StringBuilder("/analytics-aggregates?");
        if (window != null && !window.isEmpty()) {
            q.append("window=").append(enc(window)).append("&");
        }
        if (tenantId != null && !tenantId.isEmpty()) {
            q.append("tenantId=").append(enc(tenantId)).append("&");
        }
        return iadm04.get(q.toString(), headers);
    }

    public Map<String, Object> unavailableMarker(String source) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("source", source);
        m.put("status", "UNAVAILABLE");
        m.put("message", "Analytics source is temporarily unavailable");
        return m;
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
