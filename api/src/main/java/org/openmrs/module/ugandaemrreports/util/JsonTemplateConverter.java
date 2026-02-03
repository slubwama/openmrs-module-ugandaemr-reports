package org.openmrs.module.ugandaemrreports.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

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
        String html = renderHtmlOnly(templateJson);
        String payload = buildPayloadOnly(templateJson, flatValues, remapJsonOptional);
        return new Result(html, payload);
    }

    public String renderHtmlOnly(String templateJson) {
        try {
            JsonTemplate tpl = MAPPER.readValue(templateJson, JsonTemplate.class);
            return renderHtml(tpl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render HTML from JSON template", e);
        }
    }

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

    /* ------------------ Payload building (node internal) ------------------ */

    private ObjectNode buildPayloadNode(JsonTemplate tpl, Map<String, Object> values, RemapConfig remap) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode json = MAPPER.createObjectNode();
        root.set("json", json);

        ArrayNode dataValues = MAPPER.createArrayNode();

        int defaultValue = (tpl.mapping == null || tpl.mapping.defaultValue == null) ? 0 : tpl.mapping.defaultValue;

        if (tpl.mapping == null || tpl.mapping.groups == null) {
            json.set("dataValues", dataValues);
            return root;
        }

        for (Group g : tpl.mapping.groups) {

            // NEW: resolve dimensions per group (fallback to "age"/"sex")
            List<DimItem> ages = dimsFor(tpl, g, "age", "age");
            List<DimItem> sexes = dimsFor(tpl, g, "sex", "sex");

            if (g == null || g.indicatorCodes == null || g.keyPattern == null) continue;

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

    /* ------------------ NEW: dimension resolution per group ------------------ */

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

    /* ------------------ Helpers (Java 8 safeDim) ------------------ */

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
        try { return (int) Math.round(Double.parseDouble(s)); }
        catch (Exception ignore) { return null; }
    }

    /* ------------------ POJOs ------------------ */

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

        // NEW: {"age":"age_opd","sex":"sex"} etc.
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

    /* ------------------ HTML generation ------------------ */
    // Keep your existing renderHtml(...) implementation.
    // OPTIONAL: to make HTML also respect group-specific dims, use dimsFor(tpl, g, ...) inside renderHtml per group.
    private String renderHtml(JsonTemplate tpl) {
        /* keep your existing implementation */
        return "";
    }
}
