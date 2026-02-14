-- load_data.sql  —  Alternative data loading via LOAD DATA INFILE
-- Requires MySQL FILE privilege and secure_file_priv configuration.
-- Prefer using scripts/load_data.py for easier loading.

USE grad_project;

-- If CSV is accessible via MySQL's secure_file_priv directory:
-- LOAD DATA INFILE '/path/to/data.csv'
-- INTO TABLE interactions
-- FIELDS TERMINATED BY ',' ENCLOSED BY '"'
-- LINES TERMINATED BY '\n'
-- IGNORE 1 ROWS
-- (stu_id, time, course_index);

-- For most setups, use the Python script instead:
-- cd scripts && python load_data.py
