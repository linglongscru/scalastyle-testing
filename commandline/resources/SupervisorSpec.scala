package com.scrutinizerci.worker

import java.nio.file.{Files, Path}

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.scrutinizerci.worker.cache.{FilesystemCache, Cache, NullCache}
import com.scrutinizerci.worker.model._
import org.apache.commons.io.FileUtils
import org.scalatest._

import scala.concurrent.duration._
import scala.io.Source

class SupervisorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfter {

  var actorCount = 0

  def this() = this(ActorSystem("SupervisorSpec"))

  override def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

  "The supervisor" should "return immediately if no vectors were generated" in {
    val supervisor = createSupervisor()

    val file = File("foo.py", "")
    supervisor ! RunAnalysis(Seq(file))

    expectMsgType[AnalysisResult].duplications.size should be (0)
  }

  it should "return immediately if no ASTs could be generated" in {
    val supervisor = createSupervisor()

    val file = File("foo.py", "asdf; dfasdfa; if foo")
    supervisor ! RunAnalysis(Seq(file))

    expectMsgType[AnalysisResult].duplications.size should be (0)
  }

  it should "update file metadata in the cache" in {
    val tmpDir = Files.createTempDirectory("supervisor")
    try {
      val supervisor = createSupervisor(cache = new FilesystemCache(tmpDir))

      val file = File(FileMetadata("foo.py", "123abc", Map("py-module" -> "foo")), "abcdef")
      supervisor ! RunAnalysis(Seq(file))

      expectMsgType[AnalysisResult].duplications.size should be (0)

      Source.fromFile(tmpDir.resolve("files.meta").toFile).mkString should be (
        "[{\"name\":\"foo.py\",\"version\":\"123abc\",\"attributes\":{\"py-module\":\"foo\"}}]"
      )
    } finally {
      FileUtils.deleteDirectory(tmpDir.toFile)
    }
  }

  private def createSupervisor(config: Configuration = new Configuration(), cache: Cache = NullCache) = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    actorCount += 1
    system.actorOf(Props(classOf[Supervisor], NullOutput, config, cache, mapper), name = "supervisor-" + actorCount)
  }
}
