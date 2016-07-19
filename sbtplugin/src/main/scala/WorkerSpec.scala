package com.scrutinizerci.worker

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.scrutinizerci.worker.model._
import com.scrutinizerci.worker.parser.python.ModuleNode
import com.scrutinizerci.worker.similarity.CodeVectorFactory
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{FlatSpecLike, Matchers, BeforeAndAfterAll, FlatSpec}

import scala.concurrent.duration._

class WorkerSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("WorkerSpec"))

  override def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

  "The worker" should "parse the AST of a file" in {
    val worker = system.actorOf(Props(classOf[Worker], new Configuration, new CodeVectorFactory))

    val file = File(
      "test.py",
      """
        |def foo():
        |    print("Hello World");
        |
      """.stripMargin
    )

    worker ! ParseFile(file)
    expectMsgType[FileAst].ast.isInstanceOf[ModuleNode] should be (true)
  }

  it should "return an error if the code cannot be parsed" in {
    val worker = system.actorOf(Props(classOf[Worker], new Configuration, new CodeVectorFactory))

    val file = File("test.py", "asdfgj kdkdiifjfjf;jfi")
    worker ! ParseFile(file)

    expectMsgType[ParseError]
  }

  "Element location" should "be computed for Ruby classes" in {
    val worker = createRubyWorker

    val file = File("test.rb",
      """
        |class Foo
        |
        |end
      """.stripMargin)

    worker ! ParseFile(file)
    val ast = expectMsgType[FileAst].ast

    worker ! ComputeMetrics(file.metadata, ast)

    fishForMessage(5.seconds, "File Elements") {
      case FileElements(metadata, elements) =>
        metadata.name should be ("test.rb")
        elements.size should be (1)
        elements.head.identifier should be ("Foo")
        elements.head.t should be ("rb-class")
        elements.head.location.startLine should be (1)
        elements.head.location.endLine should be (3)

        true

      case _ => false
    }
  }

  private def createRubyWorker = {
    val config = new Configuration
    config.fileExtensions = List(".rb")

    system.actorOf(Props(classOf[Worker], config, new CodeVectorFactory))
  }
}
