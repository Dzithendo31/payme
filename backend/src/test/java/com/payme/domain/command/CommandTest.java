package com.payme.domain.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    record TestCommand(String commandId) implements Command {}

    @Test
    void command_recordImplementation_exposesCommandId() {
        Command cmd = new TestCommand("cmd-1");
        assertEquals("cmd-1", cmd.commandId());
    }

    @Test
    void command_isInstanceCheck() {
        Command cmd = new TestCommand("cmd-2");
        assertInstanceOf(Command.class, cmd);
    }

    @Test
    void command_twoCommandsWithSameId_areEqual() {
        TestCommand a = new TestCommand("cmd-1");
        TestCommand b = new TestCommand("cmd-1");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void command_differentIds_areNotEqual() {
        TestCommand a = new TestCommand("cmd-1");
        TestCommand b = new TestCommand("cmd-2");

        assertNotEquals(a, b);
    }
}
