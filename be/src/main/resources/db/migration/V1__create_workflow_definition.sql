-- Workflow definition: stored graph (nodes as JSON) and metadata
CREATE TABLE workflow_definition (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    entry_node_id VARCHAR(255) NOT NULL,
    graph_json  CLOB NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_workflow_definition_updated_at ON workflow_definition (updated_at);
