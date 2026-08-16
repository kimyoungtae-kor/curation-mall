export type UuidFactory = () => string;

function browserUuid() {
  if (typeof globalThis.crypto?.randomUUID !== "function") {
    throw new Error("안전한 주문 요청 키를 생성할 수 없습니다.");
  }
  return globalThis.crypto.randomUUID();
}

export class IdempotencyKeyManager {
  private current: { fingerprint: string; key: string } | null = null;

  constructor(private readonly createUuid: UuidFactory = browserUuid) {}

  keyFor(fingerprint: string) {
    if (this.current?.fingerprint === fingerprint) return this.current.key;
    const key = this.createUuid();
    this.current = { fingerprint, key };
    return key;
  }

  clear() {
    this.current = null;
  }
}

export function requestFingerprint(value: unknown) {
  return JSON.stringify(value);
}
