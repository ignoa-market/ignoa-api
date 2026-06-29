import http from 'k6/http';
import {Counter, Trend} from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL;
const ITEM_ID = __ENV.ITEM_ID;

if (!BASE_URL || !ITEM_ID) throw new Error('BASE_URL, ITEM_ID 환경변수가 필요합니다');

http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}));

const bid2xx = new Counter('bid_2xx');
const bid4xx = new Counter('bid_4xx');
const bid5xx = new Counter('bid_5xx');

const readLatency = new Trend('read_duration', true);
const read2xx = new Counter('read_2xx');
const readError = new Counter('read_error');

const ONLY_READ = __ENV.ONLY_READ === '1';
const ONLY_BID = __ENV.ONLY_BID === '1';

const bidScenario = {
    executor: 'ramping-arrival-rate',
    exec: 'placeBid',
    startRate: 50,
    timeUnit: '1s',
    preAllocatedVUs: 100,
    maxVUs: 1000,
    stages: [
        {duration: '45s', target: 150},
        {duration: '45s', target: 250},
        {duration: '45s', target: 400},
        {duration: '10s', target: 0},
    ],
};

const bidBaselineScenario = {
    executor: 'constant-arrival-rate',
    exec: 'placeBid',
    rate: 5,
    timeUnit: '1s',
    duration: '60s',
    preAllocatedVUs: 5,
    maxVUs: 20,
};

const readScenario = {
    executor: 'constant-arrival-rate',
    exec: 'browseItems',
    rate: 10,
    timeUnit: '1s',
    duration: ONLY_READ ? '60s' : '145s',
    preAllocatedVUs: 20,
    maxVUs: 200,
};

function buildScenarios() {
    if (ONLY_READ) return {read: readScenario};
    if (ONLY_BID) return {bid: bidBaselineScenario};
    return {bid: bidScenario, read: readScenario};
}

export const options = {
    scenarios: buildScenarios(),
    thresholds: (ONLY_READ || ONLY_BID) ? {} : {'http_req_failed{scenario:bid}': ['rate<0.1']},
};

export function setup() {
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({email: __ENV.EMAIL, password: __ENV.PASSWORD}),
        {headers: {'Content-Type': 'application/json'}}
    );

    const token = res.json('data.access_token');
    if (!token) throw new Error(`로그인 실패: ${res.status} / ${res.body}`);

    return {token};
}

export function placeBid(data) {
    const bidPrice = 10000 + exec.scenario.iterationInTest * 100;

    const res = http.post(
        `${BASE_URL}/api/items/${ITEM_ID}/bids`,
        JSON.stringify({price: bidPrice}),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${data.token}`,
            },
        }
    );

    const s = res.status;
    if (s < 400) bid2xx.add(1);
    else if (s < 500) bid4xx.add(1);
    else bid5xx.add(1);
}

export function browseItems(data) {
    const res = http.get(
        `${BASE_URL}/api/items`,
        {headers: {'Authorization': `Bearer ${data.token}`}}
    );

    readLatency.add(res.timings.duration);
    if (res.status >= 200 && res.status < 300) read2xx.add(1);
    else readError.add(1);
}
