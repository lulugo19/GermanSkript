package germanskript.util

import java.util.*

class Peekable<T>(val iterator: Iterator<T>) {
  var index = 0
    private set
  private val queue = LinkedList<T>()

  fun next(): T? = when {
    !queue.isEmpty() -> queue.remove()
    iterator.hasNext() -> iterator.next()
    else -> null
  }.also { index++ }

  fun peek(ahead: Int = 0): T? {
    return if (ahead < queue.size) {
      queue[ahead]
    } else {
      while (queue.size <= ahead && iterator.hasNext()) {
        queue.add(iterator.next())
      }
      if (ahead < queue.size) queue[ahead] else null
    }
  }
}