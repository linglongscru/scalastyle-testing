package com.scrutinizerci.worker

import java.nio.file.{Files, Paths}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.scrutinizerci.worker.churn.{Churn, ChangeTracker}
import com.scrutinizerci.worker.util.git.GitRepository

class ScrutinizerSpec extends AbstractWorkerSpec {
  val output = new StringBuilderOutput
  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  val scrutinizer = new Scrutinizer(mapper, output)

  "Scrutinizer" should "return duplicates for Python code" in {
    val config = new Configuration
    config.fileExtensions = List(".py")
    config.duplicationConfig.minMass = 1

    val project = scrutinizer.analyze(Paths.get("src/test/resources/python/similarity/SampleProject"), config)

    project.duplications.size should be (1)
  }

  it should "determine Python modules correctly" in {
    val config = new Configuration
    config.fileExtensions = List(".py")
    config.metricsEnabled = true
    config.duplicationConfig.enabled = false

    val project = scrutinizer.analyze(Paths.get("src/test/resources/python/similarity/ProjectWithModule"), config)

    project.fileResults.size should be (2)
    project.fileResults("foo/bar/baz.py").elements.map { _.identifier }.sorted should be (List("bar.Baz", "bar.Baz.foobarbaz"))
  }

  it should "analyze churn" in {
    val repoDir = Files.createTempDirectory("scrutinizer-analyze-churn-repo")
    val repo = GitRepository.init(repoDir)
    val rev = repo.beginCommit()
      .add("foo.js", "function test() { }")
      .commit("initial commit")

    val config = ChangeTracker.Configuration()

    val cacheDir = Files.createTempDirectory("scrutinizer-analyze-churn-cache")

    val rs = scrutinizer.analyzeChurn(repoDir, rev, cacheDir, config)
    rs should be (Map(
      ("js-file","foo.js") -> Churn(1,0,0),
      ("js-function","foo.js|test") -> Churn(1,0,0)
    ))
  }

}
