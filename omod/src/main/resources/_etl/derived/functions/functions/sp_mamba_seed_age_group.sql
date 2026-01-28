-- Drop old procedure (kept name, new behavior)
DROP PROCEDURE IF EXISTS sp_mamba_seed_age_group;

DELIMITER //

CREATE PROCEDURE sp_mamba_seed_age_group()
BEGIN
    /*
      Seeds:
        - mamba_dim_age_category (once)
        - mamba_dim_age_group (age bands in DAYS) for selected categories
      Notes:
        - This procedure is idempotent-ish: it uses NOT EXISTS checks to avoid duplicates.
        - Age caps use 30000 days (~82 years). Adjust if you want higher.
        - This seeds the categories + MOH 105 OPD Diagnoses (Section 1A) bands.
          You can extend with other categories similarly.
    */

    -- -----------------------------
    -- 1) Seed Age Categories
    -- -----------------------------
    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'MOH_105_OPD_DIAG') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('MOH_105_OPD_DIAG', 'MOH 105 OPD Diagnoses Age Groups',
                'Age/Gender disaggregation for OPD diagnoses in MOH 105 (Section 1A)', 'v1', TRUE);
END IF;

    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'MOH_105_NUTRITION') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('MOH_105_NUTRITION', 'MOH 105 Nutrition Age Groups',
                'Nutrition services age/gender disaggregation in MOH 105', 'v1', TRUE);
END IF;

    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'MOH_ANC') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('MOH_ANC', 'MOH ANC / MCH Age Groups',
                'ANC, maternity, postnatal and FP age disaggregation', 'v1', TRUE);
END IF;

    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'GBV') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('GBV', 'GBV Services Age Groups',
                'GBV services age/gender disaggregation', 'v1', TRUE);
END IF;

    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'HTS') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('HTS', 'HIV Testing Services Age Groups',
                'HTS age/gender disaggregation', 'v1', TRUE);
END IF;

    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'SMC') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('SMC', 'Safe Male Circumcision Age Groups',
                'SMC age/gender disaggregation', 'v1', TRUE);
END IF;

    IF NOT EXISTS (SELECT 1 FROM mamba_dim_age_category WHERE code = 'HEPATITIS') THEN
        INSERT INTO mamba_dim_age_category (code, name, description, version, is_active)
        VALUES ('HEPATITIS', 'Hepatitis Services Age Groups',
                'Hepatitis age/gender disaggregation', 'v1', TRUE);
END IF;

    -- -----------------------------
    -- 2) Seed Age Groups for MOH_105_OPD_DIAG (Section 1A)
    -- -----------------------------
BEGIN
        DECLARE v_cat_id INT;

SELECT age_category_id
INTO v_cat_id
FROM mamba_dim_age_category
WHERE code = 'MOH_105_OPD_DIAG'
    LIMIT 1;

-- 0–28 days
IF NOT EXISTS (
            SELECT 1 FROM mamba_dim_age_group
             WHERE age_category_id = v_cat_id AND code = 'D0_28'
        ) THEN
            INSERT INTO mamba_dim_age_group
                (age_category_id, code, label, min_age_days, max_age_days, sort_order, is_active)
            VALUES
                (v_cat_id, 'D0_28', '0–28 days', 0, 28, 1, TRUE);
END IF;

        -- 29 days – 4 yrs
        IF NOT EXISTS (
            SELECT 1 FROM mamba_dim_age_group
             WHERE age_category_id = v_cat_id AND code = 'D29_4Y'
        ) THEN
            INSERT INTO mamba_dim_age_group
                (age_category_id, code, label, min_age_days, max_age_days, sort_order, is_active)
            VALUES
                (v_cat_id, 'D29_4Y', '29 days – 4 yrs', 29, 1824, 2, TRUE);
END IF;

        -- 5–9 yrs
        IF NOT EXISTS (
            SELECT 1 FROM mamba_dim_age_group
             WHERE age_category_id = v_cat_id AND code = 'Y5_9'
        ) THEN
            INSERT INTO mamba_dim_age_group
                (age_category_id, code, label, min_age_days, max_age_days, sort_order, is_active)
            VALUES
                (v_cat_id, 'Y5_9', '5–9 yrs', 1825, 3649, 3, TRUE);
END IF;

        -- 10–19 yrs
        IF NOT EXISTS (
            SELECT 1 FROM mamba_dim_age_group
             WHERE age_category_id = v_cat_id AND code = 'Y10_19'
        ) THEN
            INSERT INTO mamba_dim_age_group
                (age_category_id, code, label, min_age_days, max_age_days, sort_order, is_active)
            VALUES
                (v_cat_id, 'Y10_19', '10–19 yrs', 3650, 7304, 4, TRUE);
END IF;

        -- 20 yrs & above
        IF NOT EXISTS (
            SELECT 1 FROM mamba_dim_age_group
             WHERE age_category_id = v_cat_id AND code = 'Y20P'
        ) THEN
            INSERT INTO mamba_dim_age_group
                (age_category_id, code, label, min_age_days, max_age_days, sort_order, is_active)
            VALUES
                (v_cat_id, 'Y20P', '20 yrs & above', 7305, 30000, 5, TRUE);
END IF;
END;

    -- ✅ Extend seeding for other categories here in the same style
    -- e.g., MOH_105_NUTRITION, GBV, HTS, SMC, HEPATITIS, MOH_ANC

END //

DELIMITER ;