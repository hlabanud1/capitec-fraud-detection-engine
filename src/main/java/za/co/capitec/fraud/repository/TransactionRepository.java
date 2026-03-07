package za.co.capitec.fraud.repository;

import za.co.capitec.fraud.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionId(String transactionId);

    @Query("SELECT t FROM Transaction t WHERE t.customerId = :customerId " +
            "AND t.timestamp BETWEEN :startTime AND :endTime")
    List<Transaction> findByCustomerIdAndTimestampBetween(
            @Param("customerId") String customerId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.customerId = :customerId " +
            "AND t.timestamp >= :since")
    long countByCustomerIdSince(
            @Param("customerId") String customerId,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.customerId = :customerId AND t.timestamp >= :since")
    BigDecimal sumAmountByCustomerIdSince(
            @Param("customerId") String customerId,
            @Param("since") LocalDateTime since
    );

}
