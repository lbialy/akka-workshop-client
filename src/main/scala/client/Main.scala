package client

import client.PasswordClient._
import client.Decrypting._
import com.virtuslab.akkaworkshop.Decrypter
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import scalaz.zio._
import scalaz.zio.interop.Task
import scalaz.zio.interop.catz._
import util.putStrLn

object Main extends App {

  def run(args: List[String]): IO[Nothing, ExitStatus] =
    Http1Client[Task]()
      .bracket(_.shutdown.attempt.void) { implicit client =>
        for {
          token <- requestToken("Łukasz")(client)
          _     <- decryptForever(token)
        } yield ()
      }
      .attempt
      .map {
        case Left(_)  => ExitStatus.ExitNow(1)
        case Right(_) => ExitStatus.ExitNow(0)
      }

  def decryptForever(token: Token)(implicit httpClient: Client[Task]): IO[Nothing, Unit] =
    for {
      queueRef <- Ref(List.empty[Password])
      _        <- Pool.make(8, decryptionTask(token, queueRef)).attempt.void
    } yield ()

  def decryptionTask(token: Token, queueRef: Ref[List[Password]])(
    implicit httpClient: Client[Task]
  ): IO[Throwable, Unit] =
    (for {
      decrypter         <- getDecrypter
      password          <- passwordFromQueueOrNew(queueRef, token)
      decryptedPassword <- fullDecryption(password, decrypter, queueRef)
      status            <- validatePassword(token, password.encryptedPassword, decryptedPassword)
      _                 <- putStrLn(s"Status for password: ${password.encryptedPassword}: ${status.code}")
    } yield ()).attempt.flatMap {
      case Left(err) => putStrLn(s"Encountered error: $err") *> IO.fail(err)
      case Right(_)  => IO.unit
    }

  def passwordFromQueueOrNew(queueRef: Ref[List[Password]], token: Token)(
    implicit httpClient: Client[Task]
  ): IO[Throwable, Password] =
    queueRef.modify(queue => (queue.headOption, queue.drop(1))).flatMap {
      case Some(failedPassword) => putStrLn(s"Restarting password $failedPassword") *> IO.point(failedPassword)
      case None                 => putStrLn("Fetching new password from server") *> getPassword(token)
    }

  def getDecrypter: IO[Nothing, Decrypter] = IO.point(new Decrypter)
}
