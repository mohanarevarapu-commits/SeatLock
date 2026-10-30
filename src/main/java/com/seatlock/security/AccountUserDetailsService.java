package com.seatlock.security;

import com.seatlock.model.Account;
import com.seatlock.repository.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accountRepository.findByEmail(Account.normalizeEmail(email))
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
    }
}
