package pt.isec.common.dto.auth;

public record ChangePasswordDTO(Integer sessionId, String oldPassword,
                                String newPassword) {}
