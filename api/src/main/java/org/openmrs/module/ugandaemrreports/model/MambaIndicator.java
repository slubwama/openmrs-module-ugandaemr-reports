package org.openmrs.module.ugandaemrreports.model;

import org.openmrs.BaseOpenmrsMetadata;

import javax.persistence.*;

/**
 * Core Mamba Indicator model.
 *
 * This single entity supports:
 *
 *  - BASE      (SQL-based indicator definition)
 *  - COMPOSITE (logical composition of other indicators)
 *  - FINAL     (disaggregated/tabular output definition)
 *
 * All type-specific configuration is stored in configJson.
 *
 * Independent of OpenMRS Reporting module.
 */
@Entity
@Table(name = "mamba_indicator",
        indexes = {@Index(name = "idx_mamba_indicator_uuid", columnList = "uuid"), @Index(name = "idx_mamba_indicator_kind", columnList = "kind"), @Index(name = "idx_mamba_indicator_code", columnList = "code")}
)
public class MambaIndicator extends BaseOpenmrsMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mamba_indicator_id")
    private Integer id;

    /**
     * Optional business code
     * Example: HMIS105_OPD_001
     */
    @Column(name = "code", unique = true, length = 100)
    private String code;

    /**
     * BASE | COMPOSITE | FINAL
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    /**
     * Default return type when evaluated.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_value_type", nullable = false, length = 20)
    private ValueType defaultValueType = ValueType.NUMBER;

    /**
     * Kind-specific JSON configuration.
     *
     * BASE:
     * {
     *   "base": {
     *     "sqlMode": "COUNT" | "PATIENT_SET",
     *     "sqlTemplate": "...",
     *     "denominatorSqlTemplate": "...",
     *     "parameters": [...]
     *   }
     * }
     *
     * COMPOSITE:
     * {
     *   "composite": {
     *     "operator": "AND" | "OR" | "A_AND_NOT_B",
     *     "aUuid": "...",
     *     "bUuid": "..."
     *   }
     * }
     *
     * FINAL:
     * {
     *   "final": {
     *     "sourceUuid": "...",
     *     "ageCategoryCode": "...",
     *     "dimensions": ["age","sex"],
     *     "columns": [...]
     *   }
     * }
     */
    @Lob
    @Column(name = "config_json", nullable = false)
    private String configJson;

    /**
     * Free-form metadata (tags, theme, UI hints, warehouse mapping info, etc.)
     */
    @Lob
    @Column(name = "meta_json")
    private String metaJson;

    // -------------------------------------------------
    // BaseOpenmrsMetadata requires getId()/setId()
    // -------------------------------------------------

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    // -------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public ValueType getDefaultValueType() {
        return defaultValueType;
    }

    public void setDefaultValueType(ValueType defaultValueType) {
        this.defaultValueType = defaultValueType;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getMetaJson() {
        return metaJson;
    }

    public void setMetaJson(String metaJson) {
        this.metaJson = metaJson;
    }

    // =====================================================
    // =============== INNER ENUM DEFINITIONS ==============
    // =====================================================

    /**
     * Indicator structural type.
     */
    public enum Kind {
        BASE,
        COMPOSITE,
        FINAL
    }

    /**
     * Evaluation return type.
     */
    public enum ValueType {
        NUMBER,
        TABLE,
        PATIENT_SET
    }

    /**
     * Used inside BASE config.
     */
    public enum SqlMode {
        COUNT,
        PATIENT_SET
    }

    /**
     * Used inside COMPOSITE config.
     */
    public enum CompositeOperator {
        AND,
        OR,
        A_AND_NOT_B
    }
}