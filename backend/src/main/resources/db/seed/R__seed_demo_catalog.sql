-- Fictional data for local and test profiles only. It contains no supplier or customer data.
INSERT INTO brands (id, slug, name, description)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'morrow-pet', 'Morrow Pet', '공간과 반려생활의 균형을 생각하는 가상 브랜드'),
    ('20000000-0000-0000-0000-000000000002', 'pawform', 'Pawform', '안전한 외출을 위한 가상 라이프스타일 브랜드'),
    ('20000000-0000-0000-0000-000000000003', 'nook-and-tail', 'Nook & Tail', '집 안의 편안한 반려공간을 만드는 가상 브랜드'),
    ('20000000-0000-0000-0000-000000000004', 'color-tail', 'Color Tail', '색과 형태로 즐거운 반려생활을 제안하는 가상 브랜드')
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO species (id, code, name, sort_order)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'DOG', '강아지', 10),
    ('30000000-0000-0000-0000-000000000002', 'CAT', '고양이', 20)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO categories (id, slug, name, sort_order)
VALUES
    ('40000000-0000-0000-0000-000000000001', 'feeding', '식기·급수', 10),
    ('40000000-0000-0000-0000-000000000002', 'travel', '외출·이동', 20),
    ('40000000-0000-0000-0000-000000000003', 'furniture', '가구·하우스', 30),
    ('40000000-0000-0000-0000-000000000004', 'beds', '침대·매트', 40),
    ('40000000-0000-0000-0000-000000000005', 'toys', '장난감·놀이', 50),
    ('40000000-0000-0000-0000-000000000006', 'toilet', '배변·화장실', 60),
    ('40000000-0000-0000-0000-000000000007', 'walking', '산책용품', 70),
    ('40000000-0000-0000-0000-000000000008', 'clothing', '의류·패션', 80)
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO products (
    id, brand_id, slug, name, short_description, description, status, attributes, featured, published_at
)
VALUES
    (
        '50000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'oasis-ceramic-water-bowl',
        '오아시스 세라믹 워터볼',
        '편안한 음수를 돕는 낮고 넓은 세라믹 식기',
        '집 안의 오브제처럼 어울리며 세척이 편한 가상 데모 상품입니다.',
        'PUBLISHED',
        '{"material":"ceramic","capacityMl":650}'::jsonb,
        TRUE,
        CURRENT_TIMESTAMP - INTERVAL '4 days'
    ),
    (
        '50000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000002',
        'cloud-fit-car-seat',
        '클라우드핏 카시트',
        '반려견의 안전한 차량 탑승을 위한 포근한 카시트',
        '고정 스트랩과 분리형 커버를 갖춘 가상 데모 상품입니다.',
        'PUBLISHED',
        '{"recommendedWeightKg":8,"washableCover":true}'::jsonb,
        TRUE,
        CURRENT_TIMESTAMP - INTERVAL '3 days'
    ),
    (
        '50000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000003',
        'module-cat-step-tower',
        '모듈 캣 스텝 타워',
        '오르고 숨고 쉬는 행동을 하나의 가구에 담은 캣타워',
        '실내 동선과 인테리어를 함께 고려한 가상 데모 상품입니다.',
        'PUBLISHED',
        '{"material":"birch plywood","heightCm":138}'::jsonb,
        TRUE,
        CURRENT_TIMESTAMP - INTERVAL '2 days'
    ),
    (
        '50000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000003',
        'cozy-corner-pet-bed',
        '포근 코너 펫 베드',
        '집 안 모서리에 자연스럽게 놓이는 반려동물 침대',
        '강아지와 고양이가 함께 사용할 수 있는 가상 데모 상품입니다.',
        'PUBLISHED',
        '{"cover":"removable","sizes":["M","L"]}'::jsonb,
        FALSE,
        CURRENT_TIMESTAMP - INTERVAL '1 day'
    )
ON CONFLICT (id) DO UPDATE SET
    brand_id = EXCLUDED.brand_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    short_description = EXCLUDED.short_description,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    attributes = EXCLUDED.attributes,
    featured = EXCLUDED.featured,
    published_at = EXCLUDED.published_at,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_variants (id, product_id, sku, name, price, stock_quantity, sort_order)
VALUES
    ('60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'DEMO-WATER-IVORY', '아이보리', 28000, 18, 10),
    ('60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', 'DEMO-WATER-MINT', '민트', 28000, 9, 20),
    ('60000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000002', 'DEMO-CARSEAT-BEIGE', '베이지', 89000, 7, 10),
    ('60000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000003', 'DEMO-TOWER-NATURAL', '내추럴', 249000, 3, 10),
    ('60000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000004', 'DEMO-BED-M', 'M', 69000, 12, 10),
    ('60000000-0000-0000-0000-000000000006', '50000000-0000-0000-0000-000000000004', 'DEMO-BED-L', 'L', 89000, 0, 20)
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    sku = EXCLUDED.sku,
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_images (id, product_id, storage_key, alt_text, sort_order)
VALUES
    ('70000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'demo/catalog/oasis-water-bowl.webp', '오아시스 세라믹 워터볼', 10),
    ('70000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000002', 'demo/catalog/cloud-fit-car-seat.webp', '클라우드핏 카시트', 10),
    ('70000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000003', 'demo/catalog/module-cat-step-tower.webp', '모듈 캣 스텝 타워', 10),
    ('70000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000004', 'demo/catalog/cozy-corner-pet-bed.webp', '포근 코너 펫 베드', 10)
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    storage_key = EXCLUDED.storage_key,
    alt_text = EXCLUDED.alt_text,
    sort_order = EXCLUDED.sort_order;

INSERT INTO product_categories (product_id, category_id)
VALUES
    ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001'),
    ('50000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002'),
    ('50000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000003'),
    ('50000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000004')
ON CONFLICT DO NOTHING;

-- Twenty additional fictional products complete the 24-product showroom catalogue.
WITH demo_products(n, slug, name, summary, description, price, stock, brand_no, category_no, species_code, featured) AS (
    VALUES
        (5,  'ripple-slow-feeder',       '리플 슬로우 피더',       '천천히 건강하게 먹는 시간을 돕는 곡선 식기',       '부드러운 굴곡으로 급하게 먹는 습관을 완화하는 데모 상품입니다.', 34000, 14, 1, 1, 'BOTH', FALSE),
        (6,  'breeze-water-fountain',    '브리즈 순환 급수기',     '조용하게 물을 순환시키는 미니멀 급수기',           '필터 교체가 쉽고 실내에 자연스럽게 놓이는 데모 상품입니다.', 62000, 8, 1, 1, 'BOTH', TRUE),
        (7,  'pebble-bowl-stand',        '페블 보울 스탠드',       '식사 높이를 편안하게 맞춘 원목 식기 스탠드',       '세라믹 보울 두 개와 낮은 원목 프레임으로 구성된 데모 상품입니다.', 48000, 0, 3, 1, 'BOTH', FALSE),
        (8,  'trail-comfort-harness',    '트레일 컴포트 하네스',   '몸을 안정적으로 감싸는 데일리 산책 하네스',        '길이 조절 스트랩과 통기성 패드를 적용한 데모 상품입니다.', 39000, 21, 2, 7, 'DOG', TRUE),
        (9,  'urban-window-carrier',     '어반 윈도 캐리어',       '이동 중 바깥을 볼 수 있는 구조의 소프트 캐리어',   '가벼운 프레임과 넉넉한 환기창을 갖춘 데모 상품입니다.', 118000, 5, 2, 2, 'BOTH', FALSE),
        (10, 'safe-ride-seat-belt',      '세이프 라이드 벨트',     '차량 안전고리에 연결하는 반려동물 전용 벨트',      '길이 조절과 이중 잠금 고리를 적용한 데모 상품입니다.', 24000, 30, 2, 2, 'DOG', FALSE),
        (11, 'hideaway-felt-house',      '하이드어웨이 펠트 하우스','숨고 쉬는 습성을 고려한 포근한 펠트 하우스',      '지퍼로 분리해 평평하게 보관할 수 있는 데모 상품입니다.', 79000, 9, 3, 3, 'CAT', TRUE),
        (12, 'arch-scratcher-bench',     '아치 스크래처 벤치',     '스크래칭과 휴식을 함께 즐기는 곡선형 벤치',        '교체 가능한 골판지 리필을 사용하는 데모 상품입니다.', 53000, 12, 3, 3, 'CAT', FALSE),
        (13, 'sunny-window-hammock',     '써니 윈도 해먹',         '창가 햇빛을 즐길 수 있는 흡착식 고양이 해먹',      '튼튼한 와이어와 세탁 가능한 패브릭의 데모 상품입니다.', 46000, 7, 3, 3, 'CAT', FALSE),
        (14, 'linen-grid-pet-mat',       '리넨 그리드 펫 매트',    '거실 어디에나 어울리는 차분한 체크 패턴 매트',     '커버를 분리해 세탁할 수 있는 사계절 데모 상품입니다.', 58000, 18, 3, 4, 'BOTH', FALSE),
        (15, 'calm-donut-bed',           '캄 도넛 베드',           '몸을 포근하게 감싸 안정감을 주는 원형 베드',       '복원력이 좋은 충전재와 미끄럼 방지 바닥의 데모 상품입니다.', 72000, 11, 3, 4, 'BOTH', TRUE),
        (16, 'summer-cooling-mat',       '서머 쿨링 매트',         '더운 날 체온 휴식을 돕는 접이식 쿨링 매트',        '전기 없이 사용하는 생활 방수 데모 상품입니다.', 42000, 26, 1, 4, 'BOTH', TRUE),
        (17, 'cloud-pocket-raincoat',    '클라우드 포켓 레인코트', '가볍게 접어 휴대하는 컬러 블록 레인코트',         '목과 가슴 둘레를 조절할 수 있는 데모 상품입니다.', 44000, 16, 4, 8, 'DOG', FALSE),
        (18, 'picnic-rope-leash',        '피크닉 로프 리드줄',     '손에 편안하게 감기는 투톤 로프 리드줄',            '회전 고리와 보조 손잡이를 더한 데모 상품입니다.', 31000, 34, 4, 7, 'DOG', FALSE),
        (19, 'mini-treat-pouch',         '미니 트릿 파우치',       '산책 중 간식과 배변봉투를 담는 작은 파우치',       '자석 여밈과 벨트 고리를 갖춘 데모 상품입니다.', 27000, 22, 4, 7, 'DOG', FALSE),
        (20, 'tofu-litter-box',          '두부 모래 화장실',       '모래 튐을 줄인 입구와 둥근 모서리의 화장실',       '분리 세척이 쉬운 대형 고양이 화장실 데모 상품입니다.', 98000, 6, 1, 6, 'CAT', TRUE),
        (21, 'clean-scoop-set',          '클린 스쿱 세트',         '거치대와 촘촘한 모래삽을 함께 구성한 세트',        '바닥에 닿지 않는 위생적인 구조의 데모 상품입니다.', 23000, 25, 1, 6, 'CAT', FALSE),
        (22, 'color-pop-litter-tray',    '컬러팝 리터 트레이',     '선명한 컬러로 공간에 포인트를 주는 배변 트레이',   '높은 측면과 낮은 진입부를 함께 갖춘 데모 상품입니다.', 68000, 4, 4, 6, 'CAT', FALSE),
        (23, 'soft-toy-basket',          '소프트 토이 바스켓',     '반려동물 장난감을 한곳에 담는 패브릭 바스켓',      '입구가 낮아 반려동물이 직접 꺼내기 쉬운 데모 상품입니다.', 36000, 15, 4, 5, 'BOTH', FALSE),
        (24, 'wool-play-tunnel',         '울 플레이 터널',        '숨고 달리는 놀이를 위한 접이식 패브릭 터널',      '소음을 줄인 도톰한 소재의 고양이 놀이 데모 상품입니다.', 64000, 10, 3, 5, 'CAT', TRUE)
)
INSERT INTO products (
    id, brand_id, slug, name, short_description, description, status, attributes, featured, published_at
)
SELECT
    ('50000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    ('20000000-0000-0000-0000-' || lpad(brand_no::text, 12, '0'))::uuid,
    slug, name, summary, description, 'PUBLISHED', '{}'::jsonb, featured,
    CURRENT_TIMESTAMP - make_interval(hours => (25 - n))
FROM demo_products
ON CONFLICT (id) DO UPDATE SET
    brand_id = EXCLUDED.brand_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    short_description = EXCLUDED.short_description,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    featured = EXCLUDED.featured,
    published_at = EXCLUDED.published_at,
    updated_at = CURRENT_TIMESTAMP;

WITH demo_products(n, price, stock) AS (
    VALUES
        (5,34000,14),(6,62000,8),(7,48000,0),(8,39000,21),(9,118000,5),
        (10,24000,30),(11,79000,9),(12,53000,12),(13,46000,7),(14,58000,18),
        (15,72000,11),(16,42000,26),(17,44000,16),(18,31000,34),(19,27000,22),
        (20,98000,6),(21,23000,25),(22,68000,4),(23,36000,15),(24,64000,10)
)
INSERT INTO product_variants (id, product_id, sku, name, price, stock_quantity, status, sort_order)
SELECT
    ('60000000-0000-0000-0000-' || lpad((100 + n)::text, 12, '0'))::uuid,
    ('50000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    'DEMO-ITEM-' || lpad(n::text, 2, '0'),
    '기본 옵션', price, stock, 'ACTIVE', 10
FROM demo_products
ON CONFLICT (id) DO UPDATE SET
    price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

WITH demo_products(n, category_no, species_code) AS (
    VALUES
        (5,1,'BOTH'),(6,1,'BOTH'),(7,1,'BOTH'),(8,7,'DOG'),(9,2,'BOTH'),
        (10,2,'DOG'),(11,3,'CAT'),(12,3,'CAT'),(13,3,'CAT'),(14,4,'BOTH'),
        (15,4,'BOTH'),(16,4,'BOTH'),(17,8,'DOG'),(18,7,'DOG'),(19,7,'DOG'),
        (20,6,'CAT'),(21,6,'CAT'),(22,6,'CAT'),(23,5,'BOTH'),(24,5,'CAT')
)
INSERT INTO product_categories (product_id, category_id)
SELECT
    ('50000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    ('40000000-0000-0000-0000-' || lpad(category_no::text, 12, '0'))::uuid
FROM demo_products
ON CONFLICT DO NOTHING;

WITH demo_products(n, species_code) AS (
    VALUES
        (5,'BOTH'),(6,'BOTH'),(7,'BOTH'),(8,'DOG'),(9,'BOTH'),(10,'DOG'),(11,'CAT'),
        (12,'CAT'),(13,'CAT'),(14,'BOTH'),(15,'BOTH'),(16,'BOTH'),(17,'DOG'),(18,'DOG'),
        (19,'DOG'),(20,'CAT'),(21,'CAT'),(22,'CAT'),(23,'BOTH'),(24,'CAT')
)
INSERT INTO product_species (product_id, species_id)
SELECT
    ('50000000-0000-0000-0000-' || lpad(d.n::text, 12, '0'))::uuid,
    s.id
FROM demo_products d
JOIN species s ON (d.species_code = 'BOTH' AND s.code IN ('DOG', 'CAT')) OR s.code = d.species_code
ON CONFLICT DO NOTHING;

WITH demo_products(n, source_image, name) AS (
    VALUES
        (5,'demo/catalog/oasis-water-bowl.webp','리플 슬로우 피더'),
        (6,'demo/catalog/oasis-water-bowl.webp','브리즈 순환 급수기'),
        (7,'demo/catalog/oasis-water-bowl.webp','페블 보울 스탠드'),
        (8,'demo/catalog/cloud-fit-car-seat.webp','트레일 컴포트 하네스'),
        (9,'demo/catalog/cloud-fit-car-seat.webp','어반 윈도 캐리어'),
        (10,'demo/catalog/cloud-fit-car-seat.webp','세이프 라이드 벨트'),
        (11,'demo/catalog/module-cat-step-tower.webp','하이드어웨이 펠트 하우스'),
        (12,'demo/catalog/module-cat-step-tower.webp','아치 스크래처 벤치'),
        (13,'demo/catalog/module-cat-step-tower.webp','써니 윈도 해먹'),
        (14,'demo/catalog/cozy-corner-pet-bed.webp','리넨 그리드 펫 매트'),
        (15,'demo/catalog/cozy-corner-pet-bed.webp','캄 도넛 베드'),
        (16,'demo/catalog/cozy-corner-pet-bed.webp','서머 쿨링 매트'),
        (17,'demo/catalog/cloud-fit-car-seat.webp','클라우드 포켓 레인코트'),
        (18,'demo/catalog/cloud-fit-car-seat.webp','피크닉 로프 리드줄'),
        (19,'demo/catalog/cloud-fit-car-seat.webp','미니 트릿 파우치'),
        (20,'demo/catalog/module-cat-step-tower.webp','두부 모래 화장실'),
        (21,'demo/catalog/module-cat-step-tower.webp','클린 스쿱 세트'),
        (22,'demo/catalog/module-cat-step-tower.webp','컬러팝 리터 트레이'),
        (23,'demo/catalog/cozy-corner-pet-bed.webp','소프트 토이 바스켓'),
        (24,'demo/catalog/module-cat-step-tower.webp','울 플레이 터널')
)
INSERT INTO product_images (id, product_id, storage_key, alt_text, sort_order)
SELECT
    ('70000000-0000-0000-0000-' || lpad((100 + n)::text, 12, '0'))::uuid,
    ('50000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    source_image, name, 10
FROM demo_products
ON CONFLICT (id) DO UPDATE SET
    storage_key = EXCLUDED.storage_key,
    alt_text = EXCLUDED.alt_text,
    sort_order = EXCLUDED.sort_order;

INSERT INTO product_species (product_id, species_id)
VALUES
    ('50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001'),
    ('50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002'),
    ('50000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001'),
    ('50000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000002'),
    ('50000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000001'),
    ('50000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;
