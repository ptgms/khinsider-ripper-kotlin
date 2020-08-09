package xyz.ptgms.khinsiderripper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import xyz.ptgms.khinsiderripper.downloadClass.batchDownloadLinkGetter
import xyz.ptgms.khinsiderripper.downloadClass.downloadTrack
import xyz.ptgms.khinsiderripper.downloadClass.getRedirectUrl
import xyz.ptgms.khinsiderripper.progressBar.printProgress
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess


object KhinRip {
    data class ReturnSearch(var textResults: ArrayList<String>, var linkResults: ArrayList<String>)
    data class ReturnTracks(var textResults: ArrayList<String>, var linkResults: ArrayList<String>, var available: String, var img: String)

    private const val baseUrl = "https://downloads.khinsider.com/"
    private const val baseSearchUrl = "search?search="
    private const val baseSoundtrackAlbumUrl = "game-soundtracks/album/"

    private fun getTrackList(albumName: String): ReturnTracks {
        val imgSrc = ""
        var flac = false
        var mp3 = false
        var ogg = false

        var tracklisturl = ArrayList<String>()
        var tracklist = ArrayList<String>()

        val tags: ArrayList<String> = ArrayList()

        val titlelength = ArrayList<String>()

        //albumName.text = albumNameInt
        try {
            val url = baseUrl + baseSoundtrackAlbumUrl + albumName
            val document: Document = Jsoup.connect(url).get()

            val img: Element = document.select("img").first()

            var imgSrc = img.absUrl("src")

            if (imgSrc.endsWith("/album_views.php")) {
                imgSrc = ""
            }

            val echo: Element = document.getElementById("songlist")
            for (row in echo.select("tbody")) {
                for (col in row.select("tr")) {
                    for (colPre in col.select("tr")) {
                        if (colPre.id() == "songlist_header" || colPre.id() == "songlist_footer") {
                            for (tag in colPre.select("th")) {
                                tags.add(tag.text())
                            }
                            if (tags.contains("FLAC")) {
                                flac = tags.contains("FLAC")
                            }
                            if (tags.contains("MP3")){
                                mp3 = true
                            }
                            if (tags.contains("OGG")){
                                ogg = true
                            }
                        }
                        val temptag = ArrayList<String>()
                        val songname = tags.indexOf("Song Name")
                        for (titlename in colPre.select("td")) {
                            temptag.add(titlename.text())
                            val titleurl = titlename.select("a").attr("href")
                            if (titleurl != "" && !tracklisturl.contains(titleurl)) {
                                tracklisturl.add(titleurl)
                            }
                            if (temptag.size == tags.size + 1) {
                                titlelength.add(temptag[songname + 1])
                                tracklist.add(temptag[songname])
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            println("An error occured while trying to retrieve this albums information! Does it even exist?\n" +
                    "Are you using the properly formatted album name from --search? Error: ${e.localizedMessage}")
            exitProcess(-1)
        }

        var avtext = "Available Formats: "
        if (mp3) {
            avtext += "MP3 "
        }
        if (flac) {
            avtext += "FLAC "
        }
        if (ogg) {
            avtext += "OGG "
        }
        return ReturnTracks(tracklist, tracklisturl, avtext, imgSrc)
    }


    private fun getSearch(searchterm: String): ReturnSearch {
        var linkArray = ArrayList<String>()
        var textArray = ArrayList<String>()
        val encodedSearch = java.net.URLEncoder.encode(searchterm, "utf-8")
        val url = baseUrl + baseSearchUrl + encodedSearch
        println("Read url: $url")
        val document: Document = Jsoup.connect(url).get()
        val echo: Element = document.getElementById("EchoTopic")
        for (row in echo.select("p")) {
            for (col in row.select("a")) {
                val colContent = col.text()
                val colHref = col.attr("href")
                if (colHref.contains("game-soundtracks/browse/") or colHref.contains("/forums/")) {
                    continue
                }
                if (colContent == "Windows" && colHref == "/game-soundtracks/windows") {
                    val getRedir = getRedirectUrl(url)
                    getRedir?.replace(baseUrl + baseSoundtrackAlbumUrl, "")?.replace("-", " ")?.let { textArray.add(it) }
                    getRedir?.let { linkArray.add(it) }
                    continue
                }

                textArray.add(colContent)
                linkArray.add(colHref)
            }
        }
        return ReturnSearch(textArray, linkArray)
    }

    private fun trackViewWrapper(albumName: String) {
        val (textArray, linkArray, available, imgLink) = getTrackList(albumName)
        if (imgLink != "") {
            println("Album Cover URL: $imgLink")
        }
        println(available)
        println("Number of tracks in this Album: " + textArray.size)
        for (position in 0 until textArray.size) {
            println(textArray[position] + " | " + linkArray[position])
        }
    }

    private fun searchWrapper(searchterm: String) {
        val (textArray, linkArray) = getSearch(searchterm)

        for (position in 0 until textArray.size) {
            println(textArray[position] + " | " + linkArray[position].replace(baseUrl + baseSoundtrackAlbumUrl, ""))
        }
    }

    private fun getDownloadWrapper(albumName: String, type: String = "mp3") {
        val (textArray, linkArray, _, _) = getTrackList(albumName)
        println("Please wait... gathering links...")
        val downloadLinks = batchDownloadLinkGetter(linkArray, type)
        if (downloadLinks.size != linkArray.size) {
            println("An error occurred while trying to fetch download links. Maybe your specified type doesn't exist?")
            return
        }
        var txtStore = downloadLinks.joinToString("\n")
        val outputStream = FileOutputStream(albumName + ".txt")
        val strToBytes: ByteArray = txtStore.toByteArray(Charsets.UTF_8)
        outputStream.write(strToBytes)
        outputStream.close()
        for (i in 0 until downloadLinks.size) {
            println(downloadLinks[i] + " | " + textArray[i])
        }
    }

    private fun doDownloadWrapper(albumName: String, type: String = "mp3") {
        val file = File(albumName)
        file.mkdir()
        val (textArray, linkArray, _, _) = getTrackList(albumName)
        println("Please wait... gathering links...")
        val downloadLinks = batchDownloadLinkGetter(linkArray, type)
        if (downloadLinks.size != linkArray.size) {
            println("An error occurred while trying to fetch download links.")
            return
        }
        val startTime = System.currentTimeMillis()
        println("\nDownloading...")
        for (i in 0 until downloadLinks.size) {
            printProgress(startTime, downloadLinks.size.toLong(), (i + 1).toLong())
            downloadTrack(downloadLinks[i], textArray[i], albumName, type)
        }

    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Help for Khinsider-Ripper CLI:\n" +
                    "-h/--help    | Shows this help page\n" +
                    "-s/--search  | Search for an album to retrieve album URL for Track getter\n" +
                    "-t/--tracks  | Return the tracks for an Album. Requires Format from search.\n" +
                    "-g/--get     | Get a list of direct download links for an Album. Requires formatted name.\n" +
                    "-d/--download| Downloads an entire album. Requires formatted name.")
            exitProcess(0)
        }

        when (args[0]) {
            "-s", "--search" -> {
                var toSearch = ""
                for (i in args.indices) {
                    if (i == 0) {
                        continue
                    } else {
                        toSearch += args[i] + " "
                    }
                }
                searchWrapper(toSearch)
            }
            "-t", "--tracks" -> {
                trackViewWrapper(args[1])
            }
            "-g", "--get" -> {
                var type = "mp3"
                if (args.size >= 3) {
                    when (args[2]) { "mp3", "flac", "ogg" -> type = args[2] }
                }
                getDownloadWrapper(args[1], type)
            }
            "-d", "--download" -> {
                var type = "mp3"
                if (args.size >= 3) {
                    when (args[2]) { "mp3", "flac", "ogg" -> type = args[2] }
                }
                doDownloadWrapper(args[1], type)
            }
            "-h", "--help" -> {
                println("Help for Khinsider-Ripper CLI:\n" +
                        "-h/--help    | Shows this help page\n" +
                        "-s/--search  | Search for an album to retrieve album URL for Track getter\n" +
                        "-t/--tracks  | Return the tracks for an Album. Requires Format from search.\n" +
                        "-g/--get     | Get a list of direct download links for an Album. Requires formatted name.\n" +
                        "-d/--download| Downloads an entire album. Requires formatted name.")
            }
        }
    }
}