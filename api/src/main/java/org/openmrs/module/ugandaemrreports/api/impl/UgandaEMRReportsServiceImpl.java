package org.openmrs.module.ugandaemrreports.api.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.Concept;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.logic.op.In;
import org.openmrs.module.mambacore.api.FlattenDatabaseService;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;
import org.openmrs.module.reporting.report.renderer.TextTemplateRenderer;
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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Set;

/**
 * It is a default implementation of {@link UgandaEMRReportsService}.
 */


public class UgandaEMRReportsServiceImpl extends BaseOpenmrsService implements UgandaEMRReportsService {

	private final JsonTemplateConverter converter = new JsonTemplateConverter();

	protected final Log log = LogFactory.getLog(this.getClass());

	private HibernateUgandaEMRReportsDAO dao;

	/**
	 * @return the dao
	 */
	public HibernateUgandaEMRReportsDAO getDao() {
		return dao;
	}

	/**
	 * @param dao the dao to set
	 */
	public void setDao(HibernateUgandaEMRReportsDAO dao) {
		this.dao = dao;
	}


	@Override
	public List<DashboardReportObject> getAllDashboardReportObjects() throws APIException {
		return  dao.getAllDashboardReportObjects();
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
		return  dao. getDashboardReportObjectById( id);
	}

	@Override
	public Dashboard saveDashboard(Dashboard dashboard) throws APIException {
		return  dao.saveDashboard(dashboard);
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
	public Map<Integer,Object> getLatestPatientAppointmentsScheduled(org.openmrs.cohort.Cohort patients, int limit){
		return dao.getLatestPatientAppointmentsScheduled(patients,limit);
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
	public Map<Integer, Map<String, Object>> getDrugOrderByIndicator(org.openmrs.cohort.Cohort patients, String drugIndication,OrderType orderType) {
		return dao.getDrugOrderByIndication(patients,drugIndication,orderType);
	}

	@Override
	public String renderHtmlFromJsonTemplate(ReportDesign reportDesign) {
		String templateJson = readDesignResource(reportDesign);
		return converter.renderHtmlOnly(templateJson);
	}

	@Override
	public String createPayloadJsonFromTemplate(ReportData reportData, ReportDesign reportDesign, String renderType, Map<String, Object> flatValues, String remapJsonOptional) {
		String templateJson = readDesignResource(reportDesign);
		return converter.buildPayloadOnly(templateJson, flatValues, remapJsonOptional);
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
}
