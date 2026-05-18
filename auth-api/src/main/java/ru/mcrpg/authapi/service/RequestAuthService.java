package ru.mcrpg.authapi.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.mcrpg.authapi.domain.entity.AccountEntity;
import ru.mcrpg.authapi.domain.repository.AccountRepository;
import ru.mcrpg.authapi.web.error.ApiException;

@Service
public class RequestAuthService {

    private final JwtService jwtService;
    private final AccountRepository accountRepository;

    public RequestAuthService(JwtService jwtService, AccountRepository accountRepository) {
        this.jwtService = jwtService;
        this.accountRepository = accountRepository;
    }

    public AccountEntity authenticate(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        UUID accountId = jwtService.parseAccessToken(token);
        AccountEntity account = accountRepository.findById(accountId)
            .orElseThrow(() -> ApiException.unauthorized("invalid_token", "Аккаунт для access token не найден."));
        if (!"active".equalsIgnoreCase(account.getStatus())) {
            throw ApiException.forbidden("account_inactive", "Аккаунт отключен.");
        }
        return account;
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw ApiException.unauthorized("missing_token", "Нужен авторизационный token Bearer.");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw ApiException.unauthorized("missing_token", "Нужен авторизационный token Bearer.");
        }
        return token;
    }
}
