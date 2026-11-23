// Redirect load test: creates 100 links, then VUs hammer GET /{code} for 60s.
//
//   k6 run load-test/redirect-test.js
//   k6 run -e VUS=100 -e BASE_URL=http://localhost:8080 load-test/redirect-test.js
//
// Start the backend with RATE_LIMIT_MAX=1000, otherwise setup() is stopped by the 10/min limit.
// Compare CACHE_ENABLED=true vs false to see what Redis buys you.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    redirects: {
      executor: 'constant-vus',
      // 50 VUs is plenty to saturate a laptop that also runs MySQL, Redis and the JVM (k6 itself needs ~100 MB).
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'], // fails the run (exit code 99) if p95 is 200 ms or worse
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
};

export function setup() {
  const codes = [];
  for (let i = 0; i < 100; i++) {
    const res = http.post(
      `${BASE_URL}/api/links`,
      JSON.stringify({ url: `https://example.com/load-test/${Date.now()}-${i}` }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    if (res.status !== 201) {
      throw new Error(`setup: creating link ${i} returned ${res.status} (429 = restart backend with RATE_LIMIT_MAX=1000)`);
    }
    codes.push(res.json('shortCode'));
  }
  return { codes };
}

export default function (data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  // redirects: 0 -> measure OUR 302, don't follow it to example.com.
  const res = http.get(`${BASE_URL}/${code}`, { redirects: 0 });
  check(res, { 'is 302': (r) => r.status === 302 });
}
