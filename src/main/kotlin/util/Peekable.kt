package util

class Peekable<T>(val iterator: Iterator<T>) {
  var index = 0
  var lookahead: T? = null
  var lookaheadDouble: T? = null

  fun next(): T? = when {
    lookahead != null -> lookahead.also {
      lookahead = lookaheadDouble
      lookaheadDouble = null
    }
    iterator.hasNext() -> iterator.next()
    else -> null
  }.also { index++ }

  fun peek(): T? {
    if (lookahead != null) {
       return lookahead
    }
    lookahead = when {
      iterator.hasNext() -> iterator.next()
      else -> null
    }
    return lookahead
  }

  fun peekDouble(): T? {
    if (lookaheadDouble != null)
        return lookaheadDouble
    peek()
    lookaheadDouble = when {
      iterator.hasNext() -> iterator.next()
      else -> null
    }
    return lookaheadDouble
  }
}