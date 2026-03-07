package za.co.capitec.fraud.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents a fraud alert triggered by the rule engine.
 */
@Entity
@Table(name = "fraud_alerts", indexes = {
        @Index(name = "idx_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_severity", columnList = "severity"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String ruleName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FraudSeverity severity;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private Integer riskScore;

    @Column(nullable = false, length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
