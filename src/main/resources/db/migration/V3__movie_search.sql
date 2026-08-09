-- Backs the "normalized original title contains it" search rule: normalized the same way as
-- normalized_title (see TitleNormalizer), so `Taken 3` finds `96 Hours - Taken 3`. NULL until the
-- (not yet built) importer populates it from original_title, same as normalized_title today.
ALTER TABLE movie ADD COLUMN normalized_original_title TEXT;
