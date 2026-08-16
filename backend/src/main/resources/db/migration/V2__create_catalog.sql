CREATE TABLE brands (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_brands_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
);

CREATE TABLE species (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_species_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES categories (id) ON DELETE RESTRICT,
    slug VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    brand_id UUID NOT NULL REFERENCES brands (id) ON DELETE RESTRICT,
    slug VARCHAR(160) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(500),
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN', 'DISCONTINUED'))
);

CREATE INDEX ix_products_public_list ON products (status, featured DESC, published_at DESC);
CREATE INDEX ix_products_brand ON products (brand_id, status);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    price BIGINT NOT NULL,
    stock_quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_variants_price CHECK (price >= 0),
    CONSTRAINT ck_product_variants_stock CHECK (stock_quantity >= 0),
    CONSTRAINT ck_product_variants_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uq_product_variant_name UNIQUE (product_id, name)
);

CREATE INDEX ix_product_variants_product ON product_variants (product_id, status, sort_order);

CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    storage_key VARCHAR(500) NOT NULL,
    alt_text VARCHAR(300) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_image_order UNIQUE (product_id, sort_order)
);

CREATE TABLE product_categories (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    PRIMARY KEY (product_id, category_id)
);

CREATE TABLE product_species (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    species_id UUID NOT NULL REFERENCES species (id) ON DELETE RESTRICT,
    PRIMARY KEY (product_id, species_id)
);
