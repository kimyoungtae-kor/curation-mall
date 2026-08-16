-- Fictional merchandising data for local and test profiles only.
INSERT INTO collections (
    id,
    slug,
    title,
    description,
    hero_storage_key,
    hero_alt_text,
    status,
    featured,
    sort_order,
    published_at,
    expires_at
)
VALUES
    (
        '80000000-0000-0000-0000-000000000001',
        'summer-hydration',
        '여름철 반려동물 음수량 늘리기',
        '마시는 습관을 돕는 가상 데모 아이템을 모았습니다.',
        'demo/home/summer-hydration.webp',
        '햇살이 드는 거실의 반려동물 음수 공간',
        'PUBLISHED',
        TRUE,
        10,
        CURRENT_TIMESTAMP - INTERVAL '3 days',
        NULL
    ),
    (
        '80000000-0000-0000-0000-000000000002',
        'safe-road-trip',
        '안전한 차량 탑승을 위한 베스트 아이템',
        '안정적인 이동과 편안한 휴식을 돕는 가상 데모 큐레이션입니다.',
        'demo/home/safe-road-trip.webp',
        '차량 안에 안전하게 설치된 반려동물 카시트',
        'PUBLISHED',
        TRUE,
        20,
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        NULL
    ),
    (
        '80000000-0000-0000-0000-000000000003',
        'calm-pet-room',
        '함께 머무는 차분한 펫룸',
        '반려동물의 행동과 사람의 공간을 함께 생각한 가상 데모 큐레이션입니다.',
        'demo/home/calm-pet-room.webp',
        '차분한 색감의 거실에 놓인 반려동물 가구',
        'PUBLISHED',
        TRUE,
        30,
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        NULL
    )
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    hero_storage_key = EXCLUDED.hero_storage_key,
    hero_alt_text = EXCLUDED.hero_alt_text,
    status = EXCLUDED.status,
    featured = EXCLUDED.featured,
    sort_order = EXCLUDED.sort_order,
    published_at = EXCLUDED.published_at,
    expires_at = EXCLUDED.expires_at,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO collection_products (id, collection_id, product_id, sort_order)
VALUES
    (
        '80100000-0000-0000-0000-000000000001',
        '80000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000001',
        10
    ),
    (
        '80100000-0000-0000-0000-000000000002',
        '80000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000004',
        20
    ),
    (
        '80100000-0000-0000-0000-000000000003',
        '80000000-0000-0000-0000-000000000002',
        '50000000-0000-0000-0000-000000000002',
        10
    ),
    (
        '80100000-0000-0000-0000-000000000004',
        '80000000-0000-0000-0000-000000000003',
        '50000000-0000-0000-0000-000000000003',
        10
    ),
    (
        '80100000-0000-0000-0000-000000000005',
        '80000000-0000-0000-0000-000000000003',
        '50000000-0000-0000-0000-000000000004',
        20
    )
ON CONFLICT (id) DO UPDATE SET
    collection_id = EXCLUDED.collection_id,
    product_id = EXCLUDED.product_id,
    sort_order = EXCLUDED.sort_order;

INSERT INTO home_sections (id, section_key, title, content, sort_order)
VALUES
    (
        '81000000-0000-0000-0000-000000000001',
        'ANNOUNCEMENT_HEADER',
        '공지와 탐색',
        '{"announcementText":"5만원 이상 무료배송","linkType":"HELP","linkValue":"shipping-returns"}'::jsonb,
        1
    ),
    (
        '81000000-0000-0000-0000-000000000002',
        'HERO',
        '이번 주 큐레이션',
        '{}'::jsonb,
        2
    ),
    (
        '81000000-0000-0000-0000-000000000003',
        'FEATURED_COLLECTIONS',
        '상황별 기획전',
        '{}'::jsonb,
        3
    ),
    (
        '81000000-0000-0000-0000-000000000004',
        'PRODUCT_SHOWCASE',
        '인기상품과 신상품',
        '{}'::jsonb,
        4
    ),
    (
        '81000000-0000-0000-0000-000000000005',
        'EXPLORE',
        '취향대로 둘러보기',
        '{}'::jsonb,
        5
    ),
    (
        '81000000-0000-0000-0000-000000000006',
        'LIFESTYLE',
        '함께 사는 공간',
        '{}'::jsonb,
        6
    ),
    (
        '81000000-0000-0000-0000-000000000007',
        'SERVICE_GUIDE',
        '서비스 안내',
        '{"shippingFee":3000,"freeShippingThreshold":50000,"links":["shipping-returns","terms","privacy"]}'::jsonb,
        7
    )
ON CONFLICT (id) DO UPDATE SET
    section_key = EXCLUDED.section_key,
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO home_hero_slides (
    id,
    section_id,
    title,
    description,
    image_storage_key,
    image_alt_text,
    link_type,
    link_value,
    status,
    sort_order,
    published_at,
    expires_at
)
VALUES
    (
        '82000000-0000-0000-0000-000000000001',
        '81000000-0000-0000-0000-000000000002',
        '시원한 한 모금의 습관',
        '여름철 음수 아이템을 만나보세요.',
        'demo/home/summer-hydration.webp',
        '햇살이 드는 거실의 반려동물 음수 공간',
        'COLLECTION',
        'summer-hydration',
        'PUBLISHED',
        1,
        CURRENT_TIMESTAMP - INTERVAL '3 days',
        NULL
    ),
    (
        '82000000-0000-0000-0000-000000000002',
        '81000000-0000-0000-0000-000000000002',
        '함께 떠나는 안전한 드라이브',
        '차량 이동을 편안하게 만드는 아이템을 골랐습니다.',
        'demo/home/safe-road-trip.webp',
        '차량 안에 안전하게 설치된 반려동물 카시트',
        'COLLECTION',
        'safe-road-trip',
        'PUBLISHED',
        2,
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        NULL
    ),
    (
        '82000000-0000-0000-0000-000000000003',
        '81000000-0000-0000-0000-000000000002',
        '집 안에 만드는 작은 휴식처',
        '가구와 반려생활이 자연스럽게 이어지는 공간을 제안합니다.',
        'demo/home/calm-pet-room.webp',
        '차분한 색감의 거실에 놓인 반려동물 가구',
        'COLLECTION',
        'calm-pet-room',
        'PUBLISHED',
        3,
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        NULL
    )
ON CONFLICT (id) DO UPDATE SET
    section_id = EXCLUDED.section_id,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    image_storage_key = EXCLUDED.image_storage_key,
    image_alt_text = EXCLUDED.image_alt_text,
    link_type = EXCLUDED.link_type,
    link_value = EXCLUDED.link_value,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    published_at = EXCLUDED.published_at,
    expires_at = EXCLUDED.expires_at,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO home_lifestyle_contents (
    id,
    section_id,
    title,
    description,
    image_storage_key,
    image_alt_text,
    link_type,
    link_value,
    status,
    sort_order,
    published_at,
    expires_at
)
VALUES (
    '83000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000006',
    '반려생활도 인테리어의 일부니까',
    '오르고 숨고 쉬는 행동을 공간의 리듬과 함께 살펴보세요.',
    'demo/home/calm-pet-room.webp',
    '차분한 색감의 거실에 놓인 반려동물 가구',
    'COLLECTION',
    'calm-pet-room',
    'PUBLISHED',
    10,
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    NULL
)
ON CONFLICT (id) DO UPDATE SET
    section_id = EXCLUDED.section_id,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    image_storage_key = EXCLUDED.image_storage_key,
    image_alt_text = EXCLUDED.image_alt_text,
    link_type = EXCLUDED.link_type,
    link_value = EXCLUDED.link_value,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    published_at = EXCLUDED.published_at,
    expires_at = EXCLUDED.expires_at,
    updated_at = CURRENT_TIMESTAMP;
