package org.nkvoll.javabin.service.internal

import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.inject.{ AbstractModule, Injector }
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.Node
import org.elasticsearch.rest.{ RestModule, RestController }
import scala.util.Try

trait ElasticsearchReflections {
  def getRestController(node: Node): RestController = {
    val injector = getInjector(node)
    getRestController(injector, node.client())
  }

  def getRestController(client: TransportClient): RestController = {
    val injector = getInjector(client)
    getRestController(injector, client)
  }

  def getRestController(injector: Injector, client: Client): RestController = {
    Try(enrichedInjector(injector, client).getInstance(classOf[RestController]))
      .recover { case t => injector.getInstance(classOf[RestController]) }
      .get
  }

  private def enrichedInjector(injector: Injector, client: Client) = injector.createChildInjector(new RestModule(ImmutableSettings.EMPTY), new AbstractModule {
    def configure() {
      bind(classOf[Client]).toInstance(client)
    }
  })

  private def getInjector(obj: Object): Injector = {
    val field = obj.getClass.getDeclaredField("injector")
    field.setAccessible(true)

    field.get(obj).asInstanceOf[Injector]
  }
}
