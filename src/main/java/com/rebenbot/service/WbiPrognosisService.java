package com.rebenbot.service;

import com.rebenbot.model.WbiPrognosis;
import com.rebenbot.repository.WbiPrognosisRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Service to fetch and parse WBI Freiburg disease prognosis data.
 * Source: https://www.vitimeteo-bw.de/
 * 
 * Authentication: Automatic session cookie handling via RestTemplate
 * - Initial request receives Set-Cookie header in redirect
 * - RestTemplate automatically follows redirect and sends cookie
 * - No manual credential configuration required
 */
@Service
@Slf4j
public class WbiPrognosisService {

    private final WbiPrognosisRepository prognosisRepository;
    private final RestTemplate restTemplate;

    private static final String PERONOSPORA_PDF_URL = 
            "https://www.vitimeteo-bw.de/vitimeteo/default/private_call/run/resource_rewrite?prog_dir=pero&res_name=peronospora_month.pdf&stat_id=99";
    private static final String OIDIUM_PDF_URL = 
            "https://www.vitimeteo-bw.de/vitimeteo/default/private_call/run/resource_rewrite?prog_dir=oidium&res_name=oidium.pdf&stat_id=99";
    
    // Freshness threshold: re-fetch data if older than 24 hours (1440 minutes)
    private static final int WBI_DATA_FRESHNESS_HOURS = 24;
    
    // Browser headers to mimic legitimate client
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";
    private static final String ACCEPT_HEADER = "application/pdf, */*;q=0.8";
    private static final String REFERER = "https://www.vitimeteo-bw.de/vitimeteo/";

    public WbiPrognosisService(WbiPrognosisRepository prognosisRepository, RestTemplate restTemplate) {
        this.prognosisRepository = prognosisRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Download and parse peronospora prognosis.
     * Scheduled daily at 6 AM.
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void refreshPeronosporePrognosis() {
        log.info("Starting scheduled peronospora prognosis download from WBI Freiburg");
        try {
            byte[] pdfData = downloadPdf(PERONOSPORA_PDF_URL);
            parseAndStorePdf(pdfData, "peronospora", PERONOSPORA_PDF_URL);
            log.info("Peronospora prognosis refresh completed");
        } catch (Exception e) {
            log.error("Error refreshing peronospora prognosis: {}", e.getMessage(), e);
        }
    }

    /**
     * Download and parse oidium prognosis.
     * Scheduled daily at 6:15 AM.
     */
    @Scheduled(cron = "0 15 6 * * ?")
    public void refreshOidiumPrognosis() {
        log.info("Starting scheduled oidium prognosis download from WBI Freiburg");
        try {
            byte[] pdfData = downloadPdf(OIDIUM_PDF_URL);
            parseAndStorePdf(pdfData, "oidium", OIDIUM_PDF_URL);
            log.info("Oidium prognosis refresh completed");
        } catch (Exception e) {
            log.error("Error refreshing oidium prognosis: {}", e.getMessage(), e);
        }
    }

    /**
     * Download PDF from URL using automatic session cookie handling.
     * RestTemplate automatically handles:
     * 1. Initial request -> receives Set-Cookie in redirect
     * 2. Following redirect with session cookie
     * 3. Retrieving authenticated PDF
     */
    private byte[] downloadPdf(String url) throws Exception {
        log.debug("Downloading PDF from: {}", url);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", ACCEPT_HEADER);
        headers.set("Referer", REFERER);
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Connection", "keep-alive");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        byte[] pdfData = response.getBody();
        
        if (pdfData == null || pdfData.length == 0) {
            throw new Exception("Failed to download PDF from " + url);
        }
        log.debug("PDF downloaded successfully, size: {} bytes", pdfData.length);
        return pdfData;
    }
    

    /**
     * Parse PDF and extract prognosis data.
     * Extracts today's infection risk, risk %, and incubation forecast end date with accuracy.
     */
    private void parseAndStorePdf(byte[] pdfData, String disease, String sourceUrl) throws IOException {
        // Write to temp file and load (PDFBox 3.0 prefers file-based loading)
        File tempFile = File.createTempFile("wbi_", ".pdf");
        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(pdfData);
            }
            
            try (PDDocument document = Loader.loadPDF(tempFile)) {
                PDFTextStripper textStripper = new PDFTextStripper();
                String text = textStripper.getText(document);
                
                log.debug("Extracted text from PDF");
                
                // Check if prognosis already exists for today
                Optional<WbiPrognosis> existing = prognosisRepository.findByDiseaseAndForecastDate(disease, LocalDate.now());
                
                WbiPrognosis prognosis;
                if (existing.isPresent()) {
                    prognosis = existing.get();
                    log.debug("Updating existing prognosis for {} on {}", disease, LocalDate.now());
                } else {
                    prognosis = new WbiPrognosis();
                    prognosis.setDisease(disease);
                    prognosis.setForecastDate(LocalDate.now());
                    log.debug("Creating new prognosis for {} on {}", disease, LocalDate.now());
                }
                
                prognosis.setSourceUrl(sourceUrl);
                prognosis.setRawText(text);
                
                // Parse for infection risk information
                parseRiskData(text, prognosis);
                
                try {
                    prognosisRepository.save(prognosis);
                } catch (Exception e) {
                    // Handle race condition where another thread inserted the same record
                    if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                        log.info("Record for {} on {} already exists, skipping insert", disease, LocalDate.now());
                        // Try to fetch it again
                        existing = prognosisRepository.findByDiseaseAndForecastDate(disease, LocalDate.now());
                        if (existing.isPresent()) {
                            prognosis = existing.get();
                        }
                    } else {
                        throw e;
                    }
                }
                
                // Log with disease-specific details
                if ("peronospora".equalsIgnoreCase(disease)) {
                    String incubationInfo = prognosis.getIncubationAccuracy() != null 
                        ? String.format(", Incubation accuracy: %d%%", prognosis.getIncubationAccuracy())
                        : "";
                    log.info("Stored WBI {} prognosis for {} - Risk: {}, Score: {}%{}", 
                            disease, LocalDate.now(), prognosis.getRiskLevel(), prognosis.getRiskScore(), incubationInfo);
                } else {
                    log.info("Stored WBI {} prognosis for {} - Risk: {}, Score: {}%", 
                            disease, LocalDate.now(), prognosis.getRiskLevel(), prognosis.getRiskScore());
                }
                
            } catch (Exception e) {
                log.error("Error parsing PDF for {}: {}", disease, e.getMessage(), e);
                throw e;
            }
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary PDF file: {}", tempFile.getAbsolutePath());
            }
        }
    }
    
    /**
     * Parse risk data from PDF text.
     * Handles peronospora and oidium formats separately due to different PDF structures:
     * - Peronospora: Date, !, and percentages on separate lines when extracted
     * - Oidium: All data on same line with leaf area values
     */
    private void parseRiskData(String pdfText, WbiPrognosis prognosis) {
        try {
            String[] lines = pdfText.split("\n");
            
            // Format today's date as "dd.MM." and "dd.MM" (e.g., "22.04." and "22.04")
            LocalDate today = LocalDate.now();
            String todayPatternWithDot = String.format("%02d.%02d.", today.getDayOfMonth(), today.getMonthValue());
            String todayPattern = String.format("%02d.%02d", today.getDayOfMonth(), today.getMonthValue());
            
            // Different parsing for each disease
            if ("peronospora".equalsIgnoreCase(prognosis.getDisease())) {
                parsePeronosporaFormat(lines, todayPatternWithDot, prognosis);
            } else if ("oidium".equalsIgnoreCase(prognosis.getDisease())) {
                parseOidiumFormat(lines, todayPattern, prognosis);
            }
            
            if (prognosis.getRiskScore() == null) {
                // No infection detected
                prognosis.setRiskLevel("NO_INFECTION");
                prognosis.setRiskScore(0);
                log.info("No infection risk detected in {} prognosis", prognosis.getDisease());
            }
            
        } catch (Exception e) {
            log.warn("Error parsing risk data: {}", e.getMessage());
            prognosis.setRiskLevel("PARSE_ERROR");
        }
    }
    
    /**
     * Parse peronospora format: single line with date, !, risk%, and incubation accuracy%.
     * Also extracts incubation end date from column headers.
     * Example line: "19.04 !  16%  67% 9,4 12,5 15,7 6,9 6,2 69 2    20"
     * Example header: "22.04. 28.04. Min       Ø       Max" (current date and incubation end date)
     */
    private void parsePeronosporaFormat(String[] lines, String todayPatternWithDot, WbiPrognosis prognosis) {
        String todayPatternNoDot = todayPatternWithDot.replaceAll("\\.$", "");
        LocalDate incubationEndDate = null;
        
        // First, extract incubation end date from header columns (format: "22.04. 28.04.")
        for (String line : lines) {
            Pattern headerPattern = Pattern.compile(todayPatternWithDot + "\\s+(\\d{2}\\.\\d{2}\\.)");
            Matcher headerMatcher = headerPattern.matcher(line);
            if (headerMatcher.find()) {
                String endDateStr = headerMatcher.group(1).replaceAll("\\.$", "");
                incubationEndDate = parsePartialDate(endDateStr);
                log.debug("Extracted peronospora incubation end date from headers: {}", incubationEndDate);
                break;
            }
        }
        
        // Then, find and extract infection risk and accuracy
        for (String line : lines) {
            if (line.startsWith(todayPatternNoDot) && line.contains("!")) {
                if (extractPeronosporaRisk(line, prognosis)) {
                    if (incubationEndDate != null) {
                        prognosis.setIncubationEndDate(incubationEndDate);
                    }
                    return;
                }
            }
        }
        
        // If not found today, search for most recent infection marker (backwards through lines)
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            if (line.matches("^\\d{2}\\.\\d{2}\\s+!.*")) {
                if (extractPeronosporaRisk(line, prognosis)) {
                    if (incubationEndDate != null) {
                        prognosis.setIncubationEndDate(incubationEndDate);
                    }
                    return;
                }
            }
        }
    }
    
    /**
     * Extract risk and accuracy from peronospora line with infection marker.
     * Returns true if successfully extracted, false otherwise.
     */
    private boolean extractPeronosporaRisk(String line, WbiPrognosis prognosis) {
        // Pattern: capture first % (risk) and second % (incubation accuracy)
        Pattern percentPattern = Pattern.compile("!\\s+([0-9]+)%\\s+([0-9]+)%");
        Matcher percentMatcher = percentPattern.matcher(line);
        
        if (percentMatcher.find()) {
            String riskPercent = percentMatcher.group(1);
            String accuracyPercent = percentMatcher.group(2);
            
            prognosis.setRiskLevel("INFECTION_RISK");
            prognosis.setRiskScore(Integer.parseInt(riskPercent));
            prognosis.setIncubationAccuracy(Integer.parseInt(accuracyPercent));
            
            log.info("Parsed WBI peronospora: Risk={}%, Incubation accuracy={}%", 
                    riskPercent, accuracyPercent);
            return true;
        }
        return false;
    }
    
    /**
     * Parse partial date (dd.MM) to LocalDate using current year.
     */
    private LocalDate parsePartialDate(String dateStr) {
        try {
            // Parse "dd.MM" format to dd.MM.yyyy using current year
            LocalDate today = LocalDate.now();
            int day = Integer.parseInt(dateStr.substring(0, 2));
            int month = Integer.parseInt(dateStr.substring(3, 5));
            int year = today.getYear();
            
            LocalDate date = LocalDate.of(year, month, day);
            
            // If date is before today, assume it's next year
            if (date.isBefore(today)) {
                date = date.withYear(year + 1);
            }
            
            return date;
        } catch (Exception e) {
            log.warn("Failed to parse partial date: {}", dateStr);
            return null;
        }
    }
    
    /**
     * Parse oidium format: all data on same line.
     * Examples:
     *   20.04 ! 5,6 10,4 14,5 0,0 66,5  2 20,3 9 %   (well-formed)
     *   22.04 ! 6,2 10,0 15,9 0,0 51,8  2 20,312 %   (malformed - merged decimal)
     */
    private void parseOidiumFormat(String[] lines, String todayPattern, WbiPrognosis prognosis) {
        for (String line : lines) {
            // Look for today's date AND infection marker '!' on same line
            if (line.startsWith(todayPattern) && line.contains("!")) {
                // Try malformed format first: extracted trailing digits from merged decimal like "20,312"
                Pattern oidiumMalformedPattern = Pattern.compile("\\d+,\\d+([0-9]{2,})\\s*%\\s*$");
                Matcher malformedMatcher = oidiumMalformedPattern.matcher(line);
                
                if (malformedMatcher.find()) {
                    String merged = malformedMatcher.group(1);  // e.g., "312"
                    // Take last 2 digits as risk percent
                    String riskPercent = merged.substring(merged.length() - 2);
                    prognosis.setRiskLevel("INFECTION_RISK");
                    prognosis.setRiskScore(Integer.parseInt(riskPercent));
                    log.info("Parsed WBI oidium: Risk={}%", riskPercent);
                    return;
                }
                
                // Well-formed format: leaf area ends with decimal (d,d), then risk % follows
                Pattern oidiumPattern = Pattern.compile("\\d+,\\d+\\s+([0-9]{1,2})\\s*%\\s*$");
                Matcher oidiumMatcher = oidiumPattern.matcher(line);
                
                if (oidiumMatcher.find()) {
                    String riskPercent = oidiumMatcher.group(1);
                    prognosis.setRiskLevel("INFECTION_RISK");
                    prognosis.setRiskScore(Integer.parseInt(riskPercent));
                    log.info("Parsed WBI oidium: Risk={}%", riskPercent);
                    return;
                }
            }
        }
    }

    /**
     * Get latest prognosis for a disease.
     */
    /**
     * Check if latest prognosis data is stale and auto-fetch if needed.
     * Ensures data is not older than WBI_DATA_FRESHNESS_HOURS.
     */
    private void ensureFreshPrognosisData(String disease) {
        try {
            Optional<WbiPrognosis> latest = prognosisRepository.findTopByDiseaseOrderByForecastDateDesc(disease);
            if (latest.isEmpty()) {
                log.info("No {} prognosis data found, fetching from WBI Freiburg", disease);
                refreshPrognosisData(disease);
                return;
            }

            LocalDateTime recordedTime = latest.get().getCreatedAt().atStartOfDay();
            LocalDateTime staleThreshold = LocalDateTime.now().minusHours(WBI_DATA_FRESHNESS_HOURS);
            long hoursOld = java.time.temporal.ChronoUnit.HOURS.between(recordedTime, LocalDateTime.now());

            if (recordedTime.isBefore(staleThreshold)) {
                log.info("WBI {} prognosis data is {} hours old (threshold: {} hours), fetching fresh data",
                        disease, hoursOld, WBI_DATA_FRESHNESS_HOURS);
                refreshPrognosisData(disease);
            } else {
                log.debug("WBI {} prognosis data is fresh ({} hours old, threshold: {} hours)",
                        disease, hoursOld, WBI_DATA_FRESHNESS_HOURS);
            }
        } catch (Exception e) {
            log.warn("Error checking prognosis freshness: {}", e.getMessage());
        }
    }

    /**
     * Refresh prognosis data by disease type.
     */
    private void refreshPrognosisData(String disease) {
        try {
            if ("peronospora".equalsIgnoreCase(disease)) {
                byte[] pdfData = downloadPdf(PERONOSPORA_PDF_URL);
                parseAndStorePdf(pdfData, "peronospora", PERONOSPORA_PDF_URL);
            } else if ("oidium".equalsIgnoreCase(disease)) {
                byte[] pdfData = downloadPdf(OIDIUM_PDF_URL);
                parseAndStorePdf(pdfData, "oidium", OIDIUM_PDF_URL);
            }
        } catch (Exception e) {
            log.error("Error refreshing {} prognosis: {}", disease, e.getMessage(), e);
        }
    }

    public Optional<WbiPrognosis> getLatestPrognosis(String disease) {
        ensureFreshPrognosisData(disease);
        return prognosisRepository.findTopByDiseaseOrderByForecastDateDesc(disease);
    }

    /**
     * Get prognosis history for a disease.
     */
    public List<WbiPrognosis> getPrognosisHistory(String disease, LocalDate startDate, LocalDate endDate) {
        return prognosisRepository.findByDiseaseAndForecastDateBetweenOrderByForecastDateDesc(
                disease, startDate, endDate);
    }
}
