// Load test for the read path: GET /{shortCode} -> 302
// Usage: PROFILE=smoke|load|peak|stress|spike|soak (see loadtest/README.md)
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, pickCode } from './lib/config.js';

const PROFILE = __ENV.PROFILE || 'smoke';

// arrival-rate executors fix the request rate, so latency is what we measure (no coordinated omission)
function arrival(stages, startRate = 10) {
  return {
    executor: 'ramping-arrival-rate',
    startRate,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 2000,
    stages,
  };
}

const PROFILES = {
  smoke: { executor: 'constant-vus', vus: 5, duration: '1m' },
  load: arrival([
    { target: 150, duration: '2m' },
    { target: 150, duration: '10m' },
    { target: 0, duration: '30s' },
  ]),
  peak: arrival([
    { target: 600, duration: '2m' },
    { target: 600, duration: '10m' },
    { target: 0, duration: '30s' },
  ]),
  stress: arrival(
    [100, 250, 500, 1000, 1500, 2000, 3000, 4000, 5000].flatMap((rps) => [
      { target: rps, duration: '30s' },
      { target: rps, duration: '1m30s' },
    ]),
    100
  ),
  spike: arrival(
    [
      { target: 100, duration: '1m' },
      { target: 2000, duration: '10s' },
      { target: 2000, duration: '1m' },
      { target: 100, duration: '10s' },
      { target: 100, duration: '3m' },
    ],
    100
  ),
  soak: arrival([
    { target: 300, duration: '2m' },
    { target: 300, duration: '60m' },
    { target: 0, duration: '30s' },
  ]),
};

if (!PROFILES[PROFILE]) {
  throw new Error(`Unknown PROFILE "${PROFILE}". Use one of: ${Object.keys(PROFILES).join(', ')}`);
}

// stress/spike are meant to break things: stop once errors are clearly runaway instead of hammering a dead app
const abortOnErrors = PROFILE === 'stress' || PROFILE === 'spike';

export const options = {
  scenarios: { [PROFILE]: PROFILES[PROFILE] },
  maxRedirects: 0, // measure our 302, never fetch the target site
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.001', abortOnFail: abortOnErrors, delayAbortEval: '30s' }],
    http_req_duration: ['p(95)<50', 'p(99)<150'],
    'http_req_duration{kind:hit}': ['p(95)<50'],
    checks: ['rate>0.999'],
  },
};

// 302 and 404 are both correct answers here; 429/5xx/timeouts count as failures
http.setResponseCallback(http.expectedStatuses(302, 404));

export default function () {
  const { code, exists } = pickCode();
  const res = http.get(`${BASE_URL}/${code}`, {
    tags: { name: 'GET /{shortCode}', kind: exists ? 'hit' : 'miss' },
  });

  if (exists) {
    check(res, {
      'redirect 302': (r) => r.status === 302,
      'has Location': (r) => !!r.headers['Location'],
    });
  } else {
    check(res, { 'unknown code 404': (r) => r.status === 404 });
  }
}
