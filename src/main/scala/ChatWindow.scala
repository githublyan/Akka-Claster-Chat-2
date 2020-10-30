import Main.{name, sessionManager}
import javafx.collections.FXCollections
import javafx.scene.control.TextArea
import javafx.beans.value.ObservableValue
import javafx.beans.value.ChangeListener
import javafx.application.Application
import javafx.scene.{control, text}
import javafx.stage.Stage
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.ListView
import javafx.scene.image.Image

class ChatWindow extends Application {

  override def start(primaryStage: Stage): Unit = {
    import ChatWindow._

    import javafx.geometry.Pos
    import javafx.scene.Scene
    import javafx.scene.layout.GridPane

    val grid = new GridPane
    grid.setAlignment(Pos.CENTER)
    grid.setHgap(10)
    grid.setVgap(10)
    grid.setPadding(new Insets(25, 25, 25, 25))

    import javafx.scene.text.Font
    import javafx.scene.text.FontWeight
    val scenetitle = new text.Text("Welcome, " + name)
    scenetitle.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20))
    grid.add(scenetitle, 0, 0, 2, 1)

    // получаем модель выбора элементов
    val usersSelectionModel = ChatWindow.usersListView.getSelectionModel
    // устанавливаем слушатель для отслеживания изменений
    usersSelectionModel.selectedItemProperty.addListener(new ChangeListener[String]() {
      override def changed(changed: ObservableValue[_ <: String], oldValue: String, newValue: String) = {
        userValue(0) = newValue
      }
    })
    grid.add(usersListView, 0, 3)

    textArea.setPrefColumnCount(25)
    textArea.setPrefRowCount(5)
    grid.add(textArea, 1, 3)

    grid.add(msgTextField, 0, 4)

    // кнопка Send с обработчиком
    val btn = new Button()
    btn.setText("Send")
    btn.setOnAction(new EventHandler[ActionEvent]() {
      override def handle(event: ActionEvent): Unit = {
        if (userValue(0) == name) {
          sessionManager ! SelfPrivateChatMsg(name, name, msgTextField.getText)
        }
        if (userValue(0) != name && userValue(0) != "Common room") {
          sessionManager ! PrivateChatMsg(name, userValue(0), msgTextField.getText)
          sessionManager ! SelfPrivateChatMsg(name, userValue(0), msgTextField.getText)
        }
        if (userValue(0) == "Common room") {
          sessionManager ! CommonChatMsg(name, msgTextField.getText)
        }
      }
    })
    grid.add(btn, 0, 5)

    primaryStage.getIcons.add(new Image("icon.png"))
    primaryStage.setTitle("Chat")

    val scene = new Scene(grid, 740, 510)
    primaryStage.setScene(scene)
    primaryStage.show()
  }
}

object ChatWindow {
  val userValue = Array(name)
  val users = FXCollections.observableArrayList("Common room", name)
  val usersListView = new ListView[String](users)

  val textArea = new TextArea()

  val msgTextField = new control.TextField()
}