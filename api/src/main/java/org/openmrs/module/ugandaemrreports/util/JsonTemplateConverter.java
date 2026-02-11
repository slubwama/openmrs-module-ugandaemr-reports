package org.openmrs.module.ugandaemrreports.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * JSON template -> (1) FINAL HTML (iframe-ready) + (2) payload JSON for DHIS2-like posting.
 *
 * Supports:
 * - Flat indicators via "indicatorCodes"
 * - Grouped/hierarchical indicators via "indicatorTree"
 *   (group headings are rendered in HTML but NOT emitted as payload dataValues)
 *
 * Dimensions:
 * - Uses group.dims map (e.g. {"age":"age","sex":"sex","severity":"severity"})
 * - Generates column combinations across all resolved dims
 * - Supports key patterns like "{code}_{age}_{sex}" (and extra placeholders if present)
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
                .append(".sectionTitle{margin:18px 0 8px 0;font-size:14px;font-weight:bold;}")
                .append("table{border-collapse:collapse;width:100%;margin-bottom:22px;}")
                .append("th,td{border:1px solid #ddd;padding:6px;font-size:12px;}")
                .append("th{background:#f6f6f6;text-align:center;}")
                .append("td.code{font-weight:bold;white-space:nowrap;}")
                .append("td.label{white-space:nowrap;}")
                .append("td.val{text-align:right;}")
                .append("tr.groupRow td{background:#fafafa;font-weight:bold;}")
                .append("tr.groupRow td{border-top:2px solid #e6e6e6;}")
                .append(".indent{display:inline-block;}")
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
        if (g.dims == null) g.dims = new HashMap<String, String>();

        int defaultValue = (tpl.mapping != null && tpl.mapping.defaultValue != null) ? tpl.mapping.defaultValue : 0;

        // Resolve dimensions for this group (supports >2 dims)
        List<ResolvedDim> dims = resolveDimsForGroup(tpl, g);

        // Build column combinations (cartesian product across all dims)
        List<DimCombo> combos = buildCombos(dims);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='sectionTitle'>").append(esc(g.title)).append("</div>");
        sb.append("<table>");

        // ---- Header: multi-row if multiple dimensions ----
        sb.append("<thead>");
        if (dims.isEmpty()) {
            sb.append("<tr><th>Indicator</th><th>Value</th></tr>");
        } else {
            // Indicator column spans all dim header rows
            sb.append("<tr>");
            sb.append("<th rowspan='").append(dims.size()).append("'>Indicator</th>");
            appendHeaderCellsRecursive(sb, dims, 0);
            sb.append("</tr>");

            // Remaining header rows (if dims.size() > 1)
            for (int row = 1; row < dims.size(); row++) {
                sb.append("<tr>");
                appendHeaderCellsRecursive(sb, dims, row);
                sb.append("</tr>");
            }
        }
        sb.append("</thead>");

        // ---- Body ----
        sb.append("<tbody>");

        // Prefer indicatorTree (for grouping/order). Fallback to indicatorCodes.
        if (g.indicatorTree != null && !g.indicatorTree.isEmpty()) {
            List<TreeRow> rows = flattenTree(g.indicatorTree, 0);

            for (TreeRow r : rows) {
                if (r.isGroup) {
                    // group heading row
                    int colspan = 1 + (dims.isEmpty() ? 1 : combos.size());
                    sb.append("<tr class='groupRow'>");
                    sb.append("<td colspan='").append(colspan).append("'>")
                            .append(indentSpan(r.depth))
                            .append(esc(r.displayText))
                            .append("</td>");
                    sb.append("</tr>");
                } else {
                    // leaf indicator row
                    sb.append("<tr>");
                    sb.append("<td class='code'>")
                            .append(indentSpan(r.depth))
                            .append(esc(r.displayText))
                            .append("</td>");

                    if (dims.isEmpty()) {
                        Integer v = coerceToInt(values.get(r.code));
                        if (v == null) v = defaultValue;
                        sb.append("<td class='val'>").append(v).append("</td>");
                    } else {
                        for (DimCombo combo : combos) {
                            String key = buildKeyFlexible(g.keyPattern, r.code, combo.placeholders);
                            Integer v = coerceToInt(values.get(key));
                            if (v == null) v = defaultValue;
                            sb.append("<td class='val'>").append(v).append("</td>");
                        }
                    }

                    sb.append("</tr>");
                }
            }

        } else {
            // No tree: render from indicatorCodes (flat)
            for (String code : g.indicatorCodes) {
                sb.append("<tr>");
                sb.append("<td class='code'>").append(esc(code)).append("</td>");

                if (dims.isEmpty()) {
                    Integer v = coerceToInt(values.get(code));
                    if (v == null) v = defaultValue;
                    sb.append("<td class='val'>").append(v).append("</td>");
                } else {
                    for (DimCombo combo : combos) {
                        String key = buildKeyFlexible(g.keyPattern, code, combo.placeholders);
                        Integer v = coerceToInt(values.get(key));
                        if (v == null) v = defaultValue;
                        sb.append("<td class='val'>").append(v).append("</td>");
                    }
                }

                sb.append("</tr>");
            }
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    /**
     * Recursive header generator for multi-dim columns.
     * Example for Age x Sex:
     *   Row0: Age cells colspanning sex count
     *   Row1: Sex cells repeated for each age
     */
    private void appendHeaderCellsRecursive(StringBuilder sb, List<ResolvedDim> dims, int headerRow) {
        // If we're at the row to render, emit that row's dimension cells with correct colspans.
        // Otherwise, emit blank cells? No: we rebuild each row independently by walking the structure.

        // Strategy:
        // For row k:
        // - Iterate all combos of dims[0..k-1] to know repetition count
        // - For each dim item in dims[k], colspan = product(size of dims[k+1..end])
        int repeat = 1;
        for (int i = 0; i < headerRow; i++) {
            repeat *= dims.get(i).items.size();
        }

        int colspan = 1;
        for (int i = headerRow + 1; i < dims.size(); i++) {
            colspan *= dims.get(i).items.size();
        }

        for (int r = 0; r < repeat; r++) {
            for (DimItem item : dims.get(headerRow).items) {
                sb.append("<th");
                if (colspan > 1) sb.append(" colspan='").append(colspan).append("'");
                sb.append(">").append(esc(item.label)).append("</th>");
            }
        }
    }

    private String indentSpan(int depth) {
        if (depth <= 0) return "";
        // 12px per depth
        return "<span class='indent' style='width:" + (depth * 12) + "px'></span>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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
            if (g.indicatorCodes == null) continue; // IMPORTANT: payload is leaf indicators only
            if (g.keyPattern == null || g.keyPattern.trim().length() == 0) g.keyPattern = "{code}_{age}_{sex}";
            if (g.dims == null) g.dims = new HashMap<String, String>();

            List<ResolvedDim> dims = resolveDimsForGroup(tpl, g);
            List<DimCombo> combos = buildCombos(dims);

            // If no dims exist, emit one value per code (rare)
            if (dims.isEmpty()) {
                for (String code : g.indicatorCodes) {
                    Integer v = coerceToInt(values.get(code));
                    if (v == null) v = defaultValue;

                    ObjectNode dv = MAPPER.createObjectNode();
                    dv.put("value", v);

                    Map<String, String> mapped = (remap == null)
                            ? Collections.<String, String>emptyMap()
                            : remap.apply(code);

                    String dataElement = mapped.containsKey("dataElement")
                            ? mapped.get("dataElement")
                            : code;

                    dv.put("dataElement", dataElement);

                    if (mapped.containsKey("categoryOptionCombo")) dv.put("categoryOptionCombo", mapped.get("categoryOptionCombo"));
                    if (mapped.containsKey("attributeOptionCombo")) dv.put("attributeOptionCombo", mapped.get("attributeOptionCombo"));

                    dataValues.add(dv);
                }
            } else {
                for (String code : g.indicatorCodes) {
                    for (DimCombo combo : combos) {
                        String computedKey = buildKeyFlexible(g.keyPattern, code, combo.placeholders);

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

                        if (mapped.containsKey("categoryOptionCombo")) dv.put("categoryOptionCombo", mapped.get("categoryOptionCombo"));
                        if (mapped.containsKey("attributeOptionCombo")) dv.put("attributeOptionCombo", mapped.get("attributeOptionCombo"));

                        dataValues.add(dv);
                    }
                }
            }
        }

        json.set(tpl.mapping.arrayName == null ? "dataValues" : tpl.mapping.arrayName, dataValues);
        return root;
    }

    /* -------------------- DIMENSIONS (supports >2) -------------------- */

    private static class ResolvedDim {
        final String kind;         // e.g. "age" or "sex" (placeholder name)
        final String dimName;      // template.dimensions key
        final List<DimItem> items;

        ResolvedDim(String kind, String dimName, List<DimItem> items) {
            this.kind = kind;
            this.dimName = dimName;
            this.items = items;
        }
    }

    private List<ResolvedDim> resolveDimsForGroup(JsonTemplate tpl, Group g) {
        // default is age + sex for backward compatibility
        // if g.dims contains more, include them as well
        LinkedHashMap<String, String> resolved = new LinkedHashMap<String, String>();

        // stable base order
        resolved.put("age", valueOrDefault(g.dims.get("age"), "age"));
        resolved.put("sex", valueOrDefault(g.dims.get("sex"), "sex"));

        // include any additional dims from template config
        if (g.dims != null) {
            for (Map.Entry<String, String> e : g.dims.entrySet()) {
                String kind = e.getKey();
                if (kind == null) continue;
                if ("age".equals(kind) || "sex".equals(kind)) continue;
                String dimName = valueOrDefault(e.getValue(), kind);
                resolved.put(kind, dimName);
            }
        }

        List<ResolvedDim> out = new ArrayList<ResolvedDim>();
        for (Map.Entry<String, String> e : resolved.entrySet()) {
            String kind = e.getKey();
            String dimName = e.getValue();
            List<DimItem> items = safeDim(tpl, dimName);
            if (items != null && !items.isEmpty()) {
                out.add(new ResolvedDim(kind, dimName, items));
            }
        }
        return out;
    }

    private String valueOrDefault(String v, String def) {
        if (v == null) return def;
        String t = v.trim();
        return t.length() == 0 ? def : t;
    }

    private List<DimItem> safeDim(JsonTemplate tpl, String name) {
        if (tpl == null || tpl.dimensions == null) return Collections.emptyList();
        List<DimItem> d = tpl.dimensions.get(name);
        return d == null ? Collections.<DimItem>emptyList() : d;
    }

    private static class DimCombo {
        final Map<String, String> placeholders; // kind -> dimItem.id

        DimCombo(Map<String, String> placeholders) {
            this.placeholders = placeholders;
        }
    }

    private List<DimCombo> buildCombos(List<ResolvedDim> dims) {
        if (dims == null || dims.isEmpty()) return Collections.emptyList();

        List<DimCombo> out = new ArrayList<DimCombo>();
        buildCombosRec(dims, 0, new LinkedHashMap<String, String>(), out);
        return out;
    }

    private void buildCombosRec(List<ResolvedDim> dims, int idx, Map<String, String> acc, List<DimCombo> out) {
        if (idx >= dims.size()) {
            out.add(new DimCombo(new LinkedHashMap<String, String>(acc)));
            return;
        }
        ResolvedDim d = dims.get(idx);
        for (DimItem it : d.items) {
            acc.put(d.kind, it == null ? "" : it.id);
            buildCombosRec(dims, idx + 1, acc, out);
        }
        acc.remove(d.kind);
    }

    /**
     * Replaces placeholders dynamically:
     * - Always replaces {code}
     * - Replaces {age},{sex}, and any {<kind>} in the combo map
     */
    private String buildKeyFlexible(String pattern, String code, Map<String, String> placeholders) {
        String key = pattern == null ? "{code}" : pattern;
        key = key.replace("{code}", code == null ? "" : code);

        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String kind = e.getKey();
                String val = e.getValue();
                if (kind == null) continue;
                key = key.replace("{" + kind + "}", val == null ? "" : val);
            }
        }

        // Backward compatibility if pattern contains age/sex but combo didn't include them
        if (key.contains("{age}")) key = key.replace("{age}", "");
        if (key.contains("{sex}")) key = key.replace("{sex}", "");

        return key;
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

    /* -------------------- indicatorTree support -------------------- */

    private static class TreeRow {
        final boolean isGroup;
        final int depth;
        final String code;
        final String displayText;

        TreeRow(boolean isGroup, int depth, String code, String displayText) {
            this.isGroup = isGroup;
            this.depth = depth;
            this.code = code;
            this.displayText = displayText;
        }
    }

    private List<TreeRow> flattenTree(List<IndicatorNode> nodes, int depth) {
        List<TreeRow> out = new ArrayList<TreeRow>();
        if (nodes == null) return out;

        for (IndicatorNode n : nodes) {
            if (n == null) continue;

            boolean isGroup = "group".equalsIgnoreCase(n.type) || (n.children != null && !n.children.isEmpty());

            String label = n.label;
            String code = n.code;

            String text;
            if (code != null && code.trim().length() > 0 && label != null && label.trim().length() > 0) {
                text = code + ". " + label;
            } else if (code != null && code.trim().length() > 0) {
                text = code;
            } else {
                text = label == null ? "" : label;
            }

            out.add(new TreeRow(isGroup, depth, code, text));

            if (n.children != null && !n.children.isEmpty()) {
                out.addAll(flattenTree(n.children, depth + 1));
            }
        }

        return out;
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

        // Leaf indicators only (payload uses this)
        public List<String> indicatorCodes = new ArrayList<String>();

        // NEW: hierarchical nodes (HTML rendering uses this for grouping/order)
        public List<IndicatorNode> indicatorTree = new ArrayList<IndicatorNode>();

        public String keyPattern;

        // {"age":"age","sex":"sex","severity":"severity"} etc.
        public Map<String, String> dims = new HashMap<String, String>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndicatorNode {
        public String code;
        public String label;

        // convention: "group" for group headings, null/empty for leaf
        public String type;

        public List<IndicatorNode> children = new ArrayList<IndicatorNode>();
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