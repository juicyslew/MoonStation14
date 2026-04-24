package com.juicyslew.moonstation14.util.interfaces;

public interface IMS14Attachment<A, C> extends IMS14Codeced<A> {
    C toComponent();
}
