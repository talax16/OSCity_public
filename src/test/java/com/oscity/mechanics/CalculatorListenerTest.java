package com.oscity.mechanics;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorListenerTest {

    @Test
    void parsesValidHex() {
        assertEquals(142, parseInput("0x8E"));
    }

    @Test
    void parsesPrefixedBinary() {
        assertEquals(10, parseInput("0b1010"));
    }

    @Test
    void parsesBareBinary() {
        assertEquals(10, parseInput("1010"));
    }

    @Test
    void parsesBareHex() {
        assertEquals(322, parseInput("142"));
    }

    @Test
    void parsesValidDivisionExpression() {
        assertEquals(14, parseInput("0xE0/0x10"));
    }

    @Test
    void handlesWhitespace() {
        assertEquals(14, parseInput("  0xE0 / 0x10  "));
    }

    @Test
    void emptyInputFails() {
        assertThrows(NumberFormatException.class, () -> parseInput(""));
        assertThrows(NumberFormatException.class, () -> parseInput("   "));
    }

    @Test
    void invalidFormatFails() {
        assertThrows(NumberFormatException.class, () -> parseInput("0xZZ"));
        assertThrows(NumberFormatException.class, () -> parseInput("hello"));
        assertThrows(NumberFormatException.class, () -> parseInput("0xE/0x10/0x2"));
    }

    @Test
    void divisionByZeroFails() {
        assertThrows(NumberFormatException.class, () -> parseInput("0xE/0"));
    }

    @Test
    void onlyFirstLineOfFirstPageIsUsed() {
        String input = firstLineOfFirstPage(List.of(
            "  0x8E  \n0xFF",
            "0xAB"
        ));

        assertEquals("0x8E", input);
        assertEquals(142, parseInput(input));
    }

    @Test
    void emptyFirstPageFails() {
        assertThrows(NumberFormatException.class, () -> firstLineOfFirstPage(List.of()));
        assertThrows(NumberFormatException.class, () -> firstLineOfFirstPage(List.of("")));
    }

    @Test
    void leadingBlankLinesAreIgnored() {
        String input = firstLineOfFirstPage(List.of("   \n0x8E"));

        assertEquals("0x8E", input);
        assertEquals(142, parseInput(input));
    }

    private static long parseInput(String input) {
        try {
            Method method = CalculatorListener.class.getDeclaredMethod("parseInput", String.class);
            method.setAccessible(true);
            return (long) method.invoke(newCalculatorListenerWithoutConstructor(), input);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NumberFormatException numberFormatException) {
                throw numberFormatException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static CalculatorListener newCalculatorListenerWithoutConstructor() throws ReflectiveOperationException {
        java.lang.reflect.Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        return (CalculatorListener) unsafe.allocateInstance(CalculatorListener.class);
    }

    private static String firstLineOfFirstPage(List<String> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new NumberFormatException("Empty book");
        }

        String input = pages.get(0).trim().split("\n")[0].trim();
        if (input.isEmpty()) {
            throw new NumberFormatException("Empty first line");
        }
        return input;
    }
}
