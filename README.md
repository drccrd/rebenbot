# Rebenbot - Vineyard Management Assistant

A Java-based application to assist in managing vineyards with fungicide recommendations and infection risk monitoring based on real-time weather data.

## Project Overview

**Purpose**: Monitor fungal diseases and optimize fungicide applications using weather-based infection risk calculations

**Target Diseases**: 
- Peronospora (Downy Mildew)
- Oidium (Powdery Mildew)

**Key Features**:
- Real-time weather integration (Meteoblue API)
- Infection risk assessment engine
- Fungicide database with approvals and FRAC codes
- Intelligent fungicide recommendations
- Electronic spray application diary
- Growth stage tracking with GDD calculations
- Multi-language support (German/English)

## Tech Stack

- **Backend**: Spring Boot 3.3.x (Java 21+ LTS)
- **Database**: H2 (embedded, can upgrade to PostgreSQL)
- **Build**: Maven 3.9.9+
- **Frontend**: Vue 3 (planned)

## Project Structure

```
rebenbot/
├── src/
│   ├── main/
│   │   ├── java/com/rebenbot/
│   │   │   ├── model/           # Data models (Vineyard, FungalDisease, etc.)
│   │   │   ├── repository/      # Database access layer
│   │   │   ├── controller/      # REST API endpoints
│   │   │   ├── service/         # Business logic
│   │   │   └── RebenBotApplication.java
│   │   └── resources/
│   │       └── application.yml  # Configuration
│   └── test/                     # Unit tests
├── frontend/                     # Vue 3 web interface
├── docs/                         # Documentation
└── pom.xml                       # Maven configuration
```

## Quick Start

### Prerequisites
- Java 21 LTS or higher (JDK)
- Maven 3.9.9+
- Meteoblue API key (free tier available - see [Weather Integration](docs/WEATHER_INTEGRATION.md))
- Docker & Docker Compose (for PostgreSQL - optional, H2 available for dev)

### Database Setup

#### Option A: PostgreSQL with Docker (Recommended)

1. **Start PostgreSQL container:**
```bash
cd /path/to/rebenbot  # Project root
docker-compose up -d
```
Flyway migrations run automatically on first startup.

2. **Verify database is ready:**
```bash
docker-compose ps
docker logs rebenbot-db  # Check for migration logs
```

3. **Run Spring Boot with PostgreSQL:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=postgres"
```

#### Option B: In-Memory H2 Database (Development)

```bash
# No setup needed - H2 runs in memory
mvn spring-boot:run
```

### Build & Run

```bash
# Build the project
mvn clean package

# Run with embedded H2 database (demo mode)
mvn spring-boot:run

# Run with PostgreSQL (requires Docker container running)
export SPRING_PROFILES_ACTIVE=postgres
mvn spring-boot:run

# Run with weather data fetching (API key required)
export METEOBLUE_API_KEY=YOUR_KEY
mvn spring-boot:run
```

The API will be available at: `http://localhost:8080/api`

### Stopping PostgreSQL

```bash
docker-compose down  # Stops container, preserves data
docker-compose down -v  # Stops container AND deletes data
```

### Database Migrations (Flyway)

Migrations are automatically applied on startup. To view migration history:

```bash
docker-compose exec postgres psql -U rebenbot -d rebenbot -c "\\dt flyway_schema_history"
```

### Health Check

```bash
curl http://localhost:8080/api/v1/health
```

## API Endpoints

### Vineyards
- `GET /v1/vineyards` - List all vineyards
- `POST /v1/vineyards` - Create a new vineyard
- `GET /v1/vineyards/{id}` - Get vineyard details
- `PUT /v1/vineyards/{id}` - Update vineyard
- `DELETE /v1/vineyards/{id}` - Delete vineyard

### Fungal Diseases
- `GET /v1/diseases` - List all diseases
- `POST /v1/diseases` - Add a disease
- `GET /v1/diseases/{id}` - Get disease details

### Weather Data (Meteoblue Integration)
- `POST /v1/weather/fetch?days=7` - Fetch weather data from Meteoblue (1-7 day forecast)
- `GET /v1/weather` - Get all stored weather records
- `GET /v1/weather/latest` - Get latest weather observation

### Infection Risk Assessment
- `POST /v1/risk/assess` - Calculate current infection risk based on latest weather
- `GET /v1/risk/latest` - Get latest risk score for each disease
- `GET /v1/risk/history?hours=48` - Get risk assessment history
- `GET /v1/risk/forecast?days=7` - Forecast 7-day risk based on weather forecast

### Fungicide Recommendations
- `GET /v1/fungicides/all` - List all approved German fungicides
- `GET /v1/fungicides/recommend?disease=Peronospora&riskScore=0.75&daysUntilHarvest=60` - Get ranked fungicide recommendations for specific disease and risk level
- `GET /v1/fungicides/latest-recommendations?daysUntilHarvest=60` - Get recommendations based on latest risk assessment
- `GET /v1/fungicides/{disease}` - List all approved fungicides for a disease

### Fungicide Management (Database-backed)
- `GET /v1/fungicide-management/frac-codes` - List all FRAC codes (Fungicide Resistance Action Committee classifications)
- `GET /v1/fungicide-management/by-frac/{fracCode}` - Get fungicides by mode of action (FRAC code)
- `GET /v1/fungicide-management/by-disease/{diseaseId}` - Get fungicides for a specific disease
- `GET /v1/fungicide-management/{productId}/approvals` - Get approval info (PHI, valid dates, regions) for a fungicide product
- `GET /v1/fungicide-management/rotation-strategy/{diseaseId}` - Get rotation strategy recommendations for a disease
- `GET /v1/fungicide-management/rotation-plan/{diseaseId}` - Get recommended rotation sequence (FRAC codes → products)

### Spray Logging & Diary
- `POST /v1/spray-diary/record` - Record spray application with conditions
- `GET /v1/spray-diary/recent/{vineyardId}` - Get recent sprays (last 7 days)
- `GET /v1/spray-diary/history/{vineyardId}?lastDays=30` - Get spray history
- `GET /v1/spray-diary/frequency/{vineyardId}?lastDays=30` - Analyze spray frequency

### Growth Stage
- `GET /v1/growth-stage/current` - Get current vine growth stage (BBCH)
- `POST /v1/growth-stage/set-manual?stageName=XX` - Manually override growth stage
- `POST /v1/growth-stage/use-automatic` - Revert to automatic GDD calculation
- `GET /v1/growth-stage/available-stages` - List available BBCH stages

### Rainfall & Spray Timing
- `GET /v1/spray/rainfall-summary` - 24-hour rainfall and recommendation
- `GET /v1/spray/window/peronospora?currentTemperatureC=15` - Optimal spray window for Peronospora

### System
- `GET /v1/health` - Health check endpoint

### Weather API (Meteoblue)

Rebenbot integrates with **Meteoblue** (the data source for vitimeteo.de) to fetch real-time weather data including:
- Temperature, humidity, precipitation, wind speed
- **Leaf wetness index** - critical for fungal disease prediction
- Hourly forecasts up to 7 days

**Setup:**
1. Get a free API key: https://www.meteoblue.com (Free Weather API tier, 5,000 calls/year)
2. Set environment variable: `export METEOBLUE_API_KEY=YOUR_KEY`
3. Fetch weather: `POST /api/v1/weather/fetch?days=7`

See [Weather Integration Guide](docs/WEATHER_INTEGRATION.md) for full setup instructions.

### Infection Risk Calculator

Rebenbot includes a **built-in calculator** that evaluates weather conditions against published disease thresholds to generate actionable risk assessments:

**Disease Models:**
- **Peronospora** (Downy Mildew): 10-25°C + 85%+ humidity + 10+ hours leaf wetness
- **Oidium** (Powdery Mildew): 15-27°C + 40%+ humidity (thrives in drier conditions)

**Risk Scoring:** 0.0 (no risk) to 1.0 (critical)
- **0.75-1.0**: CRITICAL → Spray immediately
- **0.50-0.75**: HIGH → Plan spray within 24h
- **0.25-0.50**: MEDIUM → Monitor conditions
- **0-0.25**: LOW/NONE → Continue monitoring

**Example Workflow:**
```bash
# 1. Fetch weather data
curl -X POST http://localhost:8080/api/v1/weather/fetch?days=7

# 2. Assess current infection risk
curl -X POST http://localhost:8080/api/v1/risk/assess

# 3. Forecast next 7 days
curl http://localhost:8080/api/v1/risk/forecast?days=7
```

See [Risk Calculator Guide](docs/RISK_CALCULATOR.md) for complete documentation, thresholds, and research sources.

### Database

- **Development**: H2 embedded database (in-memory, no setup required)
- **Production**: PostgreSQL (via Docker Compose, migrations with Flyway)

**Schema Highlights:**
- `vineyard` - Vineyard metadata
- `fungal_disease` - Disease catalog (Peronospora, Oidium)
- `frac_code` - Fungicide resistance classification (FRAC codes 3, 6, 7, 11, M, U, C)
- `fungicide_product` - Fungicide products and their active substances
- `fungicide_approval` - Regional approvals, PHI, dosages
- `fungicide_target_disease` - Which fungicides treat which diseases
- `rotation_strategy` - Resistance management rules per disease
- `weather_data`, `risk_assessment`, `spray_application`, `growth_stage` - Operational data

See `src/main/resources/db/migration/V1__initial_schema.sql` for full schema definition.

## Risk Calculator

Rebenbot includes a **built-in infection risk calculator** that evaluates hourly weather conditions against published disease thresholds:

- **Peronospora** (Downy Mildew): Requires high humidity + sustained leaf wetness at 10-25°C
- **Oidium** (Powdery Mildew): Thrives in warm, moderate-humidity conditions (15-27°C, 40%+)

Risk scores (0.0-1.0) translate to actionable recommendations:
- **0.75-1.0**: CRITICAL - Spray immediately
- **0.50-0.75**: HIGH - Plan spray within 24 hours
- **0.25-0.50**: MEDIUM - Monitor, spray if continues
- **0-0.25**: LOW/NONE - Unfavorable conditions

**Workflow:**
1. Fetch weather: `POST /api/v1/weather/fetch?days=7`
2. Assess risk: `POST /api/v1/risk/assess`
3. View forecast: `GET /api/v1/risk/forecast?days=7`

See [Risk Calculator Guide](docs/RISK_CALCULATOR.md) for complete documentation.

## Data Sources

- [Vitimeteo.de](https://www.vitimeteo-bw.de/) - Viticulture weather data
- [Baden-Württemberg Plant Protection](https://wbi.landwirtschaft-bw.de/) - Plant protection guidelines and fungicide approvals

## Development Roadmap

### Phase 1: Foundation ✅
- [x] Project structure
- [x] Core data models
- [x] REST API framework
- [x] Weather data integration (Meteoblue API)

### Phase 2: Core Features ✅
- [x] Infection risk calculation engine
- [x] Fungicide database with 8 approved German products
- [x] Fungicide recommendation engine (disease + risk → ranked products)
- [x] Weather data historical analysis

### Phase 3: UI & Notifications ✅
- [x] Vue 3 web frontend (responsive dashboard)
- [x] Real-time weather display with 7-day forecast
- [x] Interactive risk cards (color-coded by severity)
- [x] Fungicide recommendation cards with PHI/timing guidance
- [x] Spray timing recommendations with rainfall analysis
- [x] Growth stage tracking with GDD calculations
- [ ] Multi-user support

### Phase 4: Spray Diary & Advanced Features ✅
- [x] Electronic spraying diary with application logging
- [x] Fungicide dosage calculations based on vineyard size
- [x] Spray effectiveness tracking
- [x] Resistance prevention guidelines
- [x] External resource links (vitimeteo, WBI, DLR, LfL)
- [x] Rainfall-based spray timing recommendations (BEFORE/AFTER rain strategies)
- [ ] PostgreSQL database migration
- [ ] Docker deployment
- [ ] Mobile notifications (Android)
- [ ] User authentication & multi-user support
- [ ] Data export/reporting

### Phase 5: Fungicide Database Backend & Proper Data Management 🔲
- [ ] **Research & Document Fungicide Strategy**
  - [ ] Research actual German-approved fungicides (2024-2026) via EU Pesticides Database
  - [ ] Contact DLR (Dienstleistungszentren Ländlicher Raum) for regional spray guide recommendations
  - [ ] Document FRAC codes (modes of action) for each fungicide
  - [ ] Establish proper resistance management rotation strategies (minimum 3-4 different FRAC codes)
  - [ ] Verify pre-harvest intervals (PHI) and approval validity periods
  - [ ] Update buying guide with research-based recommendations instead of hardcoded values
- [ ] **Build Fungicide Management Database Backend**
  - [ ] Create FungicideRotationStrategy entity to define rotation plans by FRAC code
  - [ ] Create FungicideApproval entity to track current approvals, validity dates, PHI
  - [ ] Create ProductRotationRecommendation entity to suggest proper rotation sequences
  - [ ] Migrate fungicide data from hardcoded frontend lists to database
  - [ ] Create REST endpoints for:
    - [ ] GET /v1/fungicides/by-frac/{fracCode} - List by mode of action
    - [ ] GET /v1/fungicides/rotation-plan/{disease} - Get recommended rotation sequence
    - [ ] GET /v1/fungicides/validate-rotation - Check if spray sequence prevents resistance
  - [ ] Implement rotation validation logic in FungicideRecommendationService
  - [ ] Remove all hardcoded fungicide data from App.vue frontend
- [ ] **Update Frontend Data Binding**
  - [ ] Fetch fungicide recommendations and rotation strategies from backend
  - [ ] Display FRAC codes and mode-of-action info in buying guide
  - [ ] Show rotation strategy guidance in spray diary form
  - [ ] Remove hardcoded product lists from Vue component data

## Key Features in Detail

### Spray Timing Optimization
Rebenbot analyzes rainfall patterns and recommends spray timing:
- **PREVENTIVE**: No significant rain expected (>2mm) → spray 80% into incubation period
- **BEFORE_RAIN**: Rain expected → spray 12+ hours before precipitation
- **AFTER_RAIN**: Recent rain (last 72h) → spray immediately after drying (4h required)

**Incubation Period**: Base 5 days for Peronospora, adjusted by temperature
**Significant Rain Threshold**: >2mm in 24 hours
**Dry Time Required**: 4 hours minimum for spray efficacy

### Growth Stage Tracking
Automatic GDD (Growing Degree Days) calculation from April 1st onwards:
- Base temperature: 10°C
- Manual override option for user corrections
- Integration with fungicide PHI (Pre-Harvest Interval) and dosage adjustments
- Post-veraison (BBCH 80-81): 20% dosage reduction

### Dosage Calculations
Automatic fungicide dosage based on:
- Vineyard size (in ares)
- Standard spray volume: 400 L/hectare (4 L/are)
- Active substance concentration
- Growth stage adjustments
- Formula: `(ml/100L) × (vineyard_are × 4L / 100L)`

## Data Sources & References

- **Weather**: [Meteoblue](https://www.meteoblue.com) (used by vitimeteo.de)
- **Plant Protection**: [Baden-Württemberg Guidelines](https://wbi.landwirtschaft-bw.de/) - Fungicide approvals and recommendations
- **Viticulture Portal**: [vitimeteo.de](https://www.vitimeteo.de/) - Visualization of weather-based disease risk
- **Research Centers**: 
  - [DLR Rebschutz](https://www.dlr.de/de/unsere-aufgaben/pflanzliche-erzeugung/rebschutz) - State research center
  - [LfL Rebschutz](https://www.lfl.bayern.de/rebschutz) - Bavarian agricultural research

## Project Information

**Language**: Java with Vue.js frontend  
**Focus**: Agricultural decision support for viticulture  
**License**: Apache 2.0  
**Status**: Active Development

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
