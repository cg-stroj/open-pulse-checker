package io.openpulsechecker.notificationpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notification_route_rules")
public class NotificationRouteRuleEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID policyId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationSeverity severity;
    @Column(nullable = false)
    private boolean webhookEnabled;
    @Column(nullable = false)
    private boolean emailEnabled;
    @Column(nullable = false)
    private boolean telegramEnabled;
    @Column(nullable = false)
    private boolean slackEnabled;
    @Column(nullable = false)
    private boolean discordEnabled;
    @Column(nullable = false)
    private boolean teamsEnabled;

    @PrePersist
    void prePersist() { if (id == null) { id = UUID.randomUUID(); } }

    public Set<NotificationChannel> toChannels() {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (webhookEnabled) channels.add(NotificationChannel.WEBHOOK);
        if (emailEnabled) channels.add(NotificationChannel.EMAIL);
        if (telegramEnabled) channels.add(NotificationChannel.TELEGRAM);
        if (slackEnabled) channels.add(NotificationChannel.SLACK);
        if (discordEnabled) channels.add(NotificationChannel.DISCORD);
        if (teamsEnabled) channels.add(NotificationChannel.TEAMS);
        return channels;
    }

    public void setChannels(Set<NotificationChannel> channels) {
        webhookEnabled = channels.contains(NotificationChannel.WEBHOOK);
        emailEnabled = channels.contains(NotificationChannel.EMAIL);
        telegramEnabled = channels.contains(NotificationChannel.TELEGRAM);
        slackEnabled = channels.contains(NotificationChannel.SLACK);
        discordEnabled = channels.contains(NotificationChannel.DISCORD);
        teamsEnabled = channels.contains(NotificationChannel.TEAMS);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public NotificationSeverity getSeverity() { return severity; }
    public void setSeverity(NotificationSeverity severity) { this.severity = severity; }
    public boolean isWebhookEnabled() { return webhookEnabled; }
    public void setWebhookEnabled(boolean webhookEnabled) { this.webhookEnabled = webhookEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isTelegramEnabled() { return telegramEnabled; }
    public void setTelegramEnabled(boolean telegramEnabled) { this.telegramEnabled = telegramEnabled; }
    public boolean isSlackEnabled() { return slackEnabled; }
    public void setSlackEnabled(boolean slackEnabled) { this.slackEnabled = slackEnabled; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public void setDiscordEnabled(boolean discordEnabled) { this.discordEnabled = discordEnabled; }
    public boolean isTeamsEnabled() { return teamsEnabled; }
    public void setTeamsEnabled(boolean teamsEnabled) { this.teamsEnabled = teamsEnabled; }
}
