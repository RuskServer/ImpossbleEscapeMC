package com.lunar_prototype.impossbleEscapeMC.core.di.exceptions;

public class DependencyResolutionException extends RuntimeException {
    public DependencyResolutionException(String message) { super(message); }
    public DependencyResolutionException(String message, Throwable cause) { super(message, cause); }
}
