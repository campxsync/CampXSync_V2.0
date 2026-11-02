package com.campx.admissions;

import com.campx.admissions.config.DatabaseConfig;
import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.controller.ApplicationController;
import com.campx.admissions.repository.*;
import com.campx.admissions.server.AdmissionsServer;
import com.campx.admissions.service.AdmissionsEventPublisher;
import com.campx.admissions.service.ApplicationDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Bootstrap entry point for STM-02 Admissions Domain Service.
 * Automatically configures PostgreSQL persistence via {@link DatabaseConnectionManager}
 * when {@code CAMPX_DB_URL} is supplied.
 */
public class AdmissionsApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsApplication.class);

    public static void main(String[] args) {
        try {
            logger.info("Initializing CampXSync Admissions Domain Service (STM-02)...");

            DatabaseConfig dbConfig = new DatabaseConfig();
            boolean hasDb = dbConfig.getJdbcUrl() != null && !dbConfig.getJdbcUrl().trim().isEmpty();

            ApplicationRepository appRepo;
            AdmissionIntakeRepository intakeRepo;
            EligibilityRuleRepository ruleRepo;
            ApplicationDocumentRepository docRepo;
            ApplicationReviewRepository reviewRepo;
            AdmissionDecisionRepository decisionRepo;
            AdmissionOfferRepository offerRepo;
            AdmissionHandoverRepository handoverRepo;
            StatusHistoryRepository historyRepo;

            if (hasDb) {
                logger.info("Configuring PostgreSQL JDBC persistence for Admissions Service...");
                DatabaseConnectionManager.getInstance().initialize(dbConfig);
                appRepo = new PostgresApplicationRepository();
                intakeRepo = new PostgresAdmissionIntakeRepository();
                ruleRepo = new PostgresEligibilityRuleRepository();
                docRepo = new PostgresApplicationDocumentRepository();
                reviewRepo = new PostgresApplicationReviewRepository();
                decisionRepo = new PostgresAdmissionDecisionRepository();
                offerRepo = new PostgresAdmissionOfferRepository();
                handoverRepo = new PostgresAdmissionHandoverRepository();
                historyRepo = new PostgresStatusHistoryRepository();
                logger.info("PostgreSQL persistence initialized successfully with schema 'stu'.");
            } else {
                logger.warn("CAMPX_DB_URL not configured; falling back to in-memory repositories for local/mock execution.");
                appRepo = new InMemoryApplicationRepository();
                intakeRepo = new InMemoryAdmissionIntakeRepository();
                ruleRepo = new InMemoryEligibilityRuleRepository();
                docRepo = new InMemoryApplicationDocumentRepository();
                reviewRepo = new InMemoryApplicationReviewRepository();
                decisionRepo = new InMemoryAdmissionDecisionRepository();
                offerRepo = new InMemoryAdmissionOfferRepository();
                handoverRepo = new InMemoryAdmissionHandoverRepository();
                historyRepo = new InMemoryStatusHistoryRepository();
            }

            AdmissionsEventPublisher eventPublisher = new AdmissionsEventPublisher();

            ApplicationDomainService domainService = new ApplicationDomainService(
                    appRepo, intakeRepo, ruleRepo, docRepo, reviewRepo, decisionRepo, offerRepo, handoverRepo, historyRepo, eventPublisher
            );

            ApplicationController controller = new ApplicationController(domainService);
            AdmissionsServer server = new AdmissionsServer(controller);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                if (hasDb) {
                    DatabaseConnectionManager.getInstance().close();
                }
            }));
            logger.info("Admissions service started successfully on port {}", server.getPort());

        } catch (Exception e) {
            logger.error("Failed to start Admissions Domain Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
