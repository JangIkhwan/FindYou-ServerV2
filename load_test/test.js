import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 10,
    duration: '30s',
    insecureSkipTLSVerify: true,
};

const BASE_URL = 'http://localhost:9001';

// VU별로 유지되는 토큰 저장소
let accessToken = null;

function loginOncePerVU() {
    if (accessToken !== null) {
        return accessToken;
    }

    const loginPayload = JSON.stringify({
        deviceId: `device-${__VU}`, // VU마다 고유한 사용자
    });

    const loginRes = http.post(
        `${BASE_URL}/api/v2/auth/login/guest`,
        loginPayload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(loginRes, {
        'login status is 200': (r) => r.status === 200,
    });

    const loginBody = JSON.parse(loginRes.body);
    accessToken = loginBody.data.accessToken;

    return accessToken;
}

export default function () {
    const token = loginOncePerVU();

    const homeRes = http.get(
        `${BASE_URL}/api/v2/home`,
        {
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }
    );

    check(homeRes, {
        'home status is 200': (r) => r.status === 200,
        'home response time < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(1);
}