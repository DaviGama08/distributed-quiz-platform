package pt.isec.common.dto.auth;

public record AuthenticatedUserDTO(String id, String name, String email,
                                   String userType){}
