package client

import com.virtuslab.akkaworkshop.{Decrypter, PasswordDecoded, PasswordPrepared}
import scalaz.zio.{IO, Ref}
import util.putStrLn

object Decrypting {

  def fullDecryption(password: Password, decrypter: Decrypter, queueRef: Ref[List[Password]]): IO[Throwable, String] = {
    password match {
      case EncryptedPassword(encrypted) =>
        putStrLn(s"Preparing password: $encrypted") *>
          IO.bracket0[Throwable, Unit, PasswordPrepared](IO.unit) {
              case (_, useOutcome) =>
                useOutcome match {
                  case None =>
                    putStrLn(s"Interrupted while preparing password $encrypted") *>
                      queueRef.update(password :: _).void
                  case Some(Left(_)) =>
                    putStrLn(s"Failed during preparing password: $encrypted") *>
                      queueRef.update(password :: _).void
                  case _ => IO.unit
                }
            }(_ => preparePassword(encrypted, decrypter))
            .flatMap { prepared =>
              fullDecryption(PreparedPassword(encrypted, prepared), decrypter, queueRef)
            }

      case PreparedPassword(encrypted, prepared) =>
        putStrLn(s"Decoding password: $encrypted") *>
          IO.bracket0[Throwable, Unit, PasswordDecoded](IO.unit) {
              case (_, useOutcome) =>
                useOutcome match {
                  case None =>
                    putStrLn(s"Interrupted while decoding password $encrypted") *>
                      queueRef.update(password :: _).void
                  case Some(Left(_)) =>
                    putStrLn(s"Failed during decoding password: $encrypted") *>
                      queueRef.update(password :: _).void
                  case _ => IO.unit
                }
            }(_ => decodePassword(prepared, decrypter))
            .flatMap { decoded =>
              fullDecryption(DecodedPassword(encrypted, decoded), decrypter, queueRef)
            }

      case DecodedPassword(encrypted, decoded) =>
        putStrLn(s"Decrypting password: $encrypted") *>
          IO.bracket0[Throwable, Unit, String](IO.unit) {
            case (_, useOutcome) =>
              useOutcome match {
                case None =>
                  putStrLn(s"Interrupted while decrypting password $encrypted") *>
                    queueRef.update(password :: _).void
                case Some(Left(_)) =>
                  putStrLn(s"Failed during decrypting password: $encrypted") *>
                    queueRef.update(password :: _).void
                case _ => IO.unit
              }
          }(_ => decryptPassword(decoded, decrypter))
    }
  }

  private def preparePassword(password: String, decrypter: Decrypter): IO[Throwable, PasswordPrepared] =
    IO syncThrowable {
      decrypter prepare password
    }

  private def decodePassword(passwordPrepared: PasswordPrepared, decrypter: Decrypter): IO[Throwable, PasswordDecoded] =
    IO syncThrowable {
      decrypter decode passwordPrepared
    }

  private def decryptPassword(passwordDecoded: PasswordDecoded, decrypter: Decrypter): IO[Throwable, String] =
    IO syncThrowable {
      decrypter decrypt passwordDecoded
    }
}
