---
name: ignoa-orchestrator
description: "ignoa-api 에이전트 팀 오케스트레이터. '구현해줘', '개발해줘', '작업 시작', '리뷰해줘', '디버깅', '테스트 작성', '보안 검토', '경매 로직 검증' 등 개발 작업 요청 시 에이전트 팀에 위임. 후속 작업: 재실행, 다시 작업, 수정, 보완, 결과 개선, 부분만 다시 시에도 이 스킬 사용."
allowed-tools: Read, Glob, Grep, Bash, Write, Edit, Agent
---

# Ignoa API Orchestrator

ignoa-api (경매 기반 중고거래 마켓플레이스) 에이전트 팀을 조율하여 개발 작업을 처리하는 오케스트레이터.

## 실행 모드: 서브 에이전트

## 에이전트 구성

| 에이전트 | subagent_type | 역할 |
|---------|--------------|------|
| architect | oh-my-harness:architect | 설계·아키텍처 분석, 루트 원인 진단 |
| test-engineer | oh-my-harness:test-engineer | TDD 테스트 작성 |
| executor | oh-my-harness:executor | 코드 구현 |
| code-reviewer | oh-my-harness:code-reviewer | 코드 리뷰 |
| security-reviewer | oh-my-harness:security-reviewer | 보안 리뷰 |
| debugger | oh-my-harness:debugger | 디버깅·빌드 에러 해결 |
| auction-domain-expert | oh-my-harness:auction-domain-expert | 경매 도메인 로직 검증 |

## 워크플로우

### Phase 0: 작업 유형 분류

사용자 요청을 분석하여 어떤 에이전트를 활성화할지 결정한다.

| 요청 유형 | 활성화 에이전트 | 실행 순서 |
|----------|--------------|---------|
| 새 기능 구현 | architect → test-engineer → executor → code-reviewer | 순차 |
| 버그/에러 해결 | debugger (→ executor 필요 시) | 순차 |
| 코드 리뷰만 | code-reviewer (+ security-reviewer 병렬) | 병렬 |
| 보안 검토 | security-reviewer | 단독 |
| 경매 도메인 로직 | auction-domain-expert → architect (필요 시) | 순차 |
| 테스트 작성 | test-engineer | 단독 |
| 아키텍처 분석 | architect | 단독 |

### Phase 1: 컨텍스트 수집

작업 시작 전 필요한 컨텍스트를 수집한다.

1. 관련 파일 파악: Glob/Grep으로 관련 Java 파일 위치 확인
2. 최근 변경사항: `git log --oneline -5` 확인
3. 작업 범위 명확화: 어떤 도메인(auction/bid/item/auth/user/wish)인지 파악

### Phase 2: 에이전트 실행

**새 기능 구현 (순차 실행):**

```
1. architect → 설계 분석 및 구현 방향 제시
   - prompt: "ignoa-api에서 [기능]을 구현하기 위한 설계를 분석해줘. 
              관련 도메인: [도메인], 기존 패턴 분석 포함."
   
2. test-engineer → 실패 테스트 먼저 작성
   - prompt: "architect 분석 기반으로 [기능]의 JUnit 5 테스트를 먼저 작성해줘.
              ./gradlew test로 RED 상태 확인 필수."
   
3. executor → 구현
   - prompt: "테스트를 통과하도록 [기능]을 구현해줘.
              CLAUDE.md 원칙 준수: Guard Clause, DTO 반환, 최소 diff.
              ./gradlew build로 검증 필수."
   
4. code-reviewer → 리뷰
   - prompt: "구현된 [기능] 코드를 리뷰해줘. git diff로 변경사항 확인.
              CLAUDE.md 원칙 준수 여부 포함."
```

**버그 해결 (순차):**

```
1. debugger → 루트 원인 분석 및 수정
   - prompt: "[에러 내용]을 디버깅해줘. 스택 트레이스: [내용].
              ./gradlew build로 수정 검증 필수."
```

**코드 리뷰 (병렬):**

```
code-reviewer + security-reviewer 동시 실행
- code-reviewer: "git diff HEAD~1 기준으로 코드 리뷰해줘."
- security-reviewer: "변경된 코드의 보안 취약점을 검토해줘."
```

**경매 도메인 검증:**

```
1. auction-domain-expert → 도메인 로직 검증
   - prompt: "[경매/입찰 관련 기능]의 비즈니스 규칙과 이벤트 흐름을 검증해줘."
```

### Phase 3: 결과 통합 및 보고

1. 각 에이전트의 결과를 수집
2. 중요 이슈 우선순위 정리
3. 사용자에게 다음 항목 보고:
   - 완료된 작업 요약
   - 발견된 이슈 (severity별)
   - 권고 사항
   - 다음 단계 제안

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| 에이전트 실패 | 1회 재시도. 실패 시 결과 없이 진행하고 사용자에게 알림 |
| 빌드 실패 | debugger에게 에스컬레이션 |
| 보안 CRITICAL 발견 | 구현 중단, 사용자에게 즉시 알림 |
| 도메인 규칙 위반 | auction-domain-expert 결과를 architect에게 전달하여 재설계 |

## 프로젝트 컨텍스트

- **기술 스택**: Java 21 + Spring Boot 3.5.7 + MySQL + Redis + AWS S3 + JWT + WebSocket
- **빌드**: `./gradlew build`, `./gradlew test`
- **패키지**: `io.wisoft.ignoa_api.{domain}`
- **코딩 원칙**: CLAUDE.md 기준 (Guard Clause, DTO 반환, 최소 diff)
- **핵심 도메인**: auction(경매), bid(입찰), item(상품), auth(인증), user(사용자), wish(찜), storage(S3)
