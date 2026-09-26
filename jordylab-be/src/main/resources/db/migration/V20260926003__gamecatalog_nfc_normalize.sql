CREATE SCHEMA IF NOT EXISTS gamecatalog;
SET search_path TO gamecatalog;

-- 003: file names can be stored as NFC or NFD depending on the filesystem (macOS stores
-- NFD; Linux stores whatever was created). The scan client normalizes to NFC, so existing
-- rows are normalized too — but only after proving no two rows for the same source would
-- collapse into the same (source_id, external_ref) and violate the unique constraint.
DO $$
DECLARE collisions INTEGER;
BEGIN
    SELECT count(*) INTO collisions
    FROM (
        SELECT source_id, normalize(external_ref, nfc) AS ref
        FROM game
        GROUP BY source_id, normalize(external_ref, nfc)
        HAVING count(*) > 1
    ) c;

    IF collisions > 0 THEN
        RAISE EXCEPTION 'NFC normalization would collide % (source_id, external_ref) group(s); resolve them before applying this migration', collisions;
    END IF;
END $$;

UPDATE game
SET external_ref = normalize(external_ref, nfc),
    title = normalize(title, nfc)
WHERE external_ref <> normalize(external_ref, nfc)
   OR title <> normalize(title, nfc);
