package pt.isec.common.messages;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class TcpMessage<T extends Serializable> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private MessageType msgType;      // Tipo da mensagem (REGISTER, LOGIN, etc.)
    private T data;                   // Dados que viajam na mensagem
    private Class<T> payloadType;     // Guarda o tipo real do objeto (ex: RegisterStudentDTO.class)

    public TcpMessage() {}

    public TcpMessage(MessageType msgType, T data) {
        this.msgType = msgType;
        this.data = data;
    }

    public TcpMessage(MessageType msgType, T data, Class<T> payloadType) {
        this.msgType = msgType;
        this.data = data;
        this.payloadType = payloadType;
    }

    public MessageType getType() {
        return msgType;
    }

    public void setMsgType(MessageType msgType) {
        this.msgType = msgType;
    }
    /**
     * O compilador apaga os tipos dos genéricos em Java, em Runtime, isso significa
     * que quando formos tentar aceder ao tipo de objeto no serviço do servidor, não vamos saber
     * o tipo da DTO.
     *
     * O que o metodo getDataAs faz:
     *
     * Recebe a classe esperada (ex: RegisterStudentDTO.class)
     * e faz o cast automaticamente, validando se o tipo é compatível.
     *
     * Se o tipo for incorreto, lança ClassCastException — igual a um cast normal,
     * mas de forma mais explícita e segura.
     */
    public <U> U getDataAs(Class<U> expected) {
        return expected.cast(data);
    }

    public Class<T> getPayloadType(){ return payloadType;}

    public void setPayloadType(Class<T> p){payloadType = p;}

    public T getData() {
        return data;
    } //A confirmar

    public void setData(T data) {
        this.data = data;
    } //A confirmar

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TcpMessage<?> messages = (TcpMessage<?>) o;
        return msgType == messages.msgType && Objects.equals(data, messages.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgType, data);
    }

    @Override
    public String toString() {
        return "Messages{" +
                "msgType=" + msgType +
                ", data=" + data +
                '}';
    }
}
