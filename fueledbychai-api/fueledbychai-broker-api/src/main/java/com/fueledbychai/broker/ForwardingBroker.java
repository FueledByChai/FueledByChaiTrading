package com.fueledbychai.broker;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
public class ForwardingBroker implements IBroker {

    @Delegate(types = IBroker.class)
    @Getter(AccessLevel.PROTECTED) // gives you protected delegate() accessor
    private final IBroker delegate;
}
