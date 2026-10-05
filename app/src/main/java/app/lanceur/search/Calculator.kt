package app.lanceur.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow

/**
 * Petite calculatrice à descente récursive (aucun `eval`).
 * Grammaire : expr = terme (('+'|'-') terme)* ; terme = unaire (('*'|'/'|'%') unaire)* ;
 * unaire = ('-'|'+') unaire | puissance ; puissance = primaire ('^' unaire)? ; primaire = nombre | '(' expr ')'.
 */
object Calculator {
    sealed interface Outcome {
        data class Value(val text: String) : Outcome
        data object Undefined : Outcome
    }

    private const val MAX_LENGTH = 200
    private const val MAX_DEPTH = 20

    fun evaluate(input: String): Outcome? {
        val source = input.replace('×', '*').replace('÷', '/').replace(',', '.').filterNot { it.isWhitespace() }
        if (source.isEmpty() || source.length > MAX_LENGTH) return null
        val parser = Parser(source)
        val value = try {
            parser.parseAll()
        } catch (e: ParseError) {
            return null
        }
        if (!parser.sawOperator) return null
        if (value.isNaN() || value.isInfinite()) return Outcome.Undefined
        return Outcome.Value(format(value))
    }

    /** 10 chiffres significatifs, sans zéros inutiles, virgule décimale. */
    fun format(value: Double): String =
        BigDecimal(value).round(MathContext(10)).stripTrailingZeros().toPlainString().replace('.', ',')

    private class ParseError : Exception()

    private class Parser(private val s: String) {
        private var pos = 0
        private var depth = 0
        var sawOperator = false
            private set

        fun parseAll(): Double {
            val v = expr()
            if (pos != s.length) throw ParseError()
            return v
        }

        private fun peek(): Char? = s.getOrNull(pos)

        private fun eat(c: Char): Boolean {
            if (peek() != c) return false
            pos++
            return true
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> { sawOperator = true; v + term() }
                    eat('-') -> { sawOperator = true; v - term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    eat('*') -> { sawOperator = true; v * unary() }
                    eat('/') -> { sawOperator = true; v / unary() }
                    eat('%') -> { sawOperator = true; v % unary() }
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> power()
        }

        private fun power(): Double {
            val base = primary()
            if (!eat('^')) return base
            sawOperator = true
            return base.pow(unary())
        }

        private fun primary(): Double {
            if (eat('(')) {
                if (++depth > MAX_DEPTH) throw ParseError()
                val v = expr()
                if (!eat(')')) throw ParseError()
                depth--
                return v
            }
            val start = pos
            while (peek()?.let { it in '0'..'9' || it == '.' } == true) pos++
            val number = s.substring(start, pos)
            if (number.isEmpty() || number == "." || number.count { it == '.' } > 1) throw ParseError()
            return number.toDouble()
        }
    }
}
