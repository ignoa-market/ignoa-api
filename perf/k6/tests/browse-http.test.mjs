import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

// 방문자 흐름이 프론트와 같은 순서·묶음으로 요청하는지 실제 k6로 확인한다.
test('방문자는 메인 3종을 먼저 보고, 본 상품만 눌러 상세와 이력을 함께 요청한다', async () => {
    const items = Array.from({length: 100}, (_, i) => ({item_id: 100 - i}));
    const paths = [];
    const server = http.createServer(async (request, response) => {
        const url = new URL(request.url, 'http://localhost');
        paths.push(url.pathname + url.search);
        for await (const _ of request);
        const send = (status, data) => {
            response.writeHead(status, {'content-type': 'application/json'});
            response.end(JSON.stringify({data}));
        };
        if (url.pathname === '/api/auth/login') return send(200, {access_token: 'fixture-token'});
        const match = url.pathname.match(/^\/api\/items\/(\d+)(\/bids)?$/);
        if (match) {
            if (!match[2]) return send(200, {item_id: Number(match[1])});
            const cursor = Number(url.searchParams.get('cursor')) || 41;
            const content = Array.from({length: Math.min(20, cursor - 1)}, (_, i) => ({bid_id: cursor - 1 - i}));
            return send(200, {content, has_next: cursor > 21, next_cursor: cursor > 21 ? content.at(-1).bid_id : null});
        }
        if (url.pathname === '/api/items') {
            const cursor = Number(url.searchParams.get('cursor')) || Infinity;
            const size = Number(url.searchParams.get('size')) || 20;
            const all = items.filter(i => i.item_id < cursor);
            const content = all.slice(0, size);
            const hasNext = url.searchParams.get('view') === 'LATEST' && all.length > size;
            return send(200, {content, has_next: hasNext, next_cursor: hasNext ? content.at(-1).item_id : null});
        }
        send(404, {});
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    try {
        const base = `http://127.0.0.1:${server.address().port}`;
        const result = await new Promise((resolve, reject) => {
            const child = spawn('k6', ['run', '--quiet', '--no-usage-report',
                '-e', `BASE_URL=${base}`, '-e', 'PASSWORD=fixture', '-e', 'THINK_SCALE=0',
                fileURLToPath(new URL('./browse-smoke.js', import.meta.url))],
                {env: {...process.env, K6_NO_USAGE_REPORT: 'true'}});
            let output = '';
            child.stdout.on('data', chunk => output += chunk);
            child.stderr.on('data', chunk => output += chunk);
            child.on('error', reject);
            child.on('close', code => resolve({code, output}));
        });
        assert.equal(result.code, 0, result.output);
        const reads = paths.filter(p => p !== '/api/auth/login');
        const count = re => reads.filter(p => re.test(p)).length;
        const mains = count(/view=POPULAR&size=5$/);
        assert.ok(mains >= 30, '방문마다 메인 진입');
        assert.equal(count(/view=ENDING_SOON&size=5$/), mains);
        assert.equal(count(/view=LATEST&size=20$/), mains);
        assert.ok(count(/view=LATEST&size=20&cursor=\d+$/) > 0, '스크롤');
        const details = count(/^\/api\/items\/\d+$/);
        assert.ok(details > 0, '상품 클릭');
        assert.equal(count(/^\/api\/items\/\d+\/bids$/), details, '상세와 이력 첫 페이지는 항상 함께');
        // 상세는 목록에서 본 상품만 연다.
        const shown = new Set(items.map(i => String(i.item_id)));
        assert.ok(reads.filter(p => /^\/api\/items\/\d+$/.test(p)).every(p => shown.has(p.split('/')[3])));
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});
