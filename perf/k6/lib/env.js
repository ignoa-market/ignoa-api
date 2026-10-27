export function requiredEnv(name) {
    const value = __ENV[name];

    if (!value) {
        throw new Error(`필수 환경변수가 없습니다: ${name}`);
    }

    return value;
}
