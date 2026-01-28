-- $BEGIN
CREATE TABLE mamba_dim_age_category (
                                        age_category_id   INT AUTO_INCREMENT PRIMARY KEY,
                                        code              VARCHAR(50)  NOT NULL UNIQUE,
                                        name              VARCHAR(100) NOT NULL,
                                        description       TEXT,
                                        version           VARCHAR(20)  DEFAULT 'v1',
                                        effective_from    DATE         DEFAULT CURRENT_DATE,
                                        effective_to      DATE,
                                        is_active         BOOLEAN      DEFAULT TRUE,
                                        created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) CHARSET = UTF8MB4;

CREATE TABLE mamba_dim_age_group (
                                     age_group_id     INT AUTO_INCREMENT PRIMARY KEY,
                                     age_category_id  INT NOT NULL,
                                     code             VARCHAR(50),
                                     label            VARCHAR(100) NOT NULL,
                                     min_age_days     INT NOT NULL,
                                     max_age_days     INT NOT NULL,
                                     sort_order       INT NOT NULL,
                                     is_active        BOOLEAN DEFAULT TRUE,
                                     created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_mamba_age_category
                                         FOREIGN KEY (age_category_id)
                                             REFERENCES mamba_dim_age_category (age_category_id),

                                     CONSTRAINT chk_mamba_age_range
                                         CHECK (min_age_days <= max_age_days)
) CHARSET = UTF8MB4;
-- $END

