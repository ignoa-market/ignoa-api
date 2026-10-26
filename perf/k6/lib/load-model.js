// 조회는 모든 시나리오가 같은 방문자 흐름(browse-flow.js)을 쓰고, 입찰 부하만 다르다.
// 방문당 평균 약 10.6건이라 방문자 19명/초 ≈ 200 RPS다. 조회 RPS를 넘기면 방문자 수로 바꾼다.
const VISIT_REQUESTS = 10.6;
const PLANS = {
    'browse': {visitsPerSecond: 19, bid: null},
    'browse-bid': {visitsPerSecond: 19, bid: 'steady'},
    'stress': {visitsPerSecond: 19, bid: 'ramp'},
};

// readRps가 비어 있으면 시나리오 기본 방문자 수를 쓴다.
export function buildLoadPlan(name, readRps) {
    let base = PLANS[name];
    if (!base) throw new Error(`지원하지 않는 시나리오: ${name}`);
    if (readRps !== undefined && readRps !== '') {
        const rps = Number(readRps);
        if (!Number.isInteger(rps) || rps <= 0) throw new Error(`조회 RPS는 양의 정수여야 합니다: ${readRps}`);
        const visitsPerSecond = Math.round(rps / VISIT_REQUESTS);
        // 방문자가 0명이 되면 조회 없이 입찰만 돌아 측정이 무의미해진다.
        if (visitsPerSecond < 1) throw new Error(`조회 RPS는 ${Math.ceil(VISIT_REQUESTS / 2)} 이상이어야 합니다: ${readRps}`);
        base = {...base, visitsPerSecond};
    }
    const stress = base.bid === 'ramp';
    const totalSeconds = stress ? 730 : 370;
    const measurementWindows = stress
        ? [{name: '100rps', start: 60, end: 240}, {name: '200rps', start: 300, end: 480}, {name: '400rps', start: 540, end: 720}]
        : [{name: 'steady', start: 60, end: 360}];
    const visitStages = [
        {duration: '60s', target: base.visitsPerSecond},
        {duration: `${totalSeconds - 70}s`, target: base.visitsPerSecond},
        {duration: '10s', target: 0},
    ];
    let bidStages = null;
    if (base.bid === 'steady') bidStages = [{duration: '60s', target: 50}, {duration: '300s', target: 50}];
    if (stress) bidStages = [100, 200, 400].flatMap(target => [{duration: '60s', target}, {duration: '180s', target}]);
    if (bidStages) bidStages.push({duration: '10s', target: 0});
    return {
        name, visitsPerSecond: base.visitsPerSecond, visitStages, bidStages,
        peakBidRate: stress ? 400 : bidStages ? 50 : 0,
        measurementWindows, totalSeconds,
    };
}

// next_cursor는 서버에서 받은 값만 사용한다.
export function readCursor(page) {
    if (!page || !Array.isArray(page.content) || typeof page.has_next !== 'boolean') {
        throw new Error('커서 응답 형식이 올바르지 않습니다');
    }
    if (!page.has_next) return null;
    if (!page.content.length || !Number.isSafeInteger(page.next_cursor) || page.next_cursor <= 0) {
        throw new Error('다음 페이지가 있지만 유효한 커서가 없습니다');
    }
    return page.next_cursor;
}
