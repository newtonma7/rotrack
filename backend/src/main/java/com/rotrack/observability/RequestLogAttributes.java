package com.rotrack.observability;

public final class RequestLogAttributes {

    public static final String ERROR_CODE = RequestLogAttributes.class.getName() + ".errorCode";
    public static final String EXCEPTION_TYPE = RequestLogAttributes.class.getName() + ".exceptionType";
    public static final String UNEXPECTED_SERVER_ERROR = "unexpected";

    private RequestLogAttributes() {
    }
}
