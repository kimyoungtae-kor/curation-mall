# EC2 단일 서버 배포 실행서

마지막 갱신: 2026-08-15

이 문서는 기존 EBS 기반 EC2를 t3a.medium으로 변경하고, 한 서버에서 Docker Compose로 PostgreSQL, Spring Boot, Next.js, Nginx, Certbot을 실행하는 순서입니다. 처음부터 실제 결제 운영 서버를 만드는 절차가 아니라 취업 포트폴리오용 stage 시연 배포입니다.

## 먼저 기억할 세 가지

1. 결제는 SIMULATED 테스트 결제입니다. 화면과 포트폴리오에 실제 결제가 아니라는 문구를 표시합니다.
2. 공개 stage에는 회원·주문 seed를 넣지 않습니다. 안전한 카탈로그와 기획전 seed 두 개만 별도 스크립트로 넣습니다.
3. PostgreSQL 5432, Spring 8080, Next 3000은 인터넷에 열지 않습니다. 외부에는 80과 443만 노출합니다.

배포 명령의 공통 형태는 다음과 같습니다.

    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml ...

## 0. 지금 확인된 차단점

공개 배포 대상 저장소는 `https://github.com/kimyoungtae-kor/curation-mall.git`입니다. 현재 로컬 `main`의 과거 이력에는 공개 제외 대상 작업 문서와 개인 작성자 이메일이 있으므로 해당 브랜치를 직접 push하지 않습니다. 공개 제외 규칙으로 만든 fresh root 이력의 `main`을 먼저 push한 뒤 EC2에서 clone합니다.

- 권장: GitHub 저장소를 만들고, 비밀값이 없는 테스트 완료 커밋을 push한 뒤 EC2에서 clone합니다.
- 대안: 테스트 완료 커밋을 git archive로 묶어 SCP로 전송합니다.

대상 EC2에서는 다음 환경을 확인했습니다.

- Ubuntu 22.04.5 LTS, x86_64
- RAM 3.8GiB, swap 2GiB
- 루트 EBS 20GiB 중 약 11GiB 여유
- Docker 29.5.0, Docker Compose 5.1.3 설치됨
- 소유자가 폐기 가능하다고 확인한 기존 데모 Compose·DB 볼륨·이미지·빌드 캐시 제거 완료
- 기존 호스트 웹·DB 서비스는 자동 시작을 해제하고 설정·데이터는 새 stage 검증 전까지 보존

2026-08-16 기존 데모 컨테이너·볼륨·네트워크·이미지와 빌드 캐시를 정확한 대상으로 제거했고 호스트 웹 서비스도 중지·자동 시작 해제했습니다. 정리 후 Docker 자원은 비었고 루트 디스크 약 13GiB, 가용 메모리 약 3.2GiB, swap 2GiB를 확보했습니다. 기존 DB 서비스는 데이터 파일과 패키지를 삭제하지 않고 서비스만 중지·자동 시작 해제한 뒤 새 stage 검증까지 보존합니다.

따라서 Docker를 재설치하지 않습니다. 기존 컨테이너의 Compose 경로·볼륨, Nginx의 도메인·인증서와 호스트 MariaDB 용도를 먼저 조사한 뒤 정확한 대상만 정리합니다. 전체 `docker system prune --volumes`나 패키지 일괄 삭제는 사용하지 않습니다.

다른 서버에서 이 실행서를 재사용한다면 SSH 접속 직후 반드시 다음 결과를 먼저 봅니다.

    cat /etc/os-release
    uname -m

Ubuntu와 Amazon Linux 2023 설치 명령은 서로 다릅니다. 결과를 확인하기 전에 한쪽 명령을 추측해서 실행하지 않습니다.

## 1. 로컬에서 한 배포본을 확정하기

먼저 변경과 remote를 확인합니다.

    git status
    git diff
    git remote -v

typecheck, lint, 단위 테스트, 백엔드 테스트, production build가 통과한 한 커밋을 배포 단위로 사용합니다. .env.deploy은 절대 commit하지 않습니다.

### 방법 A: GitHub를 사용할 때

공개 안전 점검을 통과한 별도 공개 작업 폴더에서만 실행합니다.

    git remote add origin https://github.com/kimyoungtae-kor/curation-mall.git
    git push -u origin main

원본 로컬 저장소의 `main`에서는 위 push 명령을 실행하지 않습니다.

private 저장소라면 EC2에 개인 GitHub 비밀번호를 저장하지 말고 읽기 전용 deploy key 또는 짧은 수명의 인증 방법을 사용합니다.

### 방법 B: SCP로 보낼 때

작업 중인 파일 전체를 복사하지 않고 커밋된 HEAD만 묶습니다. 이렇게 하면 로컬 .env, node_modules, build 결과가 섞이지 않습니다.

    git archive --format=zip --output pet-curation-deploy.zip HEAD
    scp -i "C:\path\to\key.pem" pet-curation-deploy.zip ubuntu@ELASTIC_IP:/home/ubuntu/

Amazon Linux 2023이면 기본 접속 사용자가 보통 ec2-user이므로 경로와 사용자명을 바꿉니다.

## 2. EC2를 t3a.medium으로 변경하기

대상은 소유자와 용도를 확인했고 stage로 재사용해도 된다고 승인된 인스턴스여야 합니다. 잘못된 인스턴스를 누르는 사고를 막기 위해 다음을 기록합니다.

- 인스턴스 이름과 ID
- 현재 인스턴스 유형
- 루트 EBS 볼륨 ID와 가용 영역
- 보안 그룹
- ENI, 사설 IP, 현재 공인 IP 또는 Elastic IP
- 현재 DNS A 레코드

다음 조건을 확인합니다.

- Root device type: EBS
- Architecture: x86_64
- Virtualization: HVM
- Spot 인스턴스가 아님
- Auto Scaling Group 소속이 아닌 단독 인스턴스

변경 순서:

1. EC2 콘솔 리전이 서울 ap-northeast-2인지 확인합니다.
2. 인스턴스를 Stop instance로 정상 중지하고 stopped까지 기다립니다.
3. EBS Volumes에서 루트 볼륨을 선택해 snapshot을 만듭니다.
4. Instances에서 대상 선택 → Actions → Instance settings → Change instance type으로 이동합니다.
5. t3a.medium을 선택하고 변경합니다.
6. 같은 보안 그룹과 EIP가 연결되어 있는지 다시 확인합니다.
7. Start instance를 누르고 시스템·인스턴스·EBS 상태 검사 통과를 기다립니다.

자동 할당 공인 IPv4는 중지·시작 때 바뀔 수 있습니다. 시연 주소를 유지하려면 Elastic IP를 연결하고 DNS A 레코드를 그 주소로 지정합니다.

실패 시 Terminate를 누르지 않습니다. 다시 중지한 뒤 기록해 둔 원래 인스턴스 유형으로 변경하고 시작하면 컴퓨팅 유형을 롤백할 수 있습니다.

공식 문서:

- [EBS 기반 EC2 인스턴스 유형 변경](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/change-instance-type-of-ebs-backed-instance.html)
- [EC2 중지·시작 때 유지되거나 사라지는 항목](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/how-ec2-instance-stop-start-works.html)
- [EBS 스냅샷 생성](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-creating-snapshot.html)
- [Elastic IP 사용](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/working-with-eips.html)

## 3. 보안 그룹과 DNS

권장 인바운드 규칙:

| 포트 | 소스 | 용도 |
|---|---|---|
| TCP 22 | 현재 내 공인 IP/32 | SSH |
| TCP 80 | 0.0.0.0/0 | 인증서 발급과 HTTPS 이동 |
| TCP 443 | 0.0.0.0/0 | 공개 HTTPS |

IPv6를 실제 사용하는 경우에만 80과 443에 ::/0도 추가합니다. 22를 0.0.0.0/0으로 열지 않습니다. 3000, 5432, 8080 규칙은 만들지 않습니다.

Cloudflare를 사용한다면 도메인 또는 서브도메인의 A 레코드를 EIP로 설정하고, 최초 인증서 발급 때는 Proxy status를 `DNS only`(회색 구름)로 둡니다. EC2에 IPv6를 구성하지 않았다면 같은 호스트의 AAAA 레코드는 만들지 않습니다. 내 컴퓨터에서 다음 결과가 EIP와 같은지 확인합니다.

    nslookup shop.your-domain.com

Let's Encrypt 인증서 발급과 HTTPS 확인이 끝난 뒤 Cloudflare 프록시를 켜려면 SSL/TLS 모드를 반드시 `Full (strict)`로 설정합니다. `Flexible`은 Nginx의 HTTPS 리다이렉트와 충돌할 수 있습니다. 현재 Nginx의 요청 제한은 접속자의 실제 IP를 기준으로 설계되어 있으므로, 최초 배포는 `DNS only`를 유지합니다. 프록시 전환은 Cloudflare 신뢰 IP 대역과 `CF-Connecting-IP` 처리를 Nginx에 추가한 뒤 별도 검증합니다.

[AWS 보안 그룹 규칙 공식 문서](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html)
[Cloudflare 프록시 상태 공식 문서](https://developers.cloudflare.com/dns/proxy-status/)
[Cloudflare Full (strict) 공식 문서](https://developers.cloudflare.com/ssl/origin-configuration/ssl-modes/full-strict/)

## 4. SSH 접속 후 운영체제 확인

    cat /etc/os-release
    uname -m
    free -h
    df -h
    git --version
    docker version
    docker compose version

이미 Docker와 Compose가 정상이라면 재설치하지 않고 5단계로 넘어갑니다.

### Ubuntu 22.04 또는 24.04

Docker 공식 apt 저장소를 사용합니다.

    sudo apt update
    sudo apt install -y ca-certificates curl git openssl unzip
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
    Types: deb
    URIs: https://download.docker.com/linux/ubuntu
    Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
    Components: stable
    Architectures: $(dpkg --print-architecture)
    Signed-By: /etc/apt/keyrings/docker.asc
    EOF
    sudo apt update
    sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER"

그룹 변경 적용을 위해 SSH에서 나갔다가 다시 접속한 뒤 확인합니다.

    docker version
    docker compose version

[Ubuntu Docker Engine 설치 공식 문서](https://docs.docker.com/engine/install/ubuntu/)

### Amazon Linux 2023

    sudo dnf update -y
    sudo dnf install -y docker git curl openssl unzip
    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER"

SSH에서 나갔다가 다시 접속한 뒤 확인합니다.

    docker version
    docker compose version

docker compose가 없다면 먼저 RPM 패키지를 시도합니다.

    sudo dnf install -y docker-compose-plugin
    docker compose version

해당 AL2023 저장소에서 플러그인을 제공하지 않으면 Docker 공식 수동 설치 문서에 나온 현재 버전을 설치합니다. 수동 설치는 자동 업데이트되지 않으므로 문서의 최신 버전을 다시 확인해야 합니다. 2026-08-15 공식 예시 버전은 v5.1.2입니다.

    COMPOSE_VERSION=v5.1.2
    sudo install -m 0755 -d /usr/local/lib/docker/cli-plugins
    sudo curl -fSL "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/docker-compose-linux-x86_64" -o /usr/local/lib/docker/cli-plugins/docker-compose
    sudo chmod 0755 /usr/local/lib/docker/cli-plugins/docker-compose
    docker compose version

공식 근거:

- [AL2023에서 사용할 수 있는 컨테이너 런타임 패키지](https://docs.aws.amazon.com/linux/al2023/ug/container.html)
- [Docker Compose 플러그인 설치](https://docs.docker.com/compose/install/linux/)

Docker 그룹 사용자는 사실상 Docker 호스트의 높은 권한을 가집니다. 신뢰하는 배포 계정만 그룹에 추가합니다.

## 5. 소스 받기

### GitHub 방식

    cd "$HOME"
    git clone https://github.com/ACCOUNT/REPOSITORY.git pet-curation
    cd pet-curation
    git rev-parse --short HEAD

### SCP archive 방식

    mkdir -p "$HOME/pet-curation"
    unzip -q "$HOME/pet-curation-deploy.zip" -d "$HOME/pet-curation"
    cd "$HOME/pet-curation"

archive에는 Git 메타데이터가 없으므로 preflight가 경고를 표시합니다. 로컬에서 테스트한 정확한 HEAD archive인지 직접 확인해야 합니다.

## 6. 배포 환경값 만들기

    cp .env.deploy.example .env.deploy
    openssl rand -hex 32
    openssl rand -hex 32
    nano .env.deploy
    chmod 600 .env.deploy

두 random 결과는 각각 PostgreSQL 비밀번호와 guest 조회 토큰 비밀값에 넣으며 서로 달라야 합니다. 다음 값도 실제 값으로 바꿉니다.

- APP_DOMAIN: `https://`가 없는 대표 호스트 이름
- APP_WWW_DOMAIN: 대표 주소로 301 이동할 `www` 호스트 이름
- LEGACY_APP_DOMAIN: 대표 주소로 301 이동할 이전 호스트 이름
- CERTBOT_EMAIL: 인증서 만료 알림을 받을 실제 이메일
- POSTGRES_PASSWORD: 첫 번째 64자리 random 값
- GUEST_LOOKUP_TOKEN_SECRET: 두 번째 64자리 random 값
- MEDIA_HOST_PATH: Git checkout 밖의 상품 이미지 영속 디렉터리. 현재 기준은 `/var/lib/pet-curation/media`

compose 파일은 stage, Secure cookie, 같은 origin CORS, SIMULATED 결제를 고정합니다. .env.deploy에는 이를 중복 기재하지 않습니다.

### 상품 이미지 영속 디렉터리 최초 준비

백엔드 컨테이너는 root가 아닌 고정 UID/GID `10001:10001`로 실행합니다. 업로드 파일이 재배포 때 사라지지 않도록 checkout의 `media`를 쓰기 공간으로 사용하지 않고 전용 호스트 디렉터리를 한 번 준비합니다.

먼저 정확한 대상이 기존 다른 서비스의 폴더가 아닌지 확인합니다.

    sudo ls -ld /var/lib/pet-curation /var/lib/pet-curation/media 2>/dev/null || true

새 전용 경로임을 확인한 뒤에만 다음을 실행합니다.

    sudo install -d -o 10001 -g 10001 -m 0750 /var/lib/pet-curation/media
    sudo cp -a media/demo /var/lib/pet-curation/media/
    sudo stat -c 'path=%n owner=%u:%g mode=%a' /var/lib/pet-curation/media

마지막 출력은 `owner=10001:10001`이어야 하고 소유자 권한에 쓰기와 디렉터리 진입 권한이 있어야 합니다. `cp -a media/demo ...`는 전용 저장소 루트의 소유권을 건드리지 않고 기존 자체 제작 데모 하위 디렉터리만 처음 복사하기 위한 명령이며, 업로드 파일을 지우거나 동기화하는 명령이 아닙니다. 디렉터리가 이미 존재하거나 다른 파일이 있다면 전체 경로를 다시 확인하고 멈춥니다. 임의 경로에 `chown -R`을 실행하지 않습니다.

기존 EC2의 `.env.deploy`을 유지하는 업그레이드라면 비밀값을 새로 만들지 말고 다음 한 줄만 추가합니다.

    MEDIA_HOST_PATH=/var/lib/pet-curation/media

비밀 파일이 Git에 보이지 않는지 확인합니다.

    git status --short
    git check-ignore .env.deploy

두 번째 명령 결과가 .env.deploy이어야 합니다. archive 방식은 Git 메타데이터가 없으므로 chmod 600과 파일 관리에 더 주의합니다.

## 7. 첫 배포

Windows에서 생성된 셸 파일은 실행 비트가 없을 수 있으므로 chmod로 저장소를 변경하지 않고 항상 bash로 호출합니다.

    bash scripts/deploy/preflight.sh
    bash scripts/deploy/deploy.sh

preflight는 `MEDIA_HOST_PATH`가 절대경로이고 Git checkout 밖의 실제 디렉터리인지, 소유자가 `10001:10001`인지, 소유자 쓰기·진입 권한이 있으며 group·others에는 쓰기 권한이 없는지 확인합니다. 미디어가 위치한 파일시스템의 여유가 2GiB 미만이면 경고합니다. 조건이 다르면 자동으로 소유권을 바꾸지 않고 중단합니다. Compose도 경로가 없을 때 root 소유 폴더를 자동 생성하지 않도록 `create_host_path: false`로 고정했습니다.

deploy.sh는 4 GiB 서버에서 동시에 build하지 않도록 backend를 먼저 build하고 frontend를 다음에 build합니다. 이후 PostgreSQL, backend, frontend를 시작합니다. 첫 실행에는 인증서가 없으므로 proxy는 아직 시작하지 않고 다음 명령을 안내하는 것이 정상입니다.

상태와 로그:

    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml ps
    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml logs --tail=200 postgres backend frontend

Docker 볼륨을 지우는 down -v는 사용하지 않습니다.

## 8. 안전한 stage 시연 데이터 넣기

    bash scripts/deploy/seed-stage-demo.sh

확인 문구를 정확히 입력하면 다음 두 파일만 각자 한 transaction으로 실행합니다.

- backend/src/main/resources/db/seed/R__seed_demo_catalog.sql
- backend/src/main/resources/db/seed/R__seed_demo_merchandising.sql

R__seed_demo_identity.sql과 R__seed_demo_orders.sql은 실행하지 않습니다. 앞 파일에는 공개된 데모 비밀번호 hash가 있고, 뒤 파일에는 데모 주문이 있으므로 공개 stage와 맞지 않습니다.

## 9. 첫 HTTPS 인증서 발급

다음을 다시 확인합니다.

- DNS A 레코드가 이 EC2 EIP를 가리킴
- `APP_WWW_DOMAIN`과 `LEGACY_APP_DOMAIN`도 이 EC2로 해석됨
- 보안 그룹 80과 443이 열림
- 다른 프로그램이 호스트 80을 사용하지 않음

그 후 실행합니다.

    bash scripts/deploy/init-tls.sh

스크립트는 proxy를 중지한 상태에서 tools profile의 Certbot standalone을 포트 80에 잠시 실행합니다. 대표·www는 대표 인증서에 함께 넣고, 이전 주소의 인증서가 없을 때는 리다이렉트용 인증서를 별도로 발급합니다. 인증서 발급이 성공하면 proxy를 시작하고 대표 주소의 HTTPS API 상태를 확인합니다.

    curl -fsS https://shop.your-domain.com/api/v1/health
    curl -I https://shop.your-domain.com/

발급을 여러 번 반복하면 Let's Encrypt 제한에 걸릴 수 있습니다. 이미 인증서가 있으면 init-tls.sh가 중단하며 renew-tls.sh를 사용해야 합니다.

[Certbot standalone과 renew 공식 문서](https://eff-certbot.readthedocs.io/en/stable/using.html)

## 10. 관리자 계정 한 번 만들기

1. HTTPS 회원가입 화면에서 본인이 정한 강한 비밀번호로 새 계정을 만듭니다.
2. EC2에서 다음을 실행합니다.

       bash scripts/deploy/promote-admin.sh

3. 회원가입에 사용한 이메일을 입력하고 확인 문구를 입력합니다.
4. 스크립트는 ACTIVE 계정을 normalized email로 찾고 기존 CUSTOMER 역할을 제거한 뒤 ADMIN 역할만 부여합니다.
5. 브라우저에서 로그아웃하고 다시 로그인합니다. 기존 session에는 예전 권한이 남아 있을 수 있습니다.

비밀번호는 스크립트나 SQL에 입력하지 않고, 출력하지 않으며, DB에서 직접 바꾸지 않습니다. 회원·주문 seed도 필요하지 않습니다.

## 11. 최소 배포 QA

    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml ps
    curl -fsS https://shop.your-domain.com/api/v1/health
    docker stats --no-stream
    free -h
    df -h

브라우저에서는 다음을 확인합니다.

- HTTP 주소가 HTTPS로 이동함
- 홈·상품·기획전과 이미지가 표시됨
- 회원가입, 로그아웃, 재로그인이 됨
- session과 visitor cookie에 Secure가 적용됨
- 장바구니, 회원 주문, 비회원 주문·조회가 됨
- 결제 화면에 테스트 결제임이 분명함
- 관리자 로그인과 주문 상태 변경이 됨
- 관리자 상품 폼에서 PC 파일 선택과 모바일 사진 선택이 열리고 JPEG·PNG·WebP 업로드·미리보기·순서 변경이 됨
- 잘못된 형식, 8MB 초과 파일과 픽셀 제한 초과 이미지가 저장되지 않고 이해할 수 있는 오류로 표시됨
- 브라우저 Console과 Network에 반복 4xx/5xx가 없음

업로드 뒤 컨테이너 내부와 호스트가 같은 파일을 보는지 확인합니다.

    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml exec -T backend sh -c 'test -w /srv/media && find /srv/media/uploads -maxdepth 5 -type f | head'
    sudo find /var/lib/pet-curation/media/uploads -maxdepth 5 -type f | head

백엔드를 재생성한 뒤에도 관리자에서 올린 이미지 URL이 열리는지 확인해야 영속성 검증이 끝납니다. 현재 Nginx와 Spring multipart 요청 한도는 10MB이며 애플리케이션 파일 한도는 8MB입니다.

전체 수동 QA는 09_FUNCTIONAL_QA_CHECKLIST.md를 이어서 사용합니다.

## 12. DB·미디어 백업과 안전한 복구 연습

백업:

    bash scripts/deploy/backup-db.sh
    ls -lh backups/postgres

스크립트는 custom format dump와 SHA-256 파일을 만들며 기존 백업을 자동 삭제하지 않습니다. 같은 EBS 안의 파일만으로는 서버 장애에 대비할 수 없으므로 내 컴퓨터나 S3 같은 별도 위치로 복사합니다.

상품 DB에는 파일 자체가 아니라 `storage_key`만 있으므로 미디어 디렉터리도 같은 배포 시점에 별도 백업해야 합니다. 다음은 파일을 지우거나 원본을 변경하지 않고 압축본과 체크섬을 만드는 예입니다.

    media_backup_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    sudo tar -C /var/lib/pet-curation -czf "/home/ubuntu/pet-curation-media-${media_backup_stamp}.tar.gz" media
    sudo chown ubuntu:ubuntu "/home/ubuntu/pet-curation-media-${media_backup_stamp}.tar.gz"
    sha256sum "/home/ubuntu/pet-curation-media-${media_backup_stamp}.tar.gz" > "/home/ubuntu/pet-curation-media-${media_backup_stamp}.tar.gz.sha256"

이 압축 파일과 체크섬도 DB dump와 함께 EC2 밖으로 복사합니다. 다른 운영 계정을 사용한다면 `/home/ubuntu`와 소유자명을 실제 배포 계정에 맞게 바꿉니다. 복구 연습은 운영 경로에 바로 덮어쓰지 말고 별도 임시 디렉터리에 압축을 풀어 파일 수·체크섬·대표 이미지 조회를 확인합니다.

로컬 PC에서 복사하는 예:

    scp -i "C:\path\to\key.pem" ubuntu@ELASTIC_IP:/home/ubuntu/pet-curation/backups/postgres/FILE.dump* "C:\safe-backups\"

복구는 운영 DB에 바로 덮어쓰지 않습니다. 먼저 새 임시 DB로 연습합니다. 아래 이름이 기존 DB와 겹치지 않는지 확인한 후에만 실행합니다.

    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml exec -T postgres createdb -U pet_app pet_curation_restore_check
    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml exec -T postgres pg_restore -U pet_app -d pet_curation_restore_check --no-owner --no-privileges < backups/postgres/FILE.dump
    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml exec -T postgres psql -U pet_app -d pet_curation_restore_check -c "SELECT COUNT(*) FROM flyway_schema_history;"

임시 복구 결과를 확인한 뒤에만 정확한 임시 DB를 지웁니다.

    docker compose --env-file .env.deploy -f infra/compose.deploy.yaml exec -T postgres dropdb -U pet_app pet_curation_restore_check

실제 stage DB 복구는 서비스 중지, 직전 안전 백업, 복구 대상 dump 확인이 필요한 별도 작업이므로 자동화하지 않았습니다.

## 13. 인증서 갱신

수동 확인:

    bash scripts/deploy/renew-tls.sh

standalone 방식은 포트 80을 사용해야 해서 proxy가 잠시 중지됩니다. 스크립트는 실행 전 proxy 상태를 기억하고, 성공 또는 실패 후 원래 실행 상태로 돌립니다. Certbot은 갱신 시점이 아닐 때 인증서를 다시 발급하지 않습니다.

갱신 스크립트는 대표·www 인증서와 이전 주소 인증서를 한 번에 확인합니다. `certbot certificates`에서 두 인증서와 각 도메인 이름을 확인합니다.

하루 한 번 새벽 cron 예:

    crontab -e

다음 한 줄에서 경로를 실제 프로젝트 경로로 바꿉니다.

    17 3 * * * cd "$HOME/pet-curation" && /usr/bin/bash scripts/deploy/renew-tls.sh >> "$HOME/pet-curation-certbot.log" 2>&1

이 구성은 갱신 확인 때 짧은 proxy 중단이 있습니다. 실제 운영 서비스로 커지면 webroot 또는 DNS challenge로 바꿔 무중단 갱신을 구성합니다.

## 14. 다음 배포와 애플리케이션 롤백

배포 전 현재 커밋을 기록하고 DB 백업을 만듭니다.

    git rev-parse HEAD
    bash scripts/deploy/backup-db.sh

새 테스트 완료 커밋을 받은 뒤 다시 실행합니다.

    git pull --ff-only
    bash scripts/deploy/deploy.sh

애플리케이션 문제가 생기면 작업 트리가 깨끗한 상태에서 기록한 이전 커밋으로 이동하고 다시 배포할 수 있습니다.

    git switch --detach PREVIOUS_TESTED_COMMIT
    bash scripts/deploy/deploy.sh

단, Flyway migration은 자동으로 과거로 돌아가지 않습니다. 새 migration이 이전 애플리케이션과 호환되지 않으면 애플리케이션만 되돌리지 말고 백업·복구 계획을 먼저 세웁니다. postgres_data와 letsencrypt 볼륨, `MEDIA_HOST_PATH`의 업로드 파일을 지우지 않습니다.

## 15. 비용과 작은 서버 운영 주의

- EC2를 stopped로 두면 컴퓨팅 요금은 멈추지만 EBS, snapshot, 공인 IPv4 관련 비용은 계속될 수 있습니다.
- 현재 AWS 공인 IPv4는 사용 중인 주소도 시간당 과금 대상입니다.
- t3a.medium은 burstable이고 Unlimited CPU credit 추가 요금이 발생할 수 있습니다.
- EBS 용량, snapshot 저장량, 인터넷 데이터 전송도 별도입니다.
- Docker log는 compose에서 회전하지만 DB dump는 자동 삭제하지 않으므로 디스크를 주기적으로 확인합니다.
- 초기에는 Prometheus와 Grafana를 같은 서버에 얹지 않고 docker stats, free, df, EC2 기본 지표로 관찰합니다.

    docker stats --no-stream
    free -h
    df -h
    docker system df

[EC2 온디맨드와 CPU credit 가격](https://aws.amazon.com/ec2/pricing/on-demand/) · [공인 IPv4 가격](https://aws.amazon.com/vpc/pricing/) · [EBS 가격](https://aws.amazon.com/ebs/pricing/)

## 16. 중단 기준

다음 중 하나면 추측해서 계속하지 말고 중단합니다.

- 대상 EC2가 맞는지 확신할 수 없음
- 루트가 EBS가 아니거나 x86_64/HVM이 아님
- OS가 Ubuntu 또는 Amazon Linux 2023인지 확인되지 않음
- git remote/push 또는 안전한 archive 전송이 준비되지 않음
- DNS가 EIP를 가리키지 않음
- .env.deploy이 Git에 추적됨
- `MEDIA_HOST_PATH`가 Git checkout 안이거나 `/`, 심볼릭 링크, 다른 서비스의 디렉터리이거나 `10001:10001` 소유가 아님
- local profile 또는 실제 결제로 실행하려고 함
- 백업 없이 DB migration이나 복구를 진행하려고 함
- 5432, 8080, 3000을 공인 인터넷에 열어야 한다고 판단됨

관리자 업로드용 영속 디렉터리·읽기/쓰기 mount 변경은 코드와 설정만 준비됐고 기존 stage에는 아직 적용하지 않았습니다. 로컬 Docker는 현재 호스트의 여유 메모리 부족으로 전체 컨테이너 기동 검증을 하지 못했으므로, 실제 EC2 다음 배포에서 preflight, Compose healthcheck, HTTPS healthcheck, 이미지 업로드와 재생성 후 조회 결과를 배포 증거로 남겨야 합니다.
