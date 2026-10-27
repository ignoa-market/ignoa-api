import {createLoadTest} from '../lib/load-test.js';
import {summaryHandler} from '../lib/summary.js';

// 한계 탐색. 방문자 흐름(19명/초, 약 200 RPS) + 단일 상품 입찰 100→200→400 RPS, 단계별 1분 상승·3분 유지.
export const {options, setup, visit, bid, teardown} = createLoadTest('stress');
export const handleSummary = summaryHandler('stress');
