package util.stats

import scalaz.Show

object StatsErrors {

  sealed trait Error
  case object EmptyStream extends Error
  case object SingleElementStream extends Error
  case object NoReplacement extends Error
  case object PercentileError extends Error
  case object MedianAbsoluteDeviationError extends Error

  implicit val showError: Show[Error] = new Show[Error] {
    override def shows(e: Error) = e match {
      case EmptyStream => "descriptive statistics of empty streams can't be calculated"
      case SingleElementStream => "variance and standard deviation can only be calculated on streams larger than 1"
      case NoReplacement => "replacement is not possible on an empty stream"
      case PercentileError => "cannot return percentiles if the requested percentiles are too fine grained for the data"
      case MedianAbsoluteDeviationError => "failed to calculate Median Absolute Deviation"
    }
  }
}
