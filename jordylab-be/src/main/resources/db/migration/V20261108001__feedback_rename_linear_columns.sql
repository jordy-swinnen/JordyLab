CREATE SCHEMA IF NOT EXISTS feedback;
SET search_path TO feedback;

-- 022: the backlog is Kaneo. The two columns that held a Linear issue now hold the backlog ticket. The values are not
-- rewritten here: rows accepted into Linear keep their Linear id and url until scripts/kaneo/relink-feedback.py
-- rewrites them to the Kaneo ticket, before Linear is cancelled (spec FR-027).
ALTER TABLE feedback_ticket RENAME COLUMN linear_issue_id TO backlog_ticket_id;
ALTER TABLE feedback_ticket RENAME COLUMN linear_issue_url TO backlog_ticket_url;
