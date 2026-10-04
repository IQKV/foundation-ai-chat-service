# Implementation Plan

> **Build result:** `BUILD SUCCESS` — Java 25.0.4.1 (Corretto), Maven 3.10.0.
> All 2 tests pass (0 failures, 0 errors). `mvn checkstyle:check` reports **0 violations**.
> The plan below covers runtime warnings surfaced by the integration test, plus structural and configuration issues found by static analysis.

---

## (1) Compilation Errors

None. The project compiles clean against Java 25 with no errors or deprecation warnings from javac.

---

## (2) Test Failures

None. Both test classes pass:

- `TechnicalStructureTest` — 1 test, 0 failures
- `ServicenameApplicationTests` — 1 test, 0 failures

---

## (3) Runtime Warnings (surfaced during integration test context load)

These appear in the `ServicenameApplicationTests` output and indicate real issues that will appear in production.

- [ ] 1. **Fix Mockito agent self-attachment warning (`byte-buddy-agent`)**

    The integration test emits:

    ```
    WARNING: A Java agent has been loaded dynamically (byte-buddy-agent-1.18.10.jar)
    WARNING: Dynamic loading of agents will be disallowed by default in a future release
    ```

    This is caused by Mockito's inline-mock-maker self-attaching via the Attach API, which Java is phasing out. On Java 25 it still works but is logged as a warning; future JDK releases will disallow it, breaking the test suite.

    **Fix:** Add Mockito as a `-javaagent` in `maven-surefire-plugin`'s `argLine` in `pom.xml`:

    ```xml
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <configuration>
        <argLine>-javaagent:${settings.localRepository}/net/bytebuddy/byte-buddy-agent/${byte-buddy.version}/byte-buddy-agent-${byte-buddy.version}.jar</argLine>
      </configuration>
    </plugin>
    ```

    Note: the `byte-buddy.version` property is likely managed by the parent pom `boot-parent-pom:0.24.30`. Verify the property name by checking `mvn help:effective-pom` output, or use a Maven dependency lookup. Alternatively, add a `mockito-agent` dependency with `<scope>test</scope>` and configure `maven-dependency-plugin` to copy it, then reference the copied path in `argLine`.

    File: `pom.xml`

    Verify: `mvn test` — warning about dynamic agent loading no longer appears in test output.

- [ ] 2. **Fix logback-spring.xml — broken appender reference and duplicate `<root>` elements**

    The log confirms `SpringDoc /api-docs endpoint is enabled by default` appears garbled in the build output (interleaved console output from two threads), which is a symptom of the logback misconfiguration. More critically, static analysis reveals:

    1. `ASYNC_CONSOLE_JSON` appender is declared **outside** any `<springProfile>` (line 22–27) but references `CONSOLE_JSON`, which is only defined **inside** `<springProfile name="uat,prd">` (line 46). On local/sit profiles, logback will log an error about the undefined appender reference.
    2. **Two `<root>` elements** exist outside any `<springProfile>` (lines 39–41 and 83–85). Logback processes the second one as an override of the first, but this is an error in the config structure.

    **Fix:** Restructure `logback-spring.xml` so that:
    - Only one `CONSOLE` appender and one `<root>` element exist at the top level.
    - The stray top-level `ASYNC_CONSOLE_JSON` appender declaration (lines 22–27) is removed entirely — it is already correctly redeclared inside `<springProfile name="uat,prd">`.
    - The duplicate top-level `<root>` element (lines 83–85) is removed.

    The correct structure:

    ```xml
    <configuration>
      <!-- properties ... -->

      <!-- CONSOLE appender (text, default) -->
      <appender name="CONSOLE" .../>

      <!-- Loggers -->
      <logger name="com.iqkv" level="..."/>
      ...

      <!-- Default root (text format) -->
      <root level="${LOG_LEVEL_ROOT}">
        <appender-ref ref="CONSOLE"/>
      </root>

      <!-- uat/prd: override with JSON format -->
      <springProfile name="uat,prd">
        <appender name="CONSOLE_JSON" .../>
        <appender name="ASYNC_CONSOLE_JSON" .../>
        <root level="${LOG_LEVEL_ROOT}">
          <appender-ref ref="ASYNC_CONSOLE_JSON"/>
        </root>
      </springProfile>
    </configuration>
    ```

    File: `src/main/resources/logback-spring.xml`

    Verify: Start with `-Dspring.profiles.active=local` — no logback ERROR/WARN about undefined appender. Start with `-Dspring.profiles.active=prd` — JSON output on stdout.

- [ ] 3. **Fix CGLIB proxy warning for `OncePerRequestFilter` subclasses**

    The integration test emits:

    ```
    WARN CglibAopProxy: Unable to proxy interface-implementing method
    [public final void GenericFilterBean.init(FilterConfig)] because it is marked as final,
    consider using interface-based JDK proxies instead.
    ```

    This warning is triggered by Spring attempting to CGLIB-proxy one of the `OncePerRequestFilter` subclasses (`CorrelationIdFilter` or `TenantExtractionFilter`). Both are annotated `@Component` and `@Order`, and may be inadvertently captured by an AOP pointcut.

    Filters registered as Spring beans and added to the security filter chain via `addFilterBefore/addFilterAfter` should **not** be CGLIB-proxied. The fix is to add `@Bean(proxyTargetClass = false)` if declaring them as beans, or more correctly, to ensure no AOP advice wraps them.

    **Investigation step (before fixing):** Check whether the parent pom's AOP configuration or any `@Aspect` in the project is targeting these filter classes. If they are only annotated with `@Component`, the warning may come from Spring Security's internal handling.

    **Fix:** If the warning originates from Spring Security wrapping the filter: no code change needed (it is a Spring internals warning). If it comes from an explicit `@Aspect`, exclude filter classes from the pointcut. If the issue is that `CorrelationIdFilter` / `TenantExtractionFilter` are being proxied when they should not be, annotate them with `@Lazy(false)` or register them differently.

    This item requires runtime investigation — defer to implementation phase.

    File: `src/main/java/com/iqkv/foundation/servicename/infrastructure/security/CorrelationIdFilter.java` and/or `src/main/java/com/iqkv/foundation/servicename/tenancy/TenantExtractionFilter.java`

    Verify: `mvn test` — CGLIB proxy warning for `GenericFilterBean.init` no longer appears.

- [ ] 4. **Fix `application-test.yml` — pin JWT auth config to prevent CI breakage**

    The test context currently relies on the base `application.yml` defaults:
    - `jwks-uri: ${JWT_JWKS_URI:}` → empty string when env var unset
    - `public-key-path: ${JWT_PUBLIC_KEY_PATH:classpath:keys/public.pem}` → classpath PEM

    `AuthConfigurationProperties.validate()` treats empty string as "not set", so the PEM path is used. This works locally but **breaks in CI if `JWT_JWKS_URI` is set** in the environment — then both values are non-blank and startup throws `IllegalStateException`.

    **Fix:** Explicitly pin the auth config in `application-test.yml`:

    ```yaml
    iqkv:
        auth:
            jwt:
                jwks-uri: ""
                public-key-path: "classpath:keys/public.pem"
    ```

    File: `src/test/resources/application-test.yml`

    Verify: `mvn test` passes with `JWT_JWKS_URI=http://some-url` exported in the shell.

- [ ] 5. **Fix `application-test.yml` — suppress RabbitMQ auto-configuration in tests**

    The integration test warns about `PlanResolver` trying to connect to `foundation-billing-service` (expected — a library concern). More importantly, `spring-boot-starter-amqp` registers a `RabbitHealthIndicator` that will report DOWN since no RabbitMQ is running. This pollutes health-endpoint assertions in future integration tests.

    **Fix:** Add to `application-test.yml`:

    ```yaml
    spring:
        autoconfigure:
            exclude:
                - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
    ```

    File: `src/test/resources/application-test.yml`

    Verify: `mvn test` — no RabbitMQ connection attempts or `AmqpConnectException` in test logs; `RabbitHealthIndicator` absent from context.

---

## (4) Architectural / Structural Issues

- [ ] 6. **Move `TenantExtractionFilter` to `infrastructure.security` package**

    `TenantExtractionFilter` lives in `com.iqkv.foundation.servicename.tenancy`, a top-level package with no DDD role defined in AGENTS.md. Servlet filters are infrastructure concerns; the companion filter `CorrelationIdFilter` correctly lives in `infrastructure.security`. Having this filter in a separate `tenancy` package is inconsistent and leaves a single-file orphan package.

    **Fix:**
    1. Move `TenantExtractionFilter.java` to `src/main/java/com/iqkv/foundation/servicename/infrastructure/security/`.
    2. Update `package` declaration to `com.iqkv.foundation.servicename.infrastructure.security`.
    3. Update `SecurityConfig.java`: change import `...tenancy.TenantExtractionFilter` → `...infrastructure.security.TenantExtractionFilter`.
    4. Update `infrastructure.security.package-info.java` Javadoc `<ul>` to include `TenantExtractionFilter`.
    5. Delete the now-empty `src/main/java/com/iqkv/foundation/servicename/tenancy/` directory.

    Files:
    - `src/main/java/com/iqkv/foundation/servicename/tenancy/TenantExtractionFilter.java` → move
    - `src/main/java/com/iqkv/foundation/servicename/infrastructure/security/TenantExtractionFilter.java` → new
    - `src/main/java/com/iqkv/foundation/servicename/infrastructure/config/SecurityConfig.java` → import
    - `src/main/java/com/iqkv/foundation/servicename/infrastructure/security/package-info.java` → Javadoc

    Verify: `mvn test "-Dcheckstyle.skip=true"` passes; `TechnicalStructureTest` passes.

- [ ] 7. **Move `AllTenantsKeyProvider` from `infrastructure.config` to `infrastructure.persistence`**

    `AllTenantsKeyProvider` is a `@Component` that queries the database. Per `infrastructure.config.package-info.java`, that package is for "Spring `@Configuration` classes and bean definitions". A data-access component belongs in `infrastructure.persistence`.

    **Fix:** Move `AllTenantsKeyProvider.java` to `src/main/java/com/iqkv/foundation/servicename/infrastructure/persistence/`. Update its `package` declaration. No other files import it directly (Spring resolves by interface).

    Files:
    - `src/main/java/com/iqkv/foundation/servicename/infrastructure/config/AllTenantsKeyProvider.java` → move
    - `src/main/java/com/iqkv/foundation/servicename/infrastructure/persistence/AllTenantsKeyProvider.java` → new

    Verify: `mvn test "-Dcheckstyle.skip=true"` passes.

- [ ] 8. **Add missing `package-info.java` files**

    Three packages have source files but no `package-info.java`, inconsistent with every other package in the project:

    | Package                                   | Source files                                           |
    | ----------------------------------------- | ------------------------------------------------------ |
    | `com.iqkv.foundation.servicename.ping`    | `PingDtos.java`, `PingRestResource.java`               |
    | `com.iqkv.foundation.servicename.tenancy` | `TenantExtractionFilter.java` (or remove after item 6) |
    | `com.iqkv.foundation.servicename.shared`  | _(empty — only child package-infos exist)_             |

    **Fix:** Create `package-info.java` for each missing package using the standard Apache licence header. Example:

    ```java
    /*
     * Copyright 2026 iQKV Foundation Team.
     * Licensed under the Apache License, Version 2.0 ...
     */

    /**
     * Public, tenant-scoped, and admin ping endpoints — reachability probes
     * demonstrating the three security tiers.
     */
    package com.iqkv.foundation.servicename.ping;
    ```

    If item 6 is applied first, the `tenancy` package is deleted; skip its `package-info.java`.

    Files to create:
    - `src/main/java/com/iqkv/foundation/servicename/ping/package-info.java`
    - `src/main/java/com/iqkv/foundation/servicename/shared/package-info.java`
    - `src/main/java/com/iqkv/foundation/servicename/tenancy/package-info.java` _(only if item 6 is deferred)_

    Verify: `mvn checkstyle:check` — 0 violations (already 0; this ensures it stays 0 after adding files).

---

## (5) Configuration Issues

- [ ] 9. **`application-prd.yml` and `application-uat.yml` — switch from `public-key-path` to `jwks-uri`**

    Both profiles configure:

    ```yaml
    iqkv:
        auth:
            jwt:
                public-key-path: ${JWT_PUBLIC_KEY_PATH}
    ```

    Per `AuthConfigurationProperties` Javadoc and `SecurityConfig` comments, JWKS URI is the **preferred** strategy for deployed (K8s) environments: keys are fetched and cached from the IAM service, and key rotation is transparent without redeployment. A static PEM requires manually distributing and rotating a file across all pods.

    **Fix:** In `application-prd.yml` and `application-uat.yml`, replace `public-key-path` with `jwks-uri`:

    ```yaml
    iqkv:
        auth:
            jwt:
                jwks-uri: ${JWT_JWKS_URI}
    ```

    (`application-sit.yml` is a staging environment and might intentionally keep the PEM for simplicity — leave it as-is unless the team decides otherwise.)

    Files:
    - `src/main/resources/application-prd.yml`
    - `src/main/resources/application-uat.yml`

    Verify: `AuthConfigurationProperties.validate()` passes when `JWT_JWKS_URI` env var is set and `JWT_PUBLIC_KEY_PATH` is unset.

- [ ] 10. **Fix stale docker-compose comment in `application-sit.yml`**

    The `sit` profile header contains:

    ```
    # Run infrastructure with: docker compose up postgres-servicename rabbitmq-servicename
    ```

    `sit` is a Kubernetes environment, not a local Docker Compose setup. This comment was copied verbatim from `application-local.yml` and is misleading.

    **Fix:** Remove or replace the comment:

    ```yaml
    # SIT environment (K8s): all infrastructure is provisioned by the cluster.
    ```

    File: `src/main/resources/application-sit.yml`

    Verify: Code review only.

- [ ] 11. **Remove duplicate validation message keys from `messages.properties`**

    `messages.properties` duplicates the three keys already defined in `ValidationMessages.properties`:

    ```
    validation.email.invalid=...
    validation.field.required=...
    validation.field.size=...
    ```

    `MessageSourceConfig` registers both files as basenames. Spring resolves keys from `messages` first, so the `ValidationMessages` values are silently shadowed. If the two files diverge, validation messages will resolve inconsistently.

    **Fix:** Remove the three `validation.*` keys from `messages.properties`, keeping them only in `ValidationMessages.properties`.

    File: `src/main/resources/i18n/messages.properties`

    Verify: `mvn test "-Dcheckstyle.skip=true"` passes; validation constraint messages still resolve.

---

## Summary

| #   | File(s)                                                    | Type                                           | Severity                                       |
| --- | ---------------------------------------------------------- | ---------------------------------------------- | ---------------------------------------------- |
| 1   | `pom.xml`                                                  | Mockito agent not configured                   | **MEDIUM** — will break on future JDK          |
| 2   | `logback-spring.xml`                                       | Broken appender reference + duplicate `<root>` | **HIGH** — broken JSON logging in prd/uat      |
| 3   | `CorrelationIdFilter.java` / `TenantExtractionFilter.java` | CGLIB proxy warning                            | **LOW** — investigate; may be Spring internals |
| 4   | `application-test.yml`                                     | JWT config not pinned for tests                | **HIGH** — CI breakage if `JWT_JWKS_URI` set   |
| 5   | `application-test.yml`                                     | RabbitMQ auto-config not excluded in tests     | **MEDIUM** — health indicator DOWN noise       |
| 6   | `TenantExtractionFilter.java` + `SecurityConfig.java`      | Wrong package for a filter                     | **MEDIUM** — structural inconsistency          |
| 7   | `AllTenantsKeyProvider.java`                               | Misplaced in config vs persistence             | **LOW** — structural                           |
| 8   | 2–3 new `package-info.java`                                | Missing package docs                           | **LOW** — consistency                          |
| 9   | `application-prd.yml`, `application-uat.yml`               | PEM instead of JWKS in deployed environments   | **MEDIUM** — operational risk                  |
| 10  | `application-sit.yml`                                      | Stale comment                                  | **LOW** — clarity                              |
| 11  | `messages.properties`                                      | Duplicate validation keys                      | **LOW** — maintenance risk                     |
