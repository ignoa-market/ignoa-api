import http from 'k6/http';
import {check, sleep} from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:38080';

export const options = {
    stages: [
        {duration: '10s', target: parseInt(__ENV.VUS || '100')},
        {duration: '30s', target: parseInt(__ENV.VUS || '100')},
        {duration: '5s', target: 0},
    ],
    thresholds: {
        http_req_failed: ['rate<0.1'],
        http_req_duration: ['p(95)<3000'],
    },
};

export function setup() {
    const res = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({email: __ENV.EMAIL, password: __ENV.PASSWORD}),
        {headers: {'Content-Type': 'application/json'}}
    );

    const token = res.json('data.access_token');
    if (!token) throw new Error(`로그인 실패: ${res.status} / ${res.body}`);

    return {token};
}

export default function (data) {
    const VUS = parseInt(__ENV.VUS || '100');
    const bidPrice = 10000 + (__ITER * VUS + __VU) * 100;

    const res = http.post(
        `${BASE_URL}/api/items/${__ENV.ITEM_ID}/bids`,
        JSON.stringify({price: bidPrice}),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${data.token}`
            },
        }
    );

    check(res, {
        '성공 (2xx)': (r) => r.status >= 200 && r.status < 300,
        '입찰 실패 (4xx)': (r) => r.status >= 400 && r.status < 500,
        '서버 오류 (5xx)': (r) => r.status >= 500,
    });

    sleep(1);
}