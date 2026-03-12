# VaultGlue

HashiCorp Vault 전체 엔진을 Spring Boot AutoConfiguration으로 제공하는 라이브러리.
`spring-cloud-vault`가 해결하지 못하는 DB/Transit/PKI/TOTP/AWS 엔진을 yml 설정만으로 자동 구성.

## 프로젝트 구조

```
vault-glue-spring/
├── vault-glue-autoconfigure/          # 핵심 자동설정 모듈
│   └── src/main/java/io/vaultglue/
│       ├── autoconfigure/           # 8개 AutoConfiguration 클래스
│       ├── core/                    # Properties, Event System, FailureStrategy, Health
│       ├── database/                # DB Engine (Static/Dynamic Role, 멀티 DataSource)
│       ├── kv/                      # KV Engine (CRUD, @VaultValue, Watch)
│       ├── transit/                 # Transit Engine (암복호화, @VaultEncrypt, 키관리)
│       ├── pki/                     # PKI Engine (인증서 발급/갱신)
│       ├── totp/                    # TOTP Engine (OTP 생성/검증)
│       └── aws/                     # AWS Engine (credential rotation)
├── vault-glue-spring-boot-starter/    # Starter 의존성 묶음
├── vault-glue-test/                   # TestContainers + Vault 테스트 지원
└── docs/
    ├── 01-plan/features/vault-glue.plan.md
    └── 02-design/features/vault-glue.design.md
```

## 기술 스택

- Java 21+
- Spring Boot 3.5.11
- Spring Cloud 2025.0.1
- Gradle 8.14.3 (Kotlin DSL, 네이티브 platform() BOM)
- HikariCP (DB connection pool)

## 설정 예시 (Target UX)

```yaml
vault-glue:
  kv:
    enabled: true
    backend: app
    version: 2
  database:
    sources:
      primary:
        type: static          # static | dynamic
        role: my-service-static-dev
        backend: db
        jdbc-url: jdbc:mysql://db:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
        refresh-interval: 18000000
  transit:
    enabled: true
    backend: transit
    keys:
      user-pii:
        type: aes256-gcm96
        auto-create: true
  on-failure: retry           # restart | retry | ignore
```

## 현재 진행 상황

### 완료
- [x] Gradle 멀티모듈 프로젝트 세팅
- [x] Core: Properties, Event System (5 events), FailureStrategy (restart/retry/ignore), HealthIndicator
- [x] DB Engine: Static Role (credential provider + scheduler + graceful shutdown)
- [x] DB Engine: Dynamic Role (lease listener + auto rotation)
- [x] DB Engine: 멀티 DataSource (VaultGlueDataSources 컨테이너)
- [x] KV Engine: VaultKvOperations (CRUD + 버전 + list)
- [x] KV Engine: @VaultValue 어노테이션 + BeanPostProcessor
- [x] KV Engine: Watch 모드 (변경 감지 polling)
- [x] Transit Engine: VaultTransitOperations (encrypt/decrypt/rewrap/hmac/sign/verify)
- [x] Transit Engine: @VaultEncrypt JPA AttributeConverter
- [x] Transit Engine: 키 자동 생성 (TransitKeyInitializer)
- [x] PKI Engine: VaultPkiOperations + CertificateRenewalScheduler
- [x] TOTP Engine: VaultTotpOperations
- [x] AWS Engine: VaultAwsCredentialProvider + auto rotation
- [x] Actuator: VaultGlueHealthIndicator
- [x] AutoConfiguration.imports 등록 (8개 전체)
- [x] 빌드 성공 확인

### 미완료
- [ ] GitHub org 생성 + push (vault-glue org or vault-glue-dev)
- [ ] JitPack 또는 Maven Central 배포 설정
- [ ] 테스트 코드 작성 (TestContainers + Vault)
- [ ] README.md 작성
- [ ] GitHub Actions CI/CD
- [ ] 그룹 ID 확정 (io.github.vault-glue or io.vaultglue)

## 컨벤션

- Package: `io.vaultglue.*`
- AutoConfiguration: `*AutoConfiguration`
- Properties: `*Properties`, prefix `vault-glue.*`
- 로깅: `[VaultGlue]` prefix, credential 평문 노출 금지
- Conditional: `@ConditionalOnProperty` + `@ConditionalOnBean(VaultTemplate.class)` + `@ConditionalOnMissingBean`

## 빌드

```bash
./gradlew compileJava    # 컴파일
./gradlew build          # 전체 빌드
./gradlew test           # 테스트
```
