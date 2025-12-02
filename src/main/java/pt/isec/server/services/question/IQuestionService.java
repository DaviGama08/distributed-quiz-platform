package pt.isec.server.services.question;

import pt.isec.common.dto.question.*;
import pt.isec.common.model.question.Question;

import java.util.List;

public interface IQuestionService {

    /* ===================== CRUD ===================== */

    CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) throws Exception;
    boolean editQuestion(EditQuestionDTO dto) throws Exception;
    boolean deleteQuestion(DeleteQuestionDTO dto) throws Exception;


    /* ===================== CONSULTA ===================== */

    List<Question> listQuestions(ListQuestionsDTO dto) throws Exception;
    Question joinQuestion(JoinQuestionDTO dto) throws Exception;
}
