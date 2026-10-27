import {createLoadTest} from '../lib/load-test.js';
import {summaryHandler} from '../lib/summary.js';

// 조회만 하는 방문자 흐름. 초당 방문자 19명(약 200 RPS), 입찰 없음.
export const {options, setup, visit, teardown} = createLoadTest('browse');
export const handleSummary = summaryHandler('browse');
