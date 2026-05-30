package com.rebenbot.service;

import com.rebenbot.model.FracCode;
import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.FungicideProduct;
import com.rebenbot.model.FungicideTargetDisease;
import com.rebenbot.repository.FracCodeRepository;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.FungicideProductRepository;
import com.rebenbot.repository.FungicideTargetDiseaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synchronises fungicide product data against the BVL PSM-API
 * (Bundesamt für Verbraucherschutz und Lebensmittelsicherheit).
 *
 * <p>For each product in the local database a lookup is performed against
 * {@code https://psm-api.bvl.bund.de/ords/psm/api-v1/mittel/} to confirm the
 * German product authorisation (Zulassungsnummer) and its expiry date.
 * BVL data is updated monthly; the sync runs automatically on the 1st of each month
 * and can also be triggered manually via {@code POST /api/v1/admin/sync/bvl-api}.
 */
@Service
@Slf4j
public class FungicideDataSyncService {

    // --- static FRAC code lookup map (active substance name → FRAC code) ---------
    //
    // Maps lower-cased BVL wirkstoffname_en values to the corresponding FRAC code.
    // Sources: FRAC Code List 2024, EPPO, BVL PSM-DB. Update annually if new active
    // substances are approved for German viticulture.

    private static final Map<String, String> ACTIVE_SUBSTANCE_TO_FRAC;

    static {
        Map<String, String> m = new HashMap<>();
        // M1 – Copper-based fungicides (contact, multi-site)
        m.put("copper hydroxide", "M1");
        m.put("copper oxychloride", "M1");
        m.put("copper sulphate", "M1");
        m.put("copper sulfate", "M1");
        m.put("tribasic copper sulphate", "M1");
        m.put("copper octanoate", "M1");
        m.put("cuprous oxide", "M1");
        m.put("copper oxide", "M1");
        m.put("copper sulphate, tribasic", "M1");
        // M2 – Sulphur
        m.put("sulphur", "M2");
        m.put("sulfur", "M2");
        // M3 – Dithiocarbamates
        m.put("mancozeb", "M3");
        m.put("maneb", "M3");
        m.put("metiram", "M3");
        m.put("propineb", "M3");
        m.put("thiram", "M3");
        m.put("zineb", "M3");
        m.put("ziram", "M3");
        // M4 – Phthalimides
        m.put("folpet", "M4");
        m.put("captan", "M4");
        // FRAC 3 – DMI (triazoles / imidazoles, sterol biosynthesis inhibitors)
        m.put("tebuconazole", "3");
        m.put("myclobutanil", "3");
        m.put("penconazole", "3");
        m.put("hexaconazole", "3");
        m.put("triadimenol", "3");
        m.put("metconazole", "3");
        m.put("difenoconazole", "3");
        m.put("prothioconazole", "3");
        m.put("flutriafol", "3");
        m.put("bitertanol", "3");
        m.put("bromuconazole", "3");
        m.put("tetraconazole", "3");
        m.put("ipconazole", "3");
        m.put("triflumizole", "3");
        m.put("imazalil", "3");
        m.put("fenarimol", "3");
        m.put("triadimefon", "3");
        // FRAC 4 – Phenylamides (oomycetes — Peronospora)
        m.put("metalaxyl", "4");
        m.put("metalaxyl-m", "4");
        m.put("mefenoxam", "4");
        m.put("benalaxyl", "4");
        m.put("benalaxyl-m", "4");
        // FRAC 5 – Amines / morpholines
        m.put("spiroxamine", "5");
        m.put("tridemorph", "5");
        m.put("fenpropimorph", "5");
        m.put("fenpropidin", "5");
        // FRAC 6 – Morpholines (sterol esterification)
        m.put("bupirimate", "6");
        // FRAC 7 – SDHI (succinate dehydrogenase inhibitors)
        m.put("boscalid", "7");
        m.put("fluxapyroxad", "7");
        m.put("fluopyram", "7");
        m.put("isopyrazam", "7");
        m.put("penthiopyrad", "7");
        m.put("sedaxane", "7");
        m.put("bixafen", "7");
        m.put("pydiflumetofen", "7");
        m.put("isoflucypram", "7");
        // FRAC 9 – Anilinopyrimidines
        m.put("cyprodinil", "9");
        m.put("pyrimethanil", "9");
        m.put("mepanipyrim", "9");
        // FRAC 11 – QoI (strobilurins)
        m.put("azoxystrobin", "11");
        m.put("trifloxystrobin", "11");
        m.put("kresoxim-methyl", "11");
        m.put("picoxystrobin", "11");
        m.put("pyraclostrobin", "11");
        m.put("dimoxystrobin", "11");
        m.put("fenaminstrobin", "11");
        m.put("orysastrobin", "11");
        // FRAC 12 – Phenylpyrroles
        m.put("fludioxonil", "12");
        // FRAC 13 – Quinoline fungicides
        m.put("quinoxyfen", "13");
        // FRAC 33 – Phosphonates / phosphite
        m.put("fosetyl", "33");
        m.put("fosetyl-aluminium", "33");
        m.put("fosetyl-al", "33");
        m.put("phosphorous acid", "33");
        m.put("potassium phosphonate", "33");
        m.put("disodium phosphonate", "33");
        // FRAC 40 – CAA (carboxylic acid amides — oomycetes)
        m.put("mandipropamid", "40");
        m.put("iprovalicarb", "40");
        m.put("benthiavalicarb", "40");
        m.put("dimethomorph", "40");
        m.put("valifenalate", "40");
        m.put("pyrimorph", "40");
        m.put("flumorph", "40");
        // FRAC 45 – Aza-naphthalenes
        m.put("ametoctradin", "45");
        // FRAC U7 – Cymoxanil (unknown target)
        m.put("cymoxanil", "U7");
        // FRAC U13 – Fenpyrazamine / Quinazolinones
        m.put("fenpyrazamine", "U13");
        m.put("proquinazid", "U13");
        // FRAC 49 – OSBPI (oxysterol-binding protein inhibitors)
        m.put("oxathiapiprolin", "49");
        ACTIVE_SUBSTANCE_TO_FRAC = Collections.unmodifiableMap(m);
    }

    // --- configuration -----------------------------------------------------------

    @Value("${sync.bvl.url:https://psm-api.bvl.bund.de/ords/psm/api-v1}")
    private String bvlApiBaseUrl;

    @Value("${sync.bvl.timeout-seconds:60}")
    private int bvlTimeoutSeconds;

    @Value("${sync.bvl.enabled:true}")
    private boolean bvlSyncEnabled;

    // --- state -------------------------------------------------------------------

    private volatile LocalDateTime lastBvlSync;
    private volatile int lastBvlSyncUpdatedCount;
    private volatile String lastBvlSyncResult;

    // --- dependencies ------------------------------------------------------------

    private final FungicideProductRepository fungicideProductRepository;
    private final FracCodeRepository fracCodeRepository;
    private final FungalDiseaseRepository fungalDiseaseRepository;
    private final FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository;
    private final WebClient bvlWebClient;

    public FungicideDataSyncService(FungicideProductRepository fungicideProductRepository,
                                    FracCodeRepository fracCodeRepository,
                                    FungalDiseaseRepository fungalDiseaseRepository,
                                    FungicideTargetDiseaseRepository fungicideTargetDiseaseRepository,
                                    WebClient.Builder webClientBuilder) {
        this.fungicideProductRepository = fungicideProductRepository;
        this.fracCodeRepository = fracCodeRepository;
        this.fungalDiseaseRepository = fungalDiseaseRepository;
        this.fungicideTargetDiseaseRepository = fungicideTargetDiseaseRepository;
        this.bvlWebClient = webClientBuilder.build().mutate()
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Rebenbot/1.0 vineyard management")
                .build();
    }

    // --- public API --------------------------------------------------------------

    public Map<String, Object> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("bvlSyncEnabled", bvlSyncEnabled);
        status.put("lastBvlSync", lastBvlSync != null ? lastBvlSync.toString() : "never");
        status.put("lastBvlSyncUpdatedCount", lastBvlSyncUpdatedCount);
        status.put("lastBvlSyncResult", lastBvlSyncResult != null ? lastBvlSyncResult : "N/A");
        return status;
    }

    /**
     * Queries the BVL PSM-API for each product stored in the local database and
     * records the German product authorisation number (Zulassungsnummer) and expiry.
     *
     * <p>Each product is looked up in BVL first by exact registration number (if already
     * known), then by a case-insensitive substring match on {@code MITTELNAME}.  If BVL
     * returns multiple candidates the result whose name most closely matches the stored
     * product name (trimmed, case-insensitive) is used.
     *
     * <p>BVL data is updated monthly; the scheduled call runs on the 1st of each month.
     *
     * @return human-readable result summary
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public String triggerBvlApiSync() {
        if (!bvlSyncEnabled) {
            return "BVL sync is disabled (sync.bvl.enabled=false)";
        }
        log.info("Starting BVL PSM-API sync from {}", bvlApiBaseUrl);

        // Build active substance name cache (wirknr → wirkstoffname_en) for FRAC resolution.
        // Loaded once here and passed to both the import step and the update loop.
        Map<String, String> wirknrToName = buildWirkstoffNameCache();

        // Step 1: import any VITVI products from BVL not yet in the local DB
        int imported = importVitviProductsFromBvl(wirknrToName);

        // Step 2: verify / update products not already synced today (avoids re-processing fresh imports)
        List<FungicideProduct> products = fungicideProductRepository.findAll();
        int updated = 0, notFound = 0, errors = 0;

        FracCode unknownFracSingleton = getOrCreateUnknownFracCode();

        for (FungicideProduct product : products) {
            boolean hasKnownFrac = product.getFracCode() != null
                    && !"UNKNOWN".equals(product.getFracCode().getCode());
            boolean verifiedToday = LocalDate.now().equals(product.getBvlLastVerified());
            boolean hasDosage = product.getBaseDosageMlHa() != null;

            // Skip products that are fully up-to-date (known FRAC + dosage + verified today)
            if (hasKnownFrac && hasDosage && verifiedToday) {
                continue;
            }

            // Fast path: already verified today but missing FRAC or dosage — resolve without
            // a full BVL mittel lookup.
            if (verifiedToday && product.getBvlRegistrationNumber() != null) {
                try {
                    boolean changed = false;

                    if (!hasKnownFrac) {
                        List<String> wirknrList = fetchWirkstoffGehalteForKennr(product.getBvlRegistrationNumber());
                        FracAndSubstance fas = resolveFracAndSubstance(wirknrList, wirknrToName, unknownFracSingleton);
                        if (!"UNKNOWN".equals(fas.fracCode().getCode())) {
                            product.setFracCode(fas.fracCode());
                            if (!fas.activeSubstance().isBlank()) {
                                product.setActiveSubstance(fas.activeSubstance());
                            }
                            log.debug("FRAC resolved for '{}' → {}", product.getName(), fas.fracCode().getCode());
                            changed = true;
                        }
                    }

                    if (!hasDosage) {
                        List<BvlKultur> kulturList = fetchKulturListForKennr(product.getBvlRegistrationNumber());
                        Set<String> vitviAwgIds = kulturList.stream()
                                .filter(k -> "VITVI".equals(k.kultur()) && !"J".equals(k.ausgenommen()))
                                .map(BvlKultur::awgId)
                                .collect(Collectors.toSet());
                        if (!vitviAwgIds.isEmpty()) {
                            List<BvlAufwand> aufwandList = fetchAufwandForKennr(product.getBvlRegistrationNumber());
                            aufwandList.stream()
                                    .filter(a -> vitviAwgIds.contains(a.awgId()) && "LH".equals(a.mAufwandEinheit())
                                            && a.mAufwand() != null && a.mAufwand() > 0)
                                    .mapToDouble(BvlAufwand::mAufwand)
                                    .min()
                                    .ifPresent(lHa -> product.setBaseDosageMlHa(lHa * 1000));
                            if (product.getBaseDosageMlHa() != null) {
                                log.debug("Dosage resolved for '{}' → {} mL/ha",
                                        product.getName(), product.getBaseDosageMlHa());
                                changed = true;
                            }
                        }
                    }

                    if (changed) {
                        fungicideProductRepository.save(product);
                        updated++;
                    }
                } catch (Exception e) {
                    log.warn("Fast-path resolution failed for '{}'", product.getName(), e);
                    errors++;
                }
                continue;
            }

            // Full BVL update path (product not verified today)
            try {
                BvlMittel match = null;

                // If we already have a registration number use it for a direct lookup first.
                if (product.getBvlRegistrationNumber() != null) {
                    match = fetchBvlMittelByKennr(product.getBvlRegistrationNumber());
                }

                // Fall back to name search.
                if (match == null) {
                    match = searchBvlMittelByName(product.getName());
                }

                if (match != null) {
                    product.setBvlRegistrationNumber(match.kennr());
                    product.setBvlApprovedInGermany(
                            match.zulEnde() == null || !match.zulEnde().isBefore(LocalDate.now()));
                    product.setBvlApprovalExpiry(match.zulEnde());
                    product.setBvlLastVerified(LocalDate.now());

                    // Resolve FRAC code when still UNKNOWN
                    if (!hasKnownFrac) {
                        List<String> wirknrList = fetchWirkstoffGehalteForKennr(match.kennr());
                        FracAndSubstance fas = resolveFracAndSubstance(wirknrList, wirknrToName, unknownFracSingleton);
                        product.setFracCode(fas.fracCode());
                        if (!fas.activeSubstance().isBlank()) {
                            product.setActiveSubstance(fas.activeSubstance());
                        }
                    }

                    syncTargetDiseasesForProduct(product, match.kennr()); // may update phiDays
                    fungicideProductRepository.save(product);
                    log.debug("BVL matched '{}' → kennr={}, zul_ende={}", product.getName(),
                            match.kennr(), match.zulEnde());
                    updated++;
                } else {
                    log.debug("BVL: no match found for product '{}'", product.getName());
                    notFound++;
                }
            } catch (Exception e) {
                log.warn("BVL lookup failed for product '{}'", product.getName(), e);
                errors++;
            }
        }

        lastBvlSync = LocalDateTime.now();
        lastBvlSyncUpdatedCount = updated;
        lastBvlSyncResult = String.format("SUCCESS — %d imported, %d updated, %d not found in BVL, %d errors.",
                imported, updated, notFound, errors);
        log.info("BVL sync complete: {}", lastBvlSyncResult);
        return lastBvlSyncResult;
    }

    // --- BVL import (new products) -----------------------------------------------

    /**
     * Queries BVL for all products with a grapevine (VITVI) approved use and creates
     * a local {@link FungicideProduct} row for every one not already in the database.
     * Active substance and FRAC code are resolved via BVL {@code /wirkstoff_gehalt/}.
     *
     * @param wirknrToName  pre-loaded cache of wirknr → wirkstoffname_en
     * @return number of new products created
     */
    private int importVitviProductsFromBvl(Map<String, String> wirknrToName) {
        Set<String> vitviKennr = fetchAllVitviKennrFromBvl();
        if (vitviKennr.isEmpty()) {
            log.info("BVL import: no VITVI kennr found — skipping import");
            return 0;
        }

        Set<String> existingKennr = fungicideProductRepository.findAll().stream()
                .map(FungicideProduct::getBvlRegistrationNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> newKennr = vitviKennr.stream()
                .filter(k -> !existingKennr.contains(k))
                .collect(Collectors.toSet());

        if (newKennr.isEmpty()) {
            log.info("BVL import: all {} VITVI products already in DB", vitviKennr.size());
            return 0;
        }

        log.info("BVL import: {} new VITVI products to import (of {} total)", newKennr.size(), vitviKennr.size());
        FracCode unknownFrac = getOrCreateUnknownFracCode();
        int imported = 0;

        for (String kennr : newKennr) {
            try {
                BvlMittel mittel = fetchBvlMittelByKennr(kennr);
                if (mittel == null || mittel.mittelname() == null) continue;

                // Skip already-expired products
                if (mittel.zulEnde() != null && mittel.zulEnde().isBefore(LocalDate.now())) {
                    log.debug("BVL import: skipping expired product kennr={} ({})", kennr, mittel.zulEnde());
                    continue;
                }

                // Resolve active substance(s) and FRAC code via wirkstoff_gehalt
                List<String> wirknrList = fetchWirkstoffGehalteForKennr(kennr);
                FracAndSubstance fas = resolveFracAndSubstance(wirknrList, wirknrToName, unknownFrac);
                String activeSubstance = fas.activeSubstance().isBlank()
                        ? mittel.mittelname() : fas.activeSubstance();

                FungicideProduct product = FungicideProduct.builder()
                        .name(mittel.mittelname())
                        .activeSubstance(activeSubstance)
                        .bvlRegistrationNumber(kennr)
                        .bvlApprovedInGermany(true)
                        .bvlApprovalExpiry(mittel.zulEnde())
                        .bvlLastVerified(LocalDate.now())
                        .fracCode(fas.fracCode())
                        .build();

                FungicideProduct saved = fungicideProductRepository.save(product);
                syncTargetDiseasesForProduct(saved, kennr);
                imported++;
            } catch (Exception e) {
                log.warn("BVL import failed for kennr '{}': {}", kennr, e.getMessage());
            }
        }

        log.info("BVL import: {} products imported", imported);
        return imported;
    }

    /**
     * Paginates through {@code /awg_kultur/?q={"KULTUR":"VITVI"}} and returns the
     * set of unique Kennzeichen (kennr) values extracted from the awg_id field.
     * The awg_id format is {@code {kennr}/{sequence}}, so kennr is the substring
     * before the last {@code /}.
     */
    @SuppressWarnings("unchecked")
    private Set<String> fetchAllVitviKennrFromBvl() {
        Set<String> kennrSet = new HashSet<>();
        int offset = 0;
        final int limit = 500;

        while (true) {
            try {
                // Use $instr operator — BVL ORDS rejects plain equality filters
                String q = "{\"KULTUR\":{\"$instr\":\"VITVI\"}}";
                String uri = bvlApiBaseUrl + "/awg_kultur/?q="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8)
                        + "&limit=" + limit + "&offset=" + offset;
                Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();

                if (response == null) break;
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                if (items == null || items.isEmpty()) break;

                for (Map<String, Object> item : items) {
                    String awgId = (String) item.get("awg_id");
                    String ausgenommen = (String) item.get("ausgenommen");
                    if (awgId != null && !"J".equals(ausgenommen)) {
                        int lastSlash = awgId.lastIndexOf('/');
                        if (lastSlash > 0) {
                            kennrSet.add(awgId.substring(0, lastSlash));
                        }
                    }
                }

                if (items.size() < limit) break;
                offset += limit;
            } catch (Exception e) {
                log.warn("BVL VITVI kultur fetch failed at offset {}: {}", offset, e.getMessage());
                break;
            }
        }

        log.info("BVL import: found {} unique VITVI kennr", kennrSet.size());
        return kennrSet;
    }

    /**
     * Returns the "UNKNOWN" {@link FracCode} used as a placeholder for imported
     * products whose FRAC classification is not available from the BVL API.
     * Creates the record if it does not exist.
     */
    private FracCode getOrCreateUnknownFracCode() {
        return fracCodeRepository.findByCode("UNKNOWN").orElseGet(() -> {
            FracCode fc = new FracCode();
            fc.setCode("UNKNOWN");
            fc.setDescription("FRAC classification not yet assigned");
            return fracCodeRepository.save(fc);
        });
    }

    // --- scheduled sync ----------------------------------------------------------

    /**
     * Automatic monthly BVL PSM-API sync.
     * Runs at 04:00 on the 1st of each month (BVL publishes updates monthly).
     */
    @Scheduled(cron = "${sync.bvl.cron:0 0 4 1 * *}")
    public void scheduledBvlSync() {
        log.info("Scheduled BVL PSM-API sync triggered");
        triggerBvlApiSync();
    }

    // --- BVL API helpers ---------------------------------------------------------

    /**
     * Fetches all BVL-approved uses for a product by Kennzeichen (registration number).
     * Returns awg records enriched with pest organism data (EPPO codes) and grape PHI.
     * Called live by the approvals proxy endpoint.
     */
    public List<Map<String, Object>> fetchApprovedUsesForKennr(String kennr) {
        List<BvlAwg> awgList = fetchAwgListForKennr(kennr);
        if (awgList.isEmpty()) return List.of();

        List<BvlSchadorg> schadorgList = fetchSchadorgListForKennr(kennr);
        List<BvlWartezeit> wartezeitList = fetchWartezeitForKennrAndKultur(kennr, "VITVI");

        Map<String, List<BvlSchadorg>> schadorgByAwg = schadorgList.stream()
                .collect(Collectors.groupingBy(BvlSchadorg::awgId));
        Map<String, Integer> phiByAwgId = wartezeitList.stream()
                .filter(w -> w.phi() != null)
                .collect(Collectors.toMap(BvlWartezeit::awgId, BvlWartezeit::phi, Math::min));

        return awgList.stream().map(awg -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("awgId", awg.awgId());
            if (awg.maxApplications() != null) entry.put("maxApplicationsPerSeason", awg.maxApplications());
            if (awg.mittelaufwand() != null)   entry.put("dosageLHa", awg.mittelaufwand());
            if (awg.wirkungsbereich() != null) entry.put("wirkungsbereich", awg.wirkungsbereich());

            List<Map<String, Object>> pests = schadorgByAwg
                    .getOrDefault(awg.awgId(), List.of()).stream()
                    .filter(s -> !"J".equals(s.ausgenommen()))
                    .map(s -> {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("eppoCode", s.schadorg());
                        fungalDiseaseRepository.findByEppoCode(s.schadorg()).ifPresent(d -> {
                            p.put("commonName", d.getCommonName());
                            p.put("scientificName", d.getScientificName());
                            p.put("germanName", d.getGermanName());
                        });
                        return p;
                    })
                    .collect(Collectors.toList());
            entry.put("pestOrganisms", pests);

            Integer phi = phiByAwgId.get(awg.awgId());
            if (phi != null) entry.put("phiDaysForGrapes", phi);

            return entry;
        }).collect(Collectors.toList());
    }

    /**
     * Syncs fungicide_target_disease records for a product from BVL AWG data.
     * Only approved uses for grapevine (VITVI) are considered.
     * Also updates the product's phiDays from the minimum PHI across all grape-use records.
     * Called during BVL sync; modifies {@code product} in-place (phiDays may change).
     */
    @Transactional
    private void syncTargetDiseasesForProduct(FungicideProduct product, String kennr) {
        try {
            // Find all AWG entries where grapevine (VITVI) is an approved crop
            List<BvlKultur> kulturList = fetchKulturListForKennr(kennr);
            Set<String> vitviAwgIds = kulturList.stream()
                    .filter(k -> "VITVI".equals(k.kultur()) && !"J".equals(k.ausgenommen()))
                    .map(BvlKultur::awgId)
                    .collect(Collectors.toSet());

            if (vitviAwgIds.isEmpty()) {
                log.debug("Product '{}' (kennr={}) has no VITVI approved uses — skipping disease sync",
                        product.getName(), kennr);
                return;
            }

            // Get all pest organisms for this product, keep only those linked to VITVI awgs
            List<BvlSchadorg> schadorgList = fetchSchadorgListForKennr(kennr);
            Set<String> linkedEppoCodes = new HashSet<>();
            List<FungicideTargetDisease> newLinks = new ArrayList<>();

            for (BvlSchadorg sc : schadorgList) {
                if (!vitviAwgIds.contains(sc.awgId())) continue;
                if ("J".equals(sc.ausgenommen())) continue;
                if (linkedEppoCodes.contains(sc.schadorg())) continue;

                fungalDiseaseRepository.findByEppoCode(sc.schadorg()).ifPresent(disease -> {
                    linkedEppoCodes.add(sc.schadorg());
                    newLinks.add(FungicideTargetDisease.builder()
                            .product(product)
                            .disease(disease)
                            .build());
                });
            }

            // Update PHI and dosage from VITVI AWG records
            List<BvlWartezeit> wartezeitList = fetchWartezeitForKennrAndKultur(kennr, "VITVI");
            wartezeitList.stream()
                    .filter(w -> w.phi() != null && w.phi() >= 0)
                    .mapToInt(BvlWartezeit::phi)
                    .min()
                    .ifPresent(product::setPhiDays);

            // Derive baseDosageMlHa from BVL awg_aufwand for VITVI uses.
            // BVL stores dosage in L/ha (unit "LH"); convert to mL/ha (* 1000) and take the minimum
            // across VITVI AWG records as the conservative labeled rate.
            List<BvlAufwand> aufwandList = fetchAufwandForKennr(kennr);
            aufwandList.stream()
                    .filter(a -> vitviAwgIds.contains(a.awgId()) && "LH".equals(a.mAufwandEinheit())
                            && a.mAufwand() != null && a.mAufwand() > 0)
                    .mapToDouble(BvlAufwand::mAufwand)
                    .min()
                    .ifPresent(lHa -> product.setBaseDosageMlHa(lHa * 1000));

            // Replace existing disease links for this product
            fungicideTargetDiseaseRepository.deleteAll(
                    fungicideTargetDiseaseRepository.findByProductId(product.getId()));
            fungicideTargetDiseaseRepository.saveAll(newLinks);

            if (!newLinks.isEmpty()) {
                log.info("Disease sync for '{}': linked to {} disease(s) via EPPO codes: {}",
                        product.getName(), newLinks.size(), String.join(", ", linkedEppoCodes));
            }
        } catch (Exception e) {
            log.warn("Disease sync failed for product '{}'", product.getName(), e);
        }
    }

    // --- BVL AWG fetch helpers (one call per resource type per product) ----------

    @SuppressWarnings("unchecked")
    private List<BvlAwg> fetchAwgListForKennr(String kennr) {
        try {
            String uri = bvlApiBaseUrl + "/awg/?kennr="
                    + URLEncoder.encode(kennr, StandardCharsets.UTF_8) + "&limit=500";
            Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();
            return items.stream().map(i -> new BvlAwg(
                    (String) i.get("awg_id"),
                    (String) i.get("kennr"),
                    i.get("mittelaufwand") != null ? ((Number) i.get("mittelaufwand")).doubleValue() : null,
                    i.get("anwendungen_max_je_vegetation") != null
                            ? ((Number) i.get("anwendungen_max_je_vegetation")).intValue() : null,
                    (String) i.get("wirkungsbereich")
            )).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("BVL /awg/ fetch failed for kennr '{}': {}", kennr, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<BvlKultur> fetchKulturListForKennr(String kennr) {
        try {
            String q = "{\"AWG_ID\":{\"$instr\":\"" + kennr + "/\"}}";
            String uri = bvlApiBaseUrl + "/awg_kultur/?q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&limit=500";
            Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();
            return items.stream().map(i -> new BvlKultur(
                    (String) i.get("awg_id"),
                    (String) i.get("kultur"),
                    (String) i.get("ausgenommen")
            )).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("BVL /awg_kultur/ fetch failed for kennr '{}': {}", kennr, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<BvlSchadorg> fetchSchadorgListForKennr(String kennr) {
        try {
            String q = "{\"AWG_ID\":{\"$instr\":\"" + kennr + "/\"}}";
            String uri = bvlApiBaseUrl + "/awg_schadorg/?q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&limit=500";
            Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();
            return items.stream().map(i -> new BvlSchadorg(
                    (String) i.get("awg_id"),
                    (String) i.get("schadorg"),
                    (String) i.get("ausgenommen")
            )).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("BVL /awg_schadorg/ fetch failed for kennr '{}': {}", kennr, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<BvlAufwand> fetchAufwandForKennr(String kennr) {
        try {
            String q = "{\"AWG_ID\":{\"$instr\":\"" + kennr + "/\"}}";
            String uri = bvlApiBaseUrl + "/awg_aufwand/?q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&limit=500";
            Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();
            return items.stream().map(i -> new BvlAufwand(
                    (String) i.get("awg_id"),
                    i.get("m_aufwand") != null ? ((Number) i.get("m_aufwand")).doubleValue() : null,
                    (String) i.get("m_aufwand_einheit")
            )).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("BVL /awg_aufwand/ fetch failed for kennr '{}': {}", kennr, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<BvlWartezeit> fetchWartezeitForKennrAndKultur(String kennr, String kultur) {
        try {
            String q = "{\"AWG_ID\":{\"$instr\":\"" + kennr + "/\"},\"KULTUR\":\"" + kultur + "\"}";
            String uri = bvlApiBaseUrl + "/awg_wartezeit/?q="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&limit=500";
            Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();
            return items.stream().map(i -> {
                Number phi = (Number) i.get("gesetzt_wartezeit");
                // "XF" in gesetzt_wartezeit_bem means "keine Wartezeit erforderlich" (no waiting period)
                Integer phiDays = phi != null ? phi.intValue()
                        : "XF".equals(i.get("gesetzt_wartezeit_bem")) ? 0 : null;
                return new BvlWartezeit((String) i.get("awg_id"), phiDays);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("BVL /awg_wartezeit/ fetch failed for kennr '{}': {}", kennr, e.getMessage());
            return List.of();
        }
    }


    /**
     * Looks up a single product in BVL by registration number (Kennzeichen / KENNR).
     *
     * @return matched record or {@code null} if not found or on error
     */
    private BvlMittel fetchBvlMittelByKennr(String kennr) {
        try {
            String uri = bvlApiBaseUrl + "/mittel/?q=" +
                    URLEncoder.encode("{\"KENNR\":\"" + kennr + "\"}", StandardCharsets.UTF_8);
            Map<String, Object> response = bvlWebClient.get()
                    .uri(URI.create(uri))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds))
                    .block();
            return extractBestMatch(response, kennr);
        } catch (Exception e) {
            log.debug("BVL kennr lookup failed for '{}': {}", kennr, e.getMessage());
            return null;
        }
    }

    /**
     * Searches BVL for products whose name contains the given string (case-insensitive,
     * substring match via ORDS {@code $instr} operator).
     *
     * @return best-matching record or {@code null}
     */
    private BvlMittel searchBvlMittelByName(String name) {
        try {
            String uri = bvlApiBaseUrl + "/mittel/?q=" +
                    URLEncoder.encode("{\"MITTELNAME\":{\"$instr\":\"" + name + "\"}}", StandardCharsets.UTF_8);
            Map<String, Object> response = bvlWebClient.get()
                    .uri(URI.create(uri))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds))
                    .block();
            return extractBestMatch(response, name);
        } catch (Exception e) {
            log.debug("BVL name search failed for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the best-matching {@link BvlMittel} from an ORDS response.
     * Prefers an exact (case-insensitive) name match; falls back to first result.
     */
    @SuppressWarnings("unchecked")
    private BvlMittel extractBestMatch(Map<String, Object> response, String searchTerm) {
        if (response == null) return null;
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        if (items == null || items.isEmpty()) return null;

        // Prefer exact name match
        String searchLower = searchTerm.toLowerCase().trim();
        Map<String, Object> best = items.stream()
                .filter(item -> {
                    Object mn = item.get("mittelname");
                    return mn != null && mn.toString().toLowerCase().trim().equals(searchLower);
                })
                .findFirst()
                .orElse(items.get(0));

        String kennr = (String) best.get("kennr");
        LocalDate zulEnde = parseBvlDate(best.get("zul_ende"));
        String mittelname = (String) best.get("mittelname");
        return new BvlMittel(kennr, mittelname, zulEnde);
    }

    /**
     * Parses a BVL date value from the ORDS JSON response.
     * ORDS returns Oracle DATE columns as ISO-8601 strings like {@code "2030-12-31T00:00:00Z"}.
     */
    private LocalDate parseBvlDate(Object dateValue) {
        if (dateValue == null) return null;
        String s = dateValue.toString().trim();
        if (s.isBlank() || s.equalsIgnoreCase("null")) return null;
        try {
            // Strip time portion if present
            if (s.contains("T")) s = s.substring(0, s.indexOf('T'));
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            log.debug("Could not parse BVL date: '{}'", dateValue);
            return null;
        }
    }

    // --- FRAC code resolution from BVL wirkstoff_gehalt -------------------------

    /**
     * Fetches all active substances (wirknr) for a product from BVL
     * {@code /wirkstoff_gehalt/?kennr={kennr}}.
     *
     * @return distinct list of wirknr values, or empty list on error
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchWirkstoffGehalteForKennr(String kennr) {
        try {
            String uri = bvlApiBaseUrl + "/wirkstoff_gehalt/?kennr="
                    + URLEncoder.encode(kennr, StandardCharsets.UTF_8) + "&limit=50";
            Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();
            return items.stream()
                    .map(i -> (String) i.get("wirknr"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("BVL /wirkstoff_gehalt/ fetch failed for kennr '{}': {}", kennr, e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds a cache of wirknr → wirkstoffname_en by paginating through
     * BVL {@code /wirkstoff/}. Called once per sync run.
     *
     * @return map of wirknr to English substance name; empty on failure
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> buildWirkstoffNameCache() {
        Map<String, String> cache = new HashMap<>();
        int offset = 0;
        final int limit = 500;
        try {
            while (true) {
                String uri = bvlApiBaseUrl + "/wirkstoff/?limit=" + limit + "&offset=" + offset;
                Map<String, Object> response = bvlWebClient.get().uri(URI.create(uri)).retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .timeout(Duration.ofSeconds(bvlTimeoutSeconds)).block();
                if (response == null) break;
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                if (items == null || items.isEmpty()) break;
                for (Map<String, Object> item : items) {
                    String wirknr = (String) item.get("wirknr");
                    String nameEn = (String) item.get("wirkstoffname_en");
                    if (wirknr != null && nameEn != null && !nameEn.isBlank()) {
                        cache.put(wirknr, nameEn);
                    }
                }
                if (items.size() < limit) break;
                offset += limit;
            }
            log.info("BVL wirkstoff cache: loaded {} active substances", cache.size());
        } catch (Exception e) {
            log.warn("Failed to build BVL wirkstoff name cache: {}", e.getMessage());
        }
        return cache;
    }

    /**
     * Resolves the FRAC code and active substance names for a list of wirknr values.
     * Systemic (non-M) FRAC codes are preferred over multi-site contact codes (M*)
     * because they are the resistance-management concern in combination products.
     *
     * @param wirknrList    wirknr values from BVL wirkstoff_gehalt
     * @param wirknrToName  cache of wirknr → wirkstoffname_en built at sync start
     * @param fallback      UNKNOWN FracCode entity to use when no match found
     * @return resolved FRAC code + comma-separated active substance names
     */
    private FracAndSubstance resolveFracAndSubstance(List<String> wirknrList,
                                                      Map<String, String> wirknrToName,
                                                      FracCode fallback) {
        if (wirknrList.isEmpty()) {
            return new FracAndSubstance("", fallback);
        }

        List<String> substanceNames = new ArrayList<>();
        String systemicFrac = null;
        String contactFrac  = null;

        for (String wirknr : wirknrList) {
            String nameEn = wirknrToName.getOrDefault(wirknr, "");
            if (!nameEn.isBlank()) {
                substanceNames.add(nameEn);
            }
            String fracStr = lookupFracCode(nameEn);
            if (fracStr != null) {
                if (fracStr.startsWith("M")) {
                    if (contactFrac == null) contactFrac = fracStr;
                } else {
                    if (systemicFrac == null) systemicFrac = fracStr;
                }
            }
        }

        String activeSubstance = String.join(", ", substanceNames);
        // Prefer systemic FRAC code; fall back to contact (M*), then UNKNOWN
        String fracCodeStr = systemicFrac != null ? systemicFrac : contactFrac;
        if (fracCodeStr == null) {
            return new FracAndSubstance(activeSubstance, fallback);
        }
        FracCode fracCode = fracCodeRepository.findByCode(fracCodeStr).orElse(fallback);
        return new FracAndSubstance(activeSubstance, fracCode);
    }

    /**
     * Looks up the FRAC code string for an active substance name (case-insensitive,
     * exact match after trimming). Returns {@code null} if not found.
     */
    private static String lookupFracCode(String substanceName) {
        if (substanceName == null || substanceName.isBlank()) return null;
        return ACTIVE_SUBSTANCE_TO_FRAC.get(substanceName.toLowerCase(Locale.ROOT).trim());
    }

    // --- inner records ------------------------------------------------------------

    record BvlMittel(String kennr, String mittelname, LocalDate zulEnde) {}

    record BvlAwg(String awgId, String kennr, Double mittelaufwand, Integer maxApplications, String wirkungsbereich) {}

    record BvlAufwand(String awgId, Double mAufwand, String mAufwandEinheit) {}

    record BvlKultur(String awgId, String kultur, String ausgenommen) {}

    record BvlSchadorg(String awgId, String schadorg, String ausgenommen) {}

    record BvlWartezeit(String awgId, Integer phi) {}

    record FracAndSubstance(String activeSubstance, FracCode fracCode) {}
}
