SET @sql = (SELECT IF(
    (SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.COLUMNS WHERE
          table_schema=DATABASE() 
          AND table_name='CourseClass' 
          AND column_name='accountName'
    ) > 0,
    "SELECT 0",
    "alter table ContentRepository
      add column accountName varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      add column accountKey varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
      add column container varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    "
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
