# Pet Curation Frontend

Next.js App Router와 TypeScript로 구성한 고객·관리자 웹 프론트엔드다. 핵심 업무 데이터와 인증은 Spring Boot의 `/api/v1` API를 사용하며 프론트에서 DB에 직접 접근하지 않는다.

## 로컬 실행

1. `.env.example`을 `.env.local`로 복사하고 Spring Boot API 주소를 확인한다.
2. `npm install`
3. `npm run dev`

기본 주소는 다음과 같다.

- Web: `http://localhost:3000`
- API: `http://localhost:8080/api/v1`

API가 실행되지 않아도 프론트 빌드는 성공하며, 홈 상품 영역에서 연결 오류와 재시도 UI를 표시한다.

## 검증 명령

- `npm run lint`
- `npm run typecheck`
- `npm test`
- `npm run build`

## 현재 첫 연결 계약

홈은 `GET /api/v1/catalog/products?page=0&size=8`을 호출한다. 확정된 `{ data, page }` 응답을 우선 사용하고, 초기 통합 중에는 Spring Page의 `content`와 일반 페이지 응답의 `items`도 프론트 경계에서 정규화한다. OpenAPI가 확정되면 생성 타입으로 교체한다.
