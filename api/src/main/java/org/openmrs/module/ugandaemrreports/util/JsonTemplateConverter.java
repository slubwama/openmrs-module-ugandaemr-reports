package org.openmrs.module.ugandaemrreports.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * JsonTemplateConverter (upgraded)
 *
 * Supports BOTH:
 *  - Legacy templates: mapping.groups[].indicatorCodes (flat list)
 *  - New templates:    mapping.groups[].indicatorTree (nested groups + leaf indicators)
 *
 * Also supports:
 *  - group-specific dimensions via: mapping.groups[].dims (e.g {"age":"age","sex":"sex","severity":"severity"})
 *  - optional explicit dimension order via: mapping.groups[].dimsOrder (e.g ["age","sex","severity"])
 *
 * HTML:
 *  - renders indicatorTree with indentation
 *  - renders group nodes as section rows (not data rows)
 *
 * Payload:
 *  - emits ONLY leaf indicators (actual data elements), never group nodes
 */
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
                .append("h2{margin:0 0 10px 0;font-size:16px;}")
                .append("h3{margin:16px 0 6px 0;font-size:14px;}")
                .append("table{border-collapse:collapse;width:100%;margin-bottom:18px;}")
                .append("th,td{border:1px solid #ddd;padding:6px;font-size:12px;}")
                .append("th{background:#f6f6f6;text-align:center;}")
                .append("td.code{font-weight:bold;white-space:nowrap;}")
                .append("td.val{text-align:right;}")
                .append("tr.node-group td{background:#fafafa;font-weight:bold;}")
                .append("td.indent-0{padding-left:6px;}")
                .append("td.indent-1{padding-left:18px;}")
                .append("td.indent-2{padding-left:32px;}")
                .append("td.indent-3{padding-left:46px;}")
                .append("td.indent-4{padding-left:60px;}")
                .append("</style></head><body>");

        if (tpl == null || tpl.mapping == null || tpl.mapping.groups == null) {
            sb.append("<div>No groups defined in template.</div>");
            sb.append("</body></html>");
            return sb.toString();
        }

        if (tpl.mapping.title != null && tpl.mapping.title.trim().length() > 0) {
            sb.append("<h2>").append(esc(tpl.mapping.title)).append("</h2>");
        }

        for (Group g : tpl.mapping.groups) {
            sb.append(renderGroupTable(tpl, g, values));
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String renderGroupTable(JsonTemplate tpl, Group g, Map<String, Object> values) {
        if (g == null) return "";

        if (g.keyPattern == null || g.keyPattern.trim().length() == 0) {
            // keep old default for backwards compatibility
            g.keyPattern = "{code}_{age}_{sex}";
        }

        final int defaultValue = (tpl.mapping != null && tpl.mapping.defaultValue != null) ? tpl.mapping.defaultValue : 0;

        // Resolve dimensions and combinations
        List<String> dimKeys = resolveDimKeys(g);
        List<DimSpec> dimSpecs = resolveDimSpecs(tpl, g, dimKeys);
        List<DimCombo> combos = buildDimCombos(dimSpecs);

        // Detect classic age+sex (so we keep 2-row header)
        boolean classicAgeSex =
                dimSpecs.size() == 2
                        && "age".equals(dimSpecs.get(0).key)
                        && "sex".equals(dimSpecs.get(1).key);

        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(esc(g.title)).append("</h3>");
        sb.append("<table>");

        // ---------------- Header ----------------
        sb.append("<thead>");

        if (classicAgeSex) {
            List<DimItem> ages = dimSpecs.get(0).items;
            List<DimItem> sexes = dimSpecs.get(1).items;

            sb.append("<tr>");
            sb.append("<th rowspan='2'>Indicator</th>");
            for (DimItem age : ages) {
                sb.append("<th colspan='").append(sexes.size()).append("'>")
                        .append(esc(age.label)).append("</th>");
            }
            sb.append("</tr>");

            sb.append("<tr>");
            for (int i = 0; i < ages.size(); i++) {
                for (DimItem sex : sexes) {
                    sb.append("<th>").append(esc(sex.label)).append("</th>");
                }
            }
            sb.append("</tr>");
        } else if (!combos.isEmpty()) {
            sb.append("<tr>");
            sb.append("<th>Indicator</th>");
            for (DimCombo c : combos) {
                sb.append("<th>").append(esc(c.label)).append("</th>");
            }
            sb.append("</tr>");
        } else {
            sb.append("<tr><th>Indicator</th><th>Value</th></tr>");
        }

        sb.append("</thead>");

        // ---------------- Body ----------------
        sb.append("<tbody>");

        // New template: indicatorTree
        if (g.indicatorTree != null && !g.indicatorTree.isEmpty()) {
            for (IndicatorNode node : g.indicatorTree) {
                renderIndicatorNodeRows(sb, g, node, 0, combos, values, defaultValue);
            }
        } else {
            // Legacy template: indicatorCodes
            List<String> codes = (g.indicatorCodes == null) ? Collections.<String>emptyList() : g.indicatorCodes;
            for (String code : codes) {
                IndicatorNode leaf = new IndicatorNode();
                leaf.code = code;
                leaf.label = code;
                renderLeafRow(sb, g, leaf, 0, combos, values, defaultValue);
            }
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private void renderIndicatorNodeRows(StringBuilder sb,
                                         Group g,
                                         IndicatorNode node,
                                         int depth,
                                         List<DimCombo> combos,
                                         Map<String, Object> values,
                                         int defaultValue) {

        if (node == null) return;

        if (node.isGroup()) {
            int colSpan = 1 + Math.max(1, combos.size());
            String title = node.label != null ? node.label : node.code;

            sb.append("<tr class='node-group'>");
            sb.append("<td colspan='").append(colSpan).append("' class='indent-")
                    .append(Math.min(depth, 4)).append("'>")
                    .append(esc(title))
                    .append("</td>");
            sb.append("</tr>");

            if (node.children != null) {
                for (IndicatorNode child : node.children) {
                    renderIndicatorNodeRows(sb, g, child, depth + 1, combos, values, defaultValue);
                }
            }
        } else {
            renderLeafRow(sb, g, node, depth, combos, values, defaultValue);
        }
    }

    private void renderLeafRow(StringBuilder sb,
                               Group g,
                               IndicatorNode leaf,
                               int depth,
                               List<DimCombo> combos,
                               Map<String, Object> values,
                               int defaultValue) {

        String display = leaf.label != null ? leaf.label : leaf.code;

        sb.append("<tr>");
        sb.append("<td class='code indent-").append(Math.min(depth, 4)).append("'>")
                .append(esc(display)).append("</td>");

        if (!combos.isEmpty()) {
            for (DimCombo c : combos) {
                String key = buildKey(g.keyPattern, leaf.code, c.dimIdsByKey);
                Integer v = coerceToInt(values.get(key));
                if (v == null) v = defaultValue;
                sb.append("<td class='val'>").append(v).append("</td>");
            }
        } else {
            Integer v = coerceToInt(values.get(leaf.code));
            if (v == null) v = defaultValue;
            sb.append("<td class='val'>").append(v).append("</td>");
        }

        sb.append("</tr>");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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

            if (g.keyPattern == null || g.keyPattern.trim().length() == 0) {
                g.keyPattern = "{code}_{age}_{sex}";
            }

            // Resolve dimensions and combinations
            List<String> dimKeys = resolveDimKeys(g);
            List<DimSpec> dimSpecs = resolveDimSpecs(tpl, g, dimKeys);
            List<DimCombo> combos = buildDimCombos(dimSpecs);

            // Leaf indicators only (ignore group nodes)
            List<IndicatorNode> leafIndicators = getLeafIndicators(g);

            for (IndicatorNode leaf : leafIndicators) {
                if (leaf == null || leaf.code == null || leaf.code.trim().length() == 0) continue;

                if (!combos.isEmpty()) {
                    for (DimCombo c : combos) {
                        String computedKey = buildKey(g.keyPattern, leaf.code, c.dimIdsByKey);

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
                } else {
                    // no dims: code is key
                    Integer v = coerceToInt(values.get(leaf.code));
                    if (v == null) v = defaultValue;

                    ObjectNode dv = MAPPER.createObjectNode();
                    dv.put("value", v);

                    Map<String, String> mapped = (remap == null)
                            ? Collections.<String, String>emptyMap()
                            : remap.apply(leaf.code);

                    String dataElement = mapped.containsKey("dataElement")
                            ? mapped.get("dataElement")
                            : leaf.code;

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

        json.set(tpl.mapping.arrayName == null ? "dataValues" : tpl.mapping.arrayName, dataValues);
        return root;
    }

    /* -------------------- TREE + DIM HELPERS -------------------- */

    private List<IndicatorNode> getLeafIndicators(Group g) {
        List<IndicatorNode> out = new ArrayList<IndicatorNode>();

        if (g != null && g.indicatorTree != null && !g.indicatorTree.isEmpty()) {
            for (IndicatorNode n : g.indicatorTree) {
                collectLeafIndicators(n, out);
            }
            return out;
        }

        if (g != null && g.indicatorCodes != null) {
            for (String c : g.indicatorCodes) {
                IndicatorNode leaf = new IndicatorNode();
                leaf.code = c;
                leaf.label = c;
                out.add(leaf);
            }
        }

        return out;
    }

    private void collectLeafIndicators(IndicatorNode node, List<IndicatorNode> out) {
        if (node == null) return;
        if (node.isGroup()) {
            if (node.children != null) {
                for (IndicatorNode ch : node.children) {
                    collectLeafIndicators(ch, out);
                }
            }
        } else {
            out.add(node);
        }
    }

    private List<String> resolveDimKeys(Group g) {
        if (g != null && g.dimsOrder != null && !g.dimsOrder.isEmpty()) {
            return new ArrayList<String>(g.dimsOrder);
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<String>();
        ordered.add("age");
        ordered.add("sex");

        if (g != null && g.dims != null) {
            for (String k : g.dims.keySet()) {
                if (k == null) continue;
                String kk = k.trim();
                if (kk.length() == 0) continue;
                ordered.add(kk);
            }
        }

        return new ArrayList<String>(ordered);
    }

    private List<DimSpec> resolveDimSpecs(JsonTemplate tpl, Group g, List<String> dimKeys) {
        List<DimSpec> specs = new ArrayList<DimSpec>();
        if (dimKeys == null) return specs;

        for (String dimKey : dimKeys) {
            if (dimKey == null || dimKey.trim().length() == 0) continue;

            String dimName = dimKey;

            if (g != null && g.dims != null) {
                String configured = g.dims.get(dimKey);
                if (configured != null && configured.trim().length() > 0) {
                    dimName = configured.trim();
                }
            }

            List<DimItem> items = safeDim(tpl, dimName);
            if (items == null || items.isEmpty()) {
                // if dimension missing in template, skip it
                continue;
            }

            DimSpec spec = new DimSpec();
            spec.key = dimKey;
            spec.name = dimName;
            spec.items = items;
            specs.add(spec);
        }

        return specs;
    }

    private List<DimCombo> buildDimCombos(List<DimSpec> specs) {
        if (specs == null || specs.isEmpty()) return Collections.emptyList();

        List<DimCombo> combos = new ArrayList<DimCombo>();
        buildDimCombosRec(specs, 0, new LinkedHashMap<String, DimItem>(), combos);
        return combos;
    }

    private void buildDimCombosRec(List<DimSpec> specs,
                                   int idx,
                                   LinkedHashMap<String, DimItem> acc,
                                   List<DimCombo> out) {
        if (idx >= specs.size()) {
            DimCombo c = new DimCombo();
            c.dimIdsByKey = new LinkedHashMap<String, String>();
            StringBuilder label = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, DimItem> e : acc.entrySet()) {
                DimItem it = e.getValue();
                if (it == null) continue;

                c.dimIdsByKey.put(e.getKey(), it.id);

                if (!first) label.append(" / ");
                label.append(it.label != null ? it.label : it.id);
                first = false;
            }

            c.label = label.toString();
            out.add(c);
            return;
        }

        DimSpec spec = specs.get(idx);
        for (DimItem item : spec.items) {
            acc.put(spec.key, item);
            buildDimCombosRec(specs, idx + 1, acc, out);
        }
        acc.remove(spec.key);
    }

    private static class DimSpec {
        String key;      // e.g. "age"
        String name;     // e.g. "age" or "age_opd"
        List<DimItem> items;
    }

    private static class DimCombo {
        String label;                           // e.g. "0-28d / M"
        Map<String, String> dimIdsByKey;        // e.g. {"age":"0_28d","sex":"M"}
    }

    /* -------------------- Generic key building -------------------- */

    private String buildKey(String pattern, String code, Map<String, String> dimIdsByKey) {
        String out = pattern;
        out = out.replace("{code}", code == null ? "" : code);

        if (dimIdsByKey != null) {
            for (Map.Entry<String, String> e : dimIdsByKey.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null) continue;
                out = out.replace("{" + k + "}", v == null ? "" : v);
            }

            // Backwards compatibility: templates that still use {age}/{sex}
            if (dimIdsByKey.containsKey("age")) out = out.replace("{age}", dimIdsByKey.get("age"));
            if (dimIdsByKey.containsKey("sex")) out = out.replace("{sex}", dimIdsByKey.get("sex"));
        }

        return out;
    }

    private List<DimItem> safeDim(JsonTemplate tpl, String name) {
        if (tpl == null || tpl.dimensions == null) return Collections.emptyList();
        List<DimItem> d = tpl.dimensions.get(name);
        return d == null ? Collections.<DimItem>emptyList() : d;
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
        public String title;                 // optional (new templates)
        public String arrayName = "dataValues";
        public Integer defaultValue = 0;
        public List<Group> groups = new ArrayList<Group>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Group {
        public String title;
        public String keyPattern;

        // Legacy flat indicators
        public List<String> indicatorCodes = new ArrayList<String>();

        // New nested indicator tree (supports group nodes like EP01 Malaria)
        public List<IndicatorNode> indicatorTree = new ArrayList<IndicatorNode>();

        // {"age":"age_opd","sex":"sex"} etc.
        public Map<String, String> dims = new LinkedHashMap<String, String>();

        // Optional explicit order of dimensions
        public List<String> dimsOrder = new ArrayList<String>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndicatorNode {
        public String label;               // e.g. "EP01. Malaria"
        public String code;                // e.g. "EP01a"
        public String type;                // "group" (optional)
        public List<IndicatorNode> children = new ArrayList<IndicatorNode>();

        public boolean isGroup() {
            if (type != null && "group".equalsIgnoreCase(type)) return true;
            return children != null && !children.isEmpty();
        }
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
