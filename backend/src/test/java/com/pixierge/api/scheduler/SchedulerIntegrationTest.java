package com.pixierge.api.scheduler;

import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QScheduledJobLocks;
import com.pixierge.api.db.QScheduledJobRuns;
import com.pixierge.api.db.QScheduledJobs;
import com.pixierge.api.db.QSessions;
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulerIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "correct horse battery staple";
    private static final String CSRF_HEADER = "X-Pixierge-Csrf";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void schedulerProperties(DynamicPropertyRegistry registry) {
        registry.add("pixierge.scheduler.poll-interval-ms", () -> "600000");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SQLQueryFactory queryFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SchedulerRepository schedulerRepository;

    @Autowired
    private SchedulerJobSeeder schedulerJobSeeder;

    @BeforeEach
    void clearData() {
        waitForIdleScheduler();
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(QScheduledJobLocks.scheduledJobLocks).execute();
            queryFactory.delete(QScheduledJobRuns.scheduledJobRuns).execute();
            queryFactory.delete(QScheduledJobs.scheduledJobs).execute();
            queryFactory.delete(QSessions.sessions).execute();
            queryFactory.delete(QPasswordCredentials.passwordCredentials).execute();
            queryFactory.delete(QUserRoles.userRoles).execute();
            queryFactory.delete(QUsers.users).execute();
        });
        schedulerJobSeeder.seedJobs();
    }

    @Test
    void listsSeededJobsAndSupportsManualRunWhileDisabled() {
        CookiePair cookies = loginAsAdmin();

        ResponseEntity<List<SchedulerJobResponse>> listResponse = restTemplate.exchange(
                "/api/admin/scheduler/jobs",
                HttpMethod.GET,
                withCookie(cookies.cookie()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody()).extracting(SchedulerJobResponse::jobKey)
                .contains(CoreSchedulerJobsConfig.LIBRARY_SCAN_JOB_KEY, CoreSchedulerJobsConfig.METADATA_SCAN_JOB_KEY);

        SchedulerJobResponse metadataJob = listResponse.getBody().stream()
                .filter(job -> CoreSchedulerJobsConfig.METADATA_SCAN_JOB_KEY.equals(job.jobKey()))
                .findFirst()
                .orElseThrow();

        ResponseEntity<SchedulerJobResponse> disabled = restTemplate.exchange(
                "/api/admin/scheduler/jobs/" + metadataJob.id(),
                HttpMethod.PATCH,
                withCookieAndCsrf(cookies.cookie(), cookies.csrf(), Map.of("enabled", false)),
                SchedulerJobResponse.class
        );
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody()).isNotNull();
        assertThat(disabled.getBody().enabled()).isFalse();
        assertThat(disabled.getBody().nextRunAt()).isNull();

        ResponseEntity<SchedulerJobRunResponse> runResponse = restTemplate.exchange(
                "/api/admin/scheduler/jobs/" + metadataJob.id() + "/run",
                HttpMethod.POST,
                withCookieAndCsrf(cookies.cookie(), cookies.csrf(), null),
                SchedulerJobRunResponse.class
        );
        assertThat(runResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(runResponse.getBody()).isNotNull();
        assertThat(runResponse.getBody().triggerSource()).isEqualTo("manual");

        SchedulerJobRunRecord completedRun = waitForRunTerminal(metadataJob.id());
        assertThat(completedRun.triggerSource()).isEqualTo("manual");
        assertThat(completedRun.status()).isIn("succeeded", "failed");
    }

    @Test
    void concurrencyLockPreventsOverlappingRuns() {
        SchedulerJobRecord job = schedulerRepository.findJobByKey(CoreSchedulerJobsConfig.METADATA_SCAN_JOB_KEY).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();

        transactionTemplate.executeWithoutResult(status -> {
            UUID firstRun = schedulerRepository.insertRun(job.id(), "manual", "running", now);
            assertThat(schedulerRepository.tryAcquireLock(
                    job.concurrencyKey(), job.id(), firstRun, now, now.minusSeconds(job.timeoutSeconds())
            )).isTrue();
            assertThat(schedulerRepository.isLocked(job.concurrencyKey())).isTrue();
            assertThat(schedulerRepository.tryAcquireLock(
                    job.concurrencyKey(), job.id(), UUID.randomUUID(), now, now.minusSeconds(job.timeoutSeconds())
            )).isFalse();
        });

        assertThat(schedulerRepository.isLocked(job.concurrencyKey())).isTrue();

        CookiePair cookies = loginAsAdmin();
        ResponseEntity<String> conflict = restTemplate.exchange(
                "/api/admin/scheduler/jobs/" + job.id() + "/run",
                HttpMethod.POST,
                withCookieAndCsrf(cookies.cookie(), cookies.csrf(), null),
                String.class
        );
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        transactionTemplate.executeWithoutResult(status -> {
            UUID lockRunId = queryFactory.select(QScheduledJobLocks.scheduledJobLocks.runId)
                    .from(QScheduledJobLocks.scheduledJobLocks)
                    .where(QScheduledJobLocks.scheduledJobLocks.concurrencyKey.eq(job.concurrencyKey()))
                    .fetchOne();
            schedulerRepository.releaseLock(job.concurrencyKey(), lockRunId);
            queryFactory.delete(QScheduledJobRuns.scheduledJobRuns)
                    .where(QScheduledJobRuns.scheduledJobRuns.jobId.eq(job.id()))
                    .execute();
        });
    }

    @Test
    void expiredLockCanBeReplacedWithoutOldOwnerReleasingReplacement() {
        SchedulerJobRecord job = schedulerRepository.findJobByKey(CoreSchedulerJobsConfig.METADATA_SCAN_JOB_KEY).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();

        transactionTemplate.executeWithoutResult(status -> {
            UUID expiredRun = schedulerRepository.insertRun(
                    job.id(), "manual", "running", now.minusSeconds(job.timeoutSeconds() + 1L)
            );
            UUID replacementRun = schedulerRepository.insertRun(job.id(), "manual", "running", now);
            assertThat(schedulerRepository.tryAcquireLock(
                    job.concurrencyKey(),
                    job.id(),
                    expiredRun,
                    now.minusSeconds(job.timeoutSeconds() + 1L),
                    now.minusSeconds(job.timeoutSeconds() * 2L)
            )).isTrue();
            assertThat(schedulerRepository.tryAcquireLock(
                    job.concurrencyKey(),
                    job.id(),
                    replacementRun,
                    now,
                    now.minusSeconds(job.timeoutSeconds())
            )).isTrue();

            schedulerRepository.releaseLock(job.concurrencyKey(), expiredRun);
            assertThat(schedulerRepository.isLocked(job.concurrencyKey())).isTrue();

            schedulerRepository.releaseLock(job.concurrencyKey(), replacementRun);
            assertThat(schedulerRepository.isLocked(job.concurrencyKey())).isFalse();
        });
    }

    @Test
    void staleExecutionSnapshotCannotOverwriteEditedSchedule() {
        SchedulerJobRecord staleJob = schedulerRepository.findJobByKey(
                CoreSchedulerJobsConfig.METADATA_SCAN_JOB_KEY
        ).orElseThrow();
        OffsetDateTime editedNextRun = OffsetDateTime.now().plusHours(1);

        transactionTemplate.executeWithoutResult(status -> {
            schedulerRepository.updateJobSchedule(
                    staleJob.id(),
                    true,
                    "0 0 * * * *",
                    "Australia/Melbourne",
                    editedNextRun
            );
            assertThat(schedulerRepository.advanceNextRunIfScheduleUnchanged(
                    staleJob,
                    OffsetDateTime.now().plusDays(1)
            )).isFalse();
            schedulerRepository.updateJobRunState(staleJob.id(), OffsetDateTime.now(), "succeeded");
        });

        SchedulerJobRecord editedJob = schedulerRepository.findJob(staleJob.id()).orElseThrow();
        assertThat(editedJob.cronExpression()).isEqualTo("0 0 * * * *");
        assertThat(editedJob.timezone()).isEqualTo("Australia/Melbourne");
        assertThat(editedJob.nextRunAt()).isEqualTo(editedNextRun);
        assertThat(editedJob.lastStatus()).isEqualTo("succeeded");
    }

    @Test
    void rejectsInvalidCronOnPatch() {
        CookiePair cookies = loginAsAdmin();
        SchedulerJobRecord job = schedulerRepository.findJobByKey(CoreSchedulerJobsConfig.LIBRARY_SCAN_JOB_KEY).orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/scheduler/jobs/" + job.id(),
                HttpMethod.PATCH,
                withCookieAndCsrf(cookies.cookie(), cookies.csrf(), Map.of("cronExpression", "bad")),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void waitForIdleScheduler() {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            boolean busy = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                boolean locked = queryFactory.select(QScheduledJobLocks.scheduledJobLocks.concurrencyKey)
                        .from(QScheduledJobLocks.scheduledJobLocks)
                        .fetchFirst() != null;
                boolean running = queryFactory.select(QScheduledJobRuns.scheduledJobRuns.id)
                        .from(QScheduledJobRuns.scheduledJobRuns)
                        .where(QScheduledJobRuns.scheduledJobRuns.status.in("queued", "running"))
                        .fetchFirst() != null;
                return locked || running;
            }));
            if (!busy) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    private SchedulerJobRunRecord waitForRunTerminal(UUID jobId) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<SchedulerJobRunRecord> runs = schedulerRepository.listRuns(jobId, 1);
            if (!runs.isEmpty() && !"running".equals(runs.getFirst().status()) && !"queued".equals(runs.getFirst().status())) {
                return runs.getFirst();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new AssertionError("Timed out waiting for scheduler run to finish");
    }

    private CookiePair loginAsAdmin() {
        restTemplate.postForEntity(
                "/api/setup/admin",
                Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD),
                Map.class
        );
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/api/auth/login",
                Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD),
                Map.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new CookiePair(cookiePair(login), csrfToken(login));
    }

    private static String cookiePair(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();
        return String.join("; ", cookies.stream().map(value -> value.split(";", 2)[0]).toList());
    }

    private static String csrfToken(ResponseEntity<?> response) {
        Object body = response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        Object token = ((Map<?, ?>) body).get("csrfToken");
        assertThat(token).isInstanceOf(String.class);
        return (String) token;
    }

    private static HttpEntity<Void> withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<?> withCookieAndCsrf(String cookie, String csrf, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(CSRF_HEADER, csrf);
        if (body == null) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(body, headers);
    }

    private record CookiePair(String cookie, String csrf) {
    }
}
