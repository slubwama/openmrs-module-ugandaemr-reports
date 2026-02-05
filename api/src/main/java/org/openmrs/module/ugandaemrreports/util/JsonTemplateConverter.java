package org.openmrs.module.ugandaemrreports.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

public class JsonTemplateConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class Result {
        public final String html;
        public final String payloadJson;
        public Result(String html, String payloadJson) {
            this.html = html;
            this.payloadJson = payloadJson;
        }
    }

    public Result convert(String templateJson,
                          Map<String, Object> flatValues,
                          String remapJsonOptional) {
        String html = renderHtmlFinal(templateJson, flatValues);
        String payload = buildPayloadOnly(templateJson, flatValues, remapJsonOptional);
        return new Result(html, payload);
    }

    /* -------------------- FINAL HTML (iframe-ready) -------------------- */

    public String renderHtmlFinal(String templateJson, Map<String, Object> flatValues) {
        try {
            JsonTemplate tpl = MAPPER.readValue(templateJson, JsonTemplate.class);
            Map<String, Object> values = (flatValues == null)
                    ? Collections.<String, Object>emptyMap()
                    : flatValues;
            return renderFinalHtmlDocument(tpl, values);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render FINAL HTML from JSON template", e);
        }
    }

    private String renderFinalHtmlDocument(JsonTemplate tpl, Map<String, Object> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'/>");
        sb.append("<style>")
                .append("body{font-family:Arial,Helvetica,sans-serif;margin:12px;}")
                .append("h3{margin:16px 0 6px 0;font-size:14px;}")
                .append("table{border-collapse:collapse;width:100%;margin-bottom:18px;}")
                .append("th,td{border:1px solid #ddd;padding:6px;font-size:12px;}")
                .append("th{background:#f6f6f6;text-align:center;}")
                .append("td.code{font-weight:bold;white-space:nowrap;}")
                .append("td.val{text-align:right;}")
                .append("</style></head><body>");

        if (tpl == null || tpl.mapping == null || tpl.mapping.groups == null) {
            sb.append("<div>No groups defined in template.</div>");
            sb.append("</body></html>");
            return sb.toString();
        }

        for (Group g : tpl.mapping.groups) {
            sb.append(renderGroupTable(tpl, g, values));
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String renderGroupTable(JsonTemplate tpl, Group g, Map<String, Object> values) {
        if (g == null) return "";

        if (g.indicatorCodes == null) g.indicatorCodes = new ArrayList<String>();
        if (g.keyPattern == null || g.keyPattern.trim().length() == 0) g.keyPattern = "{code}_{age}_{sex}";

        List<DimItem> ages = dimsFor(tpl, g, "age", "age");
        List<DimItem> sexes = dimsFor(tpl, g, "sex", "sex");

        int defaultValue = (tpl.mapping != null && tpl.mapping.defaultValue != null) ? tpl.mapping.defaultValue : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(esc(g.title)).append("</h3>");
        sb.append("<table>");

        // Header
        sb.append("<thead>");
        sb.append("<tr>");
        sb.append("<th rowspan='2'>Indicator</th>");

        if (!ages.isEmpty() && !sexes.isEmpty()) {
            for (DimItem age : ages) {
                sb.append("<th colspan='").append(sexes.size()).append("'>")
                        .append(esc(age.label)).append("</th>");
            }
        } else {
            sb.append("<th>Value</th>");
        }
        sb.append("</tr>");

        sb.append("<tr>");
        if (!ages.isEmpty() && !sexes.isEmpty()) {
            for (int i = 0; i < ages.size(); i++) {
                for (DimItem sex : sexes) {
                    sb.append("<th>").append(esc(sex.label)).append("</th>");
                }
            }
        } else {
            sb.append("<th>&nbsp;</th>");
        }
        sb.append("</tr>");
        sb.append("</thead>");

        // Body
        sb.append("<tbody>");

        for (String code : g.indicatorCodes) {
            sb.append("<tr>");
            sb.append("<td class='code'>").append(esc(code)).append("</td>");

            if (!ages.isEmpty() && !sexes.isEmpty()) {
                for (DimItem age : ages) {
                    for (DimItem sex : sexes) {
                        String key = buildKey(g.keyPattern, code, age.id, sex.id);
                        Integer v = coerceToInt(values.get(key));
                        if (v == null) v = defaultValue;
                        sb.append("<td class='val'>").append(v).append("</td>");
                    }
                }
            } else {
                Integer v = coerceToInt(values.get(code));
                if (v == null) v = defaultValue;
                sb.append("<td class='val'>").append(v).append("</td>");
            }

            sb.append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /* -------------------- PAYLOAD JSON -------------------- */

    public String buildPayloadOnly(String templateJson,
                                   Map<String, Object> flatValues,
                                   String remapJsonOptional) {
        try {
            JsonTemplate tpl = MAPPER.readValue(templateJson, JsonTemplate.class);

            RemapConfig remap = null;
            if (remapJsonOptional != null && !remapJsonOptional.trim().isEmpty()) {
                remap = MAPPER.readValue(remapJsonOptional, RemapConfig.class);
            }

            ObjectNode payloadNode = buildPayloadNode(
                    tpl,
                    flatValues == null ? Collections.<String, Object>emptyMap() : flatValues,
                    remap
            );

            return MAPPER.writeValueAsString(payloadNode);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build payload JSON from template", e);
        }
    }

    private ObjectNode buildPayloadNode(JsonTemplate tpl, Map<String, Object> values, RemapConfig remap) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode json = MAPPER.createObjectNode();
        root.set("json", json);

        ArrayNode dataValues = MAPPER.createArrayNode();

        if (tpl == null || tpl.mapping == null || tpl.mapping.groups == null) {
            json.set("dataValues", dataValues);
            return root;
        }

        int defaultValue = (tpl.mapping.defaultValue == null) ? 0 : tpl.mapping.defaultValue;

        for (Group g : tpl.mapping.groups) {
            if (g == null) continue;
            if (g.indicatorCodes == null) continue;
            if (g.keyPattern == null || g.keyPattern.trim().length() == 0) g.keyPattern = "{code}_{age}_{sex}";

            List<DimItem> ages = dimsFor(tpl, g, "age", "age");
            List<DimItem> sexes = dimsFor(tpl, g, "sex", "sex");

            for (String code : g.indicatorCodes) {
                for (DimItem age : ages) {
                    for (DimItem sex : sexes) {

                        String computedKey = buildKey(g.keyPattern, code, age.id, sex.id);

                        Integer v = coerceToInt(values.get(computedKey));
                        if (v == null) v = defaultValue;

                        ObjectNode dv = MAPPER.createObjectNode();
                        dv.put("value", v);

                        Map<String, String> mapped = (remap == null)
                                ? Collections.<String, String>emptyMap()
                                : remap.apply(computedKey);

                        String dataElement = mapped.containsKey("dataElement")
                                ? mapped.get("dataElement")
                                : computedKey;

                        dv.put("dataElement", dataElement);

                        if (mapped.containsKey("categoryOptionCombo")) {
                            dv.put("categoryOptionCombo", mapped.get("categoryOptionCombo"));
                        }
                        if (mapped.containsKey("attributeOptionCombo")) {
                            dv.put("attributeOptionCombo", mapped.get("attributeOptionCombo"));
                        }

                        dataValues.add(dv);
                    }
                }
            }
        }

        json.set(tpl.mapping.arrayName == null ? "dataValues" : tpl.mapping.arrayName, dataValues);
        return root;
    }

    /* -------------------- GROUP DIM RESOLUTION -------------------- */

    private List<DimItem> dimsFor(JsonTemplate tpl, Group g, String kind, String defaultName) {
        String dimName = defaultName;

        if (g != null && g.dims != null) {
            String configured = g.dims.get(kind);
            if (configured != null && configured.trim().length() > 0) {
                dimName = configured.trim();
            }
        }
        return safeDim(tpl, dimName);
    }

    private List<DimItem> safeDim(JsonTemplate tpl, String name) {
        if (tpl == null || tpl.dimensions == null) return Collections.emptyList();
        List<DimItem> d = tpl.dimensions.get(name);
        return d == null ? Collections.<DimItem>emptyList() : d;
    }

    private String buildKey(String pattern, String code, String age, String sex) {
        return pattern.replace("{code}", code).replace("{age}", age).replace("{sex}", sex);
    }

    private Integer coerceToInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).intValue();
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (Exception ignore) {
            return null;
        }
    }

    /* -------------------- POJOs -------------------- */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonTemplate {
        public int version;
        public Map<String, List<DimItem>> dimensions;
        public Mapping mapping;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DimItem {
        public String id;
        public String label;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mapping {
        public String arrayName = "dataValues";
        public Integer defaultValue = 0;
        public List<Group> groups = new ArrayList<Group>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Group {
        public String title;
        public List<String> indicatorCodes = new ArrayList<String>();
        public String keyPattern;

        // {"age":"age_opd","sex":"sex"} etc.
        public Map<String, String> dims = new HashMap<String, String>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RemapConfig {
        public int version;
        public List<Rule> rules = new ArrayList<Rule>();
        public Map<String, String> defaults = new HashMap<String, String>();

        public Map<String, String> apply(String computedKey) {
            Map<String, String> out = new HashMap<String, String>(defaults);
            for (Rule r : rules) {
                if (r == null || r.match == null) continue;
                boolean ok = false;
                if (r.match.key != null && r.match.key.equals(computedKey)) ok = true;
                else if (r.match.prefix != null && computedKey.startsWith(r.match.prefix)) ok = true;
                if (ok && r.set != null) out.putAll(r.set);
            }
            return out;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {
        public Match match;
        public Map<String, String> set;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Match {
        public String key;
        public String prefix;
    }
}
