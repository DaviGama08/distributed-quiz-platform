package pt.isec.common.dto.answer;

import pt.isec.server.model.question.OptionLetter;

import java.io.Serializable;

public record SubmitAnswerDTO(
        Integer questionId,
        Integer studentId,
        OptionLetter selectedOption
) implements Serializable {}

