package org.openmrs.module.ugandaemrreports.api.db;

import org.openmrs.*;
import org.openmrs.Concept;
import org.openmrs.logic.op.In;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.definition.data.evaluator.SqlPreviewResult;
import org.openmrs.module.ugandaemrreports.model.*;
import org.openmrs.reporting.PatientSearch;
import org.openmrs.reporting.ReportObjectWrapper;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Set;

/**
 * Database methods for {@link UgandaEMRReportsService}.
 */
public interface UgandaEMRReportsDAO {
    void executeFlatteningScript();

    List<ReportObjectWrapper> getReportObjects(String type);
    PatientSearch getPatientSearchByUuid(String uuid);

    Cohort getPatientCurrentlyInPrograms(String uuid);

    List<Integer> getObsConceptsFromEncounters(EncounterType encounterType);
    Map<Integer, String> getPatientsConditionsStatus(org.openmrs.cohort.Cohort patients, Concept codedCondition);

    Set<Concept> getAllConditions();

    Map<Integer,Object> getLatestPatientAppointmentsScheduled(org.openmrs.cohort.Cohort patients, int limit);

    List<Object> getNonCodedOrderReasons(OrderType orderType);

    List<Concept> getCodedOrderReasons(OrderType orderType);

    Map<Integer, Map<String, Object>> getDrugOrderByIndication(org.openmrs.cohort.Cohort patients,String drugIndication,OrderType orderType);


    // -------------------------
    // MambaIndicator
    // -------------------------
    MambaIndicator saveMambaIndicator(MambaIndicator indicator);

    MambaIndicator getMambaIndicatorById(Integer id);

    MambaIndicator getMambaIndicatorByUuid(String uuid);

    MambaIndicator getMambaIndicatorByCode(String code);

    List<MambaIndicator> getMambaIndicators(String q, MambaIndicator.Kind kind, boolean includeRetired,
                                            Integer startIndex, Integer limit);

    long getMambaIndicatorsCount(String q, MambaIndicator.Kind kind, boolean includeRetired);

    void purgeMambaIndicator(MambaIndicator indicator);

    // -------------------------
    // MambaSection
    // -------------------------
    MambaSection saveMambaSection(MambaSection section);

    MambaSection getMambaSectionById(Integer id);

    MambaSection getMambaSectionByUuid(String uuid);

    MambaSection getMambaSectionByCode(String code);

    List<MambaSection> getMambaSections(String q, boolean includeRetired,
                                        Integer startIndex, Integer limit);

    long getMambaSectionsCount(String q, boolean includeRetired);

    void purgeMambaSection(MambaSection section);

    // -------------------------
    // MambaDataTheme
    // -------------------------
    MambaDataTheme saveMambaDataTheme(MambaDataTheme theme);

    MambaDataTheme getMambaDataThemeById(Integer id);

    MambaDataTheme getMambaDataThemeByUuid(String uuid);

    MambaDataTheme getMambaDataThemeByCode(String code);

    List<MambaDataTheme> getMambaDataThemes(String q, boolean includeRetired,
                                            Integer startIndex, Integer limit);

    long getMambaDataThemesCount(String q, boolean includeRetired);

    void purgeMambaDataTheme(MambaDataTheme theme);

    // -------------------------
    // Age Category & Groups
    // -------------------------
    MambaAgeCategory saveAgeCategory(MambaAgeCategory category);

    MambaAgeCategory getAgeCategoryById(Integer id);

    MambaAgeCategory getAgeCategoryByUuid(String uuid);

    MambaAgeCategory getAgeCategoryByCode(String code);

    List<MambaAgeCategory> getAgeCategories(String q, boolean includeRetired, Boolean activeOnly,
                                            Integer startIndex, Integer limit);

    long getAgeCategoriesCount(String q, boolean includeRetired, Boolean activeOnly);

    void purgeAgeCategory(MambaAgeCategory category);

    MambaAgeGroup saveAgeGroup(MambaAgeGroup group);

    MambaAgeGroup getAgeGroupById(Integer id);

    List<MambaAgeGroup> getAgeGroupsByCategoryUuid(String categoryUuid, Boolean activeOnly);

    List<MambaAgeGroup> getAgeGroupsByCategoryCode(String categoryCode, Boolean activeOnly);

    void purgeAgeGroup(MambaAgeGroup group);

    // -------------------------
    // Utility: list DB tables
    // -------------------------
    public List<String> getMambaTables();

    public List<Map> getMambaTableColumns(String tableName);

    List<MambaIndicator> getAllMambaIndicator(Integer startIndex, Integer limit);

    List<MambaAgeGroup> getAgeGroups(String q, MambaAgeCategory category, Boolean activeOnly, Integer startIndex, Integer limit);


    SqlPreviewResult previewSql(String sql, Map<String, Object> params, Integer maxRows);


    MambaReport saveMambaReport(MambaReport report);

   MambaReport getMambaReportByUuid(String uuid);

   List<MambaReport> getMambaReports(String q, boolean includeRetired, Integer startIndex, Integer limit);

   void deleteMambaReport(MambaReport report);

   void retireMambaReport(MambaReport report, String reason);

    void purgeMambaReport(MambaReport report);
}
