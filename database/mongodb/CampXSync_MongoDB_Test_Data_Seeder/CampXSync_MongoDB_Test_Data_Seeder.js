/*
 * CampXSync V2.0
 * MongoDB Test Data Seeder
 *
 * PURPOSE
 * -------
 * Inserts 10 clearly-marked test documents into every CampXSync DOMAIN
 * collection created by the enterprise bootstrap.
 *
 * IMPORTANT
 * ---------
 * The 3 SYS__ collections are intentionally excluded by default because
 * they contain control-plane metadata:
 *   - SYS__modules
 *   - SYS__collection_registry
 *   - SYS__schema_migrations
 *
 * Inserting arbitrary test documents into those collections could pollute
 * the database registry/migration metadata.
 *
 * SAFETY
 * ------
 * - Does NOT drop collections.
 * - Does NOT delete business data.
 * - Does NOT overwrite existing documents.
 * - Uses a dedicated marker: _seedMetadata.seedRun
 * - Safe to run repeatedly with a new SEED_RUN_ID.
 *
 * DEFAULT RESULT
 * --------------
 * 883 domain collections x 10 documents = 8,830 test documents.
 *
 * RUN WITH mongosh, for example:
 *   mongosh --username campxsync_admin --authenticationDatabase admin --password
 *   load("/tmp/CampXSync_MongoDB_Test_Data_Seeder.js")
 *
 * Or copy the file into the container and execute with --file.
 */

use("CampXSync");

const SEED_RUN_ID = "TEST-SEED-001";
const DOCUMENTS_PER_COLLECTION = 10;

// Keep false. The SYS__ collections are infrastructure metadata.
const INCLUDE_SYSTEM_COLLECTIONS = false;

// Optional: set true if you want the script to refuse to seed a collection
// that already contains documents. Default false allows additive test data.
const REFUSE_NON_EMPTY_COLLECTIONS = false;

const NOW = new Date();

const SYSTEM_COLLECTIONS = new Set([
  "SYS__modules",
  "SYS__collection_registry",
  "SYS__schema_migrations"
]);

function makeTestDocument(collectionName, sequence) {
  const id = `${SEED_RUN_ID}::${collectionName}::${String(sequence).padStart(2, "0")}`;

  return {
    _id: id,

    // Common CampXSync tenant/lifecycle fields
    tenantId: "TEST-TENANT-001",
    institutionId: "TEST-INSTITUTION-001",
    campusId: "TEST-CAMPUS-001",
    status: "TEST",
    version: 1,
    schemaVersion: 1,

    createdAt: NOW,
    updatedAt: NOW,
    createdBy: "SYSTEM_TEST_SEEDER",
    updatedBy: "SYSTEM_TEST_SEEDER",

    correlationId: `TEST-CORRELATION-${sequence}`,
    sourceSystem: "CampXSync-TestDataSeeder",

    // Explicitly marks this as synthetic data.
    _seedMetadata: {
      isTestData: true,
      seedRun: SEED_RUN_ID,
      collection: collectionName,
      sequence: sequence,
      generatedAt: NOW
    },

    // Generic test payload. The enterprise bootstrap allows additional
    // properties, so this does not pretend to be a real business document.
    testPayload: {
      testCode: `${collectionName}-TEST-${String(sequence).padStart(3, "0")}`,
      testName: `CampXSync Test Record ${sequence}`,
      description: `Synthetic test document for ${collectionName}`,
      active: true
    }
  };
}

const allCollections = db.getCollectionNames();

const domainCollections = allCollections
  .filter(name => !name.startsWith("system."))
  .filter(name => INCLUDE_SYSTEM_COLLECTIONS || !SYSTEM_COLLECTIONS.has(name))
  .sort();

print("============================================================");
print(" CampXSync V2.0 - MongoDB Test Data Seeder");
print("============================================================");
print(`Database                 : ${db.getName()}`);
print(`Seed run                 : ${SEED_RUN_ID}`);
print(`Documents/collection    : ${DOCUMENTS_PER_COLLECTION}`);
print(`System collections       : ${INCLUDE_SYSTEM_COLLECTIONS ? "INCLUDED" : "EXCLUDED"}`);
print(`Collections discovered   : ${domainCollections.length}`);
print("============================================================");

let totalInserted = 0;
let totalSkipped = 0;
let totalErrors = 0;

const startedAt = new Date();

for (const collectionName of domainCollections) {
  try {
    const collection = db.getCollection(collectionName);

    if (REFUSE_NON_EMPTY_COLLECTIONS && collection.estimatedDocumentCount() > 0) {
      print(`SKIPPED  ${collectionName}  (collection is not empty)`);
      totalSkipped++;
      continue;
    }

    // Prevent accidental duplicate insertion for the same seed run.
    const existingSeedCount = collection.countDocuments({
      "_seedMetadata.seedRun": SEED_RUN_ID
    });

    if (existingSeedCount >= DOCUMENTS_PER_COLLECTION) {
      print(`EXISTS   ${collectionName}  (${existingSeedCount} documents for ${SEED_RUN_ID})`);
      totalSkipped++;
      continue;
    }

    const documentsToInsert = [];

    for (let i = 1; i <= DOCUMENTS_PER_COLLECTION; i++) {
      const documentId =
        `${SEED_RUN_ID}::${collectionName}::${String(i).padStart(2, "0")}`;

      // Handles a partially completed previous run.
      if (!collection.findOne({ _id: documentId })) {
        documentsToInsert.push(makeTestDocument(collectionName, i));
      }
    }

    if (documentsToInsert.length > 0) {
      const result = collection.insertMany(documentsToInsert, { ordered: false });
      const inserted = Object.keys(result.insertedIds).length;
      totalInserted += inserted;
      print(`INSERTED ${collectionName.padEnd(55)} ${inserted}`);
    } else {
      totalSkipped++;
      print(`EXISTS   ${collectionName.padEnd(55)} 10`);
    }
  } catch (err) {
    totalErrors++;
    print(`ERROR    ${collectionName}`);
    print(`         ${err.message}`);
  }
}

const finishedAt = new Date();

print("");
print("============================================================");
print(" CampXSync V2.0 - Test Data Seeder Completed");
print("============================================================");
print(`Database                 : ${db.getName()}`);
print(`Seed run                 : ${SEED_RUN_ID}`);
print(`Collections processed    : ${domainCollections.length}`);
print(`Documents requested      : ${domainCollections.length * DOCUMENTS_PER_COLLECTION}`);
print(`Documents inserted       : ${totalInserted}`);
print(`Collections skipped      : ${totalSkipped}`);
print(`Errors                   : ${totalErrors}`);
print(`Started                  : ${startedAt.toISOString()}`);
print(`Completed                : ${finishedAt.toISOString()}`);
print("============================================================");

if (totalErrors > 0) {
  print("WARNING: One or more collections could not be seeded.");
  print("Review the ERROR entries above before proceeding.");
} else {
  print("STATUS: TEST DATA SEEDING SUCCESSFUL");
}

print("");
print("TEST DATA IDENTIFICATION");
print("-------------------------");
print(`tenantId   = TEST-TENANT-001`);
print(`status     = TEST`);
print(`seedRun    = ${SEED_RUN_ID}`);
print("");
print("To remove ONLY this seed run later, use:");
print(`db.getCollectionNames().forEach(c => {`);
print(`  if (!c.startsWith("system.")) {`);
print(`    db.getCollection(c).deleteMany({"_seedMetadata.seedRun":"${SEED_RUN_ID}"});`);
print(`  }`);
print(`});`);
print("============================================================");
