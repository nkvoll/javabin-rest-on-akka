package org.nkvoll.javabin.service.internal

import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.node.{ NodeBuilder, Node }
import org.elasticsearch.rest.RestController

trait ElasticsearchClientCreation extends ElasticsearchReflections {
  def createElasticsearchClient(mode: String, elasticsearchSettings: Settings): (Option[Node], Client, RestController) = {
    if (mode == "transport")
      createTransportElasticsearchClient(elasticsearchSettings)
    else
      createNodeElasticsearchClient(elasticsearchSettings)
  }

  def createNodeElasticsearchClient(elasticsearchSettings: Settings): (Option[Node], Client, RestController) = {
    val node = NodeBuilder.nodeBuilder().settings(elasticsearchSettings).build()

    // get the client for the node
    val client = node.client()

    // reflect the rest controller from the node, so we can send it internal http requests
    val restController = getRestController(node)

    (Some(node), client, restController)
  }

  def createTransportElasticsearchClient(elasticsearchSettings: Settings): (Option[Node], Client, RestController) = {
    // create the transport client
    val client = new TransportClient(elasticsearchSettings)

    elasticsearchSettings.getAsArray("transport.initial_hosts", Array.empty[String]) foreach { hostPort =>
      hostPort.split(':') match {
        case Array(host, port) =>
          client.addTransportAddress(new InetSocketTransportAddress(host, Integer.parseInt(port)))
      }
    }

    // reflect the rest controller from the node, so we can send it internal http requests
    val restController = getRestController(client)

    (None, client, restController)
  }
}
