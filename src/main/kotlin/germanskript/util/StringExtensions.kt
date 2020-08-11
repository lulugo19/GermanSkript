package germanskript.util

fun Char.istVokal() = when(this) {
  'a', 'e', 'i', 'o', 'u' -> true
  'A', 'E', 'I', 'O', 'U' -> true
  else -> false
}