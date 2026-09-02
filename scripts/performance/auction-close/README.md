# 경매 자동 마감 성능 측정

동시에 만료된 경매 1,000건을 자동 마감하는 데 걸리는 시간과 처리 결과를 확인하기 위한 SQL입니다.

HTTP 요청 부하 테스트가 아니라 Scheduler Job의 처리량을 측정합니다. 동일한 데이터와 환경에서 동기 처리와 Worker Pool 적용 후 결과를 비교합니다.

## 측정 시나리오

각 상품에는 서로 다른 입찰자의 활성 입찰을 10건씩 생성합니다. 따라서 자동 마감 시 상품 상태 변경뿐 아니라 최고가 입찰 1건의 낙찰, 나머지 9건의 패찰, 채팅방 생성까지 실행됩니다.

```text
판매자 1명
입찰자 10명
ACTIVE 상품 1,000건
상품별 ACTIVE 입찰 10건
```

## 실행 전 확인

- 운영 데이터가 없는 성능 측정용 DB에서 실행합니다.
- 네 SQL 파일의 `@run_id`를 모두 같은 값으로 맞춥니다.
- `expire.sql` 실행 전 애플리케이션과 모니터링 환경을 실행합니다.
- 데이터 생성 시간은 자동 마감 처리 시간에 포함하지 않습니다.
- 같은 조건으로 다시 측정할 때는 먼저 `cleanup.sql`을 실행합니다.

각 SQL은 세션의 문자열 비교 기준을 기존 테이블과 맞추기 위해 `utf8mb4_unicode_ci` Collation을 먼저 설정합니다.

## 실행 순서

MySQL에 접속한 상태에서 다음 순서로 실행합니다.

```text
1. seed.sql     테스트 사용자·상품·입찰 생성
2. expire.sql   상품 1,000건을 한 번에 만료 상태로 변경
3. verify.sql   상품·입찰·채팅방 처리 결과 확인
4. cleanup.sql  이번 측정에서 생성한 데이터 삭제
```

MySQL CLI에서는 다음과 같이 실행할 수 있습니다.

```bash
mysql -h <HOST> -P <PORT> -u <USER> -p <DATABASE> \
  < scripts/performance/auction-close/seed.sql
```

나머지 SQL도 파일명만 바꿔 같은 방식으로 실행합니다.

## 정상 처리 결과

```text
items.status = BID_CLOSED  1,000건
bids.status  = WON         1,000건
bids.status  = LOST        9,000건
chat_rooms                 1,000건
ACTIVE로 남은 테스트 상품     0건
```

## 측정 항목

| 항목 | 동기 처리 | Worker Pool 적용 |
| --- | ---: | ---: |
| 전체 처리 시간 | 측정 예정 | 측정 예정 |
| 초당 처리량 | 측정 예정 | 측정 예정 |
| 성공 건수 | 측정 예정 | 측정 예정 |
| 실패·재처리 건수 | 측정 예정 | 측정 예정 |
| HikariCP Active 최대 | 측정 예정 | 측정 예정 |
| HikariCP Pending 최대 | 측정 예정 | 측정 예정 |
| CPU 사용률 최대 | 측정 예정 | 측정 예정 |

Grafana에서는 전체 처리 시간과 함께 HikariCP Active/Pending, CPU 사용률을 확인합니다. SQL 검증 결과로는 마감 누락과 중복 후속 처리 여부를 확인합니다.
