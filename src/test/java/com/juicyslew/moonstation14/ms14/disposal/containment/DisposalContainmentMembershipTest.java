package com.juicyslew.moonstation14.ms14.disposal.containment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalContainmentMembershipTest {
    @Test
    void bodyHasAtMostOneExactOwnerAndStaleOwnerCannotUnbind() {
        var membership = new DisposalContainmentMembership<Object>();
        var body = UUID.randomUUID();
        var first = new Object();
        var second = new Object();

        assertTrue(membership.bind(body, first));
        assertFalse(membership.bind(body, second));
        assertFalse(membership.unbind(body, second));
        assertSame(first, membership.get(body));
        assertTrue(membership.unbind(body, first));
        assertTrue(membership.bind(body, second));
        assertSame(second, membership.get(body));
    }
}
