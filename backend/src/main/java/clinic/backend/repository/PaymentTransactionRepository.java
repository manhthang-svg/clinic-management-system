
package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByProviderOrderCode(
            Long providerOrderCode
    );
}
