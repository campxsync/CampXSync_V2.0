/**
 * Clone IADM-01 boilerplate into remaining IADM services (03,04,05,06,07,08)
 * then write service-specific DomainService + Controller stubs.
 * Run: node scripts/dev/scaffold_iadm_remaining.mjs
 */
import fs from "fs";
import path from "path";

const ROOT = process.cwd();
const TEMPLATE = path.join(
  ROOT,
  "services/administration-services/iadm-01-tenant-lifecycle-service"
);

const SERVICES = [
  { id: "03", port: 8101, dir: "iadm-03-identity-access-service", pkg: "com.campx.iadm.identity", pool: "IADM03", title: "IADM-03 Platform Identity and Access" },
  { id: "08", port: 8106, dir: "iadm-08-audit-ops-service", pkg: "com.campx.iadm.audit", pool: "IADM08", title: "IADM-08 Audit Health and Operations" },
  { id: "05", port: 8103, dir: "iadm-05-data-governance-service", pkg: "com.campx.iadm.privacy", pool: "IADM05", title: "IADM-05 Data Governance and Privacy" },
  { id: "07", port: 8105, dir: "iadm-07-security-compliance-service", pkg: "com.campx.iadm.securitygov", pool: "IADM07", title: "IADM-07 Security and Compliance" },
  { id: "04", port: 8102, dir: "iadm-04-billing-entitlements-service", pkg: "com.campx.iadm.billing", pool: "IADM04", title: "IADM-04 Subscription Billing and Entitlements" },
  { id: "06", port: 8104, dir: "iadm-06-platform-analytics-service", pkg: "com.campx.iadm.analytics", pool: "IADM06", title: "IADM-06 Platform Analytics and Usage" },
];

function walk(dir) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

function rewrite(content, svc) {
  return content
    .replaceAll("com.campx.iadm.lifecycle", svc.pkg)
    .replaceAll("CampXSync-IADM01-Pool", `CampXSync-${svc.pool}-Pool`)
    .replaceAll("IADM01_", `IADM${svc.id}_`)
    .replaceAll("IADM-01", `IADM-${svc.id}`)
    .replaceAll("iadm-01-tenant-lifecycle-service", svc.dir)
    .replaceAll("Iadm01", `Iadm${svc.id}`)
    .replaceAll("iadm01", `iadm${svc.id}`)
    .replaceAll("8099", String(svc.port))
    .replaceAll("/api/v1/iadm/01", `/api/v1/iadm/${svc.id}`)
    .replaceAll("Tenant Lifecycle", svc.title)
    .replaceAll("TenantLifecycle", `Iadm${svc.id}Domain`);
}

for (const svc of SERVICES) {
  const dest = path.join(ROOT, "services/administration-services", svc.dir);
  if (fs.existsSync(dest)) {
    console.log("skip existing", svc.dir);
    continue;
  }
  for (const file of walk(TEMPLATE)) {
    const rel = path.relative(TEMPLATE, file);
    let destRel = rel
      .replaceAll("com" + path.sep + "campx" + path.sep + "iadm" + path.sep + "lifecycle",
        svc.pkg.replaceAll(".", path.sep))
      .replaceAll("Iadm01", `Iadm${svc.id}`)
      .replaceAll("TenantLifecycle", `Iadm${svc.id}Domain`);
    // drop lifecycle-specific service/controller/tests; recreate below
    if (/TenantLifecycle|Iadm01Controller|Iadm01Application|Iadm01Server/.test(path.basename(file))) {
      // still copy renamed versions of app/server; skip domain-specific tests and service/controller
      if (/service[\\/]TenantLifecycle|controller[\\/]Iadm01|TenantLifecycleRbac|TenantLifecycleLive/.test(rel.replaceAll("\\", "/"))) {
        continue;
      }
    }
    const out = path.join(dest, destRel);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    let body = fs.readFileSync(file, "utf8");
    body = rewrite(body, svc);
    // strip BOM if any
    if (body.charCodeAt(0) === 0xfeff) body = body.slice(1);
    fs.writeFileSync(out, body);
  }

  // pom title
  const pomPath = path.join(dest, "pom.xml");
  let pom = fs.readFileSync(pomPath, "utf8");
  pom = pom
    .replace(/<artifactId>.*<\/artifactId>/, `<artifactId>${svc.dir}</artifactId>`)
    .replace(/<name>.*<\/name>/, `<name>CampXSync ${svc.title}</name>`);
  fs.writeFileSync(pomPath, pom);
  console.log("scaffolded", svc.dir);
}

// Update aggregator modules
const agg = path.join(ROOT, "services/administration-services/pom.xml");
let aggXml = fs.readFileSync(agg, "utf8");
for (const svc of SERVICES) {
  if (!aggXml.includes(`<module>${svc.dir}</module>`)) {
    aggXml = aggXml.replace(
      "</modules>",
      `        <module>${svc.dir}</module>\n    </modules>`
    );
  }
}
fs.writeFileSync(agg, aggXml);
console.log("aggregator updated");
