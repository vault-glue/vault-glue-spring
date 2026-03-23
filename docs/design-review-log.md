# Design Review Log

설계 검토 중 발견한 오류, 개선 사항, 결정 근거를 기록합니다.

---

## 2026-03-23 — v0.2.0 Critical Bugfix Review

전체 코드 리뷰를 통해 12개 critical/high 버그를 발견하고 수정했습니다.

### Transit Engine

| 항목 | 변경 내용 | 근거 |
|------|----------|------|
| `VaultEncryptConverter.convertToEntityAttribute()` | 평문 fallback 제거 → `IllegalStateException` throw | `@Convert` 적용된 컬럼에 평문이 있으면 보안 사고. silent passthrough는 암호화되지 않은 데이터를 정상으로 간주하게 만듦 |
| `VaultEncryptConverter.initialize()` | `synchronized (INIT_LOCK)` 추가, `getTransitOperations()`에서 single volatile read | JPA가 AutoConfiguration 완료 전에 converter를 호출할 수 있음. `applicationContext`와 `defaultKeyName` 두 필드가 원자적으로 보여야 함 |
| `DefaultVaultTransitOperations.extractBatchResults()` | `item.get(key)` null 체크 추가 → `VaultTransitException` throw | Vault 응답에 expected key가 없으면 NPE 발생. 명확한 에러 메시지로 디버깅 용이하게 변경 |

### Database Engine

| 항목 | 변경 내용 | 근거 |
|------|----------|------|
| `DynamicLeaseListener.handleCreated()` | `initialLatch.countDown()`을 `finally`에서 제거, 성공 시에만 호출 | credential이 null이어도 latch가 풀려서 placeholder credential로 DataSource가 등록되는 버그. 실패 시 30초 timeout으로 빠르게 실패하도록 변경 |
| `VaultGlueDatabaseAutoConfiguration.createDynamicDataSource()` | rotation 성공 후 placeholder `HikariDataSource.close()` 호출 | placeholder pool이 닫히지 않아 커넥션 풀 리소스 누수 발생 |
| `StaticRefreshScheduler.schedule()` | `scheduleAtFixedRate` → `scheduleWithFixedDelay` | refresh가 interval보다 오래 걸리면 동일 DataSource에 대해 concurrent refresh 발생 → 커넥션 풀 corruption 가능. `scheduleWithFixedDelay`는 이전 실행 완료 후 다음 실행 시작 |

### KV Engine

| 항목 | 변경 내용 | 근거 |
|------|----------|------|
| `VaultValueBeanPostProcessor.refreshAll()` | `cache.clear()` 제거, swap-on-success 패턴으로 변경 | clear 후 Vault 장애 시 cache가 비어있게 됨. 새 cache에 먼저 fetch하고, 성공한 항목만 기존 cache에 merge. 실패한 path는 이전 값 유지 |
| `VaultKvWatcher.start()` | `scheduleAtFixedRate` → `scheduleWithFixedDelay` | Vault 응답이 느릴 때 poll task가 큐에 쌓여 리소스 고갈. delay 기반으로 변경 |

### AWS Engine

| 항목 | 변경 내용 | 근거 |
|------|----------|------|
| `VaultAwsCredentialProvider.getCredential()` | null 반환 → `IllegalStateException` throw | `start()` 호출 전에 `getCredential()` 호출 시 NPE. 명확한 에러로 변경 |
| `VaultAwsCredentialProvider.rotate()` | STS 타입일 때 `security_token` 필수 검증 추가 | `sts`, `assumed_role`, `federation_token` 타입은 security_token 없으면 AWS SDK 호출 실패. Vault 응답 시점에 빠르게 실패하도록 변경 |
| `VaultAwsCredentialProvider.parseTtlMs()` | `replace()` → `substring()`, 파싱 실패 시 `log.warn` 추가 | `"1h30m"` 같은 compound format에서 `replace("m","")` → `"1h30"` → `NumberFormatException` silent. substring으로 정확히 마지막 문자만 제거, 실패 시 경고 로그와 함께 기본값 사용 |

### TOTP Engine

| 항목 | 변경 내용 | 근거 |
|------|----------|------|
| `DefaultVaultTotpOperations.validate()` | null response 시 `false` 반환 → `RuntimeException` throw | Vault 장애를 "코드 무효"로 처리하면 모든 OTP 인증이 거부됨. 보안상 Vault 에러와 인증 실패는 구분되어야 함 |

### 설계 판단

- **수정 범위:** targeted fix only — 리팩토링이나 새 기능 없이 실제 장애 유발 가능한 버그만 수정
- **FailureStrategy 통합은 미포함:** KV/PKI/AWS/TOTP에 FailureStrategy가 일관되게 적용되지 않는 문제는 인지했으나, 0.2.0 범위에서는 제외. 별도 작업으로 진행 예정
- **@VaultEncrypt 어노테이션 미사용 문제는 미포함:** 어노테이션의 key/context 필드가 실제로 읽히지 않는 문제는 API 변경이 필요하므로 0.3.0에서 처리 예정
