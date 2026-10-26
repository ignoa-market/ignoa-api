import {textSummary} from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// 실행 방법(run.sh, k6 run)과 상관없이 끝날 때 요약 JSON을 저장한다.
// run.sh는 SUMMARY_FILE로 회차별 경로를 넘긴다. 없으면 perf/results/k6/시나리오-시각.json (저장소 루트에서 실행).
function stamp(date) {
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

export function summaryHandler(name) {
    return function handleSummary(data) {
        const file = __ENV.SUMMARY_FILE || `perf/results/k6/${name}-${stamp(new Date())}.json`;
        console.log(`요약 저장: ${file}`);
        return {
            stdout: textSummary(data, {indent: ' ', enableColors: true}),
            [file]: JSON.stringify(data, null, 2),
        };
    };
}
