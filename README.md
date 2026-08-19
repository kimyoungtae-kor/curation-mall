# Pet Curation

반려동물 용품을 상황에 맞게 찾아볼 수 있도록 만든 풀스택 쇼핑몰 프로젝트입니다.

일반적인 카테고리 탐색뿐 아니라 `여름철 수분 관리`, `안전한 이동`, `편안한 휴식`처럼 반려동물과 함께 생활하면서 생기는 순간을 기준으로 상품을 묶었습니다. 화면만 보여주는 데서 끝내지 않고 회원·비회원 주문과 관리자 처리까지 한 흐름으로 구현했습니다.

> 현재는 포트폴리오 시연용 프로젝트이며 결제는 실제 과금이 없는 테스트 방식으로 동작합니다.

## 구현한 기능

- 회원가입, 로그인, 로그아웃과 세션 인증
- 회원·비회원 장바구니 및 로그인 시 기존 장바구니 병합
- 상품 검색, 옵션 선택, 찜, 회원·비회원 주문
- 주문 시 20분 재고 예약과 결제 성공·실패 처리
- 주문번호와 조회 토큰을 이용한 비회원 주문 조회
- 이미지 파일 업로드를 포함한 상품·옵션·재고, 주문 상태, 홈 콘텐츠 관리자 화면
- 모바일 화면을 포함한 반응형 UI

## 구현하면서 신경 쓴 부분

기존 프로젝트에서는 `controller`, `service`, `repository` 폴더에 기능이 계속 섞이면서 코드를 찾기 어려웠습니다. 이번에는 `identity`, `catalog`, `cart`, `order`, `payment`처럼 기능을 먼저 나누고, 각 기능 안에서 API·application·domain·infrastructure 역할을 구분했습니다.

주문 과정에서는 화면보다 데이터가 어긋나지 않는 것을 우선했습니다. 서버가 가격을 다시 계산하고, 주문 생성 요청이 반복되어도 같은 주문이 중복 생성되지 않도록 멱등 키를 사용했습니다. 결제에 실패하거나 예약 시간이 지나면 잡아 둔 재고를 복원하며, 관리자가 같은 상품을 동시에 수정할 때는 `version` 값으로 충돌을 확인합니다.

비밀번호와 비회원 조회 토큰은 원문으로 저장하지 않습니다. 비밀번호는 BCrypt, 조회 토큰은 해시 형태로 보관하고 세션 쿠키와 CSRF 검증을 적용했습니다.

## 기술 구성

- Frontend: Next.js, TypeScript
- Backend: Java 21, Spring Boot REST API
- Database: PostgreSQL 17, Flyway
- Test: JUnit, Testcontainers, Vitest
- Deployment: Docker Compose, Nginx, Let's Encrypt

## 로컬 실행

Java 21, Node.js 20.9 이상, Docker Desktop이 필요합니다. 저장소 루트에서 환경 파일을 만든 뒤 PostgreSQL을 실행합니다.

```powershell
Copy-Item .env.example .env
# .env에서 POSTGRES_PASSWORD와 SPRING_DATASOURCE_PASSWORD를 같은 값으로 변경
docker compose --env-file .env -f infra/compose.yaml up -d postgres
npm.cmd --prefix frontend install
```

백엔드와 프론트엔드는 각각 다른 PowerShell 터미널에서 실행합니다.

```powershell
.\scripts\dev-backend.ps1
```

```powershell
.\scripts\dev-frontend.ps1
```

- 웹: `http://localhost:3000`
- API 상태 확인: `http://localhost:8080/api/v1/health`

## 테스트

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
npm.cmd --prefix frontend run typecheck
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
```

브라우저에서 확인할 항목은 [기능 QA 체크리스트](docs/09_FUNCTIONAL_QA_CHECKLIST.md)에 기록하고 있습니다. 데이터 구조와 API는 [데이터 모델](docs/04_DATABASE.md), [API 계약](docs/08_API_CONTRACT.md)에서 확인할 수 있습니다.

## 아직 포함하지 않은 기능

- 실제 PG 결제 및 환불
- 이메일 인증과 비밀번호 재설정 메일
- 외부 이미지 스토리지
- 운영 환경용 알림과 관측 대시보드
