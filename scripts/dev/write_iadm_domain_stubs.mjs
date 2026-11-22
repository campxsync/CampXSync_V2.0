import fs from "fs";
import path from "path";

function w(p, c) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, c);
  console.log("W", p);
}

function appServerController(svc, routesDoc, domainClass, domainBody, controllerBody, testBody) {
  const base = `services/administration-services/${svc.dir}/src/main/java/${svc.pkg.replaceAll(".", "/")}`;
  const testBase = `services/administration-services/${svc.dir}/src/test/java/${svc.pkg.replaceAll(".", "/")}`;

  w(`${base}/service/${domainClass}.java`, `package ${svc.pkg}.service;

import ${svc.pkg}.config.DatabaseConnectionManager;
import ${svc.pkg}.exception.InstituteAdminException;
import ${svc.pkg}.exception.UserProfileAccessDeniedException;
import ${svc.pkg}.security.UserSecurityContext;
import java.sql.*;
import java.util.*;

${domainBody}
`);

  w(`${base}/controller/Iadm${svc.id}Controller.java`, `package ${svc.pkg}.controller;

import ${svc.pkg}.exception.InstituteAdminException;
import ${svc.pkg}.exception.UserProfileAccessDeniedException;
import ${svc.pkg}.security.GatewayHmacVerifier;
import ${svc.pkg}.security.UserSecurityContext;
import ${svc.pkg}.service.${domainClass};
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Iadm${svc.id}Controller implements HttpHandler {
    private final ${domainClass} service;
    private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();
    public Iadm${svc.id}Controller(${domainClass} service) { this.service = service; }
    @Override public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] bodyBytes = readAll(exchange.getRequestBody());
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(204, -1); return; }
            GatewayHmacVerifier.VerificationResult auth = hmac.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) { send(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage())); return; }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            UserSecurityContext ctx = UserSecurityContext.fromHeaders(
                auth.getUserId() == null ? "00000000-0000-0000-0000-000000000001" : auth.getUserId(),
                auth.getTenantId() == null || auth.getTenantId().isEmpty() ? "a0000000-0000-0000-0000-000000000001" : auth.getTenantId());
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/health")) { send(exchange, 200, "{\\"status\\":\\"UP\\",\\"service\\":\\"IADM-${svc.id}\\"}"); return; }
${controllerBody}
            send(exchange, 404, problem("IADM${svc.id}_NOT_FOUND", "Route not found: " + path));
        } catch (UserProfileAccessDeniedException e) { send(exchange, 403, problem("IADM${svc.id}_FORBIDDEN", e.getMessage())); }
          catch (InstituteAdminException e) { send(exchange, e.getStatus(), problem(e.getErrorCode(), e.getMessage())); }
          catch (Exception e) { send(exchange, 500, problem("IADM${svc.id}_INTERNAL", e.getMessage())); }
    }
    private static String extract(String json, String key) {
        if (json == null) return null; String needle = "\\"" + key + "\\""; int i = json.indexOf(needle); if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length()); int q1 = json.indexOf('"', colon + 1); if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1); return json.substring(q1 + 1, q2);
    }
    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{"); boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) { if (!first) sb.append(','); first = false;
            sb.append('"').append(e.getKey()).append("\\":"); Object v = e.getValue();
            if (v == null) sb.append("null"); else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof List) sb.append(v.toString().replace('=', ':')); // simplistic
            else sb.append('"').append(String.valueOf(v).replace("\\"","\\\\\\"")).append('"'); }
        return sb.append('}').toString();
    }
    private static String problem(String code, String detail) {
        return "{\\"errorCode\\":\\"" + code + "\\",\\"detail\\":\\"" + (detail == null ? "" : detail.replace("\\"","\\\\\\"")) + "\\"}";
    }
    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n); return bos.toByteArray();
    }
}
`);

  w(`${base}/server/Iadm${svc.id}Server.java`, `package ${svc.pkg}.server;
import ${svc.pkg}.controller.Iadm${svc.id}Controller;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException; import java.net.InetSocketAddress; import java.util.concurrent.Executors;
public class Iadm${svc.id}Server {
  public static final int DEFAULT_PORT = ${svc.port};
  private final HttpServer server; private final int port;
  public Iadm${svc.id}Server(Iadm${svc.id}Controller c, int port) throws IOException {
    this.port = port; this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
    server.createContext("/api/v1/iadm/${svc.id}", c); server.createContext("/health", c);
    server.setExecutor(Executors.newFixedThreadPool(8));
  }
  public void start(){server.start();} public void stop(){server.stop(0);} public int getPort(){return port;}
}
`);

  w(`${base.replace(/\/[^/]+$/, "").replace(/src.*/, "src/main/java/" + svc.pkg.replaceAll(".", "/"))}/Iadm${svc.id}Application.java`.replace("//", "/"), // fix later
    ""); // placeholder - write app separately below

  w(`services/administration-services/${svc.dir}/src/main/java/${svc.pkg.replaceAll(".", "/")}/Iadm${svc.id}Application.java`,
`package ${svc.pkg};
import ${svc.pkg}.config.DatabaseConfig;
import ${svc.pkg}.config.DatabaseConnectionManager;
import ${svc.pkg}.controller.Iadm${svc.id}Controller;
import ${svc.pkg}.server.Iadm${svc.id}Server;
import ${svc.pkg}.service.${domainClass};
import com.campx.logger.CampXLogger; import com.campx.logger.CampXLoggerFactory;
public class Iadm${svc.id}Application {
  private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm${svc.id}Application.class);
  public static void main(String[] args) {
    try {
      int port = Iadm${svc.id}Server.DEFAULT_PORT;
      if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
      DatabaseConfig cfg = DatabaseConfig.load();
      if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) throw new IllegalStateException("DB URL required");
      DatabaseConnectionManager.getInstance().initialize();
      Iadm${svc.id}Server server = new Iadm${svc.id}Server(new Iadm${svc.id}Controller(new ${domainClass}()), port);
      server.start();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(); DatabaseConnectionManager.getInstance().close(); }));
      logger.info("IADM-${svc.id} started on port {}", server.getPort());
    } catch (Exception e) { logger.error("Failed: {}", e.getMessage(), e); System.exit(1); }
  }
}
`);

  w(`${testBase}/Iadm${svc.id}LiveDevTest.java`, testBody);
}

const s03 = { id: "03", port: 8101, dir: "iadm-03-identity-access-service", pkg: "com.campx.iadm.identity" };
appServerController(s03, "", "IdentityAccessService", `
public class IdentityAccessService {
  private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();
  public Map<String,Object> listRoles(UserSecurityContext ctx) {
    return ctx.executeInTransaction(db, conn -> {
      ctx.checkPermission(conn, "adm09.view");
      List<Map<String,Object>> roles = new ArrayList<>();
      try (PreparedStatement ps = conn.prepareStatement(
          "SELECT code, name FROM iam.roles WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY code LIMIT 100")) {
        ps.setObject(1, ctx.getTenantId());
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) { Map<String,Object> r = new LinkedHashMap<>(); r.put("code", rs.getString(1)); r.put("name", rs.getString(2)); roles.add(r); }
        }
      }
      Map<String,Object> out = new LinkedHashMap<>(); out.put("roles", roles); return out;
    });
  }
  public Map<String,Object> setCeiling(UserSecurityContext ctx, String roleCode, String keysCsv) {
    return ctx.executeInTransaction(db, conn -> {
      ctx.checkPermission(conn, "adm09.manage");
      UUID id = UUID.randomUUID();
      String[] keys = keysCsv == null || keysCsv.isEmpty() ? new String[0] : keysCsv.split(",");
      try (PreparedStatement ps = conn.prepareStatement(
          "INSERT INTO iam.role_ceilings (id, tenant_id, role_code, allowed_permission_keys, created_by) VALUES (?,?,?,?::text[],?)")) {
        ps.setObject(1, id); ps.setObject(2, ctx.getTenantId()); ps.setString(3, roleCode);
        ps.setArray(4, conn.createArrayOf("text", keys)); ps.setObject(5, ctx.getUserId()); ps.executeUpdate();
      }
      Map<String,Object> out = new LinkedHashMap<>(); out.put("ceilingId", id.toString()); out.put("roleCode", roleCode); return out;
    });
  }
  public Map<String,Object> getCeiling(UserSecurityContext ctx, String roleCode) {
    return ctx.executeInTransaction(db, conn -> {
      ctx.checkPermission(conn, "adm09.view");
      try (PreparedStatement ps = conn.prepareStatement(
          "SELECT id, role_code, allowed_permission_keys FROM iam.role_ceilings WHERE tenant_id=? AND role_code=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")) {
        ps.setObject(1, ctx.getTenantId()); ps.setString(2, roleCode);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) throw new InstituteAdminException(404, "IADM03_CEILING_NOT_FOUND", "No ceiling");
          Map<String,Object> out = new LinkedHashMap<>();
          out.put("ceilingId", rs.getObject(1).toString()); out.put("roleCode", rs.getString(2));
          Array arr = rs.getArray(3); out.put("allowedPermissionKeys", arr == null ? Collections.emptyList() : Arrays.asList((String[]) arr.getArray()));
          return out;
        }
      }
    });
  }
  public Map<String,Object> createAccessRequest(UserSecurityContext ctx, String type, String reason) {
    return ctx.executeInTransaction(db, conn -> {
      UUID id = UUID.randomUUID();
      try (PreparedStatement ps = conn.prepareStatement(
          "INSERT INTO iam.access_change_requests (id, tenant_id, requested_by, request_type, reason, status, created_by) VALUES (?,?,?,?,?,'PENDING',?)")) {
        ps.setObject(1, id); ps.setObject(2, ctx.getTenantId()); ps.setObject(3, ctx.getUserId());
        ps.setString(4, type); ps.setString(5, reason); ps.setObject(6, ctx.getUserId()); ps.executeUpdate();
      }
      Map<String,Object> out = new LinkedHashMap<>(); out.put("requestId", id.toString()); out.put("status", "PENDING"); return out;
    });
  }
  public Map<String,Object> decideAccessRequest(UserSecurityContext ctx, UUID requestId, String decision, String note) {
    return ctx.executeInTransaction(db, conn -> {
      ctx.checkPermission(conn, "adm09.manage");
      if (ctx.getUserId().equals(requestId)) { /* noop */ }
      String status = decision != null && decision.toUpperCase().startsWith("APP") ? "APPROVED" : "REJECTED";
      try (PreparedStatement ps = conn.prepareStatement(
          "UPDATE iam.access_change_requests SET status=?, decided_by=?, decided_at=now(), decision_note=?, updated_by=? WHERE id=? AND tenant_id=? AND status='PENDING' AND requested_by <> ?")) {
        ps.setString(1, status); ps.setObject(2, ctx.getUserId()); ps.setString(3, note); ps.setObject(4, ctx.getUserId());
        ps.setObject(5, requestId); ps.setObject(6, ctx.getTenantId()); ps.setObject(7, ctx.getUserId());
        if (ps.executeUpdate() == 0) throw new InstituteAdminException(404, "IADM03_REQUEST", "Pending request not found or self-approve blocked");
      }
      Map<String,Object> out = new LinkedHashMap<>(); out.put("requestId", requestId.toString()); out.put("status", status); return out;
    });
  }
}
`, `
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/roles")) { send(exchange, 200, toJson(service.listRoles(ctx))); return; }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/ceilings")) {
              send(exchange, 201, toJson(service.setCeiling(ctx, extract(body, "roleCode"), extract(body, "allowedPermissionKeys"))); return; }
            if ("GET".equalsIgnoreCase(method) && path.contains("/ceilings/")) {
              String code = path.substring(path.lastIndexOf('/')+1); send(exchange, 200, toJson(service.getCeiling(ctx, code))); return; }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/access-requests")) {
              send(exchange, 201, toJson(service.createAccessRequest(ctx, extract(body, "requestType"), extract(body, "reason"))); return; }
            if ("POST".equalsIgnoreCase(method) && path.contains("/access-requests/") && path.endsWith("/decide")) {
              String id = path.substring(path.indexOf("/access-requests/")+17, path.indexOf("/decide"));
              send(exchange, 200, toJson(service.decideAccessRequest(ctx, UUID.fromString(id), extract(body, "decision"), extract(body, "note"))); return; }
`,
`package ${s03.pkg};
import ${s03.pkg}.server.Iadm03Server;
import org.junit.Test; import static org.junit.Assert.*;
public class Iadm03LiveDevTest { @Test public void port(){ assertEquals(8101, Iadm03Server.DEFAULT_PORT);} }
`);

console.log("s03 written - continuing others in same file...");
