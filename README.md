# 반려동물 라이프스타일 큐레이션몰

강아지·고양이 용품으로 시작해 희귀동물과 펫룸/미니미룸으로 확장할 수 있는 반려동물 라이프스타일 커머스 프로젝트다.

2026년 8월 29일까지 사업 관계자가 링크로 확인할 수 있는 작동형 MVP를 완성하는 것이 첫 번째 목표다.

## 문서 읽는 순서

1. [제품 범위](docs/01_PRODUCT_SCOPE.md)
2. [화면과 사용자 흐름](docs/02_SCREEN_MAP.md)
3. [시스템 아키텍처](docs/03_ARCHITECTURE.md)
4. [데이터 모델](docs/04_DATABASE.md)
5. [API 계약](docs/08_API_CONTRACT.md)
6. [기능 QA 체크리스트](docs/09_FUNCTIONAL_QA_CHECKLIST.md)
7. [배포 점검표](docs/06_DEPLOYMENT_CHECKLIST.md)
8. [EC2 stage 배포 실행서](docs/10_EC2_DEPLOYMENT_RUNBOOK.md)
9. [개발 일정](docs/05_ROADMAP.md)
10. [MVP 완성까지 실행 가이드](docs/07_EXECUTION_PLAYBOOK.md)
11. [데모 상품·콘텐츠 데이터 계획](docs/DEMO_DATA_PLAN.md)
12. [결정 로그](docs/DECISIONS.md)

## 현재 구현

- Next.js + TypeScript 반응형 고객·관리자 웹
- Spring Boot `/api/v1` REST API와 PostgreSQL 17, Flyway V1~V6
- 홈 7개 섹션, 기획전, 상품 24개·옵션 26개 local/test 데모 데이터
- 회원·방문자 세션, 회원 전용 찜·회원/비회원 장바구니와 기존 회원 로그인 시 장바구니 병합
- 회원·비회원 주문, 20분 재고 예약, 시뮬레이션 결제와 주문 조회
- 관리자 상품·옵션·재고·상태, 주문 상태 전이, 홈 섹션·히어로 관리
- 교체 가능한 `StorageService`와 `/media/**` 로컬 미디어 제공
- 백엔드 PostgreSQL Testcontainers와 프론트 Vitest 자동 검증
- 단일 EC2 stage용 Docker Compose, HTTPS Nginx·Certbot, 백업·시연 데이터 부트스트랩 준비

## 로컬 실행

필요 도구는 Java 21, Node.js 20.9 이상, Docker Desktop이다. Maven은 Wrapper가 포함돼 있어 별도 설치하지 않아도 된다.

저장소 루트의 PowerShell에서 최초 한 번 실행한다.

```powershell
Copy-Item .env.example .env
# .env의 로컬 전용 DB 비밀번호를 원하는 값으로 변경
docker compose --env-file .env -f infra/compose.yaml up -d postgres
npm.cmd --prefix frontend install
```

그 다음 터미널 두 개를 열어 각각 실행한다.

```powershell
.\scripts\dev-backend.ps1
```

```powershell
.\scripts\dev-frontend.ps1
```

- 웹: `http://localhost:3000`
- API 헬스: `http://localhost:8080/api/v1/health`
- 상품 API: `http://localhost:8080/api/v1/catalog/products?page=0&size=8`
- 상품 목록: `http://localhost:3000/products`
- 상품 상세 예시: `http://localhost:3000/products/cozy-corner-pet-bed`

## 검증

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
npm.cmd --prefix frontend run typecheck
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
```

수동 기능·보안·반응형·배포 검증은 [기능 QA 체크리스트](docs/09_FUNCTIONAL_QA_CHECKLIST.md)를 사용한다.
