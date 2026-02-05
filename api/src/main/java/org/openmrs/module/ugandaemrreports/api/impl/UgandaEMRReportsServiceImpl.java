package org.openmrs.module.ugandaemrreports.api.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.TextTemplateRenderer;
import org.openmrs.module.reporting.report.renderer.template.TemplateEngine;
import org.openmrs.module.reporting.report.renderer.template.TemplateEngineManager;
import org.openmrs.module.ugandaemrreports.activator.AppConfigInitializer;
import org.openmrs.module.ugandaemrreports.activator.Initializer;
import org.openmrs.module.ugandaemrreports.activator.ReportInitializer;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.api.db.hibernate.HibernateUgandaEMRReportsDAO;
import org.openmrs.module.ugandaemrreports.model.Dashboard;
import org.openmrs.module.ugandaemrreports.model.DashboardReportObject;
import org.openmrs.module.ugandaemrreports.util.JsonTemplateConverter;
import org.openmrs.reporting.PatientSearch;
import org.openmrs.reporting.ReportObjectWrapper;
import org.openmrs.util.OpenmrsUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link UgandaEMRReportsService}.
 */
public class UgandaEMRReportsServiceImpl extends BaseOpenmrsService implements UgandaEMRReportsService {

    protected final Log log = LogFactory.getLog(this.getClass());

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
     *
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
     *
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

}
