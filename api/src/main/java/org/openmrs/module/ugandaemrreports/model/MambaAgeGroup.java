package org.openmrs.module.ugandaemrreports.model;

import javax.persistence.*;

/**
 * Maps to: mamba_dim_age_group
 */
@Entity
@Table(
        name = "mamba_dim_age_group",
        indexes = {
                @Index(name = "idx_mamba_age_group_category", columnList = "age_category_id"),
                @Index(name = "idx_mamba_age_group_active", columnList = "is_active"),
                @Index(name = "idx_mamba_age_group_sort", columnList = "sort_order")
        }
)
public class MambaAgeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "age_group_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "age_category_id", nullable = false)
    private MambaAgeCategory ageCategory;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "label", length = 100, nullable = false)
    private String label;

    @Column(name = "min_age_days", nullable = false)
    private Integer minAgeDays;

    @Column(name = "max_age_days", nullable = false)
    private Integer maxAgeDays;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    // Getters/Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public MambaAgeCategory getAgeCategory() {
        return ageCategory;
    }

    public void setAgeCategory(MambaAgeCategory ageCategory) {
        this.ageCategory = ageCategory;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getMinAgeDays() {
        return minAgeDays;
    }

    public void setMinAgeDays(Integer minAgeDays) {
        this.minAgeDays = minAgeDays;
    }

    public Integer getMaxAgeDays() {
        return maxAgeDays;
    }

    public void setMaxAgeDays(Integer maxAgeDays) {
        this.maxAgeDays = maxAgeDays;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}