package io.openpulsechecker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_pages")
public class StatusPageEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(nullable = false)
    private boolean isPublic;

    @Column(length = 120)
    private String brandName;

    @Column(length = 32)
    private String brandTheme;

    @Column(length = 1024)
    private String brandLogoUrl;

    @Column(length = 240)
    private String brandCustomHeader;

    @Column(length = 500)
    private String brandCustomFooter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getBrandTheme() { return brandTheme; }
    public void setBrandTheme(String brandTheme) { this.brandTheme = brandTheme; }
    public String getBrandLogoUrl() { return brandLogoUrl; }
    public void setBrandLogoUrl(String brandLogoUrl) { this.brandLogoUrl = brandLogoUrl; }
    public String getBrandCustomHeader() { return brandCustomHeader; }
    public void setBrandCustomHeader(String brandCustomHeader) { this.brandCustomHeader = brandCustomHeader; }
    public String getBrandCustomFooter() { return brandCustomFooter; }
    public void setBrandCustomFooter(String brandCustomFooter) { this.brandCustomFooter = brandCustomFooter; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
