# Third-Party Licenses

This project is distributed under the Apache License 2.0 in [`LICENSE`](LICENSE).

That license already contains the required `"AS IS"` disclaimer in Section 7:

> Licensor provides the Work (and each Contributor provides its Contributions) on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

## Reviewed dependency sets

- Backend dependencies were reviewed from `pom.xml` plus the generated Maven inventory in [`licenses/backend-third-party.txt`](licenses/backend-third-party.txt).
- Frontend dependencies were reviewed from `frontend/package.json` plus the generated package inventory in [`licenses/frontend-third-party.txt`](licenses/frontend-third-party.txt).

## Direct backend dependencies

| Dependency | Version | License |
| --- | --- | --- |
| org.springframework.boot:spring-boot-starter-web | 3.4.0 | Apache-2.0 |
| org.springframework.boot:spring-boot-starter-data-jpa | 3.4.0 | Apache-2.0 |
| org.springframework.boot:spring-boot-starter-validation | 3.4.0 | Apache-2.0 |
| com.h2database:h2 | 2.3.232 | EPL-1.0 or MPL-2.0 |
| org.postgresql:postgresql | 42.7.4 | BSD-2-Clause |
| org.flywaydb:flyway-core | 10.20.1 | Apache-2.0 |
| org.flywaydb:flyway-database-postgresql | 10.20.1 | Apache-2.0 |
| org.springframework.boot:spring-boot-starter-webflux | 3.4.0 | Apache-2.0 |
| com.fasterxml.jackson.core:jackson-databind | 2.18.1 | Apache-2.0 |
| com.fasterxml.jackson.datatype:jackson-datatype-jsr310 | 2.18.1 | Apache-2.0 |
| org.projectlombok:lombok | 1.18.36 | MIT |
| org.springframework.boot:spring-boot-starter-test | 3.4.0 | Apache-2.0 |

## Direct frontend dependencies

| Dependency | Resolved version | License |
| --- | --- | --- |
| vue | 3.5.32 | MIT |
| axios | 1.15.0 | MIT |
| @vitejs/plugin-vue | 5.2.4 | MIT |
| vite | 5.4.21 | MIT |

## Conditions to keep when redistributing

- Preserve this repository's `LICENSE` file.
- Preserve the third-party inventory files referenced above.
- Retain upstream copyright and license notices for all bundled dependencies.
- Keep the notice text for dual-licensed or weak-copyleft components that appear in the generated inventories, especially:
  - Logback (`EPL-1.0` / `LGPL-2.1-or-later`)
  - Hibernate ORM core (`LGPL-2.1-or-later`)
  - H2 (`EPL-1.0` / `MPL-2.0`)
- For packaged distributions, include the generated inventory files alongside the application so recipients can review the applicable upstream licenses.

## Notes

- Most reviewed dependencies are permissive (`Apache-2.0`, `MIT`, `BSD`, `ISC`).
- The generated inventory files are the authoritative attachment for transitive dependencies and should be refreshed when dependency versions change.
