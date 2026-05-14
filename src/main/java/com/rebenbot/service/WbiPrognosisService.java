package com.rebenbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebenbot.model.PeronosporaInfectionEvent;
import com.rebenbot.model.VitimeteoPheno;
import com.rebenbot.model.WbiPrognosis;
import com.rebenbot.repository.PeronosporaInfectionEventRepository;
import com.rebenbot.repository.VitimeteoPhenoRepository;
import com.rebenbot.repository.WbiPrognosisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service to fetch WBI Freiburg disease prognosis data from vitimeteo-bw.de.
 *
 * Fetches three endpoints daily:
 *   - risk_data.json?idProgram=7  — peronospora daily risk + pheno (14-day series)
 *   - risk_data.json?idProgram=8  — oidium daily risk (14-day series)
 *   - expert_data.json?idProgram=7 — peronospora per-infection-event Inkubation series
 *
 * Authentication is handled automatically via session cookies (CookieManager in HttpClientConfig).
 */
@Service
@Slf4j
public class WbiPrognosisService {

    private final WbiPrognosisRepository prognosisRepository;
    private final PeronosporaInfectionEventRepository infectionEventRepository;
    private final VitimeteoPhenoRepository phenoRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String RISK_API_URL =
            "https://www.vitimeteo-bw.de/vitimeteo/station/risk_data.json";
    private static final String EXPERT_API_URL =
            "https://www.vitimeteo-bw.de/vitimeteo/station/expert_data.json";
    private static final String INDEX_URL =
            "https://www.vitimeteo-bw.de/vitimeteo/default/index";
    private static final int STATION_ID = 99;
    private static final int OIDIUM_PROGRAM_ID = 8;
    private static final int PERONOSPORA_PROGRAM_ID = 7;
    private static final int WBI_DATA_FRESHNESS_HOURS = 24;
    private static final int FORECAST_WINDOW_DAYS = 14;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    public WbiPrognosisService(WbiPrognosisRepository prognosisRepository,
                               PeronosporaInfectionEventRepository infectionEventRepository,
                               VitimeteoPhenoRepository phenoRepository,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.prognosisRepository = prognosisRepository;
        this.infectionEventRepository = infectionEventRepository;
        this.phenoRepository = phenoRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 6 * * ?")
    public void refreshPeronosporePrognosis() {
        log.info("Starting scheduled peronospora prognosis refresh from vitimeteo JSON");
        LocalDate today = LocalDate.now();
        try {
            warmUpSession();
            fetchAndStore("peronospora", PERONOSPORA_PROGRAM_ID, today);
            fetchAndStoreExpertData(today);
            log.info("Peronospora prognosis refresh completed");
        } catch (Exception e) {
            log.warn("Peronospora prognosis refresh failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 15 6 * * ?")
    public void refreshOidiumPrognosis() {
        log.info("Starting scheduled oidium prognosis refresh from vitimeteo JSON");
        try {
            warmUpSession();
            fetchAndStore("oidium", OIDIUM_PROGRAM_ID, LocalDate.now());
            log.info("Oidium prognosis refresh completed");
        } catch (Exception e) {
            log.warn("Oidium prognosis refresh failed: {}", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // HTTP helpers
    // -----------------------------------------------------------------------

    /**
     * Fetch the vitimeteo index page to obtain a session cookie.
     * The CookieManager in HttpClientConfig stores the Set-Cookie header automatically,
     * so subsequent JSON API calls will include the session_id_vitimeteo cookie.
     */
    private void warmUpSession() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            restTemplate.exchange(INDEX_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            log.debug("vitimeteo session warm-up completed");
        } catch (Exception e) {
            log.warn("vitimeteo session warm-up failed (will still attempt API calls): {}", e.getMessage());
        }
    }

    private JsonNode fetchJson(String url) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.set("Referer", "https://www.vitimeteo-bw.de/vitimeteo/default/index");
        headers.set("X-Requested-With", "XMLHttpRequest");

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new Exception("Empty response from: " + url);
        }
        return objectMapper.readTree(body);
    }

    private void fetchAndStore(String disease, int programId, LocalDate today) throws Exception {
        String url = RISK_API_URL
                + "?idStat=" + STATION_ID
                + "&startdate=" + today.minusDays(7).format(DATE_FMT)
                + "&enddate=" + today.plusDays(FORECAST_WINDOW_DAYS).format(DATE_FMT)
                + "&idProgram=" + programId;
        log.debug("Fetching {} prognosis JSON from vitimeteo: {}", disease, url);
        JsonNode root = fetchJson(url);
        parseAndStore(root, disease, today);
    }

    private void fetchAndStoreExpertData(LocalDate today) throws Exception {
        String url = EXPERT_API_URL
                + "?idStat=" + STATION_ID
                + "&startdate=" + today.minusDays(7).format(DATE_FMT)
                + "&enddate=" + today.plusDays(FORECAST_WINDOW_DAYS).format(DATE_FMT)
                + "&idProgram=" + PERONOSPORA_PROGRAM_ID;
        log.debug("Fetching peronospora expert data from vitimeteo: {}", url);
        JsonNode root = fetchJson(url);
        parseAndStoreIncubation(root, today);
    }

    // -----------------------------------------------------------------------
    // risk_data.json parsing
    // -----------------------------------------------------------------------

    private void parseAndStore(JsonNode root, String disease, LocalDate today) {
        String todayStr = today.format(DATE_FMT);
        String latestColor;
        double riskScore;
        Map<String, JsonNode> byName;

        if ("peronospora".equals(disease)) {
            JsonNode modelChart = root.path("chartObj").path("model_results").path("chart");
            if (modelChart.isMissingNode() || !modelChart.isArray() || modelChart.isEmpty()) {
                log.warn("No model_results.chart in vitimeteo peronospora response");
                return;
            }
            byName = collectSeriesByName(modelChart);
            latestColor = latestStringValue(byName.get("InfRiskColor"), todayStr);
            riskScore = latestDoubleValue(byName.get("InfektionsstärkeIndex"), todayStr);
        } else {
            JsonNode modelChart = root.path("chartObj").path("model_results").path("chart");
            if (modelChart.isMissingNode() || !modelChart.isArray() || modelChart.isEmpty()) {
                log.warn("No model_results.chart in vitimeteo oidium response");
                return;
            }
            byName = collectSeriesByName(modelChart);
            latestColor = latestStringValue(byName.get("InfRiskColor"), todayStr);
            riskScore = latestDoubleValue(byName.get("Oidiumindex"), todayStr);
        }

        String riskLevel = colorToRiskLevel(latestColor);

        WbiPrognosis prognosis = prognosisRepository
                .findByDiseaseAndForecastDate(disease, today)
                .orElseGet(() -> {
                    WbiPrognosis p = new WbiPrognosis();
                    p.setDisease(disease);
                    p.setForecastDate(today);
                    return p;
                });

        prognosis.setRiskLevel(riskLevel);
        prognosis.setRiskScore(riskScore);
        prognosis.setRiskColor(latestColor);
        prognosis.setIsForecast(false);
        prognosis.setFetchedAt(LocalDateTime.now());

        if ("peronospora".equals(disease)) {
            prognosis.setSoilInfectionCount(
                    (int) latestDoubleValue(byName.get("Bodeninfektion"), todayStr));
            prognosis.setInfectionEventCount(
                    (int) latestDoubleValue(byName.get("Infektionen"), todayStr));
            prognosis.setSporulationCount(
                    (int) latestDoubleValue(byName.get("Sporulationen"), todayStr));
            prognosis.setLeafWetnessHours(
                    latestDoubleValue(byName.get("Stunden Blattnässe"), todayStr));
            prognosis.setLeafWetnessDegreeHours(
                    latestDoubleValue(byName.get("Gradstunden Blattnässe"), todayStr));
        } else if ("oidium".equals(disease)) {
            String yesterdayStr = today.minusDays(1).format(DATE_FMT);
            prognosis.setOidiumIndex(latestDoubleValue(byName.get("Oidiumindex"), todayStr));
            prognosis.setOntogeneticIndex(
                    latestDoubleValue(byName.get("Ontogenetischer Index"), todayStr));
            prognosis.setOidiumDailyValue(
                    latestDoubleValue(byName.get("Oidium Tageswert"), yesterdayStr));
        }

        prognosisRepository.save(prognosis);
        log.info("Stored WBI {} prognosis for {} — riskLevel={}, score={}",
                disease, today, riskLevel, String.format("%.1f", riskScore));

        if ("peronospora".equals(disease)) {
            parseAndStorePheno(root, today);
        }
    }

    private void parseAndStorePheno(JsonNode root, LocalDate today) {
        JsonNode phenoChart = root.path("chartObj").path("pheno_results").path("chart");
        if (phenoChart.isMissingNode() || !phenoChart.isArray()) {
            log.debug("No pheno_results.chart in vitimeteo response");
            return;
        }

        Map<String, JsonNode> byName = collectSeriesByName(phenoChart);
        JsonNode bbchSeries = byName.get("BBCH-Code");
        if (bbchSeries == null) {
            log.debug("No BBCH-Code series in pheno_results");
            return;
        }

        // Collect all available dates (past + forecast window) from BBCH series
        Map<String, Integer> bbchByDate = new LinkedHashMap<>();
        for (JsonNode pt : bbchSeries.path("point")) {
            String dateStr = pointDate(pt.path(0));
            if (dateStr != null) bbchByDate.put(dateStr, pt.path(1).asInt(0));
        }

        Map<String, Double> huglinByDate   = extractDoubleSeriesByDate(byName.get("Huglin-Index Heute"));
        Map<String, Double> leafCountByDate = extractDoubleSeriesByDate(byName.get("Blattanzahl"));
        Map<String, Double> leafAreaByDate  = extractDoubleSeriesByDate(byName.get("Blattfläche Gesamt"));

        int saved = 0;
        for (String dateStr : bbchByDate.keySet()) {
            LocalDate phenoDate = LocalDate.parse(dateStr, DATE_FMT);
            VitimeteoPheno pheno = phenoRepository.findByPhenoDate(phenoDate)
                    .orElseGet(() -> {
                        VitimeteoPheno p = new VitimeteoPheno();
                        p.setPhenoDate(phenoDate);
                        return p;
                    });
            pheno.setBbchCode(bbchByDate.get(dateStr));
            pheno.setHuglinIndex(huglinByDate.get(dateStr));
            pheno.setLeafCount(leafCountByDate.get(dateStr));
            pheno.setLeafAreaCm2(leafAreaByDate.get(dateStr));
            pheno.setFetchedAt(LocalDateTime.now());
            phenoRepository.save(pheno);
            saved++;
        }
        log.info("Stored {} vitimeteo pheno records (up to {}+{}d)",
                saved, today, FORECAST_WINDOW_DAYS);
    }

    // -----------------------------------------------------------------------
    // expert_data.json parsing — peronospora Inkubation events
    // -----------------------------------------------------------------------

    private void parseAndStoreIncubation(JsonNode root, LocalDate today) {
        JsonNode processChart = root.path("chartObj").path("process_results").path("chart");
        if (processChart.isMissingNode() || !processChart.isArray()) {
            log.warn("No process_results.chart in vitimeteo expert_data response");
            return;
        }

        List<PeronosporaInfectionEvent> savedEvents = new ArrayList<>();

        for (JsonNode item : processChart) {
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String seriesId = entry.getKey();
                JsonNode series = entry.getValue();
                if (!"Inkubation".equals(series.path("name").asText(""))) continue;

                JsonNode pointArr = series.path("point");
                if (!pointArr.isArray() || pointArr.isEmpty()) continue;

                // Parse points: [datetime_string, pct_0_to_100]
                List<double[]> points = new ArrayList<>();
                for (JsonNode pt : pointArr) {
                    String dtStr = pt.path(0).asText("");
                    double pct = pt.path(1).asDouble();
                    try {
                        // Timestamps may omit seconds: "2026-05-14 12:00" — try both formats
                        LocalDateTime dt;
                        if (dtStr.length() == 16) {
                            dt = LocalDateTime.parse(dtStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        } else {
                            dt = LocalDateTime.parse(dtStr, DT_FMT);
                        }
                        points.add(new double[]{(double) dt.toEpochSecond(ZoneOffset.UTC), pct});
                    } catch (Exception ex) {
                        log.warn("Could not parse Inkubation point datetime '{}' in {}", dtStr, seriesId);
                    }
                }
                if (points.isEmpty()) continue;

                LocalDateTime infectionDatetime = LocalDateTime.ofEpochSecond(
                        (long) points.get(0)[0], 0, ZoneOffset.UTC);

                long nowEpoch = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
                double[] closestPoint = points.get(0);
                long closestDelta = Math.abs((long) points.get(0)[0] - nowEpoch);
                for (double[] pt : points) {
                    long delta = Math.abs((long) pt[0] - nowEpoch);
                    if (delta < closestDelta) {
                        closestDelta = delta;
                        closestPoint = pt;
                    }
                }
                double latestPct = closestPoint[1];
                boolean isActive = interpolateThreshold(points, 100.0) == null
                        || interpolateThreshold(points, 100.0).toEpochSecond(ZoneOffset.UTC) > nowEpoch;

                LocalDateTime deadline80   = interpolateThreshold(points, 80.0);
                LocalDateTime sporulation  = interpolateThreshold(points, 100.0);

                PeronosporaInfectionEvent event = infectionEventRepository.findBySeriesId(seriesId)
                        .orElseGet(() -> {
                            PeronosporaInfectionEvent ev = new PeronosporaInfectionEvent();
                            ev.setSeriesId(seriesId);
                            return ev;
                        });
                event.setInfectionDatetime(infectionDatetime);
                event.setIncubationPctLatest(latestPct);
                event.setIncubation80PctDatetime(deadline80);
                event.setSporulationDatetime(sporulation);
                event.setIsActive(isActive);
                event.setFetchedDate(today);
                infectionEventRepository.save(event);
                savedEvents.add(event);

                log.debug("Saved infection event {} — pct={}% active={}",
                        seriesId, String.format("%.1f", latestPct), isActive);
            }
        }

        log.info("Saved {} peronospora Inkubation events from expert_data", savedEvents.size());

        final int finalInfCount = savedEvents.size();
        JsonNode eventsChart = root.path("chartObj").path("events_results").path("chart");
        int soilCount = 0;
        int sporulationCount = 0;
        if (eventsChart.isArray()) {
            Map<String, JsonNode> eventsByName = collectSeriesByName(eventsChart);
            JsonNode soilInf = eventsByName.get("Bodeninfektion");
            if (soilInf != null) soilCount = soilInf.path("point").size();
            JsonNode sporul = eventsByName.get("Sporulation");
            if (sporul != null) sporulationCount = sporul.path("point").size();
        }
        final int finalSoilCount = soilCount;
        final int finalSpCount   = sporulationCount;

        prognosisRepository.findByDiseaseAndForecastDate("peronospora", today).ifPresent(prognosis -> {
            long active = savedEvents.stream()
                    .filter(ev -> Boolean.TRUE.equals(ev.getIsActive())).count();
            prognosis.setActiveIncubationEvents((int) active);
            prognosis.setInfectionEventCount(finalInfCount);
            prognosis.setSoilInfectionCount(finalSoilCount);
            prognosis.setSporulationCount(finalSpCount);

            savedEvents.stream()
                    .filter(ev -> Boolean.TRUE.equals(ev.getIsActive())
                            && ev.getIncubation80PctDatetime() != null)
                    .map(PeronosporaInfectionEvent::getIncubation80PctDatetime)
                    .min(Comparator.naturalOrder())
                    .ifPresent(dt -> prognosis.setNextSprayDeadline(dt.toLocalDate()));

            savedEvents.stream()
                    .filter(ev -> Boolean.TRUE.equals(ev.getIsActive())
                            && ev.getSporulationDatetime() != null)
                    .map(PeronosporaInfectionEvent::getSporulationDatetime)
                    .max(Comparator.naturalOrder())
                    .ifPresent(dt -> prognosis.setLastSporulationDate(dt.toLocalDate()));

            prognosisRepository.save(prognosis);
            log.info("Updated peronospora prognosis summary: activeEvents={}, nextDeadline={}",
                    active, prognosis.getNextSprayDeadline());
        });
    }

    /**
     * Interpolate or extrapolate the datetime when incubation reaches {@code target} %.
     * Each point in the list is {@code double[]{epochSeconds, pct}}.
     * Returns null if slope is zero or the data is insufficient.
     */
    private LocalDateTime interpolateThreshold(List<double[]> points, double target) {
        // Search for a bracketing pair
        for (int i = 1; i < points.size(); i++) {
            double v0 = points.get(i - 1)[1];
            double v1 = points.get(i)[1];
            if (v0 <= target && v1 >= target) {
                double frac = (target - v0) / (v1 - v0);
                double t0 = points.get(i - 1)[0];
                double t1 = points.get(i)[0];
                return LocalDateTime.ofEpochSecond((long) (t0 + frac * (t1 - t0)), 0, ZoneOffset.UTC);
            }
        }
        // Extrapolate linearly from the last two points if still trending upward
        if (points.size() >= 2) {
            int n = points.size();
            double v0 = points.get(n - 2)[1], v1 = points.get(n - 1)[1];
            double t0 = points.get(n - 2)[0], t1 = points.get(n - 1)[0];
            double dt = t1 - t0;
            if (dt > 0 && v1 > v0 && v1 < target) {
                double slope = (v1 - v0) / dt;  // pct per second
                return LocalDateTime.ofEpochSecond(
                        (long) (t1 + (target - v1) / slope), 0, ZoneOffset.UTC);
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    private Map<String, JsonNode> collectSeriesByName(JsonNode chartArray) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode item : chartArray) {
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                result.put(e.getValue().path("name").asText(""), e.getValue());
            }
        }
        return result;
    }

    /** Safely extract the date prefix (yyyy-MM-dd) from a point timestamp node. */
    private static String pointDate(JsonNode node) {
        String s = node.asText("");
        return s.length() >= 10 ? s.substring(0, 10) : null;
    }

    /** Returns the last string point value with date ≤ todayStr, or null. */
    private String latestStringValue(JsonNode series, String todayStr) {
        if (series == null) return null;
        String val = null;
        for (JsonNode pt : series.path("point")) {
            String d = pointDate(pt.path(0));
            if (d != null && d.compareTo(todayStr) <= 0) {
                String v = pt.path(1).asText(null);
                if (v != null) val = v;
            }
        }
        return val;
    }

    /** Returns the last numeric point value with date ≤ todayStr, or 0.0. */
    private double latestDoubleValue(JsonNode series, String todayStr) {
        if (series == null) return 0.0;
        double val = 0.0;
        for (JsonNode pt : series.path("point")) {
            String d = pointDate(pt.path(0));
            if (d != null && d.compareTo(todayStr) <= 0) {
                val = pt.path(1).asDouble(0.0);
            }
        }
        return val;
    }

    /** Extracts all points from a numeric series as a date-string → value map. */
    private Map<String, Double> extractDoubleSeriesByDate(JsonNode series) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (series == null) return result;
        for (JsonNode pt : series.path("point")) {
            String d = pointDate(pt.path(0));
            if (d != null) result.put(d, pt.path(1).asDouble(0.0));
        }
        return result;
    }

    /**
     * Map vitimeteo InfRiskColor value to a named risk level string.
     *
     * #FFFFFF (white) is used as a boundary/no-data sentinel → NO_INFECTION.
     *
     * Oidium uses hex colours (yellow/orange/red scale):
     *   #49EA04 / #77FC3F / lime (green) → NO_INFECTION
     *   #FFFF80 (yellow)                 → LOW
     *   #FC7625 (orange)                 → INFECTION_RISK
     *   #F32501 (red)                    → HIGH
     *
     * Peronospora uses CSS named colours and pink hex values:
     *   lime / green                     → NO_INFECTION
     *   #FFDDDD (light pink, R-G ≤ 60)  → LOW
     *   #FFAAAA (darker pink, R-G > 60) → INFECTION_RISK
     *
     * Discrimination: peronospora pinks have high B channel (≥ 100); oidium orange/red do not.
     */
    private String colorToRiskLevel(String hex) {
        if (hex == null || hex.isBlank()) return "NO_INFECTION";
        switch (hex.toLowerCase()) {
            case "#ffffff": return "NO_INFECTION";   // boundary/no-data sentinel
            case "lime":
            case "green":   return "NO_INFECTION";
            case "yellow":  return "LOW";
            case "orange":  return "INFECTION_RISK";
            case "red":     return "HIGH";
            default: break;
        }
        if (hex.length() < 7 || hex.charAt(0) != '#') return "NO_INFECTION";
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            if (r >= 250 && g >= 250 && b >= 250) return "NO_INFECTION";  // near-white guard
            if (g > r) return "NO_INFECTION";  // green dominant
            if (b > 100) {
                // Peronospora pink range: R-G determines severity
                return (r - g > 60) ? "INFECTION_RISK" : "LOW";
            }
            // Oidium yellow/orange/red range (B is low)
            if (g > 150) return "LOW";            // #FFFF80 yellow
            if (g > 50)  return "INFECTION_RISK"; // #FC7625 orange
            return "HIGH";                         // #F32501 red
        } catch (NumberFormatException e) {
            return "NO_INFECTION";
        }
    }

    // -----------------------------------------------------------------------
    // On-demand access — with staleness check
    // -----------------------------------------------------------------------

    private void ensureFreshPrognosisData(String disease) {
        try {
            Optional<WbiPrognosis> latest = prognosisRepository.findTopByDiseaseOrderByForecastDateDesc(disease);
            if (latest.isEmpty()) {
                log.info("No {} prognosis data found, fetching from vitimeteo", disease);
                refreshPrognosisData(disease);
                return;
            }

            LocalDateTime recordedTime = latest.get().getCreatedAt();
            LocalDateTime staleThreshold = LocalDateTime.now().minusHours(WBI_DATA_FRESHNESS_HOURS);
            long hoursOld = java.time.temporal.ChronoUnit.HOURS.between(recordedTime, LocalDateTime.now());

            if (recordedTime.isBefore(staleThreshold)) {
                log.info("WBI {} data is {} hours old, refreshing", disease, hoursOld);
                refreshPrognosisData(disease);
            } else {
                log.debug("WBI {} data is fresh ({} hours old)", disease, hoursOld);
            }
        } catch (Exception e) {
            log.warn("Error checking prognosis freshness: {}", e.getMessage());
        }
    }

    private void refreshPrognosisData(String disease) {
        LocalDate today = LocalDate.now();
        try {
            warmUpSession();
            int programId = "peronospora".equalsIgnoreCase(disease)
                    ? PERONOSPORA_PROGRAM_ID : OIDIUM_PROGRAM_ID;
            fetchAndStore(disease, programId, today);
            if ("peronospora".equalsIgnoreCase(disease)) {
                fetchAndStoreExpertData(today);
            }
        } catch (Exception e) {
            log.warn("WBI prognosis refresh skipped for {} — service unavailable: {}",
                    disease, e.getMessage());
        }
    }

    public Optional<WbiPrognosis> getLatestPrognosis(String disease) {
        ensureFreshPrognosisData(disease);
        return prognosisRepository.findTopByDiseaseOrderByForecastDateDesc(disease);
    }

    public List<WbiPrognosis> getPrognosisHistory(String disease, LocalDate startDate, LocalDate endDate) {
        return prognosisRepository.findByDiseaseAndForecastDateBetweenOrderByForecastDateDesc(
                disease, startDate, endDate);
    }

    public List<PeronosporaInfectionEvent> getActiveIncubationEvents() {
        return infectionEventRepository.findByIsActiveTrueOrderByInfectionDatetimeDesc();
    }

    public Optional<VitimeteoPheno> getLatestPheno() {
        return phenoRepository.findTopByPhenoDateLessThanEqualOrderByPhenoDateDesc(LocalDate.now());
    }

    public int getMaxBbchCode() {
        return phenoRepository.findMaxBbchCode().orElse(0);
    }
}
