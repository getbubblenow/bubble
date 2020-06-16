package bubble.service.cloud.job;

import lombok.Getter;

public class NodeJobException extends RuntimeException {

    @Getter private String meterError;

    public NodeJobException(String meterError, String message, Exception e) {
        super(message, e);
        this.meterError = meterError;
    }

    public NodeJobException(String meterError, String message) {
        super(message);
        this.meterError = meterError;
    }

}
