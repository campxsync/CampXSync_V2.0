/*
 * CampXSync V2.0
 * MongoDB Test Data Seeder - CORRECTED
 *
 * PURPOSE
 * -------
 * Ensures 10 synthetic test documents exist in every CampXSync DOMAIN
 * collection.
 *
 * IMPORTANT FIX
 * -------------
 * CampXSync has unique tenant/idempotency indexes such as:
 *   ux_tenant_idempotency
 *
 * Every generated test document therefore receives a unique
 * idempotencyKey. This prevents:
 *   { tenantId: "TEST-TENANT-001", idempotencyKey: null }
 * duplicate-key errors.
 *
 * SAFETY
 * ------
 * - Does NOT drop collections.
 * - Does NOT delete business data.
 * - Does NOT modify indexes.
 * - Does NOT overwrite existing documents.
 * - Preserves successful records from a previous TEST-SEED-001 run.
 * - Fills only missing records for this seed run.
 *
 * DEFAULT RESULT
 * --------------
 * 883 domain collections x 10 documents = 8,830 test documents.
 *
 * SYSTEM COLLECTIONS
 * ------------------
 * The following control-plane collections are excluded:
 *   SYS__modules
 *   SYS__collection_registry
 *   SYS__schema_migrations
 *
 * TEST DATA MARKERS
 * -----------------
 * tenantId       = TEST-TENANT-001
 * status         = TEST
 * seedRun        = TEST-SEED-001
 * createdBy      = SYSTEM_TEST_SEEDER
 *
 * RUN
 * ---
 * Copy this file into the MongoDB container, then run:
 *
 * mongosh --username campxsync_admin \
 *   --authenticationDatabase admin --password \
 *   --file /tmp/CampXSync_MongoDB_Test_Data_Seeder_CORRECTED.js
 */

use("CampXSync");

const SEED_RUN_ID = "TEST-SEED-001";
const TEST_TENANT_ID = "TEST-TENANT-001";
const DOCUMENTS_PER_COLLECTION = 10;
const TEST_STATUS = "TEST";
const SEEDER_USER = "SYSTEM_TEST_SEEDER";

const SYSTEM_COLLECTIONS = new Set([
  "SYS__modules",
  "SYS__collection_registry",
  "SYS__schema_migrations"
]);

const NOW = new Date();

function makeId(collectionName, sequence) {
  return `${SEED_RUN_ID}::${collectionName}::${String(sequence).padStart(2, "0")}`;
}

function makeIdempotencyKey(collectionName, sequence) {
  return `${SEED_RUN_ID}::${collectionName}::${String(sequence).padStart(2, "0")}`;
}

function makeTestDocument(collectionName, sequence) {
  const id = makeId(collectionName, sequence);
  const idempotencyKey = makeIdempotencyKey(collectionName, sequence);

  return {
    _id: id,

    tenantId: TEST_TENANT_ID,
    institutionId: "TEST-INSTITUTION-001",
    campusId: "TEST-CAMPUS-001",

    status: TEST_STATUS,
    version: 1,
    schemaVersion: 1,

    createdAt: NOW,
    updatedAt: NOW,
    createdBy: SEEDER_USER,
    updatedBy: SEEDER_USER,

    correlationId: `TEST-CORRELATION-${collectionName}-${String(sequence).padStart(2, "0")}`,
    sourceSystem: "CampXSync-TestDataSeeder",

    // REQUIRED FOR UNIQUE TENANT/IDEMPOTENCY INDEXES
    idempotencyKey: idempotencyKey,

    _seedMetadata: {
      isTestData: true,
      seedRun: SEED_RUN_ID,
      collection: collectionName,
      sequence: sequence,
      generatedAt: NOW
    },

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
  .filter(name => !SYSTEM_COLLECTIONS.has(name))
  .sort();

print("============================================================");
print(" CampXSync V2.0 - CORRECTED MongoDB Test Data Seeder");
print("============================================================");
print(`Database                 : ${db.getName()}`);
print(`Seed run                 : ${SEED_RUN_ID}`);
print(`Tenant                   : ${TEST_TENANT_ID}`);
print(`Documents/collection    : ${DOCUMENTS_PER_COLLECTION}`);
print(`Collections discovered   : ${domainCollections.length}`);
print("============================================================");
print("Unique idempotencyKey generation: ENABLED");
print("Existing test records will be preserved.");
print("Missing test records will be inserted.");
print("============================================================");

let totalInserted = 0;
let totalAlreadyPresent = 0;
let totalCollectionsComplete = 0;
let totalErrors = 0;

const errorCollections = [];
const startedAt = new Date();

for (const collectionName of domainCollections) {
  try {
    const collection = db.getCollection(collectionName);

    let insertedForCollection = 0;
    let existingForCollection = 0;

    for (let sequence = 1; sequence <= DOCUMENTS_PER_COLLECTION; sequence++) {
      const documentId = makeId(collectionName, sequence);

      const existing = collection.findOne({
        _id: documentId,
        "_seedMetadata.seedRun": SEED_RUN_ID
      });

      if (existing) {
        existingForCollection++;
        totalAlreadyPresent++;
        continue;
      }

      const document = makeTestDocument(collectionName, sequence);

      try {
        collection.insertOne(document);
        insertedForCollection++;
        totalInserted++;
      } catch (insertError) {
        /*
         * If an existing document has the same _id but was created without
         * the expected marker, do not overwrite it.
         *
         * For the normal TEST-SEED-001 case this should not occur.
         */
        if (insertError.code === 11000) {
          throw new Error(
            `Duplicate key while inserting sequence ${sequence}. ` +
            `Existing document may use the same _id or another unique index. ` +
            `${insertError.message}`
          );
        }
        throw insertError;
      }
    }

    const finalCount = collection.countDocuments({
      "_seedMetadata.seedRun": SEED_RUN_ID
    });

    if (finalCount >= DOCUMENTS_PER_COLLECTION) {
      totalCollectionsComplete++;
      print(
        `OK       ${collectionName.padEnd(55)} ` +
        `existing=${String(existingForCollection).padStart(2)} ` +
        `inserted=${String(insertedForCollection).padStart(2)} ` +
        `total=${finalCount}`
      );
    } else {
      print(
        `WARNING  ${collectionName.padEnd(55)} ` +
        `seeded=${finalCount}, expected=${DOCUMENTS_PER_COLLECTION}`
      );
    }

  } catch (err) {
    totalErrors++;
    errorCollections.push(collectionName);
    print(`ERROR    ${collectionName}`);
    print(`         ${err.message}`);
  }
}

const finishedAt = new Date();

print("");
print("============================================================");
print(" CampXSync V2.0 - CORRECTED Seeder Completed");
print("============================================================");
print(`Database                 : ${db.getName()}`);
print(`Seed run                 : ${SEED_RUN_ID}`);
print(`Tenant                   : ${TEST_TENANT_ID}`);
print(`Collections processed    : ${domainCollections.length}`);
print(`Collections complete     : ${totalCollectionsComplete}`);
print(`Documents target         : ${domainCollections.length * DOCUMENTS_PER_COLLECTION}`);
print(`Documents inserted       : ${totalInserted}`);
print(`Documents already present: ${totalAlreadyPresent}`);
print(`Errors                   : ${totalErrors}`);
print(`Started                  : ${startedAt.toISOString()}`);
print(`Completed                : ${finishedAt.toISOString()}`);
print("============================================================");

if (totalErrors === 0 &&
    totalCollectionsComplete === domainCollections.length) {
  print("STATUS: TEST DATA SEEDING SUCCESSFUL");
  print(
    `VERIFICATION: Every domain collection contains at least ` +
    `${DOCUMENTS_PER_COLLECTION} records for ${SEED_RUN_ID}.`
  );
} else {
  print("STATUS: TEST DATA SEEDING INCOMPLETE");
  print("Review the ERROR/WARNING entries above.");
}

if (errorCollections.length > 0) {
  print("");
  print("FAILED COLLECTIONS");
  print("------------------");
  errorCollections.forEach(name => print(name));
}

print("");
print("TEST DATA IDENTIFICATION");
print("-------------------------");
print(`tenantId         = ${TEST_TENANT_ID}`);
print(`status           = ${TEST_STATUS}`);
print(`seedRun          = ${SEED_RUN_ID}`);
print(`idempotencyKey   = unique per test document`);
print("");
print("No SYS__ control-plane collections were modified.");
print("No collections were dropped.");
print("No existing documents were deleted.");
print("No indexes were modified.");
print("============================================================");
