# Demo Media

이 디렉터리는 MVP 시연용 자체 제작 미디어를 저장한다. 현재 `demo/catalog`의 상품 이미지 4장과 `demo/home`의 홈·기획전 이미지 3장은 2026-08-12에 OpenAI 내장 이미지 생성 도구로 만든 가상 이미지이며, 실제 공급사·브랜드·판매 상품·캠페인을 재현하지 않는다.

- DB에는 파일 자체가 아니라 `demo/catalog/...` storage key만 저장한다.
- 고객 API는 storage key를 `/media/...` URL로 변환한다.
- Spring Boot의 `StorageService`가 로컬 파일을 읽고, 로컬 관리자 업로드는 Git에서 제외된 `media/uploads/` 아래에 저장한다.
- 정식 상품 사진이 확보되면 같은 계층에서 새 storage key로 교체한다.
- stage/prod에서는 `MEDIA_STORAGE_ROOT`를 반드시 명시한다. EC2 stage는 checkout 밖의 `MEDIA_HOST_PATH`를 `/srv/media`에 연결하므로 동적 업로드가 Git 소스와 섞이지 않는다.

이미지는 시연 데이터이며 실제 판매 등록 전 상품 정보·사진 사용 허가를 다시 확인한다.
