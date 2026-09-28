SET search_path TO finance;

-- 007: a disabled feed to hold admin-submitted "Save to FNA" article URLs (spec FR-017).
-- Disabled so FeedIngestionService's scheduled RSS pull skips it; BriefingGeneratorService still
-- picks these articles up — it selects by published_at, never by feed.enabled.
INSERT INTO feed (name, url, enabled) VALUES
    ('Manual Submissions', 'manual://submissions', false)
ON CONFLICT (url) DO NOTHING;
