import http from 'k6/http';
import {sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';
import {ORIGIN_HEADERS} from './config.js';
import {readCursor} from './load-model.js';

// 방문자 한 명의 흐름. 값은 실제 트래픽이 아닌 가정값이다.
// 메인 3건 + 스크롤 평균 2.5건 + 클릭 평균 1.4개 × (상세·이력 2건 + 이력 더 보기 0.2건) + 메인 복귀 평균 0.7번 × 3건
// ≈ 방문당 10.7건
const MAX_SCROLL_PAGES = 5;      // 최신순 스크롤 0~5페이지
const CLICK_RATE = 0.7;          // 상품을 누르는 방문자 비율
const MAX_CLICKS = 3;            // 누르면 1~3개
const MORE_BIDS_RATE = 0.2;      // 상세에서 입찰 이력을 더 내려 보는 비율
const THINK_SECONDS = [1, 3];    // 행동 사이 쉬는 시간
// 로컬 fixture 테스트에서만 0으로 둔다.
const THINK_SCALE = Number(__ENV.THINK_SCALE ?? 1);

export const READ_KINDS = ['popular', 'ending', 'latest', 'latest_next', 'detail', 'history', 'history_next'];
// 목록은 비어 있으면 오류로 본다. 입찰 이력은 입찰이 없는 상품이면 정상적으로 비어 있다.
const LIST_KINDS = ['popular', 'ending', 'latest', 'latest_next'];

const readDuration = new Trend('read_duration', true);
const readErrors = new Rate('read_errors');
const pageErrors = new Rate('page_errors');
const readResults = new Counter('read_results');

function randomInt(min, max) {
    return min + Math.floor(Math.random() * (max - min + 1));
}

function think() {
    sleep(THINK_SCALE * (THINK_SECONDS[0] + Math.random() * (THINK_SECONDS[1] - THINK_SECONDS[0])));
}

// 응답 하나를 기록하고, 목록·이력이면 페이지를 돌려준다. 실패하면 null.
function record(kind, response, phase) {
    const tags = {kind, phase};
    let page = null;
    let validPage = true;
    if (kind !== 'detail') {
        try {
            if (response.status !== 200) throw new Error();
            page = response.json('data');
            readCursor(page);
            if (LIST_KINDS.includes(kind) && !page.content.length) throw new Error();
        } catch (_) {
            page = null;
            validPage = false;
        }
        pageErrors.add(!validPage, tags);
    }
    const ok = response.status === 200 && validPage;
    readDuration.add(response.timings.duration, tags);
    readErrors.add(!ok, tags);
    readResults.add(1, {...tags, outcome: ok ? 'accepted' : 'error', status: String(response.status)});
    return page;
}

function itemIds(page) {
    return page ? page.content.map(item => item.item_id) : [];
}

// 방문자 한 명. baseUrl·token으로 요청하고, phase()로 현재 측정 구간을 받는다.
export function browse(baseUrl, token, phase) {
    // 여러 요청은 프론트처럼 동시에 보낸다.
    function fetchAll(requests) {
        const current = phase();
        const responses = http.batch(requests.map(([kind, path]) => ['GET', `${baseUrl}${path}`, null, {
            headers: {...ORIGIN_HEADERS, Authorization: `Bearer ${token}`},
            tags: {kind, name: `read/${kind}`}, timeout: '10s',
        }]));
        return requests.map(([kind], i) => record(kind, responses[i], current));
    }

    // 화면에 여러 번 뜬 상품도 한 번만 담아, 본 상품 중에서 고르게 누른다.
    const seen = new Set();
    const addSeen = page => itemIds(page).forEach(id => seen.add(id));
    // 메인 진입·복귀: 인기순·마감 임박·최신순 첫 페이지
    function openMain() {
        const [popular, ending, latest] = fetchAll([
            ['popular', '/api/items?view=POPULAR&size=5'],
            ['ending', '/api/items?view=ENDING_SOON&size=5'],
            ['latest', '/api/items?view=LATEST&size=20'],
        ]);
        [popular, ending, latest].forEach(addSeen);
        return latest ? readCursor(latest) : null;
    }

    // 상품 상세: 상세와 입찰 이력 첫 페이지를 함께 불러온다(ProductDetailPage와 같음).
    function openItem(itemId) {
        const [, bids] = fetchAll([
            ['detail', `/api/items/${itemId}`],
            ['history', `/api/items/${itemId}/bids`],
        ]);
        const cursor = bids ? readCursor(bids) : null;
        if (cursor !== null && Math.random() < MORE_BIDS_RATE) {
            think();
            fetchAll([['history_next', `/api/items/${itemId}/bids?cursor=${cursor}`]]);
        }
    }

    let cursor = openMain();
    const pages = randomInt(0, MAX_SCROLL_PAGES);
    for (let i = 0; i < pages && cursor !== null; i++) {
        think();
        const [page] = fetchAll([['latest_next', `/api/items?view=LATEST&size=20&cursor=${cursor}`]]);
        addSeen(page);
        cursor = page ? readCursor(page) : null;
    }

    if (!seen.size || Math.random() >= CLICK_RATE) return;
    const clicks = randomInt(1, MAX_CLICKS);
    for (let i = 0; i < clicks; i++) {
        think();
        const candidates = [...seen];
        openItem(candidates[Math.floor(Math.random() * candidates.length)]);
        if (i < clicks - 1) {
            think();
            openMain();
        }
    }
}
