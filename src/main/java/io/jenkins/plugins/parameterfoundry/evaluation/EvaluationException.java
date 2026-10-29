package io.jenkins.plugins.parameterfoundry.evaluation;

import java.io.Serial;

public class EvaluationException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final String parameter;

    public EvaluationException(ErrorCode code, String parameter, String message) {
        super(message);
        this.code = code;
        this.parameter = parameter;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getParameter() {
        return parameter;
    }

    public String getStableCode() {
        return "PARAMETER_FOUNDRY_" + code.name();
    }

    public String getSafeMessage() {
        return getStableCode() + ": " + getMessage();
    }
}
