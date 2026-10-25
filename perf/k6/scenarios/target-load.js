import {createLoadTest} from '../lib/load-test.js';
import {summaryHandler} from '../lib/summary.js';

// 운영 목표 부하. browse와 같은 방문자 흐름(약 200 RPS) + 단일 상품 입찰 50 RPS.
export const {options, setup, visit, bid, teardown} = createLoadTest('target-load');
export const handleSummary = summaryHandler('target-load');
