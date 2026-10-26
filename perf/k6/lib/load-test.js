import http from 'k6/http';
import exec from 'k6/execution';
import {Counter, Rate, Trend} from 'k6/metrics';
import {login, placeBid as requestBid, readErrorCode, classifyBidResponse, BID_RESULT} from './bid-client.js';
import {createTestItem} from './item-client.js';
import {BIDDER_EMAILS, SELLER_EMAIL, START_PRICE} from './config.js';
import {requiredEnv} from './env.js';
import {buildLoadPlan} from './load-model.js';
import {browse, READ_KINDS} from './browse-flow.js';

const BASE_URL = requiredEnv('BASE_URL');
const SEED_BIDS = 40; // 입찰 상품의 이력이 첫 페이지(20건)를 넘도록 미리 넣는다
const PRICE_STEP = 100;
const bidDuration = new Trend('bid_duration', true);
const bidErrors = new Rate('bid_errors');
const bidResults = new Counter('bid_results');

http.setResponseCallback(http.expectedStatuses({min: 200, max: 299}, 409));

function phaseAt(plan, elapsedSeconds) {
    return plan.measurementWindows.find(w => elapsedSeconds >= w.start && elapsedSeconds < w.end)?.name
        || (elapsedSeconds >= plan.totalSeconds - 10 ? 'cooldown' : 'ramp');
}

// 실행 중에 VU를 만들면 생성이 늦어 방문을 놓치고, 따라잡을 때 요청이 한꺼번에 몰린다.
// 그래서 처음부터 만들어 두되, PC 메모리를 생각해 1,000명까지만 미리 만든다.
function arrival(execName, stages, maxVUs) {
    return {
        executor: 'ramping-arrival-rate', exec: execName,
        startRate: 0, timeUnit: '1s', stages,
        preAllocatedVUs: Math.min(maxVUs, 1000), maxVUs, gracefulStop: '30s',
    };
}

export function createLoadTest(name) {
    const plan = buildLoadPlan(name, __ENV.READ_RPS);
    // 방문 하나가 쉬는 시간 포함 수십 초 걸려 동시에 수백 명이 머문다(초당 방문자 × 약 25초).
    const scenarios = {browse: arrival('visit', plan.visitStages, Math.max(1000, plan.visitsPerSecond * 25))};
    const thresholds = {
        read_errors: ['rate==0'], page_errors: ['rate==0'],
        dropped_iterations: ['count==0'],
    };
    for (const kind of READ_KINDS) {
        for (const window of plan.measurementWindows) {
            thresholds[`read_duration{kind:${kind},phase:${window.name}}`] = ['p(95)<=150', 'p(99)<=300'];
        }
    }
    if (plan.bidStages) {
        scenarios.bid = arrival('bid', plan.bidStages, Math.min(1000, Math.max(50, plan.peakBidRate * 5)));
        thresholds.bid_errors = ['rate==0'];
        thresholds['bid_results{code:BID_CONFLICT}'] = ['count>=0'];
        for (const window of plan.measurementWindows) {
            thresholds[`bid_duration{phase:${window.name}}`] = ['p(95)<=500'];
            for (const outcome of Object.values(BID_RESULT)) {
                thresholds[`bid_results{outcome:${outcome},phase:${window.name}}`] = ['count>=0'];
            }
        }
    }
    const options = {
        scenarios, thresholds, setupTimeout: '10m',
        summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
        tags: {test_profile: name},
    };

    function setup() {
        const password = requiredEnv('PASSWORD');
        const tokens = BIDDER_EMAILS.map(email => login(BASE_URL, email, password));
        let hotId = null;
        if (plan.bidStages) {
            // 입찰 상품만 새로 만든다. 최신 상품이라 방문자 목록 맨 위에 보여 자연스럽게 많이 눌린다.
            hotId = createTestItem(BASE_URL, START_PRICE, login(BASE_URL, SELLER_EMAIL, password));
            for (let i = 1; i <= SEED_BIDS; i++) {
                const response = requestBid(BASE_URL, hotId, START_PRICE + i * PRICE_STEP, tokens[(i - 1) % tokens.length]);
                if (response.status !== 201) throw new Error(`입찰 이력 준비 실패: item=${hotId}, status=${response.status}`);
            }
        }
        const bid = name === 'stress' ? '100→200→400' : plan.peakBidRate;
        console.log(`준비 완료: profile=${name}, visits=${plan.visitsPerSecond}/s, bid=${bid} RPS, users=${tokens.length}, 입찰 상품 ID=${hotId ?? '없음'}`);
        return {tokens, hotId};
    }

    function context() {
        const start = exec.scenario.startTime;
        if (exec.scenario.iterationInTest === 0) {
            console.log(`시나리오=${exec.scenario.name}, 시작 UTC=${new Date(start).toISOString()}`);
            for (const w of plan.measurementWindows) {
                console.log(`유지 구간=${w.name}, UTC=${new Date(start + w.start * 1000).toISOString()} ~ ${new Date(start + w.end * 1000).toISOString()}`);
            }
        }
        return {phase: phaseAt(plan, (Date.now() - start) / 1000)};
    }

    function visit(data) {
        context();
        const token = data.tokens[exec.scenario.iterationInTest % data.tokens.length];
        browse(BASE_URL, token, () => phaseAt(plan, (Date.now() - exec.scenario.startTime) / 1000));
    }

    function bid(data) {
        const tags = context();
        const iteration = exec.scenario.iterationInTest;
        const price = START_PRICE + (SEED_BIDS + iteration + 1) * PRICE_STEP;
        // 입찰자를 차례로 바꿔 입찰자 수·최고 입찰자 변경이 실제로 일어나게 한다.
        const token = data.tokens[iteration % data.tokens.length];
        const response = requestBid(BASE_URL, data.hotId, price, token);
        const outcome = classifyBidResponse(response);
        const expected = [BID_RESULT.APPLIED, BID_RESULT.BID_CONFLICT].includes(outcome);
        bidDuration.add(response.timings.duration, tags);
        bidErrors.add(!expected, tags);
        bidResults.add(1, {...tags, outcome, status: String(response.status), code: readErrorCode(response) || 'none'});
    }

    function teardown(data) {
        console.log(`실행 종료 UTC=${new Date().toISOString()}; profile=${name}`);
        if (data.hotId) console.log(`테스트 데이터 정리 대상(입찰 상품): ${data.hotId}`);
    }
    return {options, setup, visit, bid, teardown};
}
