package org.openmrs.module.ugandaemrreports.api.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.mambacore.api.FlattenDatabaseService;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.MessageUtil;
import org.openmrs.module.reporting.common.ObjectUtil;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.evaluation.EvaluationUtil;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.TextTemplateRenderer;
import org.openmrs.module.reporting.report.renderer.template.TemplateEngine;
import org.openmrs.module.reporting.report.renderer.template.TemplateEngineManager;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.ugandaemrreports.activator.AppConfigInitializer;
import org.openmrs.module.ugandaemrreports.activator.Initializer;
import org.openmrs.module.ugandaemrreports.activator.ReportInitializer;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.api.db.hibernate.HibernateUgandaEMRReportsDAO;
import org.openmrs.module.ugandaemrreports.definition.data.evaluator.SqlPreviewResult;
import org.openmrs.module.ugandaemrreports.definition.dataset.definition.AggregateReportDataSetDefinition;
import org.openmrs.module.ugandaemrreports.model.*;
import org.openmrs.module.ugandaemrreports.util.JsonTemplateConverter;
import org.openmrs.module.ugandaemrreports.util.MambaIndicatorValidator;
import org.openmrs.module.ugandaemrreports.util.MambaIndicatorSqlSync;
import org.openmrs.module.ugandaemrreports.util.ReportDesignFileUtil;
import org.openmrs.reporting.PatientSearch;
import org.openmrs.reporting.ReportObjectWrapper;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link UgandaEMRReportsService}.
 */
public class UgandaEMRReportsServiceImpl extends BaseOpenmrsService implements UgandaEMRReportsService {

    protected final Log log = LogFactory.getLog(this.getClass());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HibernateUgandaEMRReportsDAO dao;



    private final JsonTemplateConverter converter = new JsonTemplateConverter();

    public HibernateUgandaEMRReportsDAO getDao() {
        return dao;
    }

    public void setDao(HibernateUgandaEMRReportsDAO dao) {
        this.dao = dao;
    }

    /* -------------------- Existing DAO methods (unchanged) -------------------- */

    @Override
    public List<DashboardReportObject> getAllDashboardReportObjects() throws APIException {
        return dao.getAllDashboardReportObjects();
    }

    @Override
    public DashboardReportObject getDashboardReportObjectByUUID(String uuid) throws APIException {
        return dao.getDashboardReportObjectByUUID(uuid);
    }

    @Override
    public DashboardReportObject saveDashboardReportObject(DashboardReportObject dashboardReportObject) throws APIException {
        return dao.saveDashboardReportObject(dashboardReportObject);
    }

    @Override
    public DashboardReportObject getDashboardReportObjectById(Integer id) throws APIException {
        return dao.getDashboardReportObjectById(id);
    }

    @Override
    public Dashboard saveDashboard(Dashboard dashboard) throws APIException {
        return dao.saveDashboard(dashboard);
    }

    @Override
    public Dashboard getDashboardByUUID(String uniqueId) throws APIException {
        return dao.getDashboardByUUID(uniqueId);
    }

    @Override
    public Dashboard getDashboardById(Integer id) throws APIException {
        return dao.getDashboardById(id);
    }

    @Override
    public List<Dashboard> getAllDashboards() throws APIException {
        return dao.getAllDashboards();
    }

    @Override
    public void executeFlatteningScript() {
        dao.executeFlatteningScript();
    }

    @Override
    public List<ReportObjectWrapper> getPatientSearches(String type) {
        return dao.getReportObjects(type);
    }

    @Override
    public PatientSearch getPatientSearchByUuid(String uuid) {
        return dao.getPatientSearchByUuid(uuid);
    }

    @Override
    public Cohort getPatientCurrentlyInProgram(String programUuid) {
        return dao.getPatientCurrentlyInPrograms(programUuid);
    }

    @Override
    public Map<Integer, String> getPatientsConditionsStatus(org.openmrs.cohort.Cohort patients, Concept codedCondition) {
        return dao.getPatientsConditionsStatus(patients, codedCondition);
    }

    @Override
    public Set<Concept> getConditionsConcepts() {
        return dao.getAllConditions();
    }

    @Override
    public Map<Integer, Object> getLatestPatientAppointmentsScheduled(org.openmrs.cohort.Cohort patients, int limit) {
        return dao.getLatestPatientAppointmentsScheduled(patients, limit);
    }

    @Override
    public List<Integer> getObsConceptsFromEncounters(EncounterType encounterType) {
        return dao.getObsConceptsFromEncounters(encounterType);
    }

    @Override
    public List<Object> getNonCodedOrderReasons(OrderType orderType) {
        return dao.getNonCodedOrderReasons(orderType);
    }

    @Override
    public List<Concept> getCodedOrderReasons(OrderType orderType) {
        return dao.getCodedOrderReasons(orderType);
    }

    @Override
    public Map<Integer, Map<String, Object>> getDrugOrderByIndicator(org.openmrs.cohort.Cohort patients, String drugIndication, OrderType orderType) {
        return dao.getDrugOrderByIndication(patients, drugIndication, orderType);
    }

    /* -------------------- Setup / init -------------------- */

    public void addMambaetlProperties() {
        File appDataDir = FileUtils.getFile(OpenmrsUtil.getApplicationDataDirectory());
        File propertiesFile = new File(appDataDir, "openmrs-runtime.properties");
        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream(propertiesFile)) {
            properties.load(in);
        } catch (IOException e) {
            System.err.println("Failed to read properties file: " + e.getMessage());
            return;
        }

        String connectionUrl = properties.getProperty("connection.url");
        String dbName = null;

        if (connectionUrl != null && connectionUrl.contains("/")) {
            try {
                int lastSlash = connectionUrl.lastIndexOf('/');
                int questionMark = connectionUrl.indexOf('?', lastSlash);
                if (lastSlash != -1 && questionMark != -1) {
                    dbName = connectionUrl.substring(lastSlash + 1, questionMark);
                } else if (lastSlash != -1) {
                    dbName = connectionUrl.substring(lastSlash + 1);
                }
            } catch (Exception e) {
                System.err.println("Error parsing connection.url: " + e.getMessage());
            }
        }

        if (dbName == null || dbName.isEmpty()) {
            dbName = "openmrs";
            System.out.println("WARNING: Using fallback database name: " + dbName);
        }

        String username = properties.getProperty("connection.username", "openmrs");
        if (username.isEmpty()) {
            username = "openmrs";
            System.out.println("WARNING: Using fallback username: " + username);
        }

        String password = properties.getProperty("connection.password", "openmrs");
        if (password.isEmpty()) {
            password = "openmrs";
            System.out.println("WARNING: Using fallback password: " + password);
        }

        setIfAbsent(properties, "mambaetl.analysis.db.openmrs_database", dbName);
        setIfAbsent(properties, "mambaetl.analysis.db.etl_database", dbName);
        setIfAbsent(properties, "mambaetl.analysis.db.username", username);
        setIfAbsent(properties, "mambaetl.analysis.db.password", password);
        setIfAbsent(properties, "mambaetl.analysis.columns", "49");
        setIfAbsent(properties, "mambaetl.analysis.incremental_mode", "1");
        setIfAbsent(properties, "mambaetl.analysis.etl_interval", "3600");
        setIfAbsent(properties, "mambaetl.analysis.locale", "en");
        setIfAbsent(properties, "mambaetl.analysis.automated_flattening", "0");

        try (FileOutputStream out = new FileOutputStream(propertiesFile)) {
            properties.store(out, "Updated with MambaETL related properties (added only if missing)");
            System.out.println("MambaETL properties checked and updated successfully.");
        } catch (IOException e) {
            System.err.println("Failed to write properties file: " + e.getMessage());
        }
    }

    @Override
    public void setupMambaETL() {
        Context.getService(FlattenDatabaseService.class).setupEtl();
    }

    @Override
    public void setUpReports() {
        try {
            for (Initializer initializer : getInitializers()) {
                initializer.started();
            }
        } catch (Exception e) {
            log.error("Error setting up reports: " + e.getMessage());
        }
    }

    private static void setIfAbsent(Properties properties, String key, String value) {
        if (!properties.containsKey(key)) {
            properties.setProperty(key, value);
        }
    }

    public List<Initializer> getInitializers() {
        List<Initializer> l = new ArrayList<Initializer>();
        l.add(new AppConfigInitializer());
        l.add(new ReportInitializer());
        return l;
    }

    /* -------------------- JSON Template API (NEW) -------------------- */

    /**
     * Returns a final HTML document (iframe-ready).
     * NOTE: This method does not use @Override unless you add it to the interface.
     */
    public String renderHtmlFinalFromTemplate(ReportData reportData, ReportDesign reportDesign) {
        String templateJson = readDesignResource(reportDesign);
        Map<String, Object> values = extractFlatValues(reportData);
        return converter.renderHtmlFinal(templateJson, values);
    }

    /**
     * Preview-only HTML (no values) still useful for design preview.
     * Existing signature you already had.
     */
    @Override
    public String renderHtmlFromJsonTemplate(ReportDesign reportDesign) {
        String templateJson = readDesignResource(reportDesign);
        return converter.renderHtmlFinal(templateJson, Collections.<String, Object>emptyMap());
    }

    @Override
    public String createPayloadJsonFromTemplate(ReportData reportData,
                                                ReportDesign reportDesign,
                                                String renderType,
                                                Map<String, Object> flatValues,
                                                String remapJsonOptional) {
        String templateJson = readDesignResource(reportDesign);
        return converter.buildPayloadOnly(templateJson,
                flatValues == null ? Collections.<String, Object>emptyMap() : flatValues,
                remapJsonOptional);
    }

    /**
     * Extract values into Map<String,Object> in a GENERIC way:
     * - Supports rows that already provide flat keys like OR02_29d_4y_F -> 0
     * - Also supports rows with code/age/sex/value columns.
     * <p>
     * NOTE: Add to interface if controller calls through interface.
     */
    public Map<String, Object> extractFlatValues(ReportData reportData) {
        Map<String, Object> out = new HashMap<String, Object>();
        if (reportData == null || reportData.getDataSets() == null) {
            return out;
        }

        Map<String, DataSet> dataSets = reportData.getDataSets();
        for (String dsName : dataSets.keySet()) {
            DataSet ds = dataSets.get(dsName);
            if (ds == null) continue;

            Iterator it = ds.iterator();
            while (it.hasNext()) {
                DataSetRow row = (DataSetRow) it.next();
                Map<String, Object> cols = row.getColumnValuesByKey();
                if (cols == null || cols.isEmpty()) continue;

                // Style A: code/age/sex/value columns
                String code = firstString(cols, "code", "dataelement", "dataElement", "data_element");
                String age = firstString(cols, "age", "agegroup", "age_group");
                String sex = firstString(cols, "sex", "gender");
                Object valObj = firstObject(cols, "value", "count", "total");

                if (!isBlank(code) && !isBlank(age) && !isBlank(sex) && valObj != null) {
                    out.put(code + "_" + age + "_" + sex, valObj);
                    continue;
                }

                // Style B: already-flat keys in cols map
                for (Map.Entry<String, Object> e : cols.entrySet()) {
                    String k = e.getKey();
                    Object v = e.getValue();
                    if (k == null) continue;

                    // Accept keys like EP11_0_28d_F
                    if (looksLikeKey(k) && v != null && isNumeric(v)) {
                        out.put(k.trim(), v);
                    }
                }
            }
        }

        return out;
    }

    private boolean looksLikeKey(String k) {
        // minimal safe check: CODE_AGE_SEX (3 parts)
        // e.g. OR02_29d_4y_F
        String s = k.trim();
        int a = s.indexOf('_');
        if (a <= 0) return false;
        int b = s.indexOf('_', a + 1);
        if (b <= a + 1) return false;
        int c = s.lastIndexOf('_');
        return c > b && c < s.length() - 1;
    }

    private boolean isNumeric(Object v) {
        if (v instanceof Number) return true;
        String s = String.valueOf(v).trim();
        if (s.length() == 0) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private String firstString(Map<String, Object> cols, String... keys) {
        Object o = firstObject(cols, keys);
        return o == null ? null : String.valueOf(o).trim();
    }

    private Object firstObject(Map<String, Object> cols, String... keys) {
        for (String k : keys) {
            if (cols.containsKey(k)) {
                Object v = cols.get(k);
                if (v != null && String.valueOf(v).trim().length() > 0) {
                    return v;
                }
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /* -------------------- Legacy support (kept) -------------------- */

    /**
     * Legacy payload builder (old HTML-template approach), returns STRING.
     * Use this when renderType=legacy.
     * <p>
     * NOTE: Add to interface if you want controller to call via interface.
     */
    public String createLegacyPayloadJson(ReportData reportData, ReportDesign reportDesign) {
        try {
            TextTemplateRenderer textTemplateRenderer = new TextTemplateRenderer();
            ReportDesignResource res = textTemplateRenderer.getTemplate(reportDesign);
            String templateContents = new String(res.getContents(), StandardCharsets.UTF_8);

            // Render template using OpenMRS reporting engines
            String rendered = fillTemplateWithReportData(templateContents, reportData, reportDesign);

            // Fix value quotes and return
            return removeQuotesFromValues(rendered);

        } catch (Exception e) {
            throw new RuntimeException("Failed legacy payload build", e);
        }
    }

    private String fillTemplateWithReportData(String templateContents,
                                              ReportData reportData,
                                              ReportDesign reportDesign) throws IOException, RenderingException {

        try {
            TextTemplateRenderer renderer = new TextTemplateRenderer();
            Map<String, Object> replacements = renderer.getBaseReplacementData(reportData, reportDesign);

            String templateEngineName = reportDesign.getPropertyValue("templateType", (String) null);
            TemplateEngine engine = TemplateEngineManager.getTemplateEngineByName(templateEngineName);

            if (engine != null) {
                Map<String, Object> bindings = new HashMap<String, Object>();
                bindings.put("reportData", reportData);
                bindings.put("reportDesign", reportDesign);
                bindings.put("data", replacements);
                bindings.put("util", new ObjectUtil());
                bindings.put("dateUtil", new DateUtil());
                bindings.put("msg", new MessageUtil());
                templateContents = engine.evaluate(templateContents, bindings);
            }

            String prefix = renderer.getExpressionPrefix(reportDesign);
            String suffix = renderer.getExpressionSuffix(reportDesign);

            Object evaluated = EvaluationUtil.evaluateExpression(templateContents, replacements, prefix, suffix);
            return evaluated == null ? "" : evaluated.toString();

        } catch (RenderingException re) {
            throw re;
        } catch (Throwable t) {
            throw new RenderingException("Unable to render results due to: " + t, t);
        }
    }

    public static String removeQuotesFromValues(String input) {
        Pattern pattern = Pattern.compile("\"value\":\"(\\d+)\"");
        Matcher matcher = pattern.matcher(input);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "\"value\":" + matcher.group(1));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String readDesignResource(ReportDesign reportDesign) {
        try {
            TextTemplateRenderer renderer = new TextTemplateRenderer();
            ReportDesignResource res = renderer.getTemplate(reportDesign);
            return new String(res.getContents(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read report design resource", e);
        }
    }

    @Override
    public String buildPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType) {
        // Keeps backward compatibility:
        // - if renderType says "legacy" -> use old renderer approach
        // - otherwise -> new template payload (still returns JSON string)
        if (renderType != null && "legacy".equalsIgnoreCase(renderType)) {
            return createLegacyPayloadJson(reportData, reportDesign);
        }

        Map<String, Object> values = extractFlatValues(reportData);
        return createPayloadJsonFromTemplate(reportData, reportDesign, "json", values, null);
    }

    @Override
    public String buildFinalPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType, Date endDate) {

        // 1) Build payload (legacy or new)
        String payloadJson = buildPayloadJson(reportData, reportDesign, renderType);

        // 2) Append period (same logic you had in controller)
        String period = getYearAndQuarter(endDate);
        return appendPeriod(payloadJson, period);
    }

    @Override
    public String buildPreviewHtml(ReportData reportData, ReportDesign reportDesign) {
        // Preview HTML for iframe:
        // - Prefer actual values if present
        // - Otherwise it still renders with default 0s.
        String templateJson = readDesignResource(reportDesign);

        Map<String, Object> values = extractFlatValues(reportData);
        return converter.renderHtmlFinal(templateJson, values);
    }

    /* ------------------ helpers used by buildFinalPayloadJson ------------------ */

    private String getYearAndQuarter(Date date) {
        if (date == null) return null;
        java.time.LocalDate localDate = date.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        int quarter = (month - 1) / 3 + 1;
        return year + "Q" + quarter;
    }

    private String appendPeriod(String payloadJson, String period) {
        if (payloadJson == null) return null;
        try {
            ObjectMapper om = new ObjectMapper();
            ObjectNode root = (ObjectNode) om.readTree(payloadJson);

            ObjectNode json = (ObjectNode) root.get("json");
            if (json == null) {
                json = om.createObjectNode();
                root.set("json", json);
            }
            if (period != null) {
                json.put("period", period);
            }

            return om.writeValueAsString(root);
        } catch (Exception e) {
            // fail-safe: return original if period append fails
            return payloadJson;
        }
    }


    // =========================
    // MambaIndicator
    // =========================

    @Override
    public ReportBuilderIndicator saveReportBuilderIndicator(ReportBuilderIndicator indicator) {
        try {
            MambaIndicatorValidator.validate(indicator);

            if (indicator.getKind() == ReportBuilderIndicator.Kind.BASE) {
                MambaIndicatorSqlSync.normalizeBaseSql(indicator);
            }

            return dao.saveReportBuilderIndicator(indicator);
        } catch (IllegalArgumentException e) {
            throw new APIException(e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderIndicator getReportBuilderIndicatorById(Integer id) {
        return dao.getReportBuilderIndicatorById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderIndicator getReportBuilderIndicatorByUuid(String uuid) {
        return dao.getReportBuilderIndicatorByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderIndicator getReportBuilderIndicatorByCode(String code) {
        return dao.getReportBuilderIndicatorByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderIndicator> searchReportBuilderIndicators(String q, ReportBuilderIndicator.Kind kind, boolean includeRetired,
                                                                      Integer startIndex, Integer limit) {
        return dao.getReportBuilderIndicators(q, kind, includeRetired, startIndex, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderIndicator> getAllReportBuilderIndicator(Integer startIndex, Integer limit) {
        return dao.getAllReportBuilderaIndicator(startIndex, limit);
    }


    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderIndicator> getReportBuilderIndicators(ReportBuilderIndicator.Kind kind, boolean includeRetired, Integer startIndex, Integer limit) {
        return dao.getReportBuilderIndicators(kind, includeRetired, startIndex, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long getReportBuilderIndicatorsCount(String q, ReportBuilderIndicator.Kind kind, boolean includeRetired) {
        return dao.getReportBuilderIndicatorsCount(q, kind, includeRetired);
    }

    @Override
    public void retireReportBuilderIndicator(ReportBuilderIndicator indicator, String reason) {
        indicator.setRetired(true);
        indicator.setRetireReason(reason);
        dao.saveReportBuilderIndicator(indicator);
    }

    @Override
    public void unretireReportBuilderIndicator(ReportBuilderIndicator indicator) {
        indicator.setRetired(false);
        indicator.setRetireReason(null);
        dao.saveReportBuilderIndicator(indicator);
    }

    @Override
    public void purgeReportBuilderIndicator(ReportBuilderIndicator indicator) {
        dao.purgeReportBuilderIndicator(indicator);
    }

    // =========================
    // MambaSection
    // =========================

    @Override
    public ReportBuilderSection saveReportBuilderSection(ReportBuilderSection section) {
        return dao.saveReportBuilderSection(section);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderSection getReportBuilderSectionById(Integer id) {
        return dao.getReportBuilderSectionById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderSection getReportBuilderSectionByUuid(String uuid) {
        return dao.getReportBuilderSectionByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderSection getReportBuilderSectionByCode(String code) {
        return dao.getReportBuilderSectionByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderSection> getReportBuilderSections(String q, boolean includeRetired, Integer startIndex, Integer limit) {
        return dao.getReportBuilderSections(q, includeRetired, startIndex, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long getReportBuilderSectionsCount(String q, boolean includeRetired) {
        return dao.getReportBuilderSectionsCount(q, includeRetired);
    }

    @Override
    public void retireReportBuilderSection(ReportBuilderSection section, String reason) {
        section.setRetired(true);
        section.setRetireReason(reason);
        dao.saveReportBuilderSection(section);
    }

    @Override
    public void unretireReportBuilderSection(ReportBuilderSection section) {
        section.setRetired(false);
        section.setRetireReason(null);
        dao.saveReportBuilderSection(section);
    }

    @Override
    public void purgeReportBuilderSection(ReportBuilderSection section) {
        dao.purgeReportBuilderSection(section);
    }

    // =========================
    // MambaDataTheme
    // =========================

    @Override
    public ReportBuilderDataTheme saveReportBuilderDataTheme(ReportBuilderDataTheme theme) {
        return dao.saveReportBuilderDataTheme(theme);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderDataTheme getReportBuilderDataThemeById(Integer id) {
        return dao.getReportBuilderDataThemeById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderDataTheme getReportBuilderDataThemeByUuid(String uuid) {
        return dao.getReportBuilderDataThemeByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderDataTheme getReportBuilderDataThemeByCode(String code) {
        return dao.getReportBuilderDataThemeByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderDataTheme> getReportBuilderDataThemes(String q, boolean includeRetired, Integer startIndex, Integer limit) {
        return dao.getReportBuilderDataThemes(q, includeRetired, startIndex, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long getReportBuilderDataThemesCount(String q, boolean includeRetired) {
        return dao.getReportBuilderThemesCount(q, includeRetired);
    }

    @Override
    public void retireReportBuilderDataTheme(ReportBuilderDataTheme theme, String reason) {
        theme.setRetired(true);
        theme.setRetireReason(reason);
        dao.saveReportBuilderDataTheme(theme);
    }

    @Override
    public void unretireReportBuilderDataTheme(ReportBuilderDataTheme theme) {
        theme.setRetired(false);
        theme.setRetireReason(null);
        dao.saveReportBuilderDataTheme(theme);
    }

    @Override
    public void purgeReportBuilderDataTheme(ReportBuilderDataTheme theme) {
        dao.purgeReportBuilderDataTheme(theme);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getMambaTables() {
        return dao.getMambaTables();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map> getMambaTableColumns(String tableName) {
        return dao.getMambaTableColumns(tableName);
    }

    // Categories

    @Override
    public ReportBuilderAgeCategory saveAgeCategory(ReportBuilderAgeCategory category) {
        return dao.saveAgeCategory(category);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderAgeCategory getAgeCategoryByUuid(String uuid) {
        return dao.getAgeCategoryByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderAgeCategory getAgeCategoryByCode(String code) {
        return dao.getAgeCategoryByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderAgeCategory> getAgeCategories(String q, boolean includeRetired, Boolean activeOnly,
                                                           Integer startIndex, Integer limit) {
        return dao.getAgeCategories(q, includeRetired, activeOnly, startIndex, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long getAgeCategoriesCount(String q, boolean includeRetired, Boolean activeOnly) {
        return dao.getAgeCategoriesCount(q, includeRetired, activeOnly);
    }

    @Override
    public void retireAgeCategory(ReportBuilderAgeCategory category, String reason) {
        category.setRetired(true);
        category.setRetireReason(reason);
        dao.saveAgeCategory(category);
    }

    @Override
    public void unretireAgeCategory(ReportBuilderAgeCategory category) {
        category.setRetired(false);
        category.setRetireReason(null);
        dao.saveAgeCategory(category);
    }

    @Override
    public void purgeAgeCategory(ReportBuilderAgeCategory category) {
        dao.purgeAgeCategory(category);
    }

    // Groups

    @Override
    public ReportBuilderAgeGroup saveAgeGroup(ReportBuilderAgeGroup group) {
        return dao.saveAgeGroup(group);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportBuilderAgeGroup getAgeGroupById(Integer id) {
        return dao.getAgeGroupById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly) {
        return dao.getAgeGroupsByCategoryUuid(categoryUuid, activeOnly);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportBuilderAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly) {
        return dao.getAgeGroupsByCategoryCode(categoryCode, activeOnly);
    }

    @Override
    public void purgeAgeGroup(ReportBuilderAgeGroup group) {
        dao.purgeAgeGroup(group);
    }

    @Override
    public List<ReportBuilderAgeGroup> getAgeGroups(String q, ReportBuilderAgeCategory category, Boolean activeOnly, Integer startIndex, Integer limit) {
        return dao.getAgeGroups(q,category,activeOnly,startIndex,limit);
    }

    @Override
    public SqlPreviewResult previewSql(String sql, Map<String, Object> params, Integer maxRows) {
        return dao.previewSql(sql, params, maxRows);
    }


    @Override
    public ReportBuilderReport saveReportBuilderReport(ReportBuilderReport report) {
        if (report.getUuid() == null) {
            report.setUuid(java.util.UUID.randomUUID().toString());
        }
        return dao.saveReportBuilderReport(report);
    }

    @Override
    public ReportBuilderReport getReportBuilderReportByUuid(String uuid) {
        return dao.getReportBuilderReportByUuid(uuid);
    }

    @Override
    public List<ReportBuilderReport> getReportBuilderReports(String q, boolean includeRetired, Integer startIndex, Integer limit) {
        return dao.getReportBuilderReports(q, includeRetired, startIndex, limit);
    }

    @Override
    public void retireReportBuilderReport(ReportBuilderReport report, String reason) {
        dao.retireReportBuilderReport(report, reason);
    }

    @Override
    public void purgeReportBuilderReport(ReportBuilderReport report) {
        dao.purgeReportBuilderReport(report);
    }


    @Override
    public CompiledReportArtifacts compileReport(String reportBuilderReportUuid) {
        ReportDefinitionService reportDefinitionService = Context.getService(ReportDefinitionService.class);
        ReportService reportService = Context.getService(ReportService.class);

        ReportBuilderReport report = getReportBuilderReportByUuid(reportBuilderReportUuid);
        if (report == null) {
            throw new IllegalArgumentException("MambaReport not found: " + reportBuilderReportUuid);
        }

        JsonNode reportConfig = parseJson(report.getConfigJson(), "Invalid MambaReport configJson");

        JsonNode definitionNode = reportConfig.path("definition");
        JsonNode designNode = reportConfig.path("design");

        JsonNode sections = definitionNode.path("sections");
        if (!sections.isArray()) {
            sections = reportConfig.path("sections"); // legacy fallback
        }

        ArrayNode compiledFields = objectMapper.createArrayNode();
        ArrayNode compiledDesignGroups = objectMapper.createArrayNode();
        ObjectNode compiledDhis2 = objectMapper.createObjectNode();
        ArrayNode compiledDhis2Rows = objectMapper.createArrayNode();

        if (sections.isArray()) {
            List<JsonNode> sectionRefs = new ArrayList<JsonNode>();
            for (JsonNode s : sections) {
                if (s.path("enabled").asBoolean(true)) {
                    sectionRefs.add(s);
                }
            }

            sectionRefs.sort(Comparator.comparingInt(a -> a.path("sortOrder").asInt(9999)));

            for (JsonNode sectionRef : sectionRefs) {
                String sectionUuid = sectionRef.path("sectionUuid").asText(null);
                if (sectionUuid == null || sectionUuid.trim().isEmpty()) {
                    continue;
                }

                ReportBuilderSection section = getReportBuilderSectionByUuid(sectionUuid);
                if (section == null) {
                    continue;
                }

                JsonNode sectionConfig = parseJson(section.getConfigJson(), "Invalid section configJson for " + sectionUuid);

                String sectionName = sectionRef.path("titleOverride").asText(null);
                if (sectionName == null || sectionName.trim().isEmpty()) {
                    sectionName = section.getName();
                }

                // 1) compile executable definition fields
                ArrayNode sectionFields = compileSectionToReportFields(sectionName, sectionConfig);
                for (JsonNode f : sectionFields) {
                    compiledFields.add(f);
                }

                // 2) compile design groups
                ObjectNode designGroup = compileSectionToDesignGroup(sectionName, sectionConfig, designNode);
                if (designGroup != null) {
                    compiledDesignGroups.add(designGroup);
                }

                // 3) collect DHIS2 mappings
                appendSectionDhis2Mappings(compiledDhis2Rows, sectionConfig);
            }
        }

        // -------- Definition JSON --------
        ObjectNode compiledDefinitionRoot = objectMapper.createObjectNode();
        compiledDefinitionRoot.put("version", 1);
        compiledDefinitionRoot.put("name", report.getName());
        compiledDefinitionRoot.put("code", report.getCode());
        compiledDefinitionRoot.set("report_fields", compiledFields);

        String compiledDefinitionJson;
        try {
            compiledDefinitionJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compiledDefinitionRoot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize compiled report definition JSON", e);
        }

        String definitionFileName = buildDefinitionFileName(report);
        File definitionFile;
        try {
            definitionFile = ReportDesignFileUtil.writeJsonStringToDesignFile(definitionFileName, compiledDefinitionJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write compiled report definition file", e);
        }

        // -------- Design JSON --------
        ObjectNode compiledDesignRoot = objectMapper.createObjectNode();
        compiledDesignRoot.put("version", 1);
        compiledDesignRoot.put("name", report.getName());
        compiledDesignRoot.put("code", report.getCode());
        compiledDesignRoot.put("template", designNode.path("template").asText("section-tabular"));
        compiledDesignRoot.put("arrayName", designNode.path("arrayName").asText("results"));
        compiledDesignRoot.put("defaultValue", designNode.path("defaultValue").asInt(0));
        compiledDesignRoot.set("groups", compiledDesignGroups);

        JsonNode designDimensions = designNode.path("dimensions");
        if (designDimensions.isObject()) {
            compiledDesignRoot.set("dimensions", designDimensions);
        } else {
            compiledDesignRoot.set("dimensions", objectMapper.createObjectNode());
        }

        compiledDhis2.put("enabled", compiledDhis2Rows.size() > 0);
        compiledDhis2.set("rows", compiledDhis2Rows);
        compiledDesignRoot.set("dhis2", compiledDhis2);

        String compiledDesignJson;
        try {
            compiledDesignJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compiledDesignRoot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize compiled report design JSON", e);
        }

        ReportDefinition reportDefinition = findOrCreateReportDefinition(report, reportDefinitionService);

        AggregateReportDataSetDefinition dsd = new AggregateReportDataSetDefinition();
        dsd.setName(report.getName() + " Data Set");
        dsd.setDescription(report.getDescription());
        dsd.setReportDesign(definitionFile);
        dsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
        dsd.addParameter(new Parameter("endDate", "End Date", Date.class));

        reportDefinition.setName(report.getName());
        reportDefinition.setDescription(report.getDescription());
        reportDefinition.getParameters().clear();
        reportDefinition.addParameter(new Parameter("startDate", "Start Date", Date.class));
        reportDefinition.addParameter(new Parameter("endDate", "End Date", Date.class));
        reportDefinition.getDataSetDefinitions().clear();
        reportDefinition.addDataSetDefinition("defaultDataSet", dsd, new HashMap<String, Object>());

        reportDefinition = reportDefinitionService.saveDefinition(reportDefinition);

        // Create or update JSON ReportDesign in DB
        ReportDesign jsonDesign = saveOrUpdateJsonReportDesign(reportDefinition, compiledDesignJson, report);

        CompiledReportArtifacts out = new CompiledReportArtifacts();
        out.setReportBuilderReport(report);
        out.setReportDefinition(reportDefinition);
        out.setReportDesignFile(definitionFile); // evaluator definition file
        out.setCompiledJson(compiledDefinitionJson);
        return out;
    }

    private ObjectNode compileSectionToDesignGroup(String sectionName, JsonNode sectionConfig, JsonNode reportDesignNode) {
        ObjectNode group = objectMapper.createObjectNode();
        group.put("title", sectionName);

        ArrayNode rows = objectMapper.createArrayNode();

        // section label row
        ObjectNode sectionRow = objectMapper.createObjectNode();
        sectionRow.put("type", "section-label");
        sectionRow.put("label", sectionName);
        sectionRow.put("indent", 0);
        sectionRow.put("span", "all");
        sectionRow.put("emphasis", "section");
        rows.add(sectionRow);

        JsonNode indicators = sectionConfig.path("indicators");
        if (indicators.isArray()) {
            List<JsonNode> sorted = new ArrayList<JsonNode>();
            for (JsonNode ind : indicators) {
                sorted.add(ind);
            }
            sorted.sort(Comparator.comparingInt(a -> a.path("sortOrder").asInt(9999)));

            for (JsonNode indicator : sorted) {
                ObjectNode row = objectMapper.createObjectNode();
                row.put("type", "indicator");
                row.put("indicatorUuid", indicator.path("indicatorUuid").asText(""));
                row.put("code", indicator.path("code").asText(""));
                row.put("label", indicator.path("name").asText(""));
                row.put("indent", 1);
                row.put("keyPattern", buildIndicatorKeyPattern(indicator, sectionConfig));
                row.put("showTotal", true);
                row.put("showDisaggregation", looksDisaggregated(indicator, sectionConfig));

                ObjectNode dims = objectMapper.createObjectNode();
                if (looksDisaggregated(indicator, sectionConfig)) {
                    dims.put("age", sectionConfig.path("disaggregation").path("ageCategoryCode").asText(""));
                    dims.put("sex", "sex");
                }
                row.set("dims", dims);

                rows.add(row);
            }
        }

        group.set("rows", rows);
        return group;
    }

    private String buildIndicatorKeyPattern(JsonNode indicator, JsonNode sectionConfig) {
        if (looksDisaggregated(indicator, sectionConfig)) {
            return "{code}_{age}_{sex}";
        }
        return "{code}_TOTAL";
    }

    private void appendSectionDhis2Mappings(ArrayNode targetRows, JsonNode sectionConfig) {
        JsonNode dhis2 = sectionConfig.path("exchangeMappings").path("dhis2");
        if (!dhis2.isObject() || !dhis2.path("enabled").asBoolean(false)) {
            return;
        }

        JsonNode mappings = dhis2.path("indicatorMappings");
        if (!mappings.isArray()) {
            return;
        }

        for (JsonNode m : mappings) {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("indicatorUuid", m.path("indicatorUuid").asText(""));
            row.put("dataElementId", m.path("dataElementId").asText(""));

            JsonNode coc = m.path("categoryOptionComboByDisagg");
            if (coc.isObject()) {
                row.set("categoryOptionComboByDisagg", coc);
            } else {
                row.set("categoryOptionComboByDisagg", objectMapper.createObjectNode());
            }

            targetRows.add(row);
        }
    }

    private ReportDesign saveOrUpdateJsonReportDesign(ReportDefinition reportDefinition, String compiledDesignJson, ReportBuilderReport report) {
        ReportService reportService = Context.getService(ReportService.class);

        List<ReportDesign> existing = reportService.getReportDesigns(reportDefinition, null, false);
        ReportDesign design = null;

        if (existing != null) {
            for (ReportDesign d : existing) {
                if ("JSON".equalsIgnoreCase(d.getName())) {
                    design = d;
                    break;
                }
            }
        }

        if (design == null) {
            design = new ReportDesign();
            design.setUuid(UUID.randomUUID().toString());
            design.setName("JSON");
            design.setReportDefinition(reportDefinition);
            design.setRendererType(TextTemplateRenderer.class);
        } else {
            design.setReportDefinition(reportDefinition);
            design.setRendererType(TextTemplateRenderer.class);
            if (design.getResources() != null) {
                design.getResources().clear();
            }
        }

        ReportDesignResource resource = new ReportDesignResource();
        resource.setName("template");
        resource.setExtension("json");
        resource.setContentType("application/json");
        resource.setContents(compiledDesignJson.getBytes(StandardCharsets.UTF_8));
        resource.setReportDesign(design);

        design.addResource(resource);

        return reportService.saveReportDesign(design);
    }

    private ArrayNode compileSectionToReportFields(String sectionName, JsonNode sectionConfig) {
        ArrayNode out = objectMapper.createArrayNode();
        JsonNode indicators = sectionConfig.path("indicators");

        if (!indicators.isArray()) {
            return out;
        }

        List<JsonNode> sorted = new ArrayList<JsonNode>();
        for (JsonNode ind : indicators) {
            sorted.add(ind);
        }
        sorted.sort(Comparator.comparingInt(a -> a.path("sortOrder").asInt(9999)));

        for (JsonNode indicator : sorted) {
            String sql = indicator.path("sql").path("compiled").asText(null);
            if (sql == null || sql.trim().isEmpty()) {
                continue;
            }

            ObjectNode field = objectMapper.createObjectNode();

            // Keep closer to the legacy template structure
            field.put("indicator_name", indicator.path("code").asText(""));
            field.put("indicator_label", indicator.path("name").asText(""));
            field.put("subsection", sectionName);
            field.put("sqlQuery", decodeHtml(sql));

            boolean isDisaggregated = looksDisaggregated(indicator, sectionConfig);

            if (isDisaggregated) {
                ArrayNode dissaggregations = objectMapper.createArrayNode();
                dissaggregations.add("age_group");
                dissaggregations.add("gender");
                field.set("dissaggregations", dissaggregations);

                ArrayNode values = buildDisaggregatedValues(indicator, sectionConfig);
                if (values.size() > 0) {
                    field.set("values", values);
                } else {
                    // fail-safe fallback if groups cannot be resolved
                    field.put("value_place_holder", buildSinglePlaceholder(indicator));
                }
            } else {
                field.put("value_place_holder", buildSinglePlaceholder(indicator));
            }

            out.add(field);
        }

        return out;
    }

    private boolean looksDisaggregated(JsonNode indicator, JsonNode sectionConfig) {
        JsonNode strategy = indicator.path("sql").path("strategy");
        if (strategy.isTextual() && strategy.asText("").contains("DISAGG")) {
            return true;
        }

        JsonNode dis = sectionConfig.path("disaggregation");
        return dis.isObject() && !dis.path("none").asBoolean(false);
    }

    private ArrayNode buildDisaggregatedValues(JsonNode indicator, JsonNode sectionConfig) {
        ArrayNode out = objectMapper.createArrayNode();

        String indicatorCode = indicator.path("code").asText("IND");
        JsonNode dis = sectionConfig.path("disaggregation");
        JsonNode genders = dis.path("genders");

        String ageCategoryCode = dis.path("ageCategoryCode").asText(null);
        List<String> ageLabels = resolveAgeGroupLabels(ageCategoryCode);

        if (!ageLabels.isEmpty() && genders.isArray()) {
            for (String ageLabel : ageLabels) {
                for (JsonNode g : genders) {
                    String gender = g.asText("");

                    ObjectNode one = objectMapper.createObjectNode();
                    one.put("dissaggregations1", ageLabel);
                    one.put("dissaggregations2", gender);
                    one.put("value_place_holder", buildDisaggregatedPlaceholder(indicatorCode, ageLabel, gender));
                    out.add(one);
                }
            }
        }

        return out;
    }

    private String buildSinglePlaceholder(JsonNode indicator) {
        String code = indicator.path("code").asText("IND");
        return sanitize(code) + "_TOTAL";
    }

    private String sanitize(String s) {
        return (s == null ? "" : s.trim())
                .replace("+", "plus")
                .replace("<", "lt")
                .replace(">", "gt")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    private String buildDefinitionFileName(ReportBuilderReport report) {
        String base = report.getCode();
        if (base == null || base.trim().isEmpty()) {
            base = report.getUuid();
        }
        base = sanitize(base);
        if (base == null || base.trim().isEmpty()) {
            base = "report_" + System.currentTimeMillis();
        }
        return base + ".json";
    }

    private ReportDefinition findOrCreateReportDefinition(ReportBuilderReport report, ReportDefinitionService reportDefinitionService) {
        // TODO: later persist runtime linkage and re-use exact ReportDefinition UUID
        ReportDefinition rd = new ReportDefinition();
        rd.setName(report.getName());
        rd.setDescription(report.getDescription());
        return rd;
    }

    private JsonNode parseJson(String raw, String message) {
        try {
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    private String decodeHtml(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private List<String> resolveAgeGroupLabels(String ageCategoryCode) {
        if (ageCategoryCode == null || ageCategoryCode.trim().isEmpty()) {
            return Collections.emptyList();
        }

        ReportBuilderAgeCategory category = getAgeCategoryByCode(ageCategoryCode);
        if (category == null || category.getAgeGroups() == null || category.getAgeGroups().isEmpty()) {
            return Collections.emptyList();
        }

        return category.getAgeGroups().stream()
                .filter(g -> g != null && Boolean.TRUE.equals(g.getActive()) && g.getLabel() != null && !g.getLabel().trim().isEmpty())
                .sorted(Comparator.comparingInt(g -> g.getSortOrder() == null ? Integer.MAX_VALUE : g.getSortOrder()))
                .map(ReportBuilderAgeGroup::getLabel)
                .collect(Collectors.toList());
    }

    private String buildDisaggregatedPlaceholder(String indicatorCode, String ageLabel, String gender) {
        return sanitize(indicatorCode) + "_" + sanitize(ageLabel) + "_" + sanitize(gender);
    }
}
