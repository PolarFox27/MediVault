package ssd.medivault.data;

import java.util.List;
import java.util.Optional;

import com.yubico.webauthn.data.ByteArray;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.AppUser;
import ssd.medivault.entities.Authenticator;

@Repository
public interface AuthenticatorRepository extends CrudRepository<Authenticator, Long> {
    Optional<Authenticator> findByCredentialId(ByteArray credentialId);
    List<Authenticator> findAllByUser (AppUser user);
    List<Authenticator> findAllByCredentialId(ByteArray credentialId);
}
