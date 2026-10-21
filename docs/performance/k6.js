import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080/api';
const username = __ENV.K6_USERNAME;
const password = __ENV.K6_PASSWORD;
const mode = __ENV.MODE || 'cursor';

export const options = {
  scenarios: {
    gallery: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  if (!username || !password) throw new Error('Set K6_USERNAME and K6_PASSWORD for a dedicated test account');
  const response = http.post(`${baseUrl}/auth/login`, JSON.stringify({ username, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  const body = response.json();
  if (response.status !== 200 || !body?.success || !body?.data?.token) {
    throw new Error(`Test account login failed with HTTP ${response.status}`);
  }
  return { token: body.data.token };
}

export default function (auth) {
  const path = mode === 'offset'
    ? '/images?page=0&size=20'
    : mode === 'empty-search'
      ? '/images/search?keyword=k6-no-match-20261021'
      : '/images/cursor?size=20';
  const response = http.get(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${auth.token}` },
  });
  check(response, {
    'gallery responds 200': (r) => r.status === 200,
    'gallery returns JSON': (r) => r.headers['Content-Type']?.includes('application/json'),
  });
}
