package app.lanceur.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun value(input: String) = (Calculator.evaluate(input) as? Calculator.Outcome.Value)?.text

    @Test
    fun basic_operations() {
        assertEquals("96", value("12*8"))
        assertEquals("3,5", value("7÷2"))
        assertEquals("12", value("3×4"))
        assertEquals("1", value("10%3"))
        assertEquals("1024", value("2^10"))
        assertEquals("2,5", value("1,5+1"))
        assertEquals("0,3", value("0.1+0.2"))
        assertEquals("0,3333333333", value("1/3"))
        assertEquals("14", value(" 2 + 3 * 4 "))
        assertEquals("9", value("(1+2)*3"))
    }

    @Test
    fun unary_minus_and_right_associative_power() {
        assertEquals("-4", value("-2^2"))
        assertEquals("0,5", value("2^-1"))
        assertEquals("8", value("5--3"))
        assertEquals("512", value("2^3^2"))
    }

    @Test
    fun undefined_results() {
        listOf("1/0", "0/0", "10%0", "9^999").forEach {
            assertEquals(it, Calculator.Outcome.Undefined, Calculator.evaluate(it))
        }
    }

    @Test
    fun not_a_calculation() {
        listOf("", "42", "-5", "(5)", "2(3)", "1+", "abc", "06 12 34 56 78", "1..2+1", "50%").forEach {
            assertNull(it, Calculator.evaluate(it))
        }
    }

    @Test
    fun pathological_inputs_never_throw() {
        assertNull(Calculator.evaluate("(".repeat(30) + "1+1" + ")".repeat(30)))
        assertNull(Calculator.evaluate("(".repeat(150) + "1" + ")".repeat(150)))
        assertNull(Calculator.evaluate("١+١"))
        assertEquals("2", value("-".repeat(150) + "1+1"))
        assertEquals(Calculator.Outcome.Value::class, Calculator.evaluate("9".repeat(150) + "*9")!!::class)
    }
}
