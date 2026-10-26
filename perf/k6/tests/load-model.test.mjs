import test from 'node:test';
import assert from 'node:assert/strict';
import {buildLoadPlan, readCursor} from '../lib/load-model.js';

test('세 시나리오는 같은 방문자 흐름(19명/초)을 쓰고 입찰 부하만 다르다', () => {
    const browse = buildLoadPlan('browse');
    const target = buildLoadPlan('browse-bid');
    const stress = buildLoadPlan('stress');
    assert.equal(browse.visitsPerSecond, 19);
    assert.equal(browse.bidStages, null);
    assert.equal(target.visitsPerSecond, 19);
    assert.equal(target.peakBidRate, 50);
    assert.deepEqual(target.measurementWindows, [{name: 'steady', start: 60, end: 360}]);
    assert.equal(stress.visitsPerSecond, 19);
    assert.equal(stress.peakBidRate, 400);
});

test('조회 RPS를 넘기면 방문자 수만 바뀌고 입찰 부하는 그대로다', () => {
    for (const [rps, visits] of [['200', 19], ['400', 38], ['500', 47], ['600', 57]]) {
        assert.equal(buildLoadPlan('browse', rps).visitsPerSecond, visits);
    }
    const target = buildLoadPlan('browse-bid');
    const peak = buildLoadPlan('browse-bid', '400');
    assert.deepEqual(peak.bidStages, target.bidStages);
    assert.deepEqual(peak.measurementWindows, target.measurementWindows);
    assert.equal(buildLoadPlan('browse', '').visitsPerSecond, 19);
});

test('조회 RPS가 양의 정수가 아니면 실행 전에 거절한다', () => {
    for (const rps of ['0', '-100', '12.5', 'abc']) {
        assert.throws(() => buildLoadPlan('browse', rps));
    }
});

test('스트레스 각 단계는 별도로 측정된다', () => {
    const stress = buildLoadPlan('stress');
    assert.deepEqual(stress.measurementWindows, [
        {name: '100rps', start: 60, end: 240},
        {name: '200rps', start: 300, end: 480},
        {name: '400rps', start: 540, end: 720},
    ]);
    assert.equal(stress.totalSeconds, 730);
});

test('분류할 수 없는 시나리오는 실행 전에 거절한다', () => {
    assert.throws(() => buildLoadPlan('missing'));
    assert.throws(() => buildLoadPlan('mixed'));
});

test('커서는 실제 next_cursor와 has_next를 함께 확인한다', () => {
    assert.equal(readCursor({content: [{id: 9}], next_cursor: 9, has_next: true}), 9);
    assert.equal(readCursor({content: [{id: 9}], next_cursor: 9, has_next: false}), null);
    assert.throws(() => readCursor({content: [], next_cursor: 9, has_next: true}));
    assert.throws(() => readCursor({content: [{}], next_cursor: null, has_next: true}));
});
