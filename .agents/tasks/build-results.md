# Build Results

## Iteration 1

### `mvn clean verify -Dcheckstyle.skip=true`

```
[INFO] BUILD SUCCESS
[INFO] Total time:  17.034 s
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
  - TechnicalStructureTest: PASS (1 test)
  - ServicenameApplicationTests: PASS (1 test)
```

### `mvn checkstyle:check`

```
[INFO] You have 0 Checkstyle violations.
[INFO] BUILD SUCCESS
[INFO] Total time:  3.638 s
```

### Changes applied

| #   | File(s)                                                             | Fix                                                                                                                         |
| --- | ------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| 1   | `logback-spring.xml`                                                | Removed broken top-level `ASYNC_CONSOLE_JSON` appender (referenced undefined `CONSOLE_JSON`) and duplicate `<root>` element |
| 2–3 | `application-test.yml`                                              | Added explicit `iqkv.auth.jwt` config and `RabbitAutoConfiguration` exclusion                                               |
| 4   | `TenantExtractionFilter.java`                                       | Moved from `tenancy` → `infrastructure.security`; updated `SecurityConfig.java` import and `package-info.java`              |
| 5   | `ping/package-info.java`, `shared/package-info.java`                | Created missing package-info files                                                                                          |
| 6   | `AllTenantsKeyProvider.java`                                        | Moved from `infrastructure.config` → `infrastructure.persistence`                                                           |
| 9   | `application-sit.yml`                                               | Replaced misleading docker-compose comment with K8s note                                                                    |
| 10  | `application-prd.yml`, `application-uat.yml`, `application-sit.yml` | Replaced `public-key-path` with `jwks-uri`                                                                                  |
| 11  | `messages.properties`                                               | Removed duplicate `validation.*` keys already in `ValidationMessages.properties`                                            |

Items 7–8 from the plan required no action (import order was already correct).
