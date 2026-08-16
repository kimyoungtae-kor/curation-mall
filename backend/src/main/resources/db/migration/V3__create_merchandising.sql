CREATE TABLE collections (
    id UUID PRIMARY KEY,
    slug VARCHAR(160) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    hero_storage_key VARCHAR(500) NOT NULL,
    hero_alt_text VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_collections_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    CONSTRAINT ck_collections_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_collections_publication_period CHECK (
        published_at IS NULL OR expires_at IS NULL OR expires_at > published_at
    )
);

CREATE INDEX ix_collections_public_list
    ON collections (status, featured, sort_order, published_at, expires_at);

CREATE TABLE collection_products (
    id UUID PRIMARY KEY,
    collection_id UUID NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_collection_products_sort_order CHECK (sort_order >= 0),
    CONSTRAINT uq_collection_products_product UNIQUE (collection_id, product_id),
    CONSTRAINT uq_collection_products_order UNIQUE (collection_id, sort_order)
);

CREATE INDEX ix_collection_products_product ON collection_products (product_id);

CREATE TABLE home_sections (
    id UUID PRIMARY KEY,
    section_key VARCHAR(40) NOT NULL UNIQUE,
    title VARCHAR(200),
    content JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INTEGER NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_home_sections_key CHECK (section_key IN (
        'ANNOUNCEMENT_HEADER',
        'HERO',
        'FEATURED_COLLECTIONS',
        'PRODUCT_SHOWCASE',
        'EXPLORE',
        'LIFESTYLE',
        'SERVICE_GUIDE'
    )),
    CONSTRAINT ck_home_sections_sort_order CHECK (sort_order BETWEEN 1 AND 7)
);

CREATE TABLE home_hero_slides (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES home_sections (id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL,
    image_storage_key VARCHAR(500) NOT NULL,
    image_alt_text VARCHAR(300) NOT NULL,
    link_type VARCHAR(20) NOT NULL,
    link_value VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sort_order INTEGER NOT NULL,
    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_home_hero_slides_link_type CHECK (
        link_type IN ('COLLECTION', 'PRODUCT', 'CONTENT', 'HELP')
    ),
    CONSTRAINT ck_home_hero_slides_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    CONSTRAINT ck_home_hero_slides_slot CHECK (sort_order BETWEEN 1 AND 3),
    CONSTRAINT ck_home_hero_slides_publication_period CHECK (
        published_at IS NULL OR expires_at IS NULL OR expires_at > published_at
    ),
    CONSTRAINT uq_home_hero_slides_slot UNIQUE (section_id, sort_order)
);

CREATE INDEX ix_home_hero_slides_public_list
    ON home_hero_slides (status, sort_order, published_at, expires_at);

CREATE TABLE home_lifestyle_contents (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES home_sections (id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL,
    image_storage_key VARCHAR(500) NOT NULL,
    image_alt_text VARCHAR(300) NOT NULL,
    link_type VARCHAR(20) NOT NULL,
    link_value VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sort_order INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_home_lifestyle_contents_link_type CHECK (
        link_type IN ('COLLECTION', 'PRODUCT', 'CONTENT', 'HELP')
    ),
    CONSTRAINT ck_home_lifestyle_contents_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    CONSTRAINT ck_home_lifestyle_contents_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_home_lifestyle_contents_publication_period CHECK (
        published_at IS NULL OR expires_at IS NULL OR expires_at > published_at
    ),
    CONSTRAINT uq_home_lifestyle_contents_order UNIQUE (section_id, sort_order)
);

CREATE INDEX ix_home_lifestyle_contents_public_list
    ON home_lifestyle_contents (status, sort_order, published_at, expires_at);
