import { SharedArray } from 'k6/data';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// loaded once and shared read-only between VUs
const codes = new SharedArray('shortcodes', () =>
  open('../../data/shortcodes.csv').split('\n').filter((c) => c.length > 0)
);

const HOT_SET = Math.max(1, Math.floor(codes.length * 0.2));

// traffic mix: 80% hot 20% of codes, 18% long tail, 2% unknown codes (404 path)
export function pickCode() {
  const r = Math.random();
  if (r < 0.02) {
    return { code: `missing${Math.floor(Math.random() * 1e9)}`, exists: false };
  }
  if (r < 0.82) {
    return { code: codes[Math.floor(Math.random() * HOT_SET)], exists: true };
  }
  return { code: codes[HOT_SET + Math.floor(Math.random() * (codes.length - HOT_SET))], exists: true };
}

export const codeCount = codes.length;
