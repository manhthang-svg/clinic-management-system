package clinic.backend.mapper;

import clinic.backend.dto.response.SessionResponse;
import clinic.backend.service.SessionTokens;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps issued session tokens to the login/refresh response. The refresh token is never mapped: it travels
 * only in the HttpOnly cookie.
 */
@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresAt", source = "accessTokenExpiresAt")
    SessionResponse toSessionResponse(SessionTokens tokens);

    SessionResponse.AccountSummaryResponse toAccountSummaryResponse(SessionTokens.AccountSummary account);
}
