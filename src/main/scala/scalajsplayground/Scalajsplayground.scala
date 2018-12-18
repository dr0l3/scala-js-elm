package scalajsplayground

import cats.effect.IO
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveChildNode
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax

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



object V2 extends ElmApp[AppState, AppActions](dom.document.querySelector("#app"), AppState.initial()) {
  override def view(stateStream: L.Signal[AppState], bus: L.WriteBus[AppActions]): ReactiveChildNode[dom.Element] = div(
    h1(
      onClick.mapTo(HeaderClick) --> bus,
      "Clickme"
    ),
    h1("User Welcomer 9000"),
    div(
      "Please state your name: ",
      input(
        inContext(thisNode => onInput.mapTo(NameUpdated(thisNode.ref.value)) --> bus),
        typ := "text"
      )
    ),
    div(
      "Please state your occupation: ",
      input(
        inContext(thisNode => onInput.mapTo(OccupationChanged(thisNode.ref.value)) --> bus),
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
      onClick.mapTo(FetchPost) --> bus
    ),
    div(
      child.text <-- stateStream.map { state =>
        state.lastResponse.fold("No response")(post => post.toString)
      }
    )
  )

  override def update(state: AppState, action: AppActions): (AppState, List[IO[AppActions]]) = {
    action match {
      case NameUpdated(updatedName) => (state.copy(name = updatedName), Nil)
      case OccupationChanged(updated) => (state.copy(occupation = updated), Nil)
      case HeaderClick => (state.copy(clicks = state.clicks + 1), Nil)
      case FetchPost => (state, List(IO.fromFuture(IO(Ajax.get(s"http://jsonplaceholder.typicode.com/posts/${state.clicks}"))).map { resp =>
        implicit val fooDecoder: Decoder[Post] = deriveDecoder[Post]

        PostResponse(decode[Post](resp.responseText))
      }))
      case PostResponse(either) =>
        (state.copy(lastResponse = either.toOption), Nil)
    }
  }

  override def subs(subs: List[L.EventStream[AppActions]]): Unit = {

  }
}


abstract class ElmApp[S, A](container: dom.Element, initialState: S) {
  def main(args: Array[String]): Unit = {
    val actionBus = new EventBus[A]

    val stateStream = actionBus.events.fold(initialState){ (state, action) =>
      val (newState, effects) = update(state, action)
      effects.foreach(_.unsafeRunAsync {
        case Left(_) => {}
        case Right(generatedAction) =>
          actionBus.writer.onNext(generatedAction)
      })
      newState
    }

    render(container, view(stateStream, actionBus.writer))
  }


  def view(stateStream: Signal[S], bus: WriteBus[A]): ReactiveChildNode[dom.Element]
  def update(state: S, action: A): (S, List[IO[A]])
  def subs(subs: List[EventStream[A]]): Unit
}
