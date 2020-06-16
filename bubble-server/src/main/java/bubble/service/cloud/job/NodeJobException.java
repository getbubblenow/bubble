/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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
