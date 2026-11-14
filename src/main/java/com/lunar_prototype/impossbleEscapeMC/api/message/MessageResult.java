package com.lunar_prototype.impossbleEscapeMC.api.message;

import java.util.UUID;
public record MessageResult(UUID sendId, Status status) {
    public enum Status { DISPATCHED, CANCELLED, OFFLINE, FAILED, STOPPED, RECURSION_LIMIT }
}
