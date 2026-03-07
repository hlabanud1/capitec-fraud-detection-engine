package za.co.capitec.fraud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.fraud.domain.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

}
