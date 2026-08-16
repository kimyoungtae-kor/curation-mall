# `/api/v1` API 계약

기준일: 2026-08-13
상태: 현재 구현 계약. `Later`로 표시한 경로는 아직 제공하지 않음

## 1. 목적과 적용 범위

이 문서는 Next.js 고객·관리자 웹과 Java Spring Boot 백엔드 사이의 현재 MVP API 계약을 정의한다. 컨트롤러·DTO·통합 테스트와 충돌하면 구현을 우선 확인하고 이 문서를 함께 고친다. 자동 생성 OpenAPI/Swagger는 아직 추가하지 않았다.

- 제품 범위: `docs/01_PRODUCT_SCOPE.md`
- 화면·사용자 흐름: `docs/02_SCREEN_MAP.md`
- 인증·배포 경계: `docs/03_ARCHITECTURE.md`
- 테이블·무결성·상태: `docs/04_DATABASE.md`
- 확정 기본값과 실행 순서: `docs/07_EXECUTION_PLAYBOOK.md`

MVP의 모든 업무 API는 `/api/v1` 아래에 둔다. 이 문서에서 경로 앞의 `/api/v1`은 생략하지 않는다. 실제 구현에서 호환성을 깨는 변경이 필요하면 기존 응답 필드를 조용히 재해석하지 않고 `/api/v2` 또는 명시적인 마이그레이션 기간을 사용한다.

## 2. 공통 규칙

### 2.1 전송 형식

- 요청·응답 본문은 특별한 설명이 없으면 `application/json; charset=UTF-8`이다.
- JSON 필드명은 `camelCase`를 사용한다.
- 식별자는 JSON 문자열로 전달한다. 내부 UUID를 사용하더라도 숫자로 변환하지 않는다.
- 날짜·시간은 타임존을 포함한 ISO 8601 문자열을 사용한다. 예: `2026-08-12T09:30:00+09:00`.
- 금액은 KRW 원 단위 정수다. 소수와 통화 기호를 보내지 않는다. 예: `32900`.
- 수량은 정수이며 상품 옵션당 기본 최대값은 `10`이다.
- 전화번호·우편번호·SKU·주문번호는 숫자처럼 보여도 문자열이다.
- 공개 URL에 데이터베이스 순차 증가 키를 사용하지 않는다.
- 응답에 JPA Entity, 비밀번호 해시, 방문자 토큰 해시, 원가, 결제 비밀키를 노출하지 않는다.

### 2.2 공통 헤더

| 헤더 | 방향 | 규칙 |
|---|---|---|
| `Content-Type` | 양방향 | JSON 요청은 `application/json` |
| `Accept` | 요청 | `application/json` 권장 |
| `X-XSRF-TOKEN` | 요청 | 상태 변경 요청의 CSRF 토큰 |
| `Idempotency-Key` | 요청 | 주문 생성·결제 확인에서 필수인 UUID 형식 키 |
| `X-Request-Id` | 요청 | 오류 응답의 `traceId`에 사용하며 누락 시 서버가 오류 본문용 값을 생성. 응답 헤더 전파 필터는 Later |
| `Idempotent-Replayed` | 응답 | 주문 생성 재요청으로 기존 결과를 반환하면 `true`. 결제 확인은 본문 상태만 재사용 |
| `Cache-Control` | 응답 | 인증·장바구니·주문·관리자 응답은 `no-store` |

브라우저 API 클라이언트는 세션과 방문자 쿠키 전송을 위해 항상 `credentials: "include"`를 사용한다.

### 2.3 성공 응답

단일 리소스와 명령 결과는 `data`로 감싼다.

```json
{
  "data": {
    "id": "9361de90-3bcc-47ba-b13e-2409180278f1",
    "name": "데모 상품"
  }
}
```

삭제·로그아웃처럼 반환할 데이터가 없으면 `204 No Content`를 사용한다. 파일 응답이나 결제사 웹훅 응답에는 `data` 포장을 강제하지 않는다.

### 2.4 페이지네이션

페이지 목록은 0부터 시작하는 페이지 번호를 사용한다.

| Query | 기본값 | 제한 | 설명 |
|---|---:|---:|---|
| `page` | `0` | `0` 이상 | 페이지 번호 |
| `size` | `20` | `1~100` | 한 페이지 크기 |
| `sort` | API별 기본값 | 허용 필드만 | `field,asc` 또는 `field,desc` |

허용되지 않은 정렬 필드나 `size > 100`은 조용히 무시하지 않고 `400`으로 거절한다. 목록 응답은 다음 형식을 사용한다.

```json
{
  "data": [
    {
      "id": "e3d66cd4-dad1-4cda-a181-da7910c6219c",
      "name": "포레스트 세라믹 보울"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 24,
    "totalPages": 2,
    "first": true,
    "last": false
  }
}
```

`catalog/brands`, `catalog/categories`, `catalog/species`처럼 MVP에서 항목 수가 작고 전체 목록이 화면에 필요한 API는 페이지네이션 없이 배열을 반환할 수 있다.

### 2.5 오류 응답

오류는 `application/problem+json`과 RFC Problem Details 형태를 사용한다. 내부 예외 메시지, SQL, 스택 트레이스는 노출하지 않는다.

```json
{
  "type": "https://pet-curation-mall.example/problems/validation-error",
  "title": "입력값을 확인해 주세요.",
  "status": 400,
  "detail": "요청에 올바르지 않은 필드가 있습니다.",
  "instance": "/api/v1/auth/signup",
  "code": "VALIDATION_ERROR",
  "traceId": "01J5A8TR2S6E7TV7P4M1N0B3ZQ",
  "fieldErrors": [
    {
      "field": "email",
      "code": "INVALID_EMAIL",
      "message": "올바른 이메일 주소를 입력해 주세요."
    }
  ]
}
```

| HTTP | 사용 기준 | 대표 코드 |
|---:|---|---|
| `400` | JSON 문법, 타입, 필수값, 필드 검증 오류 | `INVALID_REQUEST`, `VALIDATION_ERROR` |
| `401` | 로그인 필요, 세션 만료, 웹훅 서명 불일치 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| `403` | 권한 부족, CSRF 실패 | `ACCESS_DENIED`, `CSRF_INVALID` |
| `404` | 없거나 공개·소유권상 보여줄 수 없는 리소스 | `RESOURCE_NOT_FOUND` |
| `409` | 중복, 현재 상태·버전·재고·멱등성 충돌 | `EMAIL_ALREADY_EXISTS`, `STOCK_CONFLICT`, `IDEMPOTENCY_CONFLICT` |
| `422` | 향후 세분화할 업무 규칙 오류 | 현재 핵심 충돌은 주로 `409` 사용 |
| `429` | 로그인·비회원 주문 조회 등 시도 제한 | Later: `RATE_LIMITED` |
| `500` | 공개할 수 없는 내부 오류 | `INTERNAL_ERROR` |
| `503` | DB·결제사 등 일시적 외부 장애 | `SERVICE_UNAVAILABLE`, `PAYMENT_PROVIDER_UNAVAILABLE` |

다른 회원 주문처럼 존재 여부 자체를 감춰야 하는 리소스는 `403` 대신 `404`를 반환한다.

### 2.6 대표 공통 오류 코드

| 코드 | 의미 |
|---|---|
| `INVALID_REQUEST` | JSON 또는 query 형식 오류 |
| `VALIDATION_ERROR` | 필드 단위 입력 오류 |
| `AUTHENTICATION_REQUIRED` | 회원 세션 필요 |
| `INVALID_CREDENTIALS` | 로그인 정보 불일치 |
| `ACCESS_DENIED` | 역할 또는 소유권 부족 |
| `ADMIN_GUEST_MERGE_FORBIDDEN` | 관리자 세션의 방문자 장바구니 병합 차단 |
| `CSRF_INVALID` | 누락·불일치한 CSRF 토큰 |
| `RESOURCE_NOT_FOUND` | 리소스 없음 또는 비공개 |
| `OPTIMISTIC_LOCK_CONFLICT` | 관리자 수정 중 버전 충돌 |
| `QUANTITY_LIMIT_EXCEEDED` | 옵션별 최대 10개 초과 |
| `PRODUCT_UNAVAILABLE` | 숨김·판매 종료 상품 |
| `STOCK_CONFLICT` | 품절 또는 현재 재고 부족 |
| `PRICE_CHANGED` | 장바구니 이후 가격 변경 |
| `IDEMPOTENCY_KEY_REQUIRED` | 멱등성 키 누락 |
| `IDEMPOTENCY_CONFLICT` | 같은 키를 다른 요청에 재사용 |
| `CART_CHANGED` | 주문 생성 중 장바구니 항목이 이미 소비되거나 변경됨 |
| `ORDER_RESERVATION_EXPIRED` | 결제 대기 예약 만료 |
| `ORDER_TRANSITION_NOT_ALLOWED` | 허용되지 않은 주문 상태 전이 |
| `PAYMENT_AMOUNT_MISMATCH` | 주문 총액과 결제 금액 불일치 |
| `PAYMENT_RESULT_UNKNOWN` | 결제사 결과 확인 필요 |
| `GUEST_ORDER_VERIFICATION_FAILED` | 비회원 주문 검증 실패 |

## 3. 인증·세션·CSRF

### 3.1 쿠키

| 쿠키 | 용도 | JavaScript 접근 | 기본 정책 |
|---|---|---:|---|
| `SESSION` | Spring Session JDBC 회원 세션 | 불가 | `HttpOnly`, `SameSite=Lax`, stage/prod `Secure` |
| `PET_VISITOR` | 비회원 장바구니 소유권 | 불가 | `HttpOnly`, `SameSite=Lax`, stage/prod `Secure` |
| `XSRF-TOKEN` | CSRF 토큰 전달 | 가능 | 인증 수단이 아니며 헤더로 반송 |

프론트와 API가 다른 사이트에 배포되면 검증 후 `SameSite=None; Secure`를 사용할 수 있지만, 동일 출처 배포가 우선이다. `PET_VISITOR` 원문은 DB나 로그에 저장하지 않고 서버는 해시만 저장한다. 이 쿠키를 로그인이나 비회원 주문 조회 토큰으로 재사용하지 않는다.

### 3.2 CSRF 흐름

1. 앱 시작 시 `GET /api/v1/auth/csrf`를 호출한다.
2. 서버가 `XSRF-TOKEN` 쿠키와 응답 본문의 토큰을 제공한다.
3. `POST`, `PUT`, `PATCH`, `DELETE` 요청에서 값을 `X-XSRF-TOKEN` 헤더로 보낸다.
4. 로그인·회원가입·로그아웃 후 토큰을 다시 조회한다.
5. `/api/v1/payments/webhooks/**`는 향후 결제사 서명 검증을 위해 보안 설정상 CSRF 예외 경로로 예약돼 있지만 현재 컨트롤러는 없어 `404`다. 실PG 웹훅을 추가할 때 서명 검증을 먼저 구현한다.

#### `GET /api/v1/auth/csrf`

권한: 공개  
응답: `200`, `Cache-Control: no-store`

```json
{
  "data": {
    "headerName": "X-XSRF-TOKEN",
    "token": "a7f5c3c0-92ec-44fa-a9c5-731a56c81ba1"
  }
}
```

### 3.3 현재 인증 상태

#### `GET /api/v1/auth/me`

권한: 공개. 비로그인도 `200`을 반환한다.

```json
{
  "data": {
    "authenticated": true,
    "user": {
      "id": "11179d20-00f7-4e3e-9946-0491c8415655",
      "email": "demo@example.com",
      "name": "김데모",
      "phone": "01012345678",
      "roles": ["CUSTOMER"]
    },
    "cartCount": 2,
    "wishlistCount": 3
  }
}
```

비로그인일 때 `user`는 `null`, `cartCount`는 현재 방문자 기준이고 `wishlistCount`는 `0`이다.

### 3.4 회원가입

#### `POST /api/v1/auth/signup`

권한: 공개, CSRF 필수  
성공: `201 Created`  
정책: 이메일 최대 100자·중복 불가, 국내 휴대전화 형식(`01` 계열 10~11자리), 필수 약관 동의, 성공 시 회원 세션 생성. 방문자 데이터는 새 회원에게 병합하지 않고 활성 방문자 장바구니를 만료하며 과거 방문자 찜을 정리한다.

```json
{
  "email": "demo@example.com",
  "password": "DemoPassword123!",
  "name": "김데모",
  "phone": "01012345678",
  "requiredTermsAccepted": true
}
```

```json
{
  "data": {
    "authenticated": true,
    "user": {
      "id": "11179d20-00f7-4e3e-9946-0491c8415655",
      "email": "demo@example.com",
      "name": "김데모",
      "phone": "01012345678",
      "roles": ["CUSTOMER"]
    },
    "mergeResult": {
      "merged": false,
      "cartItemCount": 0,
      "wishlistCount": 0,
      "adjustments": []
    }
  }
}
```

회원가입 성공 뒤 세션 고정 방지를 적용하고 프론트는 CSRF 토큰을 다시 조회한다. 같은 이메일은 `409 EMAIL_ALREADY_EXISTS`다. 휴대전화 형식이 잘못되면 `400 VALIDATION_ERROR`와 `fieldErrors[{"field":"phone","message":"휴대전화 번호가 올바르지 않습니다."}]`를 반환한다.

### 3.5 로그인·로그아웃

#### `POST /api/v1/auth/login`

권한: 공개, CSRF 필수  
성공: `200`, 세션 ID 교체, 기존 `CUSTOMER` 계정에 방문자 장바구니 병합

```json
{
  "email": "demo@example.com",
  "password": "DemoPassword123!"
}
```

성공 응답은 회원가입 응답의 `data`와 같은 형태다. 기존 `CUSTOMER` 로그인은 방문자 장바구니만 병합하고 방문자 찜은 병합하지 않는다. 이메일은 최대 100자다. 자격 증명 실패는 이메일 존재 여부를 구분하지 않고 `401 INVALID_CREDENTIALS`를 반환한다. `ADMIN` 로그인은 방문자 장바구니를 병합하거나 방문자 카트를 소비하지 않으며 빈 `mergeResult`를 반환한다.

로그인 화면의 `next` 값은 같은 출처의 `/`로 시작하는 내부 경로만 허용한다. `//`, 원문 역슬래시 또는 URL 디코딩 뒤 역슬래시·이중 슬래시가 생기는 값은 홈(`/`)으로 대체한다.

#### `POST /api/v1/auth/logout`

권한: 회원, CSRF 필수  
응답: `204 No Content`

로그아웃은 회원 세션만 종료하며 과거 회원 장바구니·찜을 삭제하지 않는다.

#### `POST /api/v1/auth/merge-guest-data`

권한: 회원, CSRF 필수  
목적: 로그인 성공 시 자동 병합이 네트워크 오류로 완료되지 않았을 때의 명시적 재시도  
응답: `200`, 회원가입 응답의 `mergeResult` 형식

이 API는 멱등적이다. 이미 병합된 방문자 카트는 다시 더하지 않는다. 관리자 세션은 `403 ADMIN_GUEST_MERGE_FORBIDDEN`이며 방문자 장바구니를 변경하지 않는다.

## 4. 헬스·공개 홈·카탈로그·기획전

### 4.1 엔드포인트 목록

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/health` | 외부 스모크용 최소 상태 |
| `GET` | `/api/v1/home` | 홈 7개 고정 섹션 데이터 |
| `GET` | `/api/v1/catalog/products` | 공개 상품 목록 |
| `GET` | `/api/v1/catalog/products/{slug}` | 공개 상품 상세 |
| `GET` | `/api/v1/collections` | 공개 기획전 목록 |
| `GET` | `/api/v1/collections/{slug}` | 공개 기획전 상세·연결 상품 |

공개 API는 `PUBLISHED` 상태와 공개 기간을 만족하는 데이터만 반환한다. 숨김·임시저장·판매종료 상품의 상세는 존재 여부를 드러내지 않고 `404`를 반환한다.

브랜드·카테고리·동물 종은 현재 홈·상품 응답에 필요한 요약 정보가 포함되며 공개 전용 목록 API는 아직 구현하지 않았다. 관리자 상품 편집용 참조 목록만 `GET /api/v1/admin/brands`, `/categories`, `/species`로 제공한다. 공개 전용 API가 필요해지면 컨트롤러와 통합 테스트를 먼저 추가한 뒤 이 계약에 등록한다.

### 4.2 헬스 체크

#### `GET /api/v1/health`

```json
{
  "data": {
    "status": "UP",
    "database": "UP",
    "timestamp": "2026-08-12T09:30:00+09:00"
  }
}
```

공개 응답에는 DB 주소, 버전, 환경 변수, 상세 예외를 포함하지 않는다. 필수 의존성이 준비되지 않았으면 `503`을 반환한다.

### 4.3 홈

#### `GET /api/v1/home`

홈은 화면 순서가 정해진 7개 섹션을 반환한다. 히어로는 최대 3개다.

```json
{
  "data": {
    "announcement": {
      "text": "5만원 이상 무료배송",
      "link": { "type": "HELP", "value": "shipping-returns" }
    },
    "heroSlides": [
      {
        "id": "29d66b8c-e33e-4f55-ad24-67a5c6922c21",
        "title": "시원한 한 모금의 습관",
        "description": "여름철 음수 아이템을 만나보세요.",
        "image": {
          "url": "/media/hero/summer-water.webp",
          "alt": "밝은 거실에 놓인 반려동물 물그릇"
        },
        "link": { "type": "COLLECTION", "value": "summer-hydration" },
        "sortOrder": 1
      }
    ],
    "featuredCollections": [],
    "popularProducts": [],
    "newProducts": [],
    "explore": {
      "species": [],
      "categories": [],
      "brands": []
    },
    "lifestyleContents": [],
    "serviceGuide": {
      "shippingFee": 3000,
      "freeShippingThreshold": 50000,
      "links": ["shipping-returns", "terms", "privacy"]
    }
  }
}
```

`popularProducts`와 `newProducts`의 항목은 아래 `ProductCard` 형식을 사용한다.

### 4.4 상품 목록

#### `GET /api/v1/catalog/products`

지원 query:

| Query | 설명 |
|---|---|
| `q` | 상품명·브랜드명 기본 검색 |
| `brand` | 브랜드 slug |
| `category` | 카테고리 slug |
| `species` | 동물 종 slug |
| `inStock` | `true`이면 구매 가능한 옵션이 있는 상품만 |
| `page`, `size` | 공통 페이지네이션 |
| `sort` | `newest,desc`, `price,asc`, `price,desc`, `name,asc` 중 하나 |

대표 `ProductCard`:

```json
{
  "id": "e3d66cd4-dad1-4cda-a181-da7910c6219c",
  "slug": "forest-ceramic-bowl",
  "brand": {
    "id": "acfc1194-1b6d-41de-9356-32b66771937f",
    "slug": "mellow-tail",
    "name": "멜로우테일"
  },
  "name": "포레스트 세라믹 보울",
  "summary": "공간에 자연스럽게 어울리는 낮은 식기",
  "thumbnail": {
    "url": "/media/products/forest-bowl-main.webp",
    "alt": "연두색 세라믹 반려동물 식기"
  },
  "listPrice": 36000,
  "salePrice": 32900,
  "currency": "KRW",
  "inStock": true,
  "wishlisted": false
}
```

### 4.5 상품 상세

#### `GET /api/v1/catalog/products/{slug}`

```json
{
  "data": {
    "id": "e3d66cd4-dad1-4cda-a181-da7910c6219c",
    "slug": "forest-ceramic-bowl",
    "brand": {
      "id": "acfc1194-1b6d-41de-9356-32b66771937f",
      "slug": "mellow-tail",
      "name": "멜로우테일"
    },
    "name": "포레스트 세라믹 보울",
    "summary": "공간에 자연스럽게 어울리는 낮은 식기",
    "description": "데모용 상품 설명입니다.",
    "images": [
      {
        "url": "/media/products/forest-bowl-main.webp",
        "alt": "연두색 세라믹 반려동물 식기",
        "sortOrder": 1
      }
    ],
    "species": [
      { "slug": "dog", "name": "강아지" },
      { "slug": "cat", "name": "고양이" }
    ],
    "categories": [
      { "slug": "feeding", "name": "식기·음수" }
    ],
    "attributes": {
      "material": "세라믹",
      "diameterCm": 14
    },
    "variants": [
      {
        "id": "b34067dd-a808-4a2b-bef2-8f97e96026ae",
        "sku": "DEMO-BOWL-GREEN-M",
        "optionLabel": "그린 / M",
        "listPrice": 36000,
        "salePrice": 32900,
        "stockQuantity": 8,
        "purchasable": true,
        "maxPurchaseQuantity": 8
      }
    ],
    "wishlisted": false
  }
}
```

`maxPurchaseQuantity`는 `min(현재 재고, 옵션별 최대 구매 10)`이다. 공개 응답의 재고는 MVP에서 실제 수량을 제공하지만 향후 `IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK` 형태로 축소할 수 있다.

### 4.6 기획전

#### `GET /api/v1/collections`

공통 페이지네이션을 사용하며 `sort=sortOrder,asc`가 기본이다.

#### `GET /api/v1/collections/{slug}`

```json
{
  "data": {
    "id": "4c07acd9-1786-4abd-b56f-58f4cdccba50",
    "slug": "summer-hydration",
    "title": "여름철 반려동물 음수량 늘리기",
    "description": "마시는 습관을 돕는 데모 아이템을 모았습니다.",
    "heroImage": {
      "url": "/media/collections/summer-hydration.webp",
      "alt": "여름 거실의 반려동물 음수 공간"
    },
    "products": [],
    "publishedAt": "2026-08-12T09:00:00+09:00"
  }
}
```

연결 상품 중 숨김·판매종료 상품은 공개 응답에서 제외한다.

## 5. 방문자 장바구니·회원 찜·병합

### 5.1 소유권

- 로그인 회원 요청은 회원의 활성 장바구니와 찜을 사용한다.
- 비로그인 장바구니 요청은 `PET_VISITOR` 쿠키의 방문자 데이터를 사용한다.
- 쿠키가 없는 상태에서 장바구니를 처음 조회하거나 변경하면 방문자 쿠키를 발급할 수 있다.
- 클라이언트가 `userId`, `visitorId`, `cartId`를 보내 소유권을 선택할 수 없다.
- 다른 소유자의 item ID를 사용한 요청은 `404`다.

### 5.2 장바구니 엔드포인트

| Method | Path | 설명 | 성공 |
|---|---|---|---|
| `GET` | `/api/v1/cart` | 현재 소유자의 활성 장바구니 | `200` |
| `POST` | `/api/v1/cart/items` | 옵션 추가 또는 동일 옵션 수량 합산 | `200` |
| `PATCH` | `/api/v1/cart/items/{itemId}` | 절대 수량으로 변경 | `200` |
| `DELETE` | `/api/v1/cart/items/{itemId}` | 항목 삭제 | `200` |

추가 요청:

```json
{
  "variantId": "b34067dd-a808-4a2b-bef2-8f97e96026ae",
  "quantity": 2
}
```

수량 변경 요청:

```json
{
  "quantity": 3
}
```

모든 장바구니 변경 응답은 최신 장바구니 전체를 반환한다.

```json
{
  "data": {
    "id": "2224f642-d848-4621-bf19-970a36a3ab3b",
    "status": "ACTIVE",
    "items": [
      {
        "id": "05a15437-dd1f-46cd-8263-966257335d4d",
        "product": {
          "slug": "forest-ceramic-bowl",
          "brandName": "멜로우테일",
          "name": "포레스트 세라믹 보울",
          "thumbnailUrl": "/media/products/forest-bowl-main.webp"
        },
        "variantId": "b34067dd-a808-4a2b-bef2-8f97e96026ae",
        "sku": "DEMO-BOWL-GREEN-M",
        "optionLabel": "그린 / M",
        "quantity": 2,
        "unitPriceAtAdd": 32900,
        "currentUnitPrice": 32900,
        "lineAmount": 65800,
        "availability": "AVAILABLE",
        "priceChanged": false,
        "maxPurchaseQuantity": 8
      }
    ],
    "itemsAmount": 65800,
    "shippingAmountEstimate": 0,
    "totalAmountEstimate": 65800,
    "itemCount": 2,
    "updatedAt": "2026-08-12T10:00:00+09:00"
  }
}
```

`availability` 값은 `AVAILABLE`, `PRICE_CHANGED`, `OUT_OF_STOCK`, `UNAVAILABLE`이다. 장바구니 합계는 화면 안내용이며 주문 금액의 확정 근거가 아니다.

### 5.3 찜 엔드포인트

권한: `CUSTOMER` 회원 전용. 비로그인은 `401 AUTHENTICATION_REQUIRED`, `ADMIN` 등 비고객 역할은 `403 ACCESS_DENIED`다. 비회원 상품 상세는 찜 버튼 대신 로그인 안내를 표시한다.

| Method | Path | 설명 | 성공 |
|---|---|---|---|
| `GET` | `/api/v1/wishlist` | 현재 회원의 찜 목록, 페이지네이션 | `200` |
| `POST` | `/api/v1/wishlist/{productId}` | 상품 찜, 이미 있으면 그대로 성공 | `200` |
| `DELETE` | `/api/v1/wishlist/{productId}` | 찜 해제, 이미 없어도 성공 | `204` |

찜은 상품 단위다. 옵션은 장바구니에서 선택한다.

```json
{
  "data": {
    "productId": "e3d66cd4-dad1-4cda-a181-da7910c6219c",
    "wishlisted": true,
    "wishlistCount": 3
  }
}
```

### 5.4 기존 고객 로그인 장바구니 병합

기존 `CUSTOMER` 로그인 시 자동 병합과 명시적 재시도 결과는 다음 형식이다. 신규 회원가입 응답도 같은 `mergeResult` 구조를 사용하지만 `merged=false`, `cartItemCount=0`, `wishlistCount=0`, `adjustments=[]`로 시작한다.

```json
{
  "merged": true,
  "cartItemCount": 2,
  "wishlistCount": 3,
  "adjustments": [
    {
      "variantId": "b34067dd-a808-4a2b-bef2-8f97e96026ae",
      "beforeMemberQuantity": 7,
      "beforeVisitorQuantity": 5,
      "mergedQuantity": 8,
      "reason": "STOCK_LIMIT"
    }
  ]
}
```

조정 이유는 `STOCK_LIMIT`, `PURCHASE_LIMIT`, `VARIANT_UNAVAILABLE`이다. 방문자 활성 카트는 기존 고객 로그인 병합 후 `MERGED`가 되고 같은 요청을 다시 처리해도 회원 수량을 다시 늘리지 않는다. 방문자 찜은 병합하지 않으며 `wishlistCount`는 회원 계정의 현재 찜 수만 보고한다. 병합 중에는 가격을 확정하지 않는다.

## 6. 주문 견적·생성·조회

### 6.1 주문 원칙

- 견적은 재고를 예약하지 않으며 주문 생성 시 모든 값을 다시 검증한다.
- 요청에 상품 가격·할인·배송비·총액을 받지 않는다.
- 기본 배송비는 `3000`, 상품 합계 `50000` 이상은 무료배송이다.
- 할인·쿠폰은 MVP에 없다.
- 주문 생성 시 기본 20분 재고 예약을 만든다.
- 회원·비회원 모두 구매자·수령자·주소·상품·옵션·가격을 스냅샷으로 저장한다.
- 관리자 역할로 로그인한 세션은 고객 주문을 생성할 수 없다.
- 주문 생성은 선택한 장바구니 행을 정렬해 잠그고 주문 뒤 삭제 건수를 검증한다. 같은 장바구니 행을 동시에 주문하면 한 요청만 성공하고 다른 요청은 `409 CART_CHANGED` 계열 충돌로 끝난다.

### 6.2 견적

#### `POST /api/v1/orders/quote`

권한: 방문자 또는 회원, CSRF 필수  
재고 예약: 하지 않음

```json
{
  "orderType": "GUEST",
  "cartItemIds": [
    "05a15437-dd1f-46cd-8263-966257335d4d"
  ]
}
```

`orderType`은 `MEMBER` 또는 `GUEST`다. `MEMBER`는 회원 세션이 필요하다. 회원이 명시적으로 `GUEST`를 선택하면 주문에 `userId`를 연결하지 않는다.

```json
{
  "data": {
    "orderType": "GUEST",
    "lines": [
      {
        "cartItemId": "05a15437-dd1f-46cd-8263-966257335d4d",
        "variantId": "b34067dd-a808-4a2b-bef2-8f97e96026ae",
        "productName": "포레스트 세라믹 보울",
        "optionLabel": "그린 / M",
        "quantity": 2,
        "unitPrice": 32900,
        "lineAmount": 65800,
        "availability": "AVAILABLE"
      }
    ],
    "itemsAmount": 65800,
    "discountAmount": 0,
    "shippingAmount": 0,
    "totalAmount": 65800,
    "currency": "KRW",
    "warnings": [],
    "quotedAt": "2026-08-12T10:10:00+09:00"
  }
}
```

가격이 변경됐으면 최신 금액과 `PRICE_CHANGED` 경고를 반환할 수 있지만, 품절·판매종료 항목이 하나라도 있으면 `409 STOCK_CONFLICT` 또는 `409 PRODUCT_UNAVAILABLE`로 주문 진행을 막는다.

### 6.3 주문 생성

#### `POST /api/v1/orders`

권한: 방문자 또는 회원, CSRF 필수  
필수 헤더: `Idempotency-Key`  
첫 생성: `201 Created`  
같은 키·같은 요청 재시도: `200`, `Idempotent-Replayed: true`

```json
{
  "orderType": "GUEST",
  "cartItemIds": [
    "05a15437-dd1f-46cd-8263-966257335d4d"
  ],
  "buyer": {
    "name": "박테스트",
    "email": "guest@example.com",
    "phone": "01098765432"
  },
  "shipping": {
    "recipientName": "박테스트",
    "recipientPhone": "01098765432",
    "postalCode": "06234",
    "address1": "서울특별시 데모구 테스트로 10",
    "address2": "101호",
    "deliveryMessage": "문 앞에 놓아 주세요."
  },
  "agreements": {
    "purchaseTermsAccepted": true,
    "privacyCollectionAccepted": true
  }
}
```

회원 주문도 주문 당시 구매자·배송 정보를 요청으로 받고 스냅샷으로 저장한다. 프로필과 주소록은 기본값을 채우는 용도일 뿐 주문 데이터의 대체물이 아니다.

```json
{
  "data": {
    "replayed": false,
    "order": {
      "orderNumber": "P20260812-7K9M4Q2X",
      "orderType": "GUEST",
      "orderStatus": "PENDING_PAYMENT",
      "paymentStatus": "READY",
      "itemsAmount": 65800,
      "discountAmount": 0,
      "shippingAmount": 0,
      "totalAmount": 65800,
      "currency": "KRW",
      "reservationExpiresAt": "2026-08-12T10:30:00+09:00",
      "createdAt": "2026-08-12T10:10:00+09:00"
    },
    "payment": {
      "paymentAttemptId": "8958fa7a-d0c4-479c-9138-31365be44b40",
      "provider": "SIMULATED",
      "amount": 65800,
      "status": "READY"
    },
    "guestLookupToken": "t2yk6Fz-TkDeA5uHxoQfVw"
  }
}
```

`guestLookupToken`은 비회원 주문의 첫 생성과 같은 키·같은 요청의 멱등 재응답에 동일하게 존재하며, 회원 주문에서는 `null`이다. 서버는 32바이트 이상의 배포별 `GUEST_LOOKUP_TOKEN_SECRET`과 변경되지 않는 주문번호·주문 멱등성 키·요청 해시를 HMAC-SHA256으로 결합해 원문을 결정 생성하고 DB에는 그 원문의 SHA-256 해시만 저장한다. 프론트는 토큰을 URL·로그·분석 이벤트에 넣지 않고 주문 완료 화면에서 안내한다. stage/prod는 비밀이 없으면 기동하지 않으며, 배포 중 값을 변경하면 기존 주문의 멱등 재응답 토큰을 재현할 수 없으므로 안정적으로 유지한다. local/test seed의 고정 조회 토큰은 기존 해시 검증 방식이라 이 생성 규칙의 영향을 받지 않는다.

멱등성 규칙:

- 키는 클라이언트가 작업 단위로 생성한 UUID다.
- 같은 키와 정규화된 같은 요청은 주문을 더 만들지 않고 기존 결과를 반환한다.
- 같은 키에 다른 장바구니·주소·주문유형을 사용하면 `409 IDEMPOTENCY_CONFLICT`다.
- 네트워크 오류 때문에 응답을 받지 못한 경우 새 키를 만들지 않고 같은 키로 재시도한다.

### 6.4 회원 주문 조회

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/orders` | 현재 회원 주문 목록, 페이지네이션 |
| `GET` | `/api/v1/orders/{orderNumber}` | 현재 회원 본인 주문 상세 |

회원 역할과 세션이 필수다. 타 회원 주문은 `404`다. 목록 기본 정렬은 `createdAt,desc`다.

대표 주문 상세:

```json
{
  "data": {
    "orderNumber": "P20260812-7K9M4Q2X",
    "orderType": "MEMBER",
    "orderStatus": "PAID",
    "paymentStatus": "APPROVED",
    "buyer": {
      "name": "김데모",
      "email": "demo@example.com",
      "phone": "01012345678"
    },
    "shipping": {
      "recipientName": "김데모",
      "recipientPhone": "01012345678",
      "postalCode": "06234",
      "address1": "서울특별시 데모구 테스트로 10",
      "address2": "101호",
      "deliveryMessage": null
    },
    "items": [
      {
        "productName": "포레스트 세라믹 보울",
        "brandName": "멜로우테일",
        "sku": "DEMO-BOWL-GREEN-M",
        "optionLabel": "그린 / M",
        "unitPrice": 32900,
        "quantity": 2,
        "lineAmount": 65800,
        "imageUrl": "/media/products/forest-bowl-main.webp"
      }
    ],
    "itemsAmount": 65800,
    "discountAmount": 0,
    "shippingAmount": 0,
    "totalAmount": 65800,
    "payments": [],
    "statusHistory": [],
    "orderedAt": "2026-08-12T10:10:00+09:00",
    "paidAt": "2026-08-12T10:12:00+09:00"
  }
}
```

이 응답의 상품·가격·주소는 현재 상품·회원 테이블 조인이 아니라 주문 스냅샷이다.

### 6.5 비회원 주문 조회

#### `POST /api/v1/guest-orders/lookup`

권한: 공개, CSRF 필수. 속도 제한은 아직 미구현
요청 본문에만 조회 정보를 보낸다.

```json
{
  "orderNumber": "P20260812-7K9M4Q2X",
  "guestLookupToken": "t2yk6Fz-TkDeA5uHxoQfVw"
}
```

성공 응답은 회원 주문 상세와 동일한 주문 스냅샷 형식이다. 실패 시 주문 존재 여부나 어느 값이 틀렸는지 구분하지 않고 `404 GUEST_ORDER_VERIFICATION_FAILED`를 반환한다. 반복 실패에 대한 `429` 레이트리밋은 배포 전 보안 과제다. 비회원 주문 상세 화면의 브라우저 경로에 주문번호를 사용할 수 있지만 조회 토큰을 query string이나 path에 넣지 않는다.

## 7. 테스트 결제

### 7.1 결제 확인

#### `POST /api/v1/payments/confirm`

권한: 주문 소유 회원 또는 비회원 조회 토큰 보유자, CSRF 필수  
필수 헤더: `Idempotency-Key`  
승인·시뮬레이션 실패: `200`

예약 만료: `409 ORDER_RESERVATION_EXPIRED`

현재 provider는 `SIMULATED`만 구현돼 있다. local/test는 활성화되고 stage는 `SIMULATED_PAYMENT_ENABLED` 설정을 사용하며, 공통·운영 기본값은 비활성화다.

```json
{
  "provider": "SIMULATED",
  "orderNumber": "P20260812-7K9M4Q2X",
  "simulationResult": "APPROVE",
  "amount": 65800,
  "guestLookupToken": "t2yk6Fz-TkDeA5uHxoQfVw"
}
```

`simulationResult`는 `APPROVE` 또는 `FAIL`이다. 회원 주문은 `guestLookupToken`을 보내지 않고, 비회원 주문은 반드시 보낸다. `amount`는 서버 DB 주문의 `totalAmount`와 일치해야 하며 다르면 `409 PAYMENT_AMOUNT_MISMATCH`다. `paymentKey` 필드는 DTO 호환을 위해 존재하지만 시뮬레이터에서는 사용하지 않는다.

승인 응답:

```json
{
  "data": {
    "orderNumber": "P20260812-7K9M4Q2X",
    "orderStatus": "PAID",
    "payment": {
      "paymentAttemptId": "8958fa7a-d0c4-479c-9138-31365be44b40",
      "provider": "SIMULATED",
      "method": "CARD",
      "status": "APPROVED",
      "amount": 65800,
      "approvedAt": "2026-08-12T10:12:00+09:00",
      "testPayment": true
    }
  }
}
```

결제 확인 멱등성 규칙은 주문 생성과 같다. 같은 키·같은 요청은 결제·주문·재고를 다시 변경하지 않고 저장된 결과를 반환하며, 같은 키에 다른 요청은 `409 IDEMPOTENCY_CONFLICT`다. 단, 예약 만료로 결제·주문이 `CANCELLED`되고 예약이 `EXPIRED`가 된 요청은 실패 코드와 상태 이력을 저장한 뒤 첫 요청과 같은 키 재요청에도 계속 `409 ORDER_RESERVATION_EXPIRED`를 반환한다. 재고와 이력은 한 번만 반영한다. 결제 응답에는 현재 `Idempotent-Replayed` 헤더를 별도로 붙이지 않는다.

### 7.2 실PG·웹훅 — Later

`POST /api/v1/payments/webhooks/{provider}`는 아직 구현된 API가 아니다. `payment_events` 테이블과 CSRF 예외 경로만 준비돼 있다. 실제 PG를 연결할 때 승인 조회·취소, provider 거래 키 중복 방지, 원문 서명 검증, 이벤트 멱등 처리, `UNKNOWN` 복구를 함께 구현한다.

## 8. 관리자 API

### 8.1 공통 권한과 동시 수정

- 모든 `/api/v1/admin/**` 요청은 `ADMIN` 역할을 서버에서 확인한다.
- 일반 회원은 관리자 화면 경로를 알아도 API를 호출할 수 없다.
- 모든 상태 변경 요청은 CSRF 검증 대상이다.
- 수정 요청에는 응답에서 받은 `version`을 포함한다.
- 다른 관리자가 먼저 수정했으면 `409 OPTIMISTIC_LOCK_CONFLICT`를 반환한다.
- 주문·결제·감사 데이터에는 물리 삭제 API를 제공하지 않는다.
- 현재 관리자 변경은 수행자, 대상, 행위, 요약, 시각을 감사 로그에 남긴다. request ID와 완전한 전후 diff는 Later다.

### 8.2 상품·분류·이미지

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/admin/products` | 상품 검색·상태 필터·페이지네이션 |
| `POST` | `/api/v1/admin/products` | 상품·초기 옵션 등록 |
| `GET` | `/api/v1/admin/products/{productId}` | 관리자 상품 상세 |
| `PUT` | `/api/v1/admin/products/{productId}` | 상품·옵션 전체 수정 |
| `PATCH` | `/api/v1/admin/products/{productId}/status` | 판매·공개 상태 변경 |
| `PATCH` | `/api/v1/admin/variants/{variantId}/stock` | 옵션 재고와 버전 수정 |
| `GET` | `/api/v1/admin/brands` | 기존 브랜드 참조 목록 |
| `GET` | `/api/v1/admin/categories` | 기존 카테고리 참조 목록 |
| `GET` | `/api/v1/admin/species` | 기존 동물 종 참조 목록 |

상품 목록 query는 현재 `q`, `status`, `page`, `size`를 지원한다. 내부 SKU와 전체 재고는 관리자 응답에 포함할 수 있지만 공급처 비밀정보는 별도 권한 설계 전까지 저장·노출하지 않는다.

상품 생성 예시:

```json
{
  "name": "포레스트 세라믹 보울",
  "slug": "forest-ceramic-bowl",
  "summary": "공간에 자연스럽게 어울리는 낮은 식기",
  "description": "데모용 상품 설명입니다.",
  "brandId": "acfc1194-1b6d-41de-9356-32b66771937f",
  "categoryIds": ["d4d68296-b0f1-4839-af53-38964a5e1c47"],
  "speciesIds": ["4e0829a6-f03a-4da9-8c97-d9e581869393"],
  "status": "DRAFT",
  "featured": true,
  "variants": [
    {
      "sku": "DEMO-BOWL-GREEN-M",
      "optionLabel": "그린 / M",
      "price": 32900,
      "stockQuantity": 8,
      "status": "ACTIVE",
      "sortOrder": 0
    }
  ],
  "images": [
    {
      "storageKey": "demo/catalog/oasis-water-bowl.webp",
      "alt": "포레스트 세라믹 보울",
      "sortOrder": 0
    }
  ]
}
```

생성 성공은 `201`, 수정 성공은 `200`이다. 상품 상태 변경 요청은 다음과 같다.

```json
{
  "status": "PUBLISHED",
  "version": 3
}
```

상품 상태는 `DRAFT`, `PUBLISHED`, `HIDDEN`, `DISCONTINUED`다. 주문 이력이 있는 상품·옵션은 물리 삭제하지 않는다.

상품 전체 수정의 최상위 `version`은 상품 행에 적용하고, 기존 `variants[]`의 각 항목은 응답에서 받은 `id`와 `version`을 함께 보내야 한다. 서버는 `id + productId + version`이 모두 일치할 때만 옵션을 갱신하며 오래된 version이나 다른 상품 옵션 ID는 `409 OPTIMISTIC_LOCK_CONFLICT`다. 새 옵션은 `id`와 `version`을 보내지 않는다. 별도 재고 PATCH도 옵션 `version`을 필수로 사용한다.

현재 상품 생성·수정은 이미 존재하는 `storageKey`, `alt`, `sortOrder` 메타데이터를 JSON으로 받는다. `multipart/form-data` 업로드·물리 삭제와 브랜드·카테고리·동물 종 생성·수정 API는 Later다.

### 8.3 기획전·홈 섹션

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/admin/home-sections` | 고정 홈 슬롯 전체 조회 |
| `PUT` | `/api/v1/admin/home-sections/{sectionId}` | 슬롯 콘텐츠·순서 수정 |
| `GET` | `/api/v1/admin/hero-slides` | 히어로 전체 조회 |
| `PUT` | `/api/v1/admin/hero-slides/{slideId}` | 히어로 콘텐츠·상태·순서 수정 |

홈 섹션 수정은 `title`, JSON 문자열인 `content`, `sortOrder`, `version`을 받는다. 모든 content는 JSON 객체여야 하고, `ANNOUNCEMENT_HEADER`는 비어 있지 않은 `announcementText`·유효한 `linkType`·`linkValue`, `SERVICE_GUIDE`는 0 이상의 정수 `shippingFee`·`freeShippingThreshold`와 문자열 배열 `links`를 요구한다. 검증 실패는 저장 없이 `400 VALIDATION_ERROR`다. 히어로 수정은 제목·설명·`imageStorageKey`·`imageAlt`·연결 유형/값·상태·순서·버전을 받는다.

관리자 기획전 CRUD와 연결 상품 편집은 Later다. 공개 기획전은 현재 local/test seed와 공개 조회 API로 제공한다.

향후 기획전 연결 상품 요청 예시:

```json
{
  "version": 2,
  "products": [
    {
      "productId": "e3d66cd4-dad1-4cda-a181-da7910c6219c",
      "sortOrder": 1
    }
  ]
}
```

홈 히어로 슬롯은 DB에서 1~3으로 제한한다. 현재 같은 순서를 가진 히어로끼리 직접 위치를 맞바꾸면 고유 제약 충돌이 날 수 있으므로 단일 슬롯 내용 편집을 우선 사용한다. 홈은 자유형 HTML이나 임의 스크립트를 저장하지 않고 정해진 슬롯·문구·이미지·연결 대상만 수정한다.

### 8.4 주문·결제 조회와 상태 전이

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/admin/orders` | 회원·비회원 전체 주문 목록 |
| `GET` | `/api/v1/admin/orders/{orderNumber}` | 주문 스냅샷·결제 시도·이력 상세 |
| `POST` | `/api/v1/admin/orders/{orderNumber}/transitions` | 허용된 주문 상태 전이 |

주문 목록 query:

- `q`: 주문번호·구매자 이름·연락처의 허용된 검색
- `status`: 주문 상태
- `page`, `size`; 기본 주문 시각 내림차순

상태 전이 요청:

```json
{
  "toStatus": "PREPARING",
  "reason": "결제 확인 후 상품 준비 시작",
  "version": 4
}
```

성공 응답에는 변경된 주문 상태, 새 `version`, 상태 이력 항목을 반환한다. 관리자가 `paymentStatus`를 직접 덮어쓰는 API는 제공하지 않는다.

### 8.5 회원 조회

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/admin/users` | 회원 목록·검색·페이지네이션 |

현재는 회원 목록·검색·페이지네이션 API만 제공한다. 회원 상세 API와 관리자 화면은 Later다. 비밀번호 해시, 세션, 방문자 토큰, 비회원 조회 토큰은 응답하지 않는다.

## 9. 상태 계약

### 9.1 상품·장바구니

```text
ProductStatus
DRAFT -> PUBLISHED -> HIDDEN
   └───────────────-> DISCONTINUED
PUBLISHED -> DISCONTINUED
HIDDEN -> PUBLISHED 또는 DISCONTINUED
```

- `DRAFT`, `HIDDEN`, `DISCONTINUED`는 공개 상세·신규 주문에서 사용할 수 없다.
- 옵션 재고가 0이면 `purchasable=false`다.

```text
CartStatus
ACTIVE -> MERGED
ACTIVE -> ORDERED
ACTIVE -> EXPIRED
```

### 9.2 주문

```text
PENDING_PAYMENT -> PAID -> PREPARING -> SHIPPED -> DELIVERED
PENDING_PAYMENT -> CANCELLED
```

- 결제 승인만 `PENDING_PAYMENT -> PAID`를 수행한다.
- 결제 실패·사용자 취소·예약 만료는 `PENDING_PAYMENT -> CANCELLED`로 끝내며 사유를 기록한다.
- 관리자는 결제되지 않은 주문을 `PREPARING` 또는 `SHIPPED`로 바꿀 수 없다.
- 임의 역방향 전이는 허용하지 않는다.
- `CANCEL_REQUESTED` 상태는 DB 허용값에 포함돼 있지만 현재 관리자 전이에는 연결하지 않았다.
- 허용되지 않은 전이는 현재 `409 ORDER_TRANSITION_NOT_ALLOWED`다.
- 실제 PG 환불·부분취소 자동화는 MVP 이후다. 테스트 주문의 취소 상태와 실제 금전 환불을 같은 것으로 표시하지 않는다.

### 9.3 주문의 결제 상태와 결제 시도

결제 시도 상태:

```text
READY -> APPROVED
READY -> FAILED
READY -> CANCELLED   // 예약 만료
APPROVED -> CANCELLED 또는 PARTIAL_CANCELLED  // 실운영 전환 이후
```

현재 시뮬레이터는 `READY`에서 `APPROVED`, `FAILED` 또는 예약 만료 시 `CANCELLED`로 직접 전이한다. `PROCESSING`과 `UNKNOWN`은 실PG 연동을 위한 스키마 허용값이며 현재 API에서는 생성하지 않는다.

### 9.4 재고 예약

```text
ACTIVE -> COMMITTED   결제 승인, 재고 확정
ACTIVE -> RELEASED    결제 실패·주문 취소, 재고 한 번 복원
ACTIVE -> EXPIRED     20분 만료, 재고 한 번 복원
```

재고 예약 상태는 고객이 직접 변경할 수 없으며 관리자 일반 API에도 변경 엔드포인트를 제공하지 않는다.

## 10. 캐시·보안·로그 기준

- `/home`, 공개 상품·기획전 GET은 짧은 캐시와 ETag를 적용할 수 있다.
- 사용자별 `wishlisted`를 포함하는 응답은 공유 캐시에 저장하지 않는다.
- 인증, CSRF, 장바구니, 찜, 주문, 결제, 관리자 응답은 `Cache-Control: no-store`다.
- 이메일·전화번호·주소·결제키·조회 토큰은 구조화 로그에서 마스킹하거나 제외한다.
- `Idempotency-Key`는 추적용으로 일부 마스킹할 수 있지만 전체 요청 본문과 함께 로그에 남기지 않는다.
- 비회원 조회·로그인 실패 속도 제한은 아직 미구현이며 외부 배포 전 남은 보안 과제다.
- 관리자 목록은 페이지 크기 상한을 우회할 수 없다.
- OpenAPI 예제에는 실제 개인정보·실제 결제키·실제 관리자 비밀번호를 넣지 않는다.

## 11. 2026-08-13 구현·검증 상태

현재 자동 검증은 백엔드 PostgreSQL Testcontainers 56개, 프론트 Vitest 16개 파일·51개, TypeScript typecheck, ESLint, Next.js production build를 통과했다. 아래 계약은 테스트로 확인했다.

1. 비회원 쿠키와 회원 세션의 장바구니 소유권 분리 및 찜 회원 전용 권한
2. 기존 고객 로그인 병합 재호출 시 수량 중복 증가 방지와 신규 회원가입 데이터 격리
3. 서버 가격 재계산과 재고 부족 거절
4. 주문 생성·결제 확인 멱등성과 승인·실패 시 재고 1회 반영
5. 주문번호만으로 비회원 주문 조회 불가
6. 일반 회원의 관리자 API 차단과 관리자 계정의 고객 주문 차단
7. 허용되지 않은 주문 상태 전이 거절과 감사 로그 생성
8. 홈 섹션·히어로, 상품·재고 관리자 변경
9. 같은 장바구니 행의 동시 주문에서 한 요청만 성공하고 재고·예약이 한 번만 반영됨
10. 비회원 주문 첫 응답과 멱등 재응답의 조회 토큰 일치와 DB 해시 전용 저장
11. 만료 결제의 원자 취소·재고 복원과 같은 키 반복 `409 ORDER_RESERVATION_EXPIRED`
12. 관리자 옵션 version 충돌과 홈 섹션별 JSON 스키마 검증
13. 관리자 로그인·병합의 방문자 데이터 비소비, 이메일 길이와 안전한 로그인 `next` 검증

아직 이 계약의 완료 조건으로 남은 항목은 자동 생성 OpenAPI, 외부 HTTPS 환경 E2E, 비회원 조회·로그인 레이트리밋, 실PG·웹훅 중복/서명 검증, 이미지 업로드, 관리자 기획전·분류 CRUD다. 이들은 현재 제공되는 API처럼 문서화하지 않고 Later로 구분한다.
