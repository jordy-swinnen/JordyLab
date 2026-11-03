-- The native Android app is replaced by the installable web app (spec 018): its release-hosting module is gone, so
-- its schema (one table, created by V20260928004) and the two notification events it consumed are dropped with it.
DROP TABLE IF EXISTS mobile.mobile_release;
DROP SCHEMA IF EXISTS mobile CASCADE;

-- Spring Modulith's publication registry (public.event_publication) still holds rows for the two events whose classes
-- were deleted with the listener. An incomplete row would try to deserialize a class that no longer exists, so both
-- complete and incomplete rows for these event types go.
DELETE FROM event_publication
WHERE event_type LIKE '%UserSignUpPending'
   OR event_type LIKE '%BriefingReady';
