CREATE TABLE status_pages (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    is_public BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_status_pages_public ON status_pages(is_public);

CREATE TABLE status_page_monitors (
    status_page_id UUID NOT NULL,
    monitor_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (status_page_id, monitor_id),
    CONSTRAINT fk_status_page_monitors_page FOREIGN KEY (status_page_id) REFERENCES status_pages(id) ON DELETE CASCADE,
    CONSTRAINT fk_status_page_monitors_monitor FOREIGN KEY (monitor_id) REFERENCES monitors(id) ON DELETE CASCADE
);

CREATE INDEX idx_status_page_monitors_page_order ON status_page_monitors(status_page_id, display_order);
CREATE INDEX idx_status_page_monitors_monitor ON status_page_monitors(monitor_id);
