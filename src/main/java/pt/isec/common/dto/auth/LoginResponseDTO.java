package pt.isec.common.dto.auth;

public record LoginResponseDTO(String sessionId, String userId, String userType,
                                String name, String email) {}
