package org.openmrs.module.ugandaemrreports.web.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.RenderingMode;
import org.openmrs.module.reporting.report.service.ReportService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


import javax.servlet.http.HttpServletRequest;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + EvaluateReportDefinitionRestController.UGANDAEMRREPORTS + EvaluateReportDefinitionRestController.SET)
public class EvaluateReportDefinitionRestController {
    public static final String JSON_REPORT_RENDERER_TYPE = "org.openmrs.module.reporting.report.renderer.TextTemplateRenderer";
    public static final String EXCEL_REPORT_RENDERER_TYPE = "org.openmrs.module.reporting.report.renderer.XlsReportRenderer";

    public static final String UGANDAEMRREPORTS = "/ugandaemrreports";
    public static final String SET = "/reportingDefinition";

    public String rdUuid;

    @Autowired
    public GenericConversionService conversionService;

    @Autowired
    public ReportService reportService;


    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public Object getReportData(HttpServletRequest request,
                                @RequestParam(required = true, value = "uuid") String reportDefinitionUuid,
                                @RequestParam(required = false, value = "renderType") String rendertype) {
        try {
            rdUuid = reportDefinitionUuid;
            if (!validateDateIsValidFormat(request.getParameter("endDate"))) {
                SimpleObject message = new SimpleObject();
                message.put("error", "given date " + request.getParameter("endDate") + "is not valid");

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON).body(message);

            }
            EvaluationContext context = new EvaluationContext();
            ReportDefinitionService service = Context.getService(ReportDefinitionService.class);
            ReportDefinition rd = service.getDefinitionByUuid(reportDefinitionUuid);
            ReportData reportData = null;
            if (rd != null) {
                Collection<Parameter> missingParameters = new ArrayList<Parameter>();
                Map<String, Object> parameterValues = new HashMap<String, Object>();

                for (Parameter parameter : rd.getParameters()) {
                    String name = parameter.getName();
                    String submitted = request.getParameter(name);
                    Class<?> targetType = parameter.getType();

                    if (parameter.getCollectionType() != null) {
                        throw new IllegalStateException("Collection parameters not yet implemented");
                    }

                    Object converted = null;

                    boolean hasValue = submitted != null && !submitted.trim().isEmpty();

                    if (!hasValue) {
                        converted = parameter.getDefaultValue();
                    } else {
                        try {
                            converted = conversionService.convert(submitted, targetType);
                        } catch (Exception e) {

                            if (java.util.Date.class.isAssignableFrom(targetType)) {
                                converted = tryParseDate(submitted);
                            }
                        }
                    }

                    if (converted == null) {
                        missingParameters.add(parameter);
                    }

                    parameterValues.put(name, converted);
                }



                context.setParameterValues(parameterValues);

//                makeExcelReportRequest(rd,parameterValues);
                reportData = getReportDefinitionService().evaluate(rd, context);

            }

            if (rendertype == null) {
                Map<String, List<SimpleObject>> listMap = new HashMap<>();
                Map<String, DataSet> dataSets = reportData.getDataSets();
                Set<String> keySet = dataSets.keySet();
                for (String key : keySet) {
                    DataSet dataSet = dataSets.get(key);
                    List<SimpleObject> simpleObjectList = convertDataSetToSimpleObject(dataSet);
                    listMap.put(key, simpleObjectList);
                }


                return ResponseEntity.status(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON).body(listMap);
            } else {

                List<ReportDesign> reportDesigns = Context.getService(ReportService.class).getReportDesigns(rd, null, false);

                ReportDesign reportDesign = reportDesigns.stream().filter(p -> "JSON".equals(p.getName())).findAny().orElse(null);

                if (reportDesign != null) {
                    String reportRendergingMode = JSON_REPORT_RENDERER_TYPE + "!" + reportDesign.getUuid();
                    RenderingMode renderingMode = new RenderingMode(reportRendergingMode);
                    if (!renderingMode.getRenderer().canRender(rd)) {
                        throw new IllegalArgumentException("Unable to render Report with " + reportRendergingMode);
                    }
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    Date endDate = dateFormat.parse(request.getParameter("endDate"));
                    String report =processFinalPayload(reportData,reportDesign,rendertype,endDate);


                        return ResponseEntity.status(HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON).body(report);

                } else {
                    return new ResponseEntity<String>("{'Error': 'No design to preview report'}", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }

        } catch (Exception ex) {
            return new ResponseEntity<String>("{Error: " + ex.getMessage() + "}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Boolean validateDateIsValidFormat(String date) {
        try {
            DateUtil.parseYmd(date);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private ReportDefinitionService getReportDefinitionService() {
        return Context.getService(ReportDefinitionService.class);
    }

    public List<SimpleObject> convertDataSetToSimpleObject(DataSet d) {
        Iterator iterator = d.iterator();

        List<SimpleObject> dataList = new ArrayList<SimpleObject>();
        while (iterator.hasNext()) {
            DataSetRow r = (DataSetRow) iterator.next();
            Map<String, Object> columns = r.getColumnValuesByKey();
            Set<String> keys = columns.keySet();
            SimpleObject details = new SimpleObject();

            for (String key : keys) {
                Object object = r.getColumnValue(key);
                if (object == null) {
                    details.add(key, "");
                } else {
                    try {
                        details.add(key, object.toString());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            dataList.add(details);

        }
        return dataList;
    }
    public static String getYearAndQuarter(Date date) {
        if (date == null) {
            System.out.println("Date cannot be null.");
            return null;
        }

        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        int quarter = (month - 1) / 3 + 1;
        return year + "Q" + quarter;
    }

    public String processFinalPayload(ReportData reportData, ReportDesign reportDesign, String rendertype, Date endDate) {

        // 1) Build values map (for now you can start empty and still get a valid payload with default 0s)
        Map<String, Object> values = extractValuesFromReportData(reportData); // see method below

        // 2) Build payload JSON string via the new service method (no JsonNode in interface)
        UgandaEMRReportsService service = Context.getService(UgandaEMRReportsService.class);
        String payloadJson = service.createPayloadJsonFromTemplate(reportData, reportDesign, "json", values, null);

        // 3) Add period to root.json using Jackson ONLY here in the controller (web layer)
        try {
            ObjectMapper om = new ObjectMapper();
            ObjectNode root = (ObjectNode) om.readTree(payloadJson);

            ObjectNode jsonNode = (ObjectNode) root.get("json");
            if (jsonNode == null) {
                jsonNode = om.createObjectNode();
                root.set("json", jsonNode);
            }

            String period = getYearAndQuarter(endDate);
            jsonNode.put("period", period);

            return om.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to append period to payload JSON", e);
        }
    }

    private Map<String, Object> extractValuesFromReportData(ReportData reportData) {
        Map<String, Object> values = new HashMap<String, Object>();

        if (reportData == null || reportData.getDataSets() == null) {
            return values;
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

                // --- Case A: "tall" dataset: separate code/age/sex columns ---
                String code = firstString(cols, "code", "dataelement", "dataElement", "data_element");
                String age  = firstString(cols, "age", "agegroup", "age_group");
                String sex  = firstString(cols, "sex", "gender");
                Object valObj = firstObject(cols, "value", "count", "total");

                if (!isBlank(code) && !isBlank(age) && !isBlank(sex) && valObj != null) {
                    String key = code + "_" + age + "_" + sex;
                    values.put(key, valObj);
                    continue;
                }

                // --- Case B: "wide" dataset: keys already look like CODE_AGE_SEX ---
                // Example: OR02_29d_4y_F -> 0
                for (Map.Entry<String, Object> e : cols.entrySet()) {
                    String k = e.getKey();
                    Object v = e.getValue();

                    if (isBlank(k)) continue;

                    // Only accept keys that look like our pattern: <something>_<something>_<M|F>
                    // Works even if code contains letters/numbers like EM01f or CR02b
                    if (looksLikeIndicatorKey(k)) {
                        values.put(k.trim(), v);
                    }
                }
            }
        }

        return values;
    }

    private boolean looksLikeIndicatorKey(String key) {
        // Expect last part is sex M/F and at least 2 underscores total.
        // Example valid: OR02_29d_4y_F, EM01f_20p_M
        // Reject random stuff like "location" or "period"
        String k = key.trim();
        int lastUnderscore = k.lastIndexOf('_');
        if (lastUnderscore < 0) return false;

        String sex = k.substring(lastUnderscore + 1);
        if (!("M".equals(sex) || "F".equals(sex))) return false;

        // ensure at least two underscores total (code_age_sex minimum)
        int firstUnderscore = k.indexOf('_');
        return firstUnderscore > 0 && firstUnderscore < lastUnderscore;
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



    private Date tryParseDate(String value) {
        List<String> patterns = Arrays.asList("yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss", "dd/MM/yyyy", "MM/dd/yyyy");

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(value);
            } catch (ParseException ignored) {
            }
        }

        return null;
    }
}
