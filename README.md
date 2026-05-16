# Rebenbot

Vineyard management assistant for monitoring fungal disease pressure and optimising fungicide applications. Combines real-time weather data, disease infection models, a curated German fungicide database, and WBI Freiburg prognosis data into a single REST API with a Vue 3 dashboard.

**Target diseases:** Peronospora (Downy Mildew), Oidium (Powdery Mildew)

## Tech Stack

- **Backend:** Spring Boot 3.4.0, Java 21, Spring Data JPA, Lombok
- **Database:** H2 (embedded, default) or PostgreSQL (Docker, `postgres` profile)
- **Migrations:** Flyway
- **Build:** Maven 3.9.9+
- **Frontend:** Vue 3 + Vite (in `frontend/`)
- **External data:** Meteoblue API (weather), BVL PSM-API (fungicide approvals), vitimeteo-bw.de (WBI prognosis)

## Project Structure

```
rebenbot/
├── src/main/
│   ├── java/com/rebenbot/
│   │   ├── config/          # Spring config (RestTemplate, CookieManager)
│   │   ├── controller/      # REST controllers + dto/
│   │   ├── init/            # DataInitializer (demo data seeding)
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Spring Data JPA repositories
│   │   └── service/         # Business logic
│   └── resources/
│       ├── application.yml           # Default profile (H2)
│       ├── application-postgres.yml  # PostgreSQL profile
│       └── db/migration/             # Flyway SQL scripts
├── frontend/                # Vue 3 + Vite dashboard
├── docs/                    # Additional documentation
└── pom.xml
```

## Quick Start

### Prerequisites

- Java 21 JDK
- Maven 3.9.9+
- Docker & Docker Compose (optional — only needed for PostgreSQL)
- Meteoblue API key (optional — free tier: 5,000 calls/year at meteoblue.com)

### Run with H2 (no setup)

```bash
mvn spring-boot:run
```

API available at `http://localhost:8080/api`. H2 is in-memory; data resets on restart.

### Run with PostgreSQL

```bash
docker-compose up -d                    # start PostgreSQL container
export SPRING_PROFILES_ACTIVE=postgres
mvn spring-boot:run                     # Flyway migrations run automatically
```

```bash
docker-compose down       # stop, preserve data
docker-compose down -v    # stop and delete data
```

### Build

```bash
mvn clean package
mvn test
```

### Frontend

```bash
cd frontend
npm install
npm run dev    # dev server with HMR
npm run build  # production build
```

### Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `METEOBLUE_API_KEY` | -- | Weather data (Meteoblue API) |
| `DB_PASSWORD` | -- | PostgreSQL password (from docker-compose) |
| `SPRING_PROFILES_ACTIVE` | default (H2) | Set to `postgres` for PostgreSQL |

### Health Check

```bash
curl http://localhost:8080/api/v1/health
```

## API Reference

All endpoints are under the `/api` context path. Versioned routes use `/api/v1/`.

### Vineyards
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/vineyards` | List all vineyards |
| `POST` | `/v1/vineyards` | Create vineyard |
| `GET` | `/v1/vineyards/{id}` | Get vineyard |
| `PUT` | `/v1/vineyards/{id}` | Update vineyard |
| `DELETE` | `/v1/vineyards/{id}` | Delete vineyard |

### Weather
| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/weather/fetch?days=7` | Fetch from Meteoblue (1–7 days) |
| `GET` | `/v1/weather` | All stored records |
| `GET` | `/v1/weather/latest` | Latest observation |

### Infection Risk
| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/risk/assess` | Run risk assessment from latest weather |
| `GET` | `/v1/risk/latest` | Latest risk score per disease |
| `GET` | `/v1/risk/history?hours=48` | Risk history |
| `GET` | `/v1/risk/forecast?days=7` | 7-day risk forecast |

Risk scores run 0.0–1.0: **≥0.75** critical, **0.50–0.75** high, **0.25–0.50** medium, **<0.25** low.

Disease thresholds:
- **Peronospora**: 10–25°C, ≥85% humidity, ≥10 h leaf wetness
- **Oidium**: 15–27°C, ≥40% humidity (drier conditions)

### Fungicide Recommendations
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/fungicides/all` | All approved products |
| `GET` | `/v1/fungicides/{disease}` | Products for a disease |
| `GET` | `/v1/fungicides/recommend` | Ranked recommendations (`?disease=&riskScore=&daysUntilHarvest=`) |
| `GET` | `/v1/fungicides/latest-recommendations` | Recommendations from latest risk (`?daysUntilHarvest=`) |

### Fungicide Management
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/fungicide-management/frac-codes` | All FRAC codes |
| `GET` | `/v1/fungicide-management/by-frac/{fracCode}` | Products by mode of action |
| `GET` | `/v1/fungicide-management/by-disease/{diseaseId}` | Products for a disease |
| `GET` | `/v1/fungicide-management/{productId}/approvals` | Approval details (PHI, validity) |
| `GET` | `/v1/fungicide-management/rotation-strategy/{diseaseId}` | Rotation strategy |
| `GET` | `/v1/fungicide-management/rotation-plan/{diseaseId}` | Recommended FRAC rotation sequence |
| `GET` | `/v1/fungicide-management/approvals/expiring?daysAhead=N` | Expiring approvals |
| `POST` | `/v1/fungicide-management/validate-rotation` | Check FRAC rotation compliance |

### Spray Diary
| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/spray-diary/record` | Record a spray application |
| `GET` | `/v1/spray-diary/recent/{vineyardId}` | Last 7 days |
| `GET` | `/v1/spray-diary/history/{vineyardId}?lastDays=30` | Spray history |
| `GET` | `/v1/spray-diary/frequency/{vineyardId}?lastDays=30` | Frequency analysis |

### Vineyard Diary
| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/vineyard-diary/create` | Create diary entry |
| `GET` | `/v1/vineyard-diary/entries/{vineyardId}` | All entries |
| `GET` | `/v1/vineyard-diary/entries/{vineyardId}/range` | Entries by date range |
| `GET` | `/v1/vineyard-diary/entries/{vineyardId}/type?type=OBSERVATION` | Entries by type |
| `GET` | `/v1/vineyard-diary/entries/{vineyardId}/tag?tag=budbreak` | Entries by tag |
| `GET` | `/v1/vineyard-diary/entry/{entryId}` | Single entry |
| `PUT` | `/v1/vineyard-diary/entry/{entryId}` | Update entry |
| `DELETE` | `/v1/vineyard-diary/entry/{entryId}` | Delete entry |

Entry types: `OBSERVATION`, `WEATHER`, `PEST_DISEASE`, `MAINTENANCE`, `HARVEST`, `OTHER`

### Growth Stage
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/growth-stage/current` | Current BBCH stage |
| `POST` | `/v1/growth-stage/set-manual?stageName=XX` | Override growth stage |
| `POST` | `/v1/growth-stage/use-automatic` | Revert to GDD-based calculation |
| `GET` | `/v1/growth-stage/available-stages` | Available BBCH stages |

### Spray Timing
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/spray/rainfall-summary` | 24-h rainfall + timing recommendation |
| `GET` | `/v1/spray/window/peronospora?currentTemperatureC=15` | Optimal spray window |

Strategies: **PREVENTIVE** (no rain expected), **BEFORE_RAIN** (rain forecast within 12 h), **AFTER_RAIN** (recent rain, wait for 4 h drying).

### WBI Prognosis (vitimeteo-bw.de)
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/wbi-prognosis/...` | WBI Freiburg daily prognosis data |

Data is fetched automatically at 06:00 (Peronospora) and 06:15 (Oidium) daily from vitimeteo-bw.de.

### Data Sync
| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/data-sync/...` | Trigger BVL fungicide approval sync |

BVL PSM-API is synced automatically at 04:00 on the 1st of each month.

### Fungal Diseases
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/diseases` | List diseases |
| `POST` | `/v1/diseases` | Add disease |
| `GET` | `/v1/diseases/{id}` | Disease detail |

## Database Schema

Schema managed by Flyway. Key tables:

| Table | Description |
|---|---|
| `vineyard` | Vineyard metadata |
| `fungal_disease` | Disease catalog (Peronospora, Oidium) |
| `frac_code` | FRAC resistance classifications |
| `fungicide_product` | Products and active substances |
| `fungicide_approval` | Regional approvals, PHI, validity |
| `fungicide_target_disease` | Product–disease mappings |
| `rotation_strategy` | Resistance management rules |
| `wbi_prognosis` | Daily WBI prognosis rows |
| `peronospora_infection_event` | Per-infection-event incubation data |
| `vitimeteo_pheno` | Phenological stage data from vitimeteo |
| `weather_data` | Stored Meteoblue observations |
| `infection_risk` | Calculated risk assessments |
| `vineyard_log_entry` | Spray applications and diary entries |

See `src/main/resources/db/migration/V1__initial_schema.sql` for the full schema.

## Data Sources

- [vitimeteo-bw.de](https://www.vitimeteo-bw.de/) — WBI Freiburg disease prognosis
- [Meteoblue](https://www.meteoblue.com) — Weather data API
- [BVL PSM-API](https://psm-api.bvl.bund.de/) — German fungicide approval database

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

- Direct dependency license inventories:
  - [licenses/backend-third-party.txt](licenses/backend-third-party.txt)
  - [licenses/frontend-third-party.txt](licenses/frontend-third-party.txt)
- The Apache 2.0 `LICENSE` file already includes the required Section 7 `"AS IS"` warranty disclaimer.
