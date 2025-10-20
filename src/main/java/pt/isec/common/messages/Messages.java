package pt.isec.common.messages;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class Messages <T extends Serializable> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private MessageType msgType;
    private T data;

    public Messages() {}

    public Messages(MessageType msgType, T data) {
        this.msgType = msgType;
        this.data = data;
    }

    public MessageType getMsgType() {
        return msgType;
    }

    public void setMsgType(MessageType msgType) {
        this.msgType = msgType;
    }

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
        Messages<?> messages = (Messages<?>) o;
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
