package client
import scalaz.zio.{IO, Promise}
import util.Semaphore

object Pool {

  def make[E, A](parallelism: Int, task: IO[E, A]): IO[E, Unit] = {

    def interruptionLoop(s: Semaphore): IO[E, Unit] = {
      (for {
        promise <- Promise.make[E, Unit]
        _       <- taskLoop(s, promise)
      } yield ()).forever.void
    }

    def taskLoop(s: Semaphore, p: Promise[E, Unit]): IO[E, Unit] =
      (s.acquire *>
        task
          .ensuring(s.release)
          .void
          .race(p.get)
          .fork0(_ => p.complete(()).void)).forever.race(p.get)

    for {
      semaphore <- Semaphore(parallelism)
      _         <- interruptionLoop(semaphore)
    } yield ()
  }

}

//    def cancellationLoop(s: Semaphore): IO[E, Unit] = {
//      (for {
//        promise <- Promise.make[E, Unit]
//        _       <- taskLoop(s, promise)
//      } yield ()).forever.void
//    }

//    def taskLoop(s: Semaphore): IO[E, Unit] =
//      (s.acquire *> task.ensuring(s.release).void.fork).forever
