package util

import java.lang.StringBuilder

class SimpleLogger {

  private val lines = StringBuilder()

  fun addLine(line: String) {
    lines.append(line)
    lines.append('\n')
  }

  fun print() {
     print(string())
  }

  fun string(): String = lines.toString()
}