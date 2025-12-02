package pt.isec.server.services.question;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.model.question.Answer;

import java.util.List;

public interface IAnswerService {

    /* ===================== RESPOSTAS ===================== */

    boolean submitAnswer(SubmitAnswerDTO dto) throws Exception;
    List<Answer> viewAnswers(ViewAnswersDTO dto) throws Exception;

    /* ===================== HISTÓRICO ===================== */

    List<Answer> getStudentHistory(Integer studentId) throws Exception;
}
