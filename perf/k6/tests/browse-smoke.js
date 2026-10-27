import {createLoadTest} from '../lib/load-test.js';

// 실제 서버가 아닌 로컬 fixture API 전용. THINK_SCALE=0으로 쉬는 시간 없이 실행한다.
const load = createLoadTest('browse');
export const {setup, visit} = load;
export const options = {
    ...load.options,
    scenarios: {browse: {executor: 'shared-iterations', exec: 'visit', vus: 2, iterations: 30, maxDuration: '20s'}},
    thresholds: {read_errors: ['rate==0'], page_errors: ['rate==0']},
};
