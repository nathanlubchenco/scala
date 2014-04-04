package util.stats

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck._
import util.stats._
import util.stats.StatsErrors._
import scalaz._
import scalaz.syntax.either._
import Math._

class StatsSpec extends Specification with ScalaCheck {
  implicit val arbStream: Arbitrary[Stream[Double]] = Arbitrary(Gen.containerOf[Stream, Double](Gen.posNum[Double]))
  implicit val arbReservoirSize: Arbitrary[Int] = Arbitrary(Gen.posNum[Int])

  def withinErrorBounds(d1: Double, d2: Double, error: Double) = if (d1 > (d2 - error) && d1 < (d2 + error)) true else false
  def emptyStreamOrNoReplacement(error: Error) = if (error == EmptyStream || error == NoReplacement) true else false
  def emptyStreamOrPercentileError(error: Error) = if ( error == EmptyStream || error == PercentileError) true else false

  def percentileToIndex(percentile: Int, data: Stream[_]):Int = ((percentile / 100d) * (data.size)).floor.toInt

  "streamingMean" should {
    "return the correct mean" in check {
      (data: Stream[Double]) =>
        val streamedMean = StreamingStats.streamingMean(data)
        if (data.isEmpty) {
          streamedMean.fold(error => EmptyStream mustEqual error, _ => ???)
        }
        else {
          val mean = (data.foldLeft(0.0)(_ + _) / data.size)
          streamedMean.fold(error => EmptyStream mustEqual error, result => withinErrorBounds(result.d, mean, 0.001d) must beTrue)
        }
    }
  }

  "streamingVariance" should {
    "return the correct variance" in check {
      (data: Stream[Double]) =>
        val streamVariance = StreamingStats.streamingVariance(data)
        if (data.size < 2) {
          streamVariance.fold(error => SingleElementStream mustEqual error, _ => ???)
        }
        else {
          val n = data.size
          val mean = data.foldLeft(0.0)(_ + _) / n
          //val variance = for {
          //  x <- data
          //} yield Variance(pow((x - mean), 2) / (n - 1))
          val sumOfSquaredDifferences = data.map(x => pow((x - mean), 2))
          val variance = sumOfSquaredDifferences.foldLeft(0.0)(_ + _) / (n - 1)

          streamVariance.fold(error => EmptyStream mustEqual error, result => withinErrorBounds(result.d, variance, 0.001d) must beTrue)
        }
    }
  }

  "streamingStandardDeviation" should {
    "return the correct standard deviation" in check {
      (data: Stream[Double]) =>
        val streamStandardDeviation = StreamingStats.streamingStandardDeviation(data)
        if (data.size < 2) {
          streamStandardDeviation.fold(error => SingleElementStream mustEqual error, _ => ???)
        }
        else {
          val n = data.size
          val mean = data.foldLeft(0.0)(_ + _) / n
          //val variance = for {
          //  x <- data
          //} yield Variance(pow((x - mean), 2) / (n - 1))
          val sumOfSquaredDifferences = data.map(x => pow((x - mean), 2))
          val standardDeviation = sqrt(sumOfSquaredDifferences.foldLeft(0.0)(_ + _) / (n - 1))

          streamStandardDeviation.fold(error => EmptyStream mustEqual error, result => withinErrorBounds(result.d, standardDeviation, 0.001d) must beTrue)
        }
    }
  }

  "medianApproximation" should {
    "return a 'reasonable' value - note that the current error threshold is very permissive" in check {
      //checking streams of 1000+ elements would enable testing a much more appropriate error threshold
      (data: Stream[Double]) =>
          val approxMedian = StreamingStats.medianApproximation(data, 3, 10)
          if (data.isEmpty) {
            approxMedian.fold(error => emptyStreamOrNoReplacement(error) must beTrue, _ => ???)
          } else if (data.size == 1) {
            approxMedian.fold(error => emptyStreamOrNoReplacement(error) must beTrue, result => withinErrorBounds(result.d, data(0), 0.001d) must beTrue)
          }
          else {
            val size = data.size
            val sorted = data.sorted
            val median = if (size % 2 == 0) ((sorted((size / 2) - 1) + sorted(size / 2)) / 2) else sorted(size / 2)

            approxMedian.fold(
              error => emptyStreamOrNoReplacement(error) must beTrue,
              result => {
                val errorBound = data.max - data.min
                withinErrorBounds(result.d, median, errorBound) must beTrue
              })
          }
        }
    }

  "percentileApproximation" should {
    "ensure that the 1st percentile is the lowest value reported " in check {
      (data: Stream[Double]) =>
        val percentilesWanted = List(1, 25, 50, 75, 90, 95, 99)
        val approxPercentiles = StreamingStats.percentileApproximations(data, 100, percentilesWanted )
        approxPercentiles.fold(
          error => emptyStreamOrPercentileError(error) must beTrue,
          result => {
            result.map(_._2).forall(x => x >= result(percentileToIndex(1, data))) must beTrue
          }
        )
    }
    "ensure that the 99th percentile is the highest value reported " in check {
      (data: Stream[Double]) =>
        val percentilesWanted = List(1, 25, 50, 75, 90, 95, 99)
        val approxPercentiles = StreamingStats.percentileApproximations(data, 100, percentilesWanted )
        approxPercentiles.fold(
          error => emptyStreamOrPercentileError(error) must beTrue,
          result => {
            result.map(_._2).forall(x => x <= result(percentileToIndex(99, data))) must beTrue
          }
        )
    }
    "ensure that the 50th percentile is neither the highest or lowest value reported " in check {
      (data: Stream[Double]) =>
        val percentilesWanted = List(1, 25, 50, 75, 90, 95, 99)
        val approxPercentiles = StreamingStats.percentileApproximations(data, 100, percentilesWanted )
        approxPercentiles.fold(
          error => emptyStreamOrPercentileError(error) must beTrue,
          result => {
            result.map(_._2).exists(x => x <= result(percentileToIndex(50, data)))must beTrue
            result.map(_._2).exists(x => x >= result(percentileToIndex(50, data)))must beTrue

          }
        )
    }
  }

  "reservoir" should {
    "return the initial stream when the stream is smaller than the reservoir size" in check {
      (data: Stream[Double], arbReservoirSize: Int) =>
        data.size < arbReservoirSize ==> {
          val sampled = Sampling.reservoir(data, arbReservoirSize)
          sampled.fold(l => l mustEqual NoReplacement, r => r mustEqual data)
          sampled.fold(l => l mustEqual NoReplacement, r => r.size mustEqual data.size)
        }
    }

    "return a stream of the specified reservoir size when the stream is larger than the reservoir size" in check {
      (data: Stream[Double], arbReservoirSize: Int) =>
        (data.size > arbReservoirSize) ==> {
          val sampled = Sampling.reservoir(data, arbReservoirSize)
          sampled.fold(l => l mustEqual NoReplacement, r => r.size mustEqual arbReservoirSize)
        }
    }
  }
}
