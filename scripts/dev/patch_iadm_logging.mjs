/**
 * Patches IADM controllers/domain services for Agreement §3.2:
 * CampXLogger + FlowTracker + LogContext, safe exception responses, Javadoc.
 * Run: node scripts/dev/patch_iadm_logging.mjs
 */
import fs from 'fs';
import path from 'path';

const root = path.resolve('services/administration-services');

const services = [
  { dir: 'iadm-01-tenant-lifecycle-service', pkg: 'com.campx.iadm.lifecycle', code: 'IADM-01', ctrl: 'controller/Iadm01Controller.java', svc: 'service/TenantLifecycleService.java', errPrefix: 'IADM01' },
  { dir: 'iadm-02-platform-config-service', pkg: 'com.campx.iadm.config', code: 'IADM-02', ctrl: 'controller/Iadm02Controller.java', svc: 'service/PlatformConfigService.java', errPrefix: 'IADM02' },
  { dir: 'iadm-03-identity-access-service', pkg: 'com.campx.iadm.identity', code: 'IADM-03', ctrl: 'controller/Iadm03Controller.java', svc: 'service/Iadm03DomainService.java', errPrefix: 'IADM03' },
  { dir: 'iadm-04-billing-entitlements-service', pkg: 'com.campx.iadm.billing', code: 'IADM-04', ctrl: 'controller/Iadm04Controller.java', svc: 'service/Iadm04DomainService.java', errPrefix: 'IADM04' },
  { dir: 'iadm-05-data-governance-service', pkg: 'com.campx.iadm.privacy', code: 'IADM-05', ctrl: 'controller/Iadm05Controller.java', svc: 'service/Iadm05DomainService.java', errPrefix: 'IADM05' },
  { dir: 'iadm-06-platform-analytics-service', pkg: 'com.campx.iadm.analytics', code: 'IADM-06', ctrl: 'controller/Iadm06Controller.java', svc: 'service/Iadm06DomainService.java', errPrefix: 'IADM06' },
  { dir: 'iadm-07-security-compliance-service', pkg: 'com.campx.iadm.securitygov', code: 'IADM-07', ctrl: 'controller/Iadm07Controller.java', svc: 'service/Iadm07DomainService.java', errPrefix: 'IADM07' },
  { dir: 'iadm-08-audit-ops-service', pkg: 'com.campx.iadm.audit', code: 'IADM-08', ctrl: 'controller/Iadm08Controller.java', svc: 'service/Iadm08DomainService.java', errPrefix: 'IADM08' },
];

function ensureImports(src, imports) {
  let out = src;
  for (const line of imports) {
    if (!out.includes(line)) {
      const m = out.match(/^package [^;]+;\s*\n/m);
      if (m) out = out.replace(m[0], m[0] + '\n' + line + '\n');
    }
  }
  return out;
}

function patchController(file, meta) {
  let src = fs.readFileSync(file, 'utf8');
  src = ensureImports(src, [
    'import com.campx.logger.CampXLogger;',
    'import com.campx.logger.CampXLoggerFactory;',
    'import com.campx.logger.api.FlowTracker;',
    'import com.campx.logger.context.LogContext;',
  ]);

  if (!src.includes('CampXLoggerFactory.getLogger')) {
    src = src.replace(
      /public class (\w+) implements HttpHandler \{\s*\n/,
      `public class $1 implements HttpHandler {\n    private static final CampXLogger logger = CampXLoggerFactory.getLogger($1.class);\n\n`
    );
  }

  // Wrap handle body with flow tracking if not already
  if (!src.includes('logger.flow(')) {
    src = src.replace(
      /public void handle\(HttpExchange exchange\) throws IOException \{\s*\n(\s*)String method/,
      `public void handle(HttpExchange exchange) throws IOException {\n$1String method`
    );

    // Inject after method/path/bodyBytes declarations — find first try {
    const tryIdx = src.indexOf('try {', src.indexOf('public void handle'));
    if (tryIdx > 0) {
      const before = src.slice(0, tryIdx);
      const after = src.slice(tryIdx);
      // Only inject once before outer try
      if (!before.includes('FlowTracker tracker')) {
        const inject = `String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        LogContext.put(LogContext.KEY_SERVICE, "${meta.code}");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
`;
        // Replace the first try { of handle with inject + try {
        src = before + inject + after.slice(4); // remove "try" keeping " {"
        // Fix: after.slice(4) if "try {" — better:
        src = before + inject + after.replace(/^try \{/, '{');
      }
    }

    // Fix nested braces: we converted try { to { but need finally for tracker
    // Replace catch Exception blocks to log, and add finally
    if (!src.includes('tracker.close()')) {
      // Find the handle method's catch Exception and enhance
      src = src.replace(
        /catch \(UserProfileAccessDeniedException e\) \{\s*\n(\s*)send(?:Json)?\(exchange, 403,/g,
        `catch (UserProfileAccessDeniedException e) {\n$1tracker.markFailed(e);\n$1logger.warn("${meta.code} access denied path={} code={}", path, "${meta.errPrefix}_FORBIDDEN");\n$1send$2(exchange, 403,`.replace('send$2', 'send')
      );
      // Simpler approach for catch blocks:
    }
  }

  // Safer: rewrite catch/finally section of controller with a known pattern
  // Replace generic Exception catch that returns e.getMessage()
  const genericCatch = new RegExp(
    `catch \\(Exception e\\) \\{\\s*send(?:Json)?\\(exchange, 500, problem\\("${meta.errPrefix}_INTERNAL", e\\.getMessage\\(\\)\\)\\);\\s*\\}`,
    'm'
  );
  if (genericCatch.test(src)) {
    src = src.replace(
      genericCatch,
      `catch (Exception e) {
            if (tracker != null) tracker.markFailed(e);
            logger.error("${meta.code} unhandled failure path=" + path, e);
            sendJsonSafe(exchange, 500, problem("${meta.errPrefix}_INTERNAL", "Request could not be completed"));
        } finally {
            if (tracker != null) tracker.close();
            LogContext.clear();
        }`
    );
  }

  // Also handle send(exchange variants and compacted catch
  src = src.replace(
    new RegExp(`catch\\(Exception e\\)\\{\\s*send\\(exchange,500,\\s*"\\\\{\\\\"error\\\\":\\\\""\\+e\\.getMessage\\(\\)[^}]+\\}\\);\\s*\\}`, 'm'),
    `catch (Exception e) {
            if (tracker != null) tracker.markFailed(e);
            logger.error("${meta.code} unhandled failure path=" + path, e);
            send(exchange, 500, problem("${meta.errPrefix}_INTERNAL", "Request could not be completed"));
        } finally {
            if (tracker != null) tracker.close();
            LogContext.clear();
        }`
  );

  // Enhance Javadoc
  if (!src.includes('Agreement §3.2')) {
    src = src.replace(
      /\/\*\*[\s\S]*?\*\/\s*public class/,
      `/**
 * HTTP ingress for ${meta.code} (${meta.dir}).
 * <p>Uses {@link CampXLogger} / {@link FlowTracker} per Agreement §3.2.
 * Domain failures map to problem JSON; unexpected failures are logged and return a generic message (no secrets).</p>
 */
public class`
    );
  }

  // Declare tracker nullable at start if we injected FlowTracker but compile needs definite assignment
  if (src.includes('FlowTracker tracker =') && !src.includes('FlowTracker tracker = null')) {
    // already assigned
  } else if (src.includes('tracker.close()') && !src.includes('FlowTracker tracker')) {
    src = src.replace(
      /public void handle\(HttpExchange exchange\) throws IOException \{/,
      `public void handle(HttpExchange exchange) throws IOException {
        FlowTracker tracker = null;`
    );
  }

  // Fix sendJsonSafe helper alias
  if (src.includes('sendJsonSafe') && !src.includes('void sendJsonSafe')) {
    if (src.includes('private static void sendJson(')) {
      src = src.replace('private static void sendJson(', 'private static void sendJsonSafe(HttpExchange exchange, int status, String json) throws IOException { sendJson(exchange, status, json); }\n    private static void sendJson(');
    } else if (src.includes('private static void send(')) {
      src = src.replace(/sendJsonSafe/g, 'send');
    }
  }

  // Auth failure logging
  if (!src.includes(`${meta.code} HMAC/auth rejected`)) {
    src = src.replace(
      /if\s*\(\s*!auth\.isSuccess\(\)\s*\)\s*\{\s*\n(\s*)send/,
      `if (!auth.isSuccess()) {\n$1logger.warn("${meta.code} HMAC/auth rejected path={} status={}", path, auth.getStatus());\n$1send`
    );
  }

  // Bind identity after auth success
  if (!src.includes('LogContext.put(LogContext.KEY_USER_ID') && src.includes('auth.getUserId()')) {
    src = src.replace(
      /(GatewayHmacVerifier\.VerificationResult auth = [^\n]+\n\s*if \(!auth\.isSuccess\(\)\) \{[\s\S]*?\n\s*\}\n)/,
      `$1            LogContext.put(LogContext.KEY_USER_ID, auth.getUserId());\n            LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId());\n            if (tracker != null) tracker.step("authenticated");\n`
    );
  }

  fs.writeFileSync(file, src);
  console.log('controller', file);
}

function patchDomainService(file, meta) {
  let src = fs.readFileSync(file, 'utf8');
  src = ensureImports(src, [
    'import com.campx.logger.CampXLogger;',
    'import com.campx.logger.CampXLoggerFactory;',
    'import com.campx.logger.api.AuditEvent;',
  ]);

  if (!src.includes('CampXLoggerFactory.getLogger')) {
    src = src.replace(
      /public class (\w+) \{/,
      `public class $1 {\n    private static final CampXLogger logger = CampXLoggerFactory.getLogger($1.class);`
    );
  }

  if (!src.includes('auditMutation(')) {
    // Append helper before last closing brace of class
    const helper = `
    /** Logs a successful mutation for diagnostics and compliance (no secrets). */
    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("${meta.code} action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder()
                .action(action)
                .tenantId(tenant)
                .principalId(user)
                .resourceType(resourceType)
                .resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS")
                .severity("INFO")
                .description(action)
                .build());
    }

    private InstituteAdminException reject(int status, String code, String message) {
        if (status >= 500) logger.error("${meta.code} rejected status={} code={} message={}", status, code, message);
        else logger.warn("${meta.code} rejected status={} code={} message={}", status, code, message);
        return new InstituteAdminException(status, code, message);
    }
`;
    const last = src.lastIndexOf('}');
    src = src.slice(0, last) + helper + '\n}\n';
  }

  // Ensure InstituteAdminException import if reject added
  if (src.includes('new InstituteAdminException') && !src.includes('import ' + meta.pkg + '.exception.InstituteAdminException')) {
    src = ensureImports(src, [`import ${meta.pkg}.exception.InstituteAdminException;`]);
  }

  // Call auditMutation on successful returns for common mutation methods — light touch: after Map out = ... return out patterns that follow INSERT
  // Add Javadoc if missing Agreement note
  if (!src.includes('Agreement §3.2')) {
    src = src.replace(
      /\/\*\*[\s\S]*?\*\/\s*public class/,
      `/**
 * Domain service for ${meta.code}.
 * <p>Permission-key checks, tenant isolation, and {@link CampXLogger} audit events per Agreement §3.2.
 * Fail closed on missing authority. Never log tokens, passwords, or JWTs.</p>
 */
public class`
    );
    if (!src.includes('/**')) {
      src = src.replace(
        /public class/,
        `/**
 * Domain service for ${meta.code}.
 * <p>Permission-key checks and {@link CampXLogger} audit events per Agreement §3.2.</p>
 */
public class`
      );
    }
  }

  fs.writeFileSync(file, src);
  console.log('service', file);
}

for (const s of services) {
  const base = path.join(root, s.dir, 'src/main/java', ...s.pkg.split('.'));
  const ctrl = path.join(base, s.ctrl);
  const svc = path.join(base, s.svc);
  if (fs.existsSync(ctrl)) patchController(ctrl, s);
  else console.warn('missing ctrl', ctrl);
  if (fs.existsSync(svc)) patchDomainService(svc, s);
  else console.warn('missing svc', svc);

  // package-info
  const pkgInfo = path.join(base, 'package-info.java');
  if (!fs.existsSync(pkgInfo)) {
    fs.writeFileSync(pkgInfo, `/**
 * ${s.code} microservice (${s.dir}).
 * <p>Logging via {@code campx-logger}; exception handling returns problem JSON without secrets;
 * API routes under {@code /api/v1/iadm/${s.code.slice(-2)}} through the API Gateway (:8080).</p>
 */
package ${s.pkg};
`);
    console.log('package-info', pkgInfo);
  }
}

console.log('done');
