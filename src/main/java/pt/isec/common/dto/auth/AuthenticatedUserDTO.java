package pt.isec.common.dto.auth;

public record AuthenticatedUserDTO(Integer id, String name, String email,
                                   String userType){}
