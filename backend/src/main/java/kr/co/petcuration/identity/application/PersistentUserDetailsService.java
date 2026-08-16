package kr.co.petcuration.identity.application;

import java.util.Locale;
import kr.co.petcuration.identity.infrastructure.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public PersistentUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        return userRepository.findByNormalizedEmail(normalizedEmail)
                .map(PetUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
    }
}
