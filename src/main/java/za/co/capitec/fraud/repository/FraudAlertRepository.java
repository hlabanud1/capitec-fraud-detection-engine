package za.co.capitec.fraud.repository;

import za.co.capitec.fraud.domain.FraudAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    @Query("SELECT fa FROM FraudAlert fa JOIN FETCH fa.transaction WHERE fa.transaction.id = :transactionId")
    List<FraudAlert> findByTransaction_Id(@Param("transactionId") Long transactionId);

    @Query("SELECT fa FROM FraudAlert fa JOIN FETCH fa.transaction t " +
            "WHERE t.customerId = :customerId ORDER BY fa.createdAt DESC")
    List<FraudAlert> findByCustomerId(@Param("customerId") String customerId);

    @EntityGraph(attributePaths = {"transaction"})
    @Query(value = "SELECT fa FROM FraudAlert fa JOIN fa.transaction t " +
            "WHERE t.customerId = :customerId",
            countQuery = "SELECT COUNT(fa) FROM FraudAlert fa JOIN fa.transaction t WHERE t.customerId = :customerId")
    Page<FraudAlert> findByCustomerIdPaginated(@Param("customerId") String customerId, Pageable pageable);

    @Query("SELECT fa FROM FraudAlert fa JOIN FETCH fa.transaction WHERE fa.createdAt >= :since " +
            "ORDER BY fa.createdAt DESC")
    List<FraudAlert> findRecentAlerts(@Param("since") LocalDateTime since);

    @EntityGraph(attributePaths = {"transaction"})
    @Query(value = "SELECT fa FROM FraudAlert fa JOIN fa.transaction WHERE fa.createdAt >= :since",
            countQuery = "SELECT COUNT(fa) FROM FraudAlert fa WHERE fa.createdAt >= :since")
    Page<FraudAlert> findRecentAlertsPaginated(@Param("since") LocalDateTime since, Pageable pageable);
}
