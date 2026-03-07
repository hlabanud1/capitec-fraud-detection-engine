package za.co.capitec.fraud.repository;

import za.co.capitec.fraud.domain.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

}
