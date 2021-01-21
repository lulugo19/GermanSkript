package germanskript

import java.io.File

open class PipelineKomponente(protected val startDatei: File)

interface IInterpretierer {
  fun interpretiere()
}