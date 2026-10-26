import {createLoadTest} from '../lib/load-test.js';

// 실제 서버가 아닌 로컬 fixture API 전용. THINK_SCALE=0으로 쉬는 시간 없이 실행한다.
const load = createLoadTest('browse-bid');
export const {setup, visit, bid, teardown} = load;
export const options = {
    ...load.options,
    scenarios: Object.fromEntries(Object.entries(load.options.scenarios).map(([name, scenario]) => [name, {
        executor: 'shared-iterations', exec: scenario.exec, vus: 1, iterations: 6, maxDuration: '15s',
    }])),
    thresholds: {read_errors: ['rate==0'], bid_errors: ['rate==0'], page_errors: ['rate==0']},
};
