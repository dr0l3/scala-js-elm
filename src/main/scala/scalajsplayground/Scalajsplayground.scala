package scalajsplayground

import cats.effect.IO
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveChildNode
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._

import scala.scalajs.js
import scala.util.Success

class InputBox(val node: Div, val inputNode: Input)

object InputBox {
  def make(caption: String): InputBox = {
    val inputNode = input(typ := "text")
    val node = div(caption, inputNode)
    new InputBox(node, inputNode)
  }
}

case class AppState(name: String, occupation: String, clicks: Int, lastResponse: Option[Post])

object AppState {
  def initial() = {
    AppState("", "", 1, None)
  }
}

case class Post(userId: Int, id: Int, title: String, body: String)

trait AppActions
case class NameUpdated(name: String) extends AppActions
case class OccupationChanged(occupation: String) extends AppActions
case object HeaderClick extends AppActions
case object FetchPost extends AppActions
case class PostResponse(res: Either[Throwable, Post]) extends AppActions

object Scalajsplayground {
  def stateUpdater(appState: AppState, actions: AppActions): (AppState, List[IO[AppActions]]) = {
    val res = actions match {
      case NameUpdated(updatedName) => (appState.copy(name = updatedName), Nil)
      case OccupationChanged(updated) => (appState.copy(occupation = updated), Nil)
      case HeaderClick => (appState.copy(clicks = appState.clicks + 1), Nil)
      case FetchPost => (appState, List(IO.fromFuture(IO(Ajax.get(s"http://jsonplaceholder.typicode.com/posts/${appState.clicks}"))).map { resp =>
        implicit val fooDecoder: Decoder[Post] = deriveDecoder[Post]

        PostResponse(decode[Post](resp.responseText))
      }))
      case PostResponse(either) =>
        (appState.copy(lastResponse = either.toOption), Nil)
    }
    println(s"($appState, $actions) -> ${res}")
    res
  }

  def main(args: Array[String]): Unit = {

    val actionBus = new EventBus[AppActions]

    val stateStream: Signal[AppState] = actionBus.events.fold(AppState.initial()){ (state, action) =>
      val (newState, effects) = stateUpdater(state, action)
      effects.foreach(_.unsafeRunAsync {
        case Left(_) => {}
        case Right(generatedAction) =>
          actionBus.writer.onNext(generatedAction)
      })
      newState
    }

    val appDiv = div(
      h1(
        onClick.mapTo(HeaderClick) --> actionBus.writer,
        "Clickme"
      ),
      h1("User Welcomer 9000"),
      div(
        "Please state your name: ",
        input(
          inContext(thisNode => onInput.mapTo(NameUpdated(thisNode.ref.value)) --> actionBus.writer),
          typ := "text"
        )
      ),
      div(
        "Please state your occupation: ",
        input(
          inContext(thisNode => onInput.mapTo(OccupationChanged(thisNode.ref.value)) --> actionBus.writer),
          typ := "text"
        )
      ),
      div(
        "Please accept our greeting: ",
        div(
          fontSize := "20px",
          color <-- stateStream.map { state =>
            if (state.name == "Rune") "red" else "black"
          },
          strong("Hello, "),
          child.text <-- stateStream.map(_.name),
        )
      ),
      div(
        child.text <-- stateStream.map { state =>
          if (state.occupation.isEmpty) "" else s"Nice to see a fellow ${state.occupation}"
        }
      ),
      div(
        child.text <-- stateStream.map(_.clicks.toString)
      ),
      h1(
        "Send stuff",
        onClick.mapTo(FetchPost) --> actionBus.writer
      ),
      div(
        child.text <-- stateStream.map { state =>
          state.lastResponse.fold("No response")(post => post.toString)
        }
      )
    )

    render(dom.document.querySelector("#app"), appDiv)
  }

}


object Stuff {
  def view[S, A](state: S, bus: WriteBus[A]): ReactiveChildNode[dom.Element] = ???
  def update[S,A](state: S, action: A): (S, List[IO[A]]) = ???
  def subs[A](subs: List[EventStream[A]]): Unit = ???
}