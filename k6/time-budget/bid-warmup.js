import http from 'k6/http';
import {check} from 'k6';
import {Counter} from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL;
const ITEM_ID = __ENV.ITEM_ID;
const EMAIL = __ENV.EMAIL;
const PASSWORD = __ENV.PASSWORD;
const START_PRICE = Number(__ENV.START_PRICE);

validateEnvironment();

http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

const warmupApplied = new Counter('warmup_bid_applied');
const warmupRejected = new Counter('warmup_bid_rejected');
const warmupServerError = new Counter('warmup_bid_server_error');

// Raspberry Pi의 JVM, DB, Redis와 입찰 코드 경로를 낮은 부하로 2분간 데운다.
export const options = {
    scenarios: {
        bid_warmup: {
            executor: 'ramping-arrival-rate',
            exec: 'placeBid',
            startRate: 1,
            timeUnit: '1s',
            preAllocatedVUs: 10,
            maxVUs: 30,
            stages: [
                {duration: '30s', target: 5},
                {duration: '60s', target: 10},
                {duration: '30s', target: 10},
            ],
            gracefulStop: '5s',
        },
    },
    thresholds: {
        'http_req_failed{scenario:bid_warmup}': ['rate==0'],
        'dropped_iterations{scenario:bid_warmup}': ['count==0'],
    },
};

export function setup() {
    const response = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({email: EMAIL, password: PASSWORD}),
        {
            headers: {'Content-Type': 'application/json'},
            tags: {name: 'POST /api/auth/login'},
        },
    );

    const loginSucceeded = check(response, {
        'login succeeded': (res) => res.status >= 200 && res.status < 300,
    });
    const token = response.json('data.access_token');

    if (!loginSucceeded || !token) {
        throw new Error(`로그인 실패: status=${response.status}, body=${response.body}`);
    }

    return {token};
}

export function placeBid(data) {
    const price = START_PRICE + exec.scenario.iterationInTest;
    const response = http.post(
        `${BASE_URL}/api/items/${ITEM_ID}/bids`,
        JSON.stringify({price}),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${data.token}`,
            },
            tags: {name: 'POST /api/items/{itemId}/bids'},
        },
    );

    check(response, {
        'warmup bid returned 2xx or 4xx': (res) => res.status >= 200 && res.status < 500,
    });

    if (response.status >= 200 && response.status < 300) {
        warmupApplied.add(1);
    } else if (response.status >= 500) {
        warmupServerError.add(1);
    } else {
        warmupRejected.add(1);
    }
}

function validateEnvironment() {
    const missing = [];

    if (!BASE_URL) missing.push('BASE_URL');
    if (!ITEM_ID) missing.push('ITEM_ID');
    if (!EMAIL) missing.push('EMAIL');
    if (!PASSWORD) missing.push('PASSWORD');
    if (!__ENV.START_PRICE) missing.push('START_PRICE');

    if (missing.length > 0) {
        throw new Error(`필수 환경변수가 없습니다: ${missing.join(', ')}`);
    }
    if (!Number.isFinite(START_PRICE) || START_PRICE <= 0) {
        throw new Error('START_PRICE는 0보다 큰 숫자여야 합니다');
    }
}
