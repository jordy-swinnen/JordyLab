-- Spring Modulith's JDBC event publication registry (JpaEventPublication). Lives in
-- `public`, not a JordyLab module schema, since it's framework bookkeeping shared
-- across all modules rather than domain data owned by one of them.
CREATE TABLE event_publication (
    id                uuid NOT NULL,
    listener_id       character varying(512) NOT NULL,
    event_type        character varying(512) NOT NULL,
    serialized_event  text NOT NULL,
    publication_date  timestamp without time zone NOT NULL,
    completion_date   timestamp without time zone,
    CONSTRAINT event_publication_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);
CREATE INDEX idx_event_publication_serialized_event ON event_publication (serialized_event);
