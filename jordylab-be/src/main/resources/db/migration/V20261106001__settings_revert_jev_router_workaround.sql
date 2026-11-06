CREATE SCHEMA IF NOT EXISTS settings;
SET search_path TO settings;

-- BUG-075 chose `typesafe/jev-router` for gamecatalog.chat.query as a workaround for the invalid-JSON reply. BUG-076
-- fixed the underlying schema mismatch, and JOR-72 measured LibBot answers at 5 to 18 seconds with that router. Revert
-- only that known workaround so the code default (Claude Haiku) applies again; every other saved model choice stays.
DELETE FROM settings.ai_feature_model_setting WHERE model_id = 'typesafe/jev-router';
