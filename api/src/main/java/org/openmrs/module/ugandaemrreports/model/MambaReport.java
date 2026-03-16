package org.openmrs.module.ugandaemrreports.model;

import org.openmrs.BaseOpenmrsMetadata;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "mamba_report")
public class MambaReport extends BaseOpenmrsMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mamba_report_id")
    private Integer id;

    @Column(name = "code", length = 100)
    private String code;

    @Lob
    @Column(name = "config_json")
    private String configJson;

    @Lob
    @Column(name = "meta_json")
    private String metaJson;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getIdAsObject() {
        return getId();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getDisplay() {
        if (getName() != null && code != null && !code.trim().isEmpty()) {
            return getName() + " (" + code + ")";
        }
        return getName();
    }
}