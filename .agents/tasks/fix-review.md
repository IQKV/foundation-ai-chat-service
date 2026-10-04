# Coder fixes: structure, config, and package correctness

The coder applied all 9 actionable plan items (items 7–8 were confirmed no-ops). Both the build (`mvn clean verify -Dcheckstyle.skip=true`) and style check (`mvn checkstyle:check`) passed green with 0 violations and all 2 tests passing. No new issues were introduced.

**Watch for:** The `TenantExtractionFilter` import update in `SecurityConfig.java` is confirmed correct — the grep search surfaced duplicate-looking context lines, but the actual file has a single clean field declaration and constructor parameter. No duplication exists.

**Verdict**: APPROVED

## High-level view

Every plan item resolves a real problem with a targeted fix. The logback restructure removes the broken top-level `ASYNC_CONSOLE_JSON` that referenced an appender only available inside `<springProfile name="uat,prd">`, and eliminates the duplicate root element; the resulting XML is structurally valid for all profiles. The test profile hardening pins JWT config and excludes `RabbitAutoConfiguration`, removing environment-variable sensitivity and health-indicator noise from CI runs.

The package moves for `TenantExtractionFilter` (into `infrastructure.security`) and `AllTenantsKeyProvider` (into `infrastructure.persistence`) align both classes with their actual roles and with the DDD package conventions in AGENTS.md. The old `tenancy` package is fully deleted. The three deployed-environment YAMLs (`sit`, `uat`, `prd`) now consistently use `jwks-uri` rather than `public-key-path`, enabling transparent key rotation via JWKS. The duplicate `validation.*` keys in `messages.properties` are removed, eliminating the divergence risk.

<details>
<summary>Issues (0)</summary>

No blocking issues found.

</details>

<details>
<summary>Details</summary>

### Logback structure

The broken `ASYNC_CONSOLE_JSON` appender and duplicate `<root>` element are gone. The file now has a single top-level `CONSOLE` appender with a `<root>` that references it, and a `<springProfile name="uat,prd">` block that defines both JSON appenders and overrides `<root>`. The structure is correct for all profiles.

### Test profile hardening

`application-test.yml` now explicitly sets `iqkv.auth.jwt.jwks-uri: ""` and `iqkv.auth.jwt.public-key-path: "classpath:keys/public.pem"`, and excludes `RabbitAutoConfiguration`. The JWT config is no longer sensitive to the `JWT_JWKS_URI` environment variable in CI; the RabbitMQ exclusion removes the `RabbitHealthIndicator` DOWN risk.

### Package moves

`TenantExtractionFilter` is in `com.iqkv.foundation.servicename.infrastructure.security` with a correct package declaration and the `package-info.java` Javadoc updated to list it alongside `CorrelationIdFilter`. `SecurityConfig.java` imports it from the new path. The `tenancy` package is fully removed (no files match that path).

`AllTenantsKeyProvider` is in `com.iqkv.foundation.servicename.infrastructure.persistence` with a correct package declaration. No other file held a direct import of this class (Spring autowires by interface), so no secondary updates were needed.

### New package-info files

`ping/package-info.java` and `shared/package-info.java` are present, follow the Apache licence header pattern, and contain meaningful one-sentence Javadoc. This is consistent with every other package in the project.

### Deployed-environment YAML

`application-sit.yml`, `application-uat.yml`, and `application-prd.yml` all use `jwks-uri: ${JWT_JWKS_URI}` with no `public-key-path` key. The `sit` header comment now references Kubernetes rather than `docker compose`. `AuthConfigurationProperties.validate()` will see `hasJwksUri=true`, `hasPublicKeyPath=false` — the valid deployed-environment state.

### messages.properties deduplication

The three `validation.*` keys are removed from `messages.properties`. The keys remain in `ValidationMessages.properties`, which `MessageSourceConfig` already registers as a basename alongside `messages`. No behaviour change; maintenance risk eliminated.

</details>

## File map

<details>
<summary>Changed files</summary>

| File                                                                      | Change                                                                               |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `src/main/resources/logback-spring.xml`                                   | Removed stray top-level `ASYNC_CONSOLE_JSON` appender and duplicate `<root>` element |
| `src/test/resources/application-test.yml`                                 | Added explicit JWT config; added `RabbitAutoConfiguration` exclusion                 |
| `src/main/java/.../infrastructure/security/TenantExtractionFilter.java`   | Moved from `tenancy`; package declaration updated                                    |
| `src/main/java/.../infrastructure/config/SecurityConfig.java`             | Import updated to new `TenantExtractionFilter` package                               |
| `src/main/java/.../infrastructure/security/package-info.java`             | Javadoc updated to list `TenantExtractionFilter`                                     |
| `src/main/java/.../infrastructure/persistence/AllTenantsKeyProvider.java` | Moved from `infrastructure.config`; package declaration updated                      |
| `src/main/java/.../ping/package-info.java`                                | Created                                                                              |
| `src/main/java/.../shared/package-info.java`                              | Created                                                                              |
| `src/main/resources/application-sit.yml`                                  | Comment corrected; `public-key-path` replaced with `jwks-uri`                        |
| `src/main/resources/application-uat.yml`                                  | `public-key-path` replaced with `jwks-uri`                                           |
| `src/main/resources/application-prd.yml`                                  | `public-key-path` replaced with `jwks-uri`                                           |
| `src/main/resources/i18n/messages.properties`                             | Removed duplicate `validation.*` keys                                                |

</details>
