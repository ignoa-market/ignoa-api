import encoding from 'k6/encoding';
import http from 'k6/http';
import {ORIGIN_HEADERS} from './config.js';

const TEST_IMAGE = encoding.b64decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
);

export function createTestItem(baseUrl, startPrice, sellerToken) {
    const request = {
        title: `k6 load test ${Date.now()}`,
        description: 'Automatically created for a k6 load test.',
        category: 'ETC',
        item_condition: 'GOOD',
        start_price: startPrice,
        buy_now_price: startPrice + 1_000_000_000,
        brand: 'k6',
        end_at: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
    };

    const response = http.post(
        `${baseUrl}/api/items`,
        {
            request: http.file(JSON.stringify(request), 'request.json', 'application/json'),
            files: http.file(TEST_IMAGE, 'k6-test.png', 'image/png'),
        },
        {
            headers: {...ORIGIN_HEADERS, 'Authorization': `Bearer ${sellerToken}`},
            tags: {name: 'POST /api/items (setup)'},
        },
    );

    const itemId = readJson(response, 'data.item_id');
    if (response.status !== 201 || !itemId) {
        throw new Error(`테스트 상품 생성 실패: status=${response.status}, body=${response.body}`);
    }

    return itemId;
}

function readJson(response, selector) {
    try {
        return response.json(selector);
    } catch (_) {
        return null;
    }
}
