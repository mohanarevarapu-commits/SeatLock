package com.seatlock.security;

import com.seatlock.model.Account;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in buyer as Spring Security sees them. Only the account id is relied on after sign-in;
 * profile details are always read fresh from the database.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_BUYER"));

    private final Long id;
    private final String email;
    private String passwordHash;

    public AccountPrincipal(Account account) {
        this.id = account.getId();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
    }

    /** The account id of a possibly anonymous caller; null for a guest. */
    public static Long idOf(AccountPrincipal principal) {
        return principal == null ? null : principal.id;
    }

    public Long id() {
        return id;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    /** Drops the password hash once sign-in has checked it, so it is never kept in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
