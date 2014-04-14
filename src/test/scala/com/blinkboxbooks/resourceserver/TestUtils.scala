package com.blinkboxbooks.resourceserver

import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

object TestUtils extends MockitoSugar {

  /**
   *  @returns an execution context that executes task synchronously,
   *  useful for testing.
   */
  def directExecutionContext = ExecutionContext.fromExecutor(new Executor {
    override def execute(task: Runnable) = task.run()
  })

}
