-- V1__initial_schema.sql
-- Initial Weinbot database schema with fungicide management

-- Vineyard table (existing concept)
CREATE TABLE IF NOT EXISTS vineyards (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    size_ares DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    region VARCHAR(255),
    description TEXT,
    last_spray_date TIMESTAMP,
    growth_stage VARCHAR(10),
    is_manual_growth_stage BOOLEAN DEFAULT FALSE,
    growth_stage_last_updated TIMESTAMP,
    accumulated_gdd DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Fungal Disease table (existing concept)
CREATE TABLE IF NOT EXISTS fungal_diseases (
    id BIGSERIAL PRIMARY KEY,
    common_name VARCHAR(100) NOT NULL UNIQUE,
    scientific_name VARCHAR(255),
    german_name VARCHAR(100),
    temp_min_c DOUBLE PRECISION,
    temp_max_c DOUBLE PRECISION,
    humidity_min_percent DOUBLE PRECISION,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- FRAC Code table - Fungicide Resistance Action Committee classifications
CREATE TABLE IF NOT EXISTS frac_code (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    chemical_class VARCHAR(100),
    description TEXT,
    resistance_risk_level VARCHAR(20) CHECK (resistance_risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Fungicide Product table
CREATE TABLE IF NOT EXISTS fungicide_product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    active_substance VARCHAR(255) NOT NULL,
    concentration_percent DOUBLE PRECISION,
    manufacturer_name VARCHAR(255),
    frac_code_id BIGINT NOT NULL REFERENCES frac_code(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name)
);

-- Fungicide Approval table - Regional approvals and usage rules
CREATE TABLE IF NOT EXISTS fungicide_approval (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES fungicide_product(id) ON DELETE CASCADE,
    region VARCHAR(100) NOT NULL,
    approval_valid_from DATE,
    approval_valid_until DATE,
    phi_days_before_harvest INTEGER,
    max_dosage_ml_per_100l DOUBLE PRECISION,
    approval_status VARCHAR(20) CHECK (approval_status IN ('ACTIVE', 'PENDING', 'EXPIRED')),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, region)
);

-- Fungicide Target Disease table - What each product targets
CREATE TABLE IF NOT EXISTS fungicide_target_disease (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES fungicide_product(id) ON DELETE CASCADE,
    disease_id BIGINT NOT NULL REFERENCES fungal_diseases(id) ON DELETE CASCADE,
    recommended_dosage_ml_per_100l DOUBLE PRECISION NOT NULL,
    efficacy_rating INTEGER CHECK (efficacy_rating >= 0 AND efficacy_rating <= 5),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, disease_id)
);

-- Rotation Strategy table - Resistance management rules per disease
CREATE TABLE IF NOT EXISTS rotation_strategy (
    id BIGSERIAL PRIMARY KEY,
    disease_id BIGINT NOT NULL REFERENCES fungal_diseases(id) ON DELETE CASCADE,
    recommended_frac_codes VARCHAR(255),
    min_days_before_repeating_class INTEGER,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(disease_id)
);

-- Weather Data table (existing concept)
CREATE TABLE IF NOT EXISTS weather_data (
    id BIGSERIAL PRIMARY KEY,
    vineyard_id BIGINT NOT NULL REFERENCES vineyards(id) ON DELETE CASCADE,
    recorded_at TIMESTAMP NOT NULL,
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    precipitation_mm DOUBLE PRECISION,
    wind_speed_msec DOUBLE PRECISION,
    leaf_wetness_index DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Risk Assessment table (existing concept)
CREATE TABLE IF NOT EXISTS risk_assessment (
    id BIGSERIAL PRIMARY KEY,
    vineyard_id BIGINT NOT NULL REFERENCES vineyards(id) ON DELETE CASCADE,
    disease_id BIGINT NOT NULL REFERENCES fungal_diseases(id) ON DELETE CASCADE,
    assessed_at TIMESTAMP NOT NULL,
    risk_score DOUBLE PRECISION,
    risk_level VARCHAR(20),
    recommendation TEXT,
    calculation_breakdown TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Spray Application table (existing concept)
CREATE TABLE IF NOT EXISTS spray_application (
    id BIGSERIAL PRIMARY KEY,
    vineyard_id BIGINT NOT NULL REFERENCES vineyards(id) ON DELETE CASCADE,
    fungicide_id BIGINT NOT NULL REFERENCES fungicide_product(id),
    disease_id BIGINT NOT NULL REFERENCES fungal_diseases(id),
    application_date TIMESTAMP NOT NULL,
    growth_stage_bbch VARCHAR(10),
    dosage_liters_per_are DOUBLE PRECISION,
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    wind_speed_msec DOUBLE PRECISION,
    notes TEXT,
    efficacy_assessment DOUBLE PRECISION,
    efficacy_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Growth Stage table - BBCH stages and thresholds
CREATE TABLE IF NOT EXISTS growth_stage (
    id BIGSERIAL PRIMARY KEY,
    vineyard_id BIGINT NOT NULL REFERENCES vineyards(id) ON DELETE CASCADE,
    bbch_stage VARCHAR(10),
    stage_name VARCHAR(100),
    gdd_accumulated DOUBLE PRECISION,
    recorded_date DATE,
    is_manual_override BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_weather_vineyard_id ON weather_data(vineyard_id);
CREATE INDEX idx_weather_recorded_at ON weather_data(recorded_at);
CREATE INDEX idx_risk_vineyard_disease ON risk_assessment(vineyard_id, disease_id);
CREATE INDEX idx_risk_assessed_at ON risk_assessment(assessed_at);
CREATE INDEX idx_spray_vineyard_date ON spray_application(vineyard_id, application_date);
CREATE INDEX idx_fungicide_approval_region ON fungicide_approval(region);
CREATE INDEX idx_fungicide_target_disease ON fungicide_target_disease(product_id, disease_id);
CREATE INDEX idx_growth_stage_vineyard ON growth_stage(vineyard_id);

-- Initial data: Fungal Diseases
INSERT INTO fungal_diseases (common_name, german_name, description) VALUES
    ('Peronospora', 'Falscher Mehltau', 'Downy mildew - requires high humidity and cool temperatures'),
    ('Oidium', 'Echter Mehltau', 'Powdery mildew - thrives in warm, dry conditions')
ON CONFLICT (common_name) DO NOTHING;

-- Initial data: FRAC Codes (Fungicide Resistance Action Committee)
INSERT INTO frac_code (code, chemical_class, description, resistance_risk_level) VALUES
    ('M', 'Multi-site contact', 'Contact fungicides, low resistance risk', 'LOW'),
    ('3', 'Demethylation inhibitors (DMI)', 'Triazoles, ergosterol biosynthesis inhibitors', 'MEDIUM'),
    ('6', 'Morpholines', 'Sterol esterification inhibitors', 'MEDIUM'),
    ('7', 'Amines', 'Sterol dealkylation inhibitors', 'MEDIUM'),
    ('11', 'QoI fungicides', 'Quinone outside inhibitors, mitochondrial respiration', 'HIGH'),
    ('U', 'Oomycete cell wall biosynthesis', 'Active against Peronospora', 'LOW'),
    ('C', 'Acylamine compounds', 'Oomycete wall biosynthesis', 'MEDIUM')
ON CONFLICT (code) DO NOTHING;

-- Initial data: Fungicide Products
INSERT INTO fungicide_product (name, active_substance, concentration_percent, manufacturer_name, frac_code_id) VALUES
    ('Bordeaux Mixture', 'Copper sulfate/Lime', 50.0, 'Various', (SELECT id FROM frac_code WHERE code = 'M')),
    ('Sulfur Powder', 'Sulfur', 95.0, 'Various', (SELECT id FROM frac_code WHERE code = 'M')),
    ('Folpet', 'Folpet', 50.0, 'Syngenta', (SELECT id FROM frac_code WHERE code = 'M')),
    ('Mancozeb', 'Mancozeb', 80.0, 'Various', (SELECT id FROM frac_code WHERE code = 'M')),
    ('Tebuconazole', 'Tebuconazole', 25.0, 'Bayer', (SELECT id FROM frac_code WHERE code = '3')),
    ('Penconazole', 'Penconazole', 10.0, 'Syngenta', (SELECT id FROM frac_code WHERE code = '3')),
    ('Triadimefon', 'Triadimefon', 50.0, 'Various', (SELECT id FROM frac_code WHERE code = '3')),
    ('Cymoxanil', 'Cymoxanil', 8.0, 'Dupont', (SELECT id FROM frac_code WHERE code = 'U')),
    ('Amisulbrom', 'Amisulbrom', 20.0, 'Valent', (SELECT id FROM frac_code WHERE code = 'U')),
    ('Metalaxyl', 'Metalaxyl', 8.0, 'Syngenta', (SELECT id FROM frac_code WHERE code = 'C'))
ON CONFLICT (name) DO NOTHING;

-- Initial data: Fungicide Target Diseases (Peronospora and Oidium)
-- Peronospora (Disease ID 1) targets
INSERT INTO fungicide_target_disease (product_id, disease_id, recommended_dosage_ml_per_100l, efficacy_rating, notes) VALUES
    ((SELECT id FROM fungicide_product WHERE name = 'Bordeaux Mixture'), 1, 200.0, 4, 'Traditional copper-based fungicide'),
    ((SELECT id FROM fungicide_product WHERE name = 'Folpet'), 1, 150.0, 4, 'Protectant fungicide'),
    ((SELECT id FROM fungicide_product WHERE name = 'Mancozeb'), 1, 200.0, 4, 'Dithiocarbamate fungicide'),
    ((SELECT id FROM fungicide_product WHERE name = 'Cymoxanil'), 1, 75.0, 5, 'Excellent for Peronospora control'),
    ((SELECT id FROM fungicide_product WHERE name = 'Amisulbrom'), 1, 100.0, 5, 'Modern QiI fungicide'),
    ((SELECT id FROM fungicide_product WHERE name = 'Metalaxyl'), 1, 80.0, 4, 'Systemic fungicide for Oomycetes')
ON CONFLICT (product_id, disease_id) DO NOTHING;

-- Oidium (Disease ID 2) targets
INSERT INTO fungicide_target_disease (product_id, disease_id, recommended_dosage_ml_per_100l, efficacy_rating, notes) VALUES
    ((SELECT id FROM fungicide_product WHERE name = 'Sulfur Powder'), 2, 500.0, 5, 'Most effective for Oidium'),
    ((SELECT id FROM fungicide_product WHERE name = 'Bordeaux Mixture'), 2, 200.0, 3, 'Some efficacy against Oidium'),
    ((SELECT id FROM fungicide_product WHERE name = 'Tebuconazole'), 2, 75.0, 4, 'DMI fungicide effective for Oidium'),
    ((SELECT id FROM fungicide_product WHERE name = 'Penconazole'), 2, 60.0, 4, 'DMI alternative for Oidium'),
    ((SELECT id FROM fungicide_product WHERE name = 'Triadimefon'), 2, 100.0, 3, 'Older DMI fungicide')
ON CONFLICT (product_id, disease_id) DO NOTHING;

-- Initial data: Fungicide Approvals (Germany region)
INSERT INTO fungicide_approval (product_id, region, approval_status, phi_days_before_harvest, max_dosage_ml_per_100l, notes) VALUES
    ((SELECT id FROM fungicide_product WHERE name = 'Bordeaux Mixture'), 'Germany', 'ACTIVE', 7, 200.0, 'Approved for organic viticulture'),
    ((SELECT id FROM fungicide_product WHERE name = 'Sulfur Powder'), 'Germany', 'ACTIVE', 14, 500.0, 'Approved for organic viticulture'),
    ((SELECT id FROM fungicide_product WHERE name = 'Folpet'), 'Germany', 'ACTIVE', 28, 150.0, 'BVL approved'),
    ((SELECT id FROM fungicide_product WHERE name = 'Mancozeb'), 'Germany', 'ACTIVE', 35, 200.0, 'BVL approved'),
    ((SELECT id FROM fungicide_product WHERE name = 'Tebuconazole'), 'Germany', 'ACTIVE', 14, 75.0, 'BVL approved'),
    ((SELECT id FROM fungicide_product WHERE name = 'Penconazole'), 'Germany', 'ACTIVE', 21, 60.0, 'BVL approved'),
    ((SELECT id FROM fungicide_product WHERE name = 'Triadimefon'), 'Germany', 'EXPIRED', 28, 100.0, 'Approval expired 2022'),
    ((SELECT id FROM fungicide_product WHERE name = 'Cymoxanil'), 'Germany', 'ACTIVE', 3, 75.0, 'Used in mixtures'),
    ((SELECT id FROM fungicide_product WHERE name = 'Amisulbrom'), 'Germany', 'ACTIVE', 14, 100.0, 'BVL approved'),
    ((SELECT id FROM fungicide_product WHERE name = 'Metalaxyl'), 'Germany', 'ACTIVE', 14, 80.0, 'Approved for Oomycetes')
ON CONFLICT (product_id, region) DO NOTHING;

-- Initial data: Rotation Strategies
INSERT INTO rotation_strategy (disease_id, recommended_frac_codes, min_days_before_repeating_class, description) VALUES
    (1, 'M,U,C', 14, 'Peronospora: Alternate between contact (M), cell wall inhibitors (U), and acylamines (C) to prevent resistance'),
    (2, 'M,3', 21, 'Oidium: Alternate between sulfur (M) and DMI fungicides (3) to prevent resistance')
ON CONFLICT (disease_id) DO NOTHING;
