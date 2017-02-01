package com.michaeltroger.flickr

import javax.swing.ImageIcon

import akka.stream.ActorMaterializer
import play.api.libs.json._
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.routing.sird._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.swing.{FlowPanel, Label}

trait UpdateImages {
  implicit val actorSystem = akka.actor.ActorSystem()
  implicit val wsClient = AhcWSClient()(ActorMaterializer()(actorSystem))

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val photoRead = Json.reads[Photo]
  implicit val photosReads = Json.reads[Photos]
  implicit val photoRootReads = Json.reads[PhotosRoot]

  var imagePanel : FlowPanel
  var queryString: Array[(String, String)]

  def getImageUrls(additionalParam: (String,String) = ("", "")): Unit = {
    val queryStringsExtended : ListBuffer[(String, String)] = queryString.to[ListBuffer]
    queryStringsExtended += additionalParam
    //imagePanel.contents.foreach{ case l : Label => l.icon = null } // optionally remove images before inserting the new
    val latestImagesListRequest: WSRequest = wsClient.url("https://api.flickr.com/services/rest/").withQueryString(queryStringsExtended: _*)
    val responseFuture: Future[WSResponse] = latestImagesListRequest.get()

    responseFuture.map {wsResponse =>
      val jsonString: JsValue = Json.parse(wsResponse.body)
      val photosRootFromJson: JsResult[PhotosRoot] = Json.fromJson[PhotosRoot](jsonString)

      var photosRoot : Option[PhotosRoot] = None
      photosRootFromJson match {
        case JsSuccess(r: PhotosRoot, path: JsPath) => photosRoot = Option(r)
        case e: JsError => println("Errors: " + JsError.toJson(e).toString())
      }

      if (photosRoot.isDefined) {
        for ((photo, i)  <- photosRoot.get.photos.photo.zipWithIndex) {
          val imageUrlWithoutFilending = "https://farm" + photo.farm + ".staticflickr.com/" + photo.server + "/" + photo.id + "_" + photo.secret
          val miniatureUrlWithoutFilending = imageUrlWithoutFilending + "_q"
          val imageUrl = imageUrlWithoutFilending + ".jpg"
          val miniatureUrl = miniatureUrlWithoutFilending + ".jpg"
          requestAndUpdateImages(imageUrl, miniatureUrl, i)
        }

      }
    }
  }

  def requestAndUpdateImages(imageUrl: String, miniatureUrl: String, index: Int) : Unit = {
    val imageRequest: WSRequest = wsClient.url(miniatureUrl)
    val imageResponseFuture: Future[WSResponse] = imageRequest.get()
    imageResponseFuture.map{ wsResponse1 =>
      val bytesString = wsResponse1.bodyAsBytes
      val img = new ImageIcon(bytesString.toArray)
      imagePanel.contents(index) match {
        case l : Label =>
          l.icon = img
          l.tooltip = imageUrl
      }
    }
  }
}

case class PhotosRoot(photos: Photos, stat: String)
case class Photos(page: Int, pages: Int, perpage: Int, photo: Array[Photo]) // "total" left out -> sometimes int sometimes string
case class Photo(id: String, owner: String, secret: String, server: String, farm: Int, title: String, ispublic: Int, isfriend: Int, isfamily: Int)
