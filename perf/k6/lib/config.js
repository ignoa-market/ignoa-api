export const START_PRICE = 1_000;

// perf/sql/data/3-accounts.sql이 만드는 k6 전용 계정. 비밀번호는 k6.env의 PASSWORD.
export const SELLER_EMAIL = 'k6-seller@ignoa.perf';
export const BIDDER_EMAILS = Array.from({length: 50}, (_, i) => `k6-bidder-${i + 1}@ignoa.perf`);

// prod의 CloudFrontOriginFilter가 /api/** 요청에 요구하는 헤더.
// 값이 없으면 빈 객체라 필터가 꺼진 환경에서는 영향이 없다.
const originSecret = __ENV.CLOUDFRONT_ORIGIN_VERIFY_SECRET;
export const ORIGIN_HEADERS = originSecret ? {'X-Origin-Verify': originSecret} : {};
