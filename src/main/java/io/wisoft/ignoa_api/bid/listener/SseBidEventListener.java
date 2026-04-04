package io.wisoft.ignoa_api.bid.listener;

// V1: SSE 기반 실시간 입찰가 브로드캐스트 (학습용 보관)
// V2(현재): BidEventListener (WebSocket/STOMP 기반)
//
// @Component
// @RequiredArgsConstructor
// public class SseBidEventListener {
//
//     private final SseEmitterManager sseEmitterManager;
//
//     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//     public void onBidPlaced(BidPlaceEvent event) {
//         BidBroadcast broadcast = new BidBroadcast(
//                 event.itemId(),
//                 event.currentPrice(),
//                 event.bidderNickname(),
//                 event.createdAt()
//         );
//
//         sseEmitterManager.send(event.itemId(), broadcast);
//     }
// }
