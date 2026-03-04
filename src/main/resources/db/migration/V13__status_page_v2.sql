ALTER TABLE status_pages ADD COLUMN brand_name VARCHAR(120);
ALTER TABLE status_pages ADD COLUMN brand_theme VARCHAR(32);
ALTER TABLE status_pages ADD COLUMN brand_logo_url VARCHAR(1024);
ALTER TABLE status_pages ADD COLUMN brand_custom_header VARCHAR(240);
ALTER TABLE status_pages ADD COLUMN brand_custom_footer VARCHAR(500);

CREATE TABLE status_page_component_groups (
    id UUID PRIMARY KEY,
    status_page_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_status_page_component_groups_page FOREIGN KEY (status_page_id) REFERENCES status_pages(id) ON DELETE CASCADE
);

CREATE INDEX idx_status_page_component_groups_page_order ON status_page_component_groups(status_page_id, display_order);

ALTER TABLE status_page_monitors ADD COLUMN component_group_id UUID;
ALTER TABLE status_page_monitors ADD CONSTRAINT fk_status_page_monitors_group FOREIGN KEY (component_group_id) REFERENCES status_page_component_groups(id) ON DELETE SET NULL;

CREATE INDEX idx_status_page_monitors_component_group ON status_page_monitors(component_group_id);

CREATE TABLE status_page_maintenance_announcements (
    id UUID PRIMARY KEY,
    status_page_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    publish_at TIMESTAMP WITH TIME ZONE NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    is_public BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_status_page_maint_ann_page FOREIGN KEY (status_page_id) REFERENCES status_pages(id) ON DELETE CASCADE
);

CREATE INDEX idx_status_page_maint_ann_page_publish ON status_page_maintenance_announcements(status_page_id, publish_at);
