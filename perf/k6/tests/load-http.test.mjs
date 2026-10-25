import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

// 요청 URL·커서·가격을 실제 k6로 실행해 확인한다. 외부 네트워크는 사용하지 않는다.
test('target-load는 방문자 흐름으로 조회하고 여러 입찰자로 준비 가격 이후부터 입찰한다', async () => {
    // seed 데이터 대신 상품 60개를 둔다. 홀수 상품만 입찰 이력 25건이 있고 짝수 상품은 이력이 비어 있다.
    const items = [];
    const history = new Map();
    const paths = [];
    const prices = [];
    const bidTokens = new Set();
    const created = [];
    let bidId = 0;
    for (let id = 1; id <= 60; id++) {
        items.unshift({item_id: id});
        history.set(id, id % 2 ? Array.from({length: 25}, () => ({bid_id: ++bidId})).reverse() : []);
    }
    const server = http.createServer(async (request, response) => {
        const url = new URL(request.url, 'http://localhost');
        paths.push(url.pathname + url.search);
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        const body = Buffer.concat(chunks).toString();
        const send = (status, data) => {
            response.writeHead(status, {'content-type': 'application/json'});
            response.end(JSON.stringify({data}));
        };
        if (url.pathname === '/api/auth/login') return send(200, {access_token: `token-${JSON.parse(body).email}`});
        if (request.method === 'POST' && url.pathname === '/api/items') {
            const id = 100_001 + created.length;
            created.push(id);
            items.unshift({item_id: id});
            history.set(id, []);
            return send(201, {item_id: id});
        }
        const match = url.pathname.match(/^\/api\/items\/(\d+)(\/bids)?$/);
        if (match) {
            const id = Number(match[1]);
            if (!match[2]) return send(200, {item_id: id});
            if (!history.has(id)) return send(404, {});
            if (request.method === 'POST') {
                const price = JSON.parse(body).price;
                const bids = history.get(id);
                if (bids.length >= 40) {
                    prices.push(price);
                    bidTokens.add(request.headers.authorization);
                }
                bids.unshift({bid_id: ++bidId, price});
                return send(201, {bid_id: bidId});
            }
            const cursor = Number(url.searchParams.get('cursor')) || Infinity;
            const all = history.get(id).filter(b => b.bid_id < cursor);
            const content = all.slice(0, 20);
            return send(200, {content, has_next: all.length > 20, next_cursor: all.length > 20 ? content.at(-1).bid_id : null});
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
                fileURLToPath(new URL('./smoke.js', import.meta.url))],
                {env: {...process.env, K6_NO_USAGE_REPORT: 'true'}});
            let output = '';
            child.stdout.on('data', chunk => output += chunk);
            child.stderr.on('data', chunk => output += chunk);
            child.on('error', reject);
            child.on('close', code => resolve({code, output}));
        });
        assert.equal(result.code, 0, result.output);
        assert.equal(created.length, 1);
        assert.equal(bidTokens.size, 6);
        assert.ok(paths.includes('/api/items?view=POPULAR&size=5'));
        assert.ok(paths.includes('/api/items?view=ENDING_SOON&size=5'));
        assert.ok(paths.includes('/api/items?view=LATEST&size=20'));
        assert.equal(prices.length, 6);
        assert.equal(Math.min(...prices), 5100);
        assert.equal(Math.max(...prices), 5600);
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});
