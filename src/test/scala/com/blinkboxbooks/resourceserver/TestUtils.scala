package com.blinkboxbooks.resourceserver

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executor

object TestUtils {

  /**
   *  @returns an execution context that executes task synchronously,
   *  useful for testing.
   */
  def directExecutionContext = ExecutionContext.fromExecutor(new Executor {
    override def execute(task: Runnable) = task.run()
  })

}
