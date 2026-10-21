import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080/api';
const token = __ENV.AUTH_TOKEN;
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

export default function () {
  if (!token) throw new Error('Set AUTH_TOKEN to a valid bearer token');
  const path = mode === 'offset' ? '/images?page=0&size=20' : '/images/cursor?size=20';
  const response = http.get(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(response, {
    'gallery responds 200': (r) => r.status === 200,
    'gallery returns JSON': (r) => r.headers['Content-Type']?.includes('application/json'),
  });
  sleep(1);
}
