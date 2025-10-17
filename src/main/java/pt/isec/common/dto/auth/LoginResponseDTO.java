package pt.isec.common.dto.auth;

public record LoginResponseDTO(Integer sessionId, Integer userId, String userType,
                                String name, String email) {}
