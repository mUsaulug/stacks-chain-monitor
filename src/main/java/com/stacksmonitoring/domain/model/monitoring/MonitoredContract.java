package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a smart contract being monitored by a user.
 * Users can monitor multiple contracts and create alert rules for them.
 */
@Entity
@Table(name = "monitored_contract",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_contract", columnNames = {"user_id", "contract_identifier"})
    },
    indexes = {
        @Index(name = "idx_monitored_contract_identifier", columnList = "contract_identifier"),
        @Index(name = "idx_monitored_contract_user", columnList = "user_id"),
        @Index(name = "idx_monitored_contract_active", columnList = "is_active")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonitoredContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "contract_identifier", nullable = false, length = 150)
    private String contractIdentifier;

    @Column(name = "contract_name", length = 100)
    private String contractName;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "monitoredContract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertRule> alertRules = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Business key based equals using user and contractIdentifier.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonitoredContract)) return false;
        MonitoredContract that = (MonitoredContract) o;
        return user != null && user.equals(that.user)
            && contractIdentifier != null && contractIdentifier.equals(that.contractIdentifier);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Convenience method to add an alert rule.
     */
    public void addAlertRule(AlertRule rule) {
        alertRules.add(rule);
        rule.setMonitoredContract(this);
    }

    /**
     * Activate this monitored contract.
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Deactivate this monitored contract.
     */
    public void deactivate() {
        this.isActive = false;
    }
}
