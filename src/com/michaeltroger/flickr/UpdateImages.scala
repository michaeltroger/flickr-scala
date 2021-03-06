package com.michaeltroger.flickr

import javax.swing.ImageIcon

import akka.stream.ActorMaterializer
import play.api.libs.json._
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.swing.{FlowPanel, Label}

trait UpdateImages {
  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] implicit val actorSystem = akka.actor.ActorSystem()
  private[this] implicit val wsClient = AhcWSClient()(ActorMaterializer()(actorSystem))

  private[this] implicit val photoRead = Json.reads[Photo]
  private[this] implicit val photosReads = Json.reads[Photos]
  private[this] implicit val photoRootReads = Json.reads[PhotosRoot]

  private[this] val FLICKR_REST_URL : String = "https://api.flickr.com/services/rest/"
  val imagePanel : FlowPanel
  val queryString : Array[(String, String)]
  val removeImagesBeforeInsertingNew : Boolean

  def loadImages(additionalParam: (String,String) = ("", "")): Unit = {
    val queryStringsExtended : ListBuffer[(String, String)] = queryString.to[ListBuffer]
    queryStringsExtended += additionalParam
    if (removeImagesBeforeInsertingNew) {
      imagePanel.contents.foreach{ case l : Label => l.icon = null }
    }
    val latestImagesListRequest: WSRequest = wsClient.url(FLICKR_REST_URL).withQueryString(queryStringsExtended: _*)
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
          requestAndDisplayImageInPanel(imageUrl, miniatureUrl, i)
        }

      }
    }
  }

  private[this] def requestAndDisplayImageInPanel(imageUrl: String, miniatureUrl: String, index: Int) : Unit = {
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
