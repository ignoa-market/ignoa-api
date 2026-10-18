# API

## 인증

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| No Login | 회원가입 (이메일 인증 완료 후 가능) | POST | `/api/auth/signup` | Yes | Yes |
| No Login | 이메일 로그인 | POST | `/api/auth/login` | Yes | Yes |
| No Login | 인증 코드 발송 (5분 유효) | POST | `/api/auth/email/send` | Yes | Yes |
| No Login | 인증 코드 검증 (검증 후 10분 유효) | POST | `/api/auth/email/verify` | Yes | Yes |
| No Login | 카카오 소셜 로그인 | POST | `/api/auth/oauth/kakao` | Yes | Yes |
| User | 액세스 토큰 재발급 | POST | `/api/auth/refresh` | Yes | Yes |
| User | 로그아웃 | POST | `/api/auth/logout` | Yes | Yes |

## 회원

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| No Login | 이메일 중복 검사 | GET | `/api/users/email/duplicate?email=` | Yes | Yes |
| No Login | 닉네임 중복 검사 | GET | `/api/users/nickname/duplicate?nickname=` | Yes | Yes |
| User | 내 정보 조회 | GET | `/api/users/me` | Yes | Yes |
| User | 내 정보 수정 (닉네임, 주소, 자기소개) | PATCH | `/api/users/me` | Yes | Yes |
| User | 프로필 이미지 수정 | PATCH | `/api/users/me/profile-image` | Yes | Yes |
| User | 프로필 이미지 삭제 | DELETE | `/api/users/me/profile-image` | Yes | Yes |
| User | 내가 등록한 상품 목록 | GET | `/api/users/me/items` | Yes | Yes |
| User | 내가 입찰한 상품 목록 | GET | `/api/users/me/bids` | Yes | Yes |
| User | 내 구매 목록 (낙찰·즉시구매, 취소 제외) | GET | `/api/users/me/purchases` | Yes | No |

## 회원 탈퇴

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| User | 회원 탈퇴 (유예 기간: 30일) | DELETE | `/api/users/me` | Yes | Yes |
| No Login | 회원 탈퇴 복구 | POST | `/api/auth/recover` | Yes | Yes |
| Server | 탈퇴 후 30일 경과 회원 개인정보 파기 | Scheduler | 매일 01:00 자동 실행 | Yes | Yes |

## 상품 / 경매

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| No Login | 상품 목록 조회 | GET | `/api/items` | Yes | Yes |
| No Login | 상품 상세 조회 | GET | `/api/items/{itemId}` | Yes | Yes |
| User | 상품 등록 | POST | `/api/items` | Yes | Yes |
| User | 상품 수정 | PATCH | `/api/items/{itemId}` | Yes | Yes |
| User | 상품 삭제 | DELETE | `/api/items/{itemId}` | Yes | Yes |
| User | 즉시 구매 | POST | `/api/items/{itemId}/buy-now` | Yes | Yes |
| User | 경매 마감 연장 | POST | `/api/items/{itemId}/extend` | Yes | Yes |

## 입찰

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| User | 입찰하기 | POST | `/api/items/{itemId}/bids` | Yes | Yes |
| No Login | 입찰 내역 조회 | GET | `/api/items/{itemId}/bids` | Yes | Yes |

## 찜

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| User | 찜 목록 조회 | GET | `/api/wishes` | Yes | Yes |
| User | 찜 등록 | POST | `/api/wishes/{itemId}` | Yes | Yes |
| User | 찜 취소 | DELETE | `/api/wishes/{itemId}` | Yes | Yes |

## 채팅

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| Server | 채팅방 자동 생성 | - | 낙찰 / 즉시구매 시 (기존 채팅방 있으면 재사용) | Yes | Yes |
| User | 채팅방 생성 (구매자가 직접 문의) | POST | `/api/items/{itemId}/chat-rooms` | Yes | Yes |
| User | 채팅방 목록 조회 | GET | `/api/chat-rooms` | Yes | Yes |
| User | 채팅방 조회 | GET | `/api/chat-rooms/{chatRoomId}` | Yes | Yes |
| User | 채팅 메시지 전송 | POST | `/api/chat-rooms/{chatRoomId}/messages` | Yes | Yes |
| User | 채팅 메시지 조회 | GET | `/api/chat-rooms/{chatRoomId}/messages` | Yes | Yes |

## WebSocket

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| User | 실시간 연결 (CONNECT 헤더에 JWT) | WS | `/ws` | Yes | Yes |
| | 실시간 경매 입찰 현황 | SUB | `/topic/items/{itemId}` | Yes | Yes |
| User | 실시간 메시지 수신 (구독) | SUB | `/user/queue/chat` | Yes | Yes |

## 결제

### ignoa-api

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| Server | 낙찰 거래 자동 생성 | Scheduler | 경매 마감 시 (기존 마감 스케줄러) | Yes | Yes |
| User | 즉시구매 거래 생성 | POST | `/api/items/{itemId}/buy-now` | Yes | Yes |
| User | 결제 준비 | POST | `/api/trades/{tradeId}/payments` | Yes | Yes |
| User | 결제 승인 | POST | `/api/trades/{tradeId}/payments/confirm` | Yes | Yes |
| User | 나의 거래 조회 | GET | `/api/items/{itemId}/trades/me` | Yes | Yes |
| Server | 미결제 거래 자동 취소 | Scheduler | 기한 경과 (낙찰 24시간 / 즉시구매 30분) | Yes | Yes |
| Server | 결제 결과 콜백 수신 (결제 서버 호출) | POST | `/internal/trades/{tradeId}/payment-result` | Yes | Yes |

### ignoa-payment

| 권한 | 기능 | Method | URI | 구현 | 연동 |
|---|---|---|---|---|---|
| Server | 결제 준비 | POST | `/internal/payments` | Yes | No |
| Server | 결제 승인 | POST | `/internal/payments/confirm` | Yes | No |
| Server | 결제 상태 조회 | GET | `/internal/payments/{orderId}` | Yes | No |
| Server | Toss 웹훅 | POST | `/payments/webhook` | Yes | No |
| Server | 미확정 결제 재조회 | Scheduler | 60초마다 | Yes | No |
| Server | 결제 결과 콜백 발송 | Scheduler | 10초마다 | Yes | No |
