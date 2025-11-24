package pt.isec.common.dto.auth;

import java.io.Serializable;

public record LoginRequestDTO(
        String email,
        String password
) implements Serializable {}
