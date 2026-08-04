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

const bidApplied = new Counter('bid_applied');
const bidLockTimeout = new Counter('bid_lock_timeout');
const bidBusinessReject = new Counter('bid_business_reject');
const bidServerError = new Counter('bid_server_error');

// Top: 어떤 부하를 만들 것인가?
export const options = {
    scenarios: {
        bid_baseline: {
            executor: 'constant-arrival-rate',
            exec: 'placeBid',
            rate: 5,
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 1,
            maxVUs: 10,
            gracefulStop: '5s',
        },
    },
    thresholds: {
        'http_req_failed{scenario:bid_baseline}': ['rate==0'],
        'dropped_iterations{scenario:bid_baseline}': ['count==0'],
    },
};

// Middle: 테스트 전에 무엇을 준비할 것인가?
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

// Bottom: 각 반복에서 실제로 어떤 요청을 보낼 것인가?
export function placeBid(data) {
    const price = START_PRICE + exec.scenario.iterationInTest * 100;
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

    const applied = response.status >= 200 && response.status < 300;

    check(response, {
        'bid returned 2xx or 4xx': (res) => res.status >= 200 && res.status < 500,
    });

    if (applied) {
        bidApplied.add(1);
        return;
    }

    if (response.status >= 500) {
        bidServerError.add(1);
        return;
    }

    if (readErrorCode(response) === 'LOCK_ACQUISITION_FAILED') {
        bidLockTimeout.add(1);
        return;
    }

    bidBusinessReject.add(1);
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

function readErrorCode(response) {
    try {
        return response.json('code');
    } catch (_) {
        return null;
    }
}
