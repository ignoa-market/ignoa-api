import http from 'k6/http';
import {ORIGIN_HEADERS} from './config.js';

export const BID_RESULT = Object.freeze({
    APPLIED: 'applied',
    BID_CONFLICT: 'bid_conflict',
    SERVER_ERROR: 'server_error',
    UNEXPECTED_RESPONSE: 'unexpected_response',
});

export function login(baseUrl, email, password) {
    const response = http.post(
        `${baseUrl}/api/auth/login`,
        JSON.stringify({email, password}),
        {
            headers: {...ORIGIN_HEADERS, 'Content-Type': 'application/json'},
            tags: {name: 'POST /api/auth/login'},
        },
    );

    if (response.status < 200 || response.status >= 300 || !response.body) {
        throw new Error(
            `로그인 요청 실패: status=${response.status}, error=${response.error || 'none'}, body=${response.body || 'empty'}`,
        );
    }

    let token;
    try {
        token = response.json('data.access_token');
    } catch (_) {
        throw new Error(`로그인 응답 JSON 파싱 실패: status=${response.status}, body=${response.body}`);
    }

    if (!token) {
        throw new Error(`로그인 토큰 없음: status=${response.status}, body=${response.body}`);
    }

    return token;
}

export function placeBid(baseUrl, itemId, price, token) {
    return http.post(
        `${baseUrl}/api/items/${itemId}/bids`,
        JSON.stringify({price}),
        {
            headers: {
                ...ORIGIN_HEADERS,
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
            },
            tags: {name: 'POST /api/items/{itemId}/bids'},
        },
    );
}

export function readErrorCode(response) {
    try {
        return response.json('code');
    } catch (_) {
        return null;
    }
}

export function classifyBidResponse(response) {
    if (response.status >= 200 && response.status < 300) {
        return BID_RESULT.APPLIED;
    }

    if (response.status === 0 || response.status >= 500) {
        return BID_RESULT.SERVER_ERROR;
    }

    const errorCode = readErrorCode(response);

    if (errorCode === 'BID_CONFLICT') {
        return BID_RESULT.BID_CONFLICT;
    }

    return BID_RESULT.UNEXPECTED_RESPONSE;
}
