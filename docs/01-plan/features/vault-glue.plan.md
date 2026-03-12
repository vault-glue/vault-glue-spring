# VaultGlue Planning Document

> **Summary**: HashiCorp Vault 전체 엔진을 Spring Boot AutoConfiguration으로 제공하는 라이브러리
>
> **Project**: vault-glue
> **Version**: 0.1.0
> **Author**: ctrdw
> **Date**: 2026-03-10
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | Spring Cloud Vault는 KV 바인딩만 지원하고, DB/Transit/PKI 등은 매 서비스마다 5~6개 보일러플레이트 클래스를 복붙해야 함. Dynamic/Static role credential rotation, graceful shutdown, 에러 핸들링 로직이 서비스마다 중복되어 유지보수 비용 증가 |
| **Solution** | yml 설정만으로 Vault 전 엔진(KV, DB, Transit, PKI, TOTP, AWS)을 자동 구성하는 Spring Boot Starter 라이브러리. DB Dynamic/Static role 자동 rotation, Transit 필드 레벨 암호화, PKI 인증서 자동 갱신 등을 내장 |
| **Function/UX Effect** | 개발자는 `vault-engines` yml 프로퍼티만 작성하면 자동 구성 완료. 보일러플레이트 0개, 새 서비스 Vault 연동 시간 수 시간 → 수 분으로 단축 |
| **Core Value** | Vault 엔진 통합 관리의 단일 진입점. 설정 기반 선언적 접근으로 인프라 복잡도를 애플리케이션에서 완전 분리 |

---

## 1. Overview

### 1.1 Purpose

HashiCorp Vault의 주요 Secret Engine들을 Spring Boot AutoConfiguration으로 제공하여, 개발자가 yml 설정만으로 Vault 전체 기능을 사용할 수 있게 한다.

현재 spring-cloud-vault가 해결하지 못하는 영역:
- **DB Engine**: Dynamic/Static role credential rotation + DataSource 자동 재생성
- **Transit Engine**: 필드 레벨 암복호화, 서명, HMAC
- **PKI Engine**: 인증서 자동 발급/갱신 + SSL 설정
- **TOTP Engine**: OTP 생성/검증
- **AWS Engine**: Cloud credential 자동 rotation

### 1.2 Background

마이크로서비스 환경에서 서비스마다 동일한 Vault 연동 코드를 복사:
- DB Static Role: `VaultStaticCredentialProvider`, `RefreshableDataSource`, `DelegatingDataSource`, `DataSourceRefreshScheduler`, `DataSourceConfig` (5개 클래스)
- DB Dynamic Role: `VaultConfig`, `VaultLeaseConfig`, `DataSourceConfig` (3개 클래스)
- 한 서비스에서 버그 수정 시 전체 서비스에 패치 불가능
- 신규 개발자 온보딩 시 Vault 연동 구조 이해에 시간 소요

### 1.3 Related Documents

- HashiCorp Vault Documentation: https://developer.hashicorp.com/vault/docs
- Spring Cloud Vault: https://spring.io/projects/spring-cloud-vault
- Spring Boot AutoConfiguration: https://docs.spring.io/spring-boot/reference/using/auto-configuration.html

---

## 2. Scope

### 2.1 In Scope

- [ ] KV Engine v1/v2 - 런타임 읽기/쓰기/버전 관리 + `@VaultValue` + watch 모드
- [ ] DB Engine Dynamic Role - Lease 기반 credential rotation + DataSource 자동 재생성
- [ ] DB Engine Static Role - 스케줄러 기반 credential rotation + DelegatingDataSource 패턴
- [ ] DB Engine 멀티 DataSource - 서비스당 2개 이상 DB 연결 지원
- [ ] Transit Engine - 암복호화/서명/HMAC + `@VaultEncrypt` JPA 필드 암호화
- [ ] PKI Engine - 인증서 자동 발급/갱신 + Spring Boot SSL 자동 설정
- [ ] TOTP Engine - OTP 키 생성/검증 편의 서비스
- [ ] AWS Secrets Engine - Cloud credential 자동 rotation
- [ ] Health Check / Actuator 엔드포인트
- [ ] Credential rotation 실패 전략 설정 (restart/retry/ignore)
- [ ] 이벤트 리스너 인터페이스 (rotation 성공/실패 콜백)

### 2.2 Out of Scope

- Vault 서버 설치/설정/관리
- Vault 인증 자체 (spring-cloud-vault의 영역: AppRole, Token, Kubernetes 등)
- ORM/JPA 설정 (Hibernate dialect, ddl-auto 등)
- Vault Agent/Proxy 연동
- Vault Enterprise 전용 기능 (Namespaces, Sentinel 등)

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| **KV Engine** ||||
| FR-KV-01 | `VaultKvOperations` Bean 자동 구성 (읽기/쓰기/삭제/버전) | P0 | Pending |
| FR-KV-02 | `@VaultValue(path, key)` 어노테이션으로 필드 주입 | P1 | Pending |
| FR-KV-03 | KV v1/v2 자동 감지 또는 설정 | P0 | Pending |
| FR-KV-04 | Watch 모드 - 변경 감지 polling 및 자동 갱신 | P2 | Pending |
| **DB Engine** ||||
| FR-DB-01 | Dynamic Role: Lease 리스너 자동 등록 + credential rotation | P0 | Pending |
| FR-DB-02 | Static Role: 스케줄러 기반 credential rotation | P0 | Pending |
| FR-DB-03 | DelegatingDataSource 패턴으로 무중단 DataSource 교체 | P0 | Pending |
| FR-DB-04 | Graceful shutdown - active connection 대기 후 old pool 종료 | P0 | Pending |
| FR-DB-05 | 멀티 DataSource 지원 (primary/secondary 등) | P0 | Pending |
| FR-DB-06 | HikariCP 설정 커스터마이징 (pool size, timeout 등) | P1 | Pending |
| FR-DB-07 | MySQL, PostgreSQL, Oracle, MariaDB 등 주요 DB 드라이버 지원 | P1 | Pending |
| **Transit Engine** ||||
| FR-TR-01 | `VaultTransitOperations` Bean 자동 구성 (encrypt/decrypt/rewrap) | P0 | Pending |
| FR-TR-02 | HMAC, Sign, Verify 지원 | P1 | Pending |
| FR-TR-03 | `@VaultEncrypt(key)` JPA AttributeConverter 자동 등록 | P1 | Pending |
| FR-TR-04 | Convergent encryption 지원 (context 파라미터) | P2 | Pending |
| FR-TR-05 | Transit key 자동 생성 옵션 (`auto-create: true`) | P2 | Pending |
| **PKI Engine** ||||
| FR-PKI-01 | 인증서 자동 발급 (role, common-name 기반) | P1 | Pending |
| FR-PKI-02 | TTL 기반 자동 갱신 | P1 | Pending |
| FR-PKI-03 | Spring Boot `server.ssl.*` 자동 설정 연동 | P2 | Pending |
| FR-PKI-04 | `VaultPkiOperations` Bean 자동 구성 | P1 | Pending |
| **TOTP Engine** ||||
| FR-TOTP-01 | `VaultTotpOperations` Bean 자동 구성 (create/validate) | P2 | Pending |
| FR-TOTP-02 | QR코드 URL 생성 지원 | P2 | Pending |
| **AWS Engine** ||||
| FR-AWS-01 | AWS 임시 credential 자동 발급/갱신 | P2 | Pending |
| FR-AWS-02 | Spring Cloud AWS `AwsCredentialsProvider` 자동 연동 | P2 | Pending |
| **공통** ||||
| FR-CM-01 | `/actuator/vault-glue` Health Check 엔드포인트 | P0 | Pending |
| FR-CM-02 | 실패 전략 설정 (`on-failure: restart/retry/ignore`) | P0 | Pending |
| FR-CM-03 | `VaultGlueEventListener` 인터페이스 (rotation 콜백) | P1 | Pending |
| FR-CM-04 | 엔진별 `enabled: true/false` 개별 활성화 | P0 | Pending |
| FR-CM-05 | Spring Boot 3.x + Java 17+ 지원 | P0 | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| Performance | DataSource rotation 시 요청 유실 0건 (무중단) | 부하 테스트 + connection 에러 카운트 |
| Reliability | Credential rotation 실패 시 자동 복구 (retry) | 통합 테스트 + Vault 장애 시뮬레이션 |
| Compatibility | Spring Boot 3.0~3.4 호환 | CI 매트릭스 빌드 |
| Compatibility | Java 17, 21 지원 | CI 매트릭스 빌드 |
| Observability | 모든 rotation 이벤트 구조화 로깅 | 로그 출력 검증 |
| Security | Credential이 로그에 평문 노출 안 됨 | 코드 리뷰 + 로그 감사 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 전체 엔진 AutoConfiguration 동작 확인
- [ ] DB Dynamic/Static Role 무중단 rotation 검증
- [ ] TestContainers + Vault 기반 통합 테스트 통과
- [ ] Spring Boot 3.x 호환성 확인
- [ ] Maven Central 배포 가능 상태
- [ ] README + 사용 가이드 작성

### 4.2 Quality Criteria

- [ ] 테스트 커버리지 80% 이상
- [ ] Checkstyle/SpotBugs 경고 0
- [ ] Gradle/Maven 빌드 성공
- [ ] Javadoc 주요 public API 문서화

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| spring-cloud-vault와 Bean 충돌 | High | Medium | `@ConditionalOnMissingBean` 활용, 기존 Bean 있으면 등록 안 함 |
| DataSource rotation 중 트랜잭션 유실 | High | Low | DelegatingDataSource 패턴으로 proxy 교체, active connection 대기 후 old pool 종료 |
| Vault 서버 장애 시 credential 발급 불가 | High | Low | retry 전략 + 마지막 유효 credential 캐싱 |
| 멀티 DataSource 시 Spring Bean 이름 충돌 | Medium | Medium | `@Qualifier` 자동 생성 + 네이밍 컨벤션 문서화 |
| Transit 암호화 키 rotation 시 기존 데이터 복호화 실패 | High | Low | rewrap API 지원 + 마이그레이션 가이드 제공 |
| Spring Boot 버전 호환성 깨짐 | Medium | Medium | CI 매트릭스 (3.0, 3.1, 3.2, 3.3, 3.4) + 버전별 조건부 설정 |

---

## 6. Architecture Considerations

### 6.1 Project Type

Spring Boot Starter 라이브러리 (애플리케이션이 아닌 라이브러리 프로젝트)

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| Build Tool | Maven / Gradle | Gradle (Kotlin DSL) | 멀티모듈 관리 용이, 빌드 스크립트 간결 |
| Java Version | 17 / 21 | 17 (minimum), 21 지원 | Spring Boot 3.x 최소 요구사항 |
| 모듈 구조 | 단일 모듈 / 멀티 모듈 | 멀티 모듈 | 엔진별 선택적 의존성, starter 분리 |
| AutoConfiguration | `spring.factories` / `AutoConfiguration.imports` | `AutoConfiguration.imports` | Spring Boot 3.x 표준 |
| 테스트 | JUnit5 + Mockito / TestContainers | 둘 다 | 단위 테스트 + Vault 통합 테스트 |
| 배포 | Maven Central / JitPack | Maven Central | 공식 라이브러리 배포 표준 |

### 6.3 모듈 구조

```
vault-glue/
├── vault-glue-autoconfigure/          # 핵심 자동설정 모듈
│   ├── core/                        # 공통 (Properties, Event, Health)
│   ├── kv/                          # KV Engine AutoConfiguration
│   ├── database/                    # DB Engine AutoConfiguration
│   │   ├── dynamic/                 #   Dynamic Role 지원
│   │   └── static_/                 #   Static Role 지원
│   ├── transit/                     # Transit Engine AutoConfiguration
│   ├── pki/                         # PKI Engine AutoConfiguration
│   ├── totp/                        # TOTP Engine AutoConfiguration
│   └── aws/                         # AWS Engine AutoConfiguration
├── vault-glue-spring-boot-starter/    # Starter 의존성 묶음
├── vault-glue-test/                   # 테스트 지원 (TestContainers + Vault)
└── vault-glue-samples/                # 사용 예제 프로젝트
    ├── sample-db-dynamic/
    ├── sample-db-static/
    ├── sample-transit/
    └── sample-multi-engine/
```

### 6.4 설정 구조 (Target UX)

```yaml
vault-glue:
  kv:
    enabled: true
    backend: app
    version: 2
    application-name: my-service
    watch:
      enabled: false
      interval: 30s

  database:
    sources:
      primary:
        enabled: true
        type: static                    # static | dynamic
        role: my-service-static-dev
        backend: db
        jdbc-url: jdbc:mysql://db:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
        refresh-interval: 18000000      # static일 때만 (ms)
        hikari:
          maximum-pool-size: 5
          minimum-idle: 1
          max-lifetime: 18000000
      secondary:                        # 멀티 DataSource
        enabled: true
        type: dynamic
        role: order-dynamic-dev
        backend: db
        jdbc-url: jdbc:mysql://order-db:3306/order
        driver-class-name: com.mysql.cj.jdbc.Driver

  transit:
    enabled: true
    backend: transit
    keys:
      user-pii:
        type: aes256-gcm96
        auto-create: true
      payment-data:
        type: rsa-4096

  pki:
    enabled: false
    backend: pki
    role: internal-service
    common-name: ${spring.application.name}.internal
    ttl: 72h
    auto-renew: true
    configure-ssl: true

  totp:
    enabled: false
    backend: totp

  aws:
    enabled: false
    backend: aws
    role: s3-upload-role
    credential-type: sts
    ttl: 1h

  # 공통 설정
  on-failure: retry                     # restart | retry | ignore
  retry:
    max-attempts: 3
    delay: 5000
  actuator:
    enabled: true
```

---

## 7. Convention Prerequisites

### 7.1 코드 컨벤션

| Category | Rule |
|----------|------|
| Package | `io.vaultglue.*` (또는 `com.github.{username}.vault-glue.*`) |
| Naming | AutoConfiguration 클래스: `*AutoConfiguration`, Properties: `*Properties` |
| Import order | java → javax → spring → vault → internal |
| 로깅 | SLF4J, 구조화 로깅 (credential 평문 노출 금지) |
| 테스트 | `*Test` (단위), `*IntegrationTest` (통합) |

### 7.2 Git 컨벤션

| Category | Rule |
|----------|------|
| Branch | `main`, `develop`, `feature/*`, `fix/*`, `release/*` |
| Commit | Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`) |
| PR | 리뷰 필수, CI 통과 필수 |

### 7.3 의존성

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.0+ | 기반 프레임워크 |
| Spring Cloud Vault | 4.0+ | Vault 연결 기반 |
| HikariCP | (Spring Boot 내장) | Connection Pool |
| TestContainers | 1.19+ | 통합 테스트 |
| Vault (Docker) | 1.15+ | 테스트용 Vault 서버 |

---

## 8. Next Steps

1. [ ] Design document 작성 (`vault-glue.design.md`)
2. [ ] Gradle 멀티모듈 프로젝트 초기화
3. [ ] P0 기능부터 구현 시작 (DB Engine → 공통 → KV → Transit)
4. [ ] GitHub Repository 설정 (CI/CD, Issues, Wiki)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-03-10 | Initial draft | ctrdw |
