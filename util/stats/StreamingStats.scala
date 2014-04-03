package util.stats

import java.lang.Math._
import scala.util.Random
import scalaz.Show
import scalaz._
import scalaz.syntax.either._
import scalaz.syntax.std.stream
import util.stats.StatsErrors._

object StreamingStats   {

  def streamingMean(data: Stream[Double]): Error \/ Mean = {
    def go(data: Stream[Double], mean: Double, count: Double): Mean = {
      if (data.isEmpty) Mean(mean)
      else {
        val newMean = ((mean * count) + data.head) / (count + 1)
        go(data.tail, newMean, count + 1)
      }
    }
    if (data.isEmpty) EmptyStream.left
    else go(data, 0, 0).right
  }

  def streamingVariance(data: Stream[Double]): Error \/ Variance = {
    def go(data: Stream[Double], sum: Double, sumSquares: Double, count: Double): Variance = {
      if (data.isEmpty) Variance((sumSquares - (sum * sum) / count) / (count - 1))
      else {
        val newSum = sum + data.head
        val newSumSquares = sumSquares + (data.head * data.head)
        go(data.tail, newSum, newSumSquares, count + 1)
      }
    }
    if (data.size < 2) SingleElementStream.left
    else go(data, 0, 0, 0).right
  }

  def streamingStandardDeviation(data: Stream[Double]): Error \/ StandardDeviation = {
    def go(data: Stream[Double], sum: Double, sumSquares: Double, count: Double): StandardDeviation = {
      if (data.isEmpty) StandardDeviation(sqrt((sumSquares - (sum * sum) / count) / (count - 1)))
      else {
        val newSum = sum + data.head
        val newSumSquares = sumSquares + (data.head * data.head)
        go(data.tail, newSum, newSumSquares, count + 1)
      }
    }
    if (data.size < 2) SingleElementStream.left
    else go(data, 0, 0, 0).right
  }

  def medianApproximation(data: Stream[Double], chunks: Int, sampleSize: Int): Error \/ Median = {
    if (data.isEmpty) EmptyStream.left
    else {
      val sample = Sampling.reservoir(data, sampleSize)
      sample.fold(
        _ => NoReplacement.left,
        r => {
          val chunkSize = (r.size / chunks) + 1
          val groups = r.grouped(chunkSize).toStream
          val mediansOfChunks = groups.map(_.sorted).map(stream => {
            if (stream.size % 2 == 0) ((stream((stream.size / 2) - 1) + stream(stream.size / 2)) / 2) else stream(stream.size / 2)
          })
          val sortedChunks = mediansOfChunks.sorted
          if (sortedChunks.size % 2 == 0) Median((sortedChunks((sortedChunks.size / 2) - 1) + sortedChunks(sortedChunks.size / 2)) / 2).right
          else Median(sortedChunks(sortedChunks.size / 2)).right
        }
      )
    }
  }

  def percentileApproximations(data: Stream[Double], sampleSize: Int, percentiles: List[Int]): Error \/ Map[Int, Double] = {
    if (data.isEmpty) EmptyStream.left
     // else if (data.size < percentiles.max || data.size < (100 -percentiles.min) ) PercentileError.left
      else if(data.size < 10) PercentileError.left
    else {
      val sample = Sampling.reservoir(data, sampleSize)
      sample.fold(
        _ => NoReplacement.left,
        r => {
          val sorted = r.sorted
          val indices = percentiles.map( x => (x / 100d) * (sorted.size)).map(_.floor.toInt)//.map(y => y -1)
          val result = for {
            index <- indices
          } yield (index -> sorted(index))
          Map(result: _*).right
        }
      )
    }
  }

  def percentileToIndex(percentile: Int, data: Stream[_]):Int = ((percentile / 100d) * (data.size)).floor.toInt

  def medianAbsoluteDeviation(data: Stream[Double], chunks: Int, sampleSize: Int): Error \/ MedianAbsoluteDeviation = {
    if (data.isEmpty) EmptyStream.left
    else {
      val approxMedian = medianApproximation(data, chunks, sampleSize)
      approxMedian.fold(
      error => MedianAbsoluteDeviationError.left, // make new error probably
      median => {
        val sample = Sampling.reservoir(data, sampleSize)
        sample.fold(
          error => NoReplacement.left,
          result => {
            val absoluteDeviation = for {
              x <- result
            } yield (x - median.d).abs

            val size = absoluteDeviation.size
            val sorted = absoluteDeviation.sorted
            if (size % 2 == 0) MedianAbsoluteDeviation((sorted((size / 2) - 1) + sorted(size / 2)) / 2).right else MedianAbsoluteDeviation(sorted(size / 2)).right
          })
      })
    }


  }
}

object Sampling {

  def reservoir(data: Stream[Double], size: Int): Error \/ Stream[Double] = {
    val rng = new Random()

    def go(data: Stream[Double], reservoir: Stream[Double], counter: Int): Error \/ Stream[Double] = {
      if ( data.isEmpty ) reservoir.right
      else {
        val inclusionProbability: Double = size.toDouble / (size.toDouble + counter.toDouble)
        val randomNumber = rng.nextDouble
        if ( randomNumber > inclusionProbability ) go(data.tail, reservoir, counter + 1)
        else {
          randomReplacementInsert(data.head, reservoir).fold(_ => NoReplacement.left, r => go(data.tail, r, counter + 1))
        }
      }
    }

    def randomReplacementInsert(element: Double, stream: Stream[Double]): Error \/ Stream[Double] = {
      if (stream.isEmpty ) NoReplacement.left
      else if ( stream.size == 1) Stream(element).right
      else {
        val index = rng.nextInt(stream.size - 1)
        (stream.take(index) ++ Stream(element) ++ stream.takeRight(stream.size - (index + 1))).right
      }
    }

    if ( data.size <= size ) data.right // should this be an error instead?
    else go(data.drop(size), data.take(size), 1)
  }
}

case class Mean(d: Double) extends AnyVal
case class Median(d: Double) extends AnyVal
case class Variance(d: Double) extends AnyVal
case class StandardDeviation(d: Double) extends AnyVal
case class MedianAbsoluteDeviation(d: Double) extends AnyVal