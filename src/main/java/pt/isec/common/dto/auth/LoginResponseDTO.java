package pt.isec.common.dto.auth;

import java.io.Serializable;

public record LoginResponseDTO(String sessionId, String userId, String userType,
                               String name, String email)implements Serializable {}
