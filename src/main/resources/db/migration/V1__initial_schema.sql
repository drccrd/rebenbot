-- V1__initial_schema.sql
-- Consolidated schema: tables + authoritative seed data
-- German product-level registration (BVL) is synced via POST /api/v1/admin/sync/bvl-api.

-- -----------------------------------------------------------------------
-- Tables
-- -----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS vineyards (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    size_ares DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    region VARCHAR(255),
    description TEXT,
    last_spray_date TIMESTAMP,
    growth_stage VARCHAR(50),
    is_manual_growth_stage BOOLEAN DEFAULT FALSE,
    growth_stage_last_updated TIMESTAMP,
    accumulated_gdd DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fungal_diseases (
    id BIGSERIAL PRIMARY KEY,
    common_name VARCHAR(100) NOT NULL UNIQUE,
    scientific_name VARCHAR(255),
    german_name VARCHAR(100),
    eppo_code VARCHAR(20),
    temp_min_c DOUBLE PRECISION,
    temp_max_c DOUBLE PRECISION,
    humidity_min_percent DOUBLE PRECISION,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS frac_code (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    chemical_class VARCHAR(100),
    description TEXT,
    resistance_risk_level VARCHAR(20) CHECK (resistance_risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fungicide_product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    active_substance VARCHAR(255) NOT NULL,
    concentration_percent DOUBLE PRECISION,
    manufacturer_name VARCHAR(255),
    base_dosage_ml_ha DOUBLE PRECISION,
    phi_days INTEGER,
    frac_code_id BIGINT NOT NULL REFERENCES frac_code(id),
    -- German product-level authorisation tracking (source: BVL PSM-API)
    bvl_registration_number VARCHAR(50),
    bvl_approved_in_germany BOOLEAN DEFAULT FALSE,
    bvl_approval_expiry DATE,
    bvl_last_verified DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fungicide_target_disease (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES fungicide_product(id) ON DELETE CASCADE,
    disease_id BIGINT NOT NULL REFERENCES fungal_diseases(id) ON DELETE CASCADE,
    efficacy_rating INTEGER CHECK (efficacy_rating >= 0 AND efficacy_rating <= 5),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, disease_id)
);

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

CREATE TABLE IF NOT EXISTS weather_data (
    id BIGSERIAL PRIMARY KEY,
    vineyard_id BIGINT NOT NULL REFERENCES vineyards(id) ON DELETE CASCADE,
    recorded_at TIMESTAMP NOT NULL,
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    precipitation_mm DOUBLE PRECISION,
    wind_speed_msec DOUBLE PRECISION,
    leaf_wetness_index DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(vineyard_id, recorded_at)
);

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

CREATE TABLE IF NOT EXISTS vineyard_log_entry (
    id BIGSERIAL PRIMARY KEY,
    vineyard_id BIGINT NOT NULL REFERENCES vineyards(id) ON DELETE CASCADE,
    log_type VARCHAR(50) NOT NULL CHECK (log_type IN ('SPRAY', 'OBSERVATION', 'WEATHER', 'PEST_DISEASE', 'MAINTENANCE', 'HARVEST', 'OTHER')),
    entry_date TIMESTAMP NOT NULL,
    fungicide_id BIGINT REFERENCES fungicide_product(id),
    disease_id BIGINT REFERENCES fungal_diseases(id),
    dosage_liters_per_are DOUBLE PRECISION,
    amount_fungicide_applied_liters DOUBLE PRECISION,
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    wind_speed_msec DOUBLE PRECISION,
    efficacy_assessment DOUBLE PRECISION,
    efficacy_notes TEXT,
    title VARCHAR(255),
    description TEXT,
    entry_type VARCHAR(50) CHECK (entry_type IN ('OBSERVATION', 'WEATHER', 'PEST_DISEASE', 'MAINTENANCE', 'HARVEST', 'OTHER')),
    tags VARCHAR(500),
    growth_stage_bbch VARCHAR(10),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wbi_prognosis (
    id BIGSERIAL PRIMARY KEY,
    forecast_date DATE NOT NULL,
    disease VARCHAR(50) NOT NULL,              -- 'peronospora' or 'oidium'
    risk_level VARCHAR(50) NOT NULL,           -- NO_INFECTION, LOW, INFECTION_RISK, HIGH
    risk_color VARCHAR(30),                    -- raw color string from vitimeteo (e.g. 'lime', '#FFAAAA')
    risk_score DOUBLE PRECISION,              -- InfektionsstärkeIndex (peronospora) or OidiumIndex (oidium)
    is_forecast BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Peronospora-specific summary columns
    soil_infection_count INTEGER,             -- Bodeninfektion daily count
    infection_event_count INTEGER,            -- Infektionen daily count
    sporulation_count INTEGER,                -- Sporulationen daily count
    leaf_wetness_hours DOUBLE PRECISION,
    leaf_wetness_degree_hours DOUBLE PRECISION,
    active_incubation_events INTEGER,         -- number of ongoing Inkubation series at this date
    next_spray_deadline DATE,                 -- earliest date any active incubation reaches 80%
    last_sporulation_date DATE,               -- most recent Sporulation event date
    -- Oidium-specific summary columns
    oidium_index DOUBLE PRECISION,
    ontogenetic_index DOUBLE PRECISION,
    oidium_daily_value DOUBLE PRECISION,
    UNIQUE(forecast_date, disease)
);

-- -----------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------

CREATE INDEX idx_weather_vineyard_id ON weather_data(vineyard_id);
CREATE INDEX idx_weather_recorded_at ON weather_data(recorded_at);
CREATE INDEX idx_risk_vineyard_disease ON risk_assessment(vineyard_id, disease_id);
CREATE INDEX idx_risk_assessed_at ON risk_assessment(assessed_at);
CREATE INDEX idx_log_entry_vineyard_date ON vineyard_log_entry(vineyard_id, entry_date DESC);
CREATE INDEX idx_log_entry_log_type ON vineyard_log_entry(vineyard_id, log_type);
CREATE INDEX idx_log_entry_entry_type ON vineyard_log_entry(vineyard_id, entry_type);
CREATE INDEX idx_log_entry_tags ON vineyard_log_entry(tags);
CREATE INDEX idx_log_entry_fungicide ON vineyard_log_entry(fungicide_id);
CREATE INDEX idx_fungicide_target_disease ON fungicide_target_disease(product_id, disease_id);
CREATE INDEX idx_growth_stage_vineyard ON growth_stage(vineyard_id);
CREATE INDEX idx_wbi_disease_date ON wbi_prognosis(disease, forecast_date DESC);
CREATE INDEX idx_wbi_created_at ON wbi_prognosis(created_at);

CREATE TABLE IF NOT EXISTS peronospora_infection_event (
    id BIGSERIAL PRIMARY KEY,
    series_id VARCHAR(30) NOT NULL UNIQUE,    -- e.g. 'series_7016_5' from expert_data.json
    infection_datetime TIMESTAMP NOT NULL,    -- first timestamp in the Inkubation series
    incubation_pct_latest DOUBLE PRECISION,   -- most recent incubation % value
    incubation_80pct_datetime TIMESTAMP,      -- estimated datetime incubation reaches 80% (spray deadline)
    sporulation_datetime TIMESTAMP,           -- datetime incubation reached/will reach 100%
    is_active BOOLEAN NOT NULL DEFAULT TRUE,  -- false once sporulation is complete
    fetched_date DATE NOT NULL                -- date this row was last updated from the API
);

CREATE TABLE IF NOT EXISTS vitimeteo_pheno_daily (
    id BIGSERIAL PRIMARY KEY,
    pheno_date DATE NOT NULL UNIQUE,
    bbch_code INTEGER,                        -- BBCH phenological stage from vitimeteo model
    huglin_index DOUBLE PRECISION,            -- accumulated Huglin heliothermal index
    leaf_count DOUBLE PRECISION,
    leaf_area_cm2 DOUBLE PRECISION,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pero_event_series ON peronospora_infection_event(series_id);
CREATE INDEX idx_pero_event_active ON peronospora_infection_event(is_active, infection_datetime DESC);
CREATE INDEX idx_pheno_date ON vitimeteo_pheno_daily(pheno_date DESC);

-- -----------------------------------------------------------------------
-- Seed: Fungal diseases
-- common_name values are matched by Java service logic (RiskAssessmentService)
-- using equalsIgnoreCase — do not change them.
-- -----------------------------------------------------------------------

INSERT INTO fungal_diseases (common_name, scientific_name, german_name, eppo_code, temp_min_c, temp_max_c, humidity_min_percent, description) VALUES
(
    'Peronospora', 'Plasmopara viticola', 'Falscher Mehltau', 'PLASVI',
    12.0, 28.0, 80.0,
    'Downy mildew — oomycete pathogen. Requires leaf wetness and temperatures 12–28 °C. '
    'Primary infection from oospores in spring; secondary spread via sporangia. '
    'Use the 3-10-10 rule: 10 cm shoot growth, 10 °C overnight, 10 mm rain within 24–48 h.'
),
(
    'Oidium', 'Erysiphe necator', 'Echter Mehltau', 'UNCINE',
    10.0, 35.0, 40.0,
    'Powdery mildew — ascomycete pathogen. Thrives 18–28 °C; tolerates low humidity (>= 40 %). '
    'Overwinters as chasmothecia in bark; primary infection from ascospores at bud burst. '
    'Most susceptible: BBCH 53–71 (pre-bloom through fruit set).'
);

-- -----------------------------------------------------------------------
-- Seed: FRAC codes
-- -----------------------------------------------------------------------

INSERT INTO frac_code (code, chemical_class, description, resistance_risk_level) VALUES
(
    'M1', 'Copper compounds',
    'Copper hydroxide, copper sulfate — multi-site contact, low resistance risk. '
    'Backbone of organic Peronospora control. EU regulation: max 3 kg Cu/ha/year averaged over 7 years.',
    'LOW'
),
(
    'M2', 'Sulphur',
    'Elemental sulphur — multi-site contact, low resistance risk. '
    'Backbone of Oidium control in organic and conventional viticulture. Phytotoxic above 27 °C.',
    'LOW'
),
(
    'M4', 'Phthalimides',
    'Folpet, Captan — multi-site contact protectant. Max 4 applications per season. '
    'BVL-approved for conventional Peronospora control.',
    'LOW'
),
(
    'U7', 'Cyanoacetamide-oximes',
    'Cymoxanil — single-site, active only during the first 48 h after infection. '
    'Always used in combination products. Medium resistance risk.',
    'MEDIUM'
),
(
    '3', 'DMI fungicides — Triazoles',
    'Demethylation inhibitors (DMI) — ergosterol biosynthesis inhibitor. '
    'Curative Oidium activity (up to 72 h post-infection). Max 3 applications per season. Medium resistance risk.',
    'MEDIUM'
),
(
    '4', 'Phenylamides',
    'Metalaxyl-M, Mefenoxam — systemic oomycete inhibitors. '
    'HIGH resistance risk: max 2 applications per season, never use alone — always combine with a contact fungicide.',
    'HIGH'
),
(
    '5', 'Morpholines / Spiroketalamines',
    'Spiroxamine — sterol biosynthesis inhibitor for Oidium. '
    'Curative and preventive activity. Medium resistance risk.',
    'MEDIUM'
),
(
    '6', 'Morpholines',
    'Sterol esterification inhibitors (older class, limited current German use).',
    'MEDIUM'
),
(
    '7', 'SDHI fungicides',
    'Succinate dehydrogenase inhibitors (SDHI) — mitochondrial respiration inhibitor. '
    'Curative and preventive Oidium activity. Used in combination products. Medium resistance risk.',
    'MEDIUM'
),
(
    '11', 'QoI fungicides — Strobilurins',
    'Quinone outside inhibitors — mitochondrial respiration inhibitor. '
    'HIGH resistance risk: max 2 applications per season, never use alone.',
    'HIGH'
),
(
    '40', 'CAA fungicides',
    'Cellulose Biosynthesis Inhibitors (CBI) — systemic Peronospora activity. '
    'Mandipropamid, iprovalicarb, valifenalate class. Medium resistance risk; max 4 applications per season.',
    'MEDIUM'
),
(
    'U13', 'Quinazolinones',
    'Proquinazid — locally systemic Oidium activity. '
    'Medium resistance risk; max 2 applications per season. PHI 42 days.',
    'MEDIUM'
),
(
    'M3', 'Dithiocarbamates',
    'Mancozeb, maneb, metiram, propineb — multi-site contact fungicide. '
    'LOW resistance risk. Commonly combined with systemic fungicides for Peronospora.',
    'LOW'
),
(
    '9', 'Anilinopyrimidines',
    'Cyprodinil, pyrimethanil — inhibit amino acid synthesis. '
    'Primarily Oidium activity, some Botrytis. MEDIUM resistance risk; max 3 applications per season.',
    'MEDIUM'
),
(
    '12', 'Phenylpyrroles',
    'Fludioxonil — osmotic signal transduction inhibitor. '
    'Broad spectrum including Botrytis. LOW resistance risk.',
    'LOW'
),
(
    '13', 'Quinoline fungicides',
    'Quinoxyfen — signal transduction inhibitor. '
    'Exclusively Oidium activity (preventive only). MEDIUM resistance risk.',
    'MEDIUM'
),
(
    '33', 'Phosphonates',
    'Fosetyl-aluminium, phosphorous acid — stimulate plant defence and inhibit Peronospora. '
    'LOW resistance risk; often used as tank-mix partner.',
    'LOW'
),
(
    '45', 'Aza-naphthalenes',
    'Ametoctradin — inhibits electron transport at complex III (Qi site). '
    'Systemic Peronospora and Botrytis activity. MEDIUM resistance risk.',
    'MEDIUM'
),
(
    '49', 'Oxysterol-binding protein inhibitors (OSBPI)',
    'Oxathiapiprolin — inhibits oxysterol-binding proteins; unique mode of action against oomycetes. '
    'Highly systemic Peronospora activity. MEDIUM resistance risk; use only in mixtures.',
    'MEDIUM'
);

-- -----------------------------------------------------------------------
-- Fungicide products, disease target relationships, and rotation strategies
-- are NOT seeded here. They must be populated through the admin interface
-- or via the BVL PSM-API sync (POST /api/v1/admin/sync/bvl-api).
--
-- Rationale: product registrations and agronomic recommendations change.
-- Hard-coded seed data goes stale and creates a false sense of authority.
-- Rotation strategies can be set via POST /api/v1/fungicide-management/rotation-strategy.
-- -----------------------------------------------------------------------
