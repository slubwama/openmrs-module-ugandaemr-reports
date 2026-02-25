package org.openmrs.module.ugandaemrreports.api;

import org.openmrs.*;
import org.openmrs.Concept;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.ugandaemrreports.model.*;
import org.openmrs.reporting.PatientSearch;
import org.openmrs.reporting.ReportObjectWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This service exposes module's core functionality. It is a Spring managed bean which is configured in
 * moduleApplicationContext.xml.
 * <p>
 * It can be accessed only via Context:<br>
 * <code>
 * Context.getService(UgandaEMRReportsService.class).someMethod();
 * </code>
 *
 * @see org.openmrs.api.context.Context
 */
@Transactional
public interface UgandaEMRReportsService extends OpenmrsService {

    /**
     * Getting all Report objects for dashboard
     *
     * @return List<DashboardReportObject> returns all report objects in a list
     * @throws APIException
     */
    List<DashboardReportObject> getAllDashboardReportObjects() throws APIException;

    /**
     * Get Report object  By uuid
     *
     * @param uuid the uuid of the report object  to return
     * @return Dashboard report object that matched the uuid parameter
     * @throws APIException
     */
    @Transactional
    DashboardReportObject getDashboardReportObjectByUUID(String uuid) throws APIException;

    /**
     * Saves the Dashboard Report Object
     *
     * @param dashboardReportObject to be saved.
     * @return DashboardReportObject saved
     * @throws APIException
     */
    @Transactional
    DashboardReportObject saveDashboardReportObject(DashboardReportObject dashboardReportObject) throws APIException;

    /**
     * @param id to get dashboardReportObject by id
     * @return DashboardReportObject
     * @throws APIException
     */
    @Transactional
    DashboardReportObject getDashboardReportObjectById(Integer id) throws APIException;


    /**
     * @param dashboard to save Dashboard
     * @return Dashboard
     */
    @Transactional
    Dashboard saveDashboard(Dashboard dashboard)  throws APIException;


    /**
     * @param uniqueId of Dashboard
     * @return Dashboard
     * @throws APIException
     */
    @Transactional
    Dashboard getDashboardByUUID(String uniqueId) throws APIException;


    /**
     * @param id for Dashboard id
     * @return Dashboard with id above
     * @throws APIException
     */
    @Transactional
    Dashboard getDashboardById(Integer id) throws APIException;


    /**
     * @return List of Dashboards in database
     * @throws APIException
     */
    @Transactional
    List<Dashboard> getAllDashboards() throws APIException;


    void executeFlatteningScript();

    List<ReportObjectWrapper> getPatientSearches(String type);

    PatientSearch getPatientSearchByUuid(String uuid);

    Cohort getPatientCurrentlyInProgram(String programUuid);

     Map<Integer, String> getPatientsConditionsStatus(org.openmrs.cohort.Cohort patients, Concept codedCondition);

     Set<Concept> getConditionsConcepts();

    Map<Integer,Object> getLatestPatientAppointmentsScheduled(org.openmrs.cohort.Cohort patients, int limit);

    List<Integer> getObsConceptsFromEncounters(EncounterType encounterType);

    List<Object> getNonCodedOrderReasons(OrderType orderType);

    List<Concept> getCodedOrderReasons(OrderType orderType);

    Map<Integer, Map<String, Object>> getDrugOrderByIndicator(org.openmrs.cohort.Cohort patients,String drugIndication,OrderType orderType);

    public  void addMambaetlProperties();

    public void setupMambaETL();

    public void setUpReports();

    /** Returns HTML as a string for preview/printing */
    public String renderHtmlFromJsonTemplate(ReportDesign reportDesign);

    /** Returns payload JSON as a string (no JsonNode leaks) */
    public String createPayloadJsonFromTemplate(ReportData reportData, ReportDesign reportDesign, String renderType, Map<String, Object> flatValues, String remapJsonOptional);

    public String buildPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType);

    public String buildFinalPayloadJson(ReportData reportData, ReportDesign reportDesign, String renderType, Date endDate);

    public String buildPreviewHtml(ReportData reportData, ReportDesign reportDesign);


    // =========================
    // MambaIndicator
    // =========================
    MambaIndicator saveMambaIndicator(MambaIndicator indicator);

    MambaIndicator getMambaIndicatorById(Integer id);

    MambaIndicator getMambaIndicatorByUuid(String uuid);

    MambaIndicator getMambaIndicatorByCode(String code);

    List<MambaIndicator> searchMambaIndicators(String q, MambaIndicator.Kind kind, boolean includeRetired, Integer startIndex, Integer limit);

    public List<MambaIndicator> getAllMambaIndicator(Integer startIndex, Integer limit);

    public List<MambaIndicator> getMambaIndicators(MambaIndicator.Kind kind, boolean includeRetired, Integer startIndex, Integer limit);

    long getMambaIndicatorsCount(String q, MambaIndicator.Kind kind, boolean includeRetired);

    void retireMambaIndicator(MambaIndicator indicator, String reason);

    void unretireMambaIndicator(MambaIndicator indicator);

    void purgeMambaIndicator(MambaIndicator indicator);

    // =========================
    // MambaSection
    // =========================
    MambaSection saveMambaSection(MambaSection section);

    MambaSection getMambaSectionById(Integer id);

    MambaSection getMambaSectionByUuid(String uuid);

    MambaSection getMambaSectionByCode(String code);

    List<MambaSection> getMambaSections(String q,
                                        boolean includeRetired,
                                        Integer startIndex,
                                        Integer limit);

    long getMambaSectionsCount(String q, boolean includeRetired);

    void retireMambaSection(MambaSection section, String reason);

    void unretireMambaSection(MambaSection section);

    void purgeMambaSection(MambaSection section);

    // =========================
    // MambaDataTheme
    // =========================
    MambaDataTheme saveMambaDataTheme(MambaDataTheme theme);

    MambaDataTheme getMambaDataThemeById(Integer id);

    MambaDataTheme getMambaDataThemeByUuid(String uuid);

    MambaDataTheme getMambaDataThemeByCode(String code);

    List<MambaDataTheme> getMambaDataThemes(String q,
                                            boolean includeRetired,
                                            Integer startIndex,
                                            Integer limit);

    long getMambaDataThemesCount(String q, boolean includeRetired);

    void retireMambaDataTheme(MambaDataTheme theme, String reason);

    void unretireMambaDataTheme(MambaDataTheme theme);

    void purgeMambaDataTheme(MambaDataTheme theme);

    List<String> getMambaTables();

    public List<Map> getMambaTableColumns(String tableName);

    // Categories
    MambaAgeCategory saveAgeCategory(MambaAgeCategory category);

    MambaAgeCategory getAgeCategoryByUuid(String uuid);

    MambaAgeCategory getAgeCategoryByCode(String code);

    List<MambaAgeCategory> getAgeCategories(String q, boolean includeRetired, Boolean activeOnly,
                                            Integer startIndex, Integer limit);

    long getAgeCategoriesCount(String q, boolean includeRetired, Boolean activeOnly);

    void retireAgeCategory(MambaAgeCategory category, String reason);

    void unretireAgeCategory(MambaAgeCategory category);

    void purgeAgeCategory(MambaAgeCategory category);

    // Groups
    MambaAgeGroup saveAgeGroup(MambaAgeGroup group);

    MambaAgeGroup getAgeGroupById(Integer id);

    List<MambaAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly);

    List<MambaAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly);

    void purgeAgeGroup(MambaAgeGroup group);
}